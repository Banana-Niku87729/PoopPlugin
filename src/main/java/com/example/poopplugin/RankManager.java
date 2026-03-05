package com.example.poopplugin;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Ko-Fi APIを利用したランク購入・付与管理クラス
 *
 * ランクID対応表:
 *   484fde → Plus
 *   594fde → Ultimate
 *
 * 購入フロー:
 *   1. プレイヤーが /rank_buy を実行
 *      → MC_<ユーザー名>@rec877.com を生成・表示（15分有効）
 *   2. プレイヤーが ko-fi.com で購入し、メールアドレスに上記を入力
 *   3. Ko-Fi Webhook（または定期ポーリング）でメールアドレス・商品IDを照合
 *   4. 一致したプレイヤーにランクタグを付与
 */
public class RankManager {

    // ── ランクID → ランク名 の対応表 ──────────────────────────
    private static final Map<String, String> RANK_PRODUCTS = new LinkedHashMap<>();
    static {
        RANK_PRODUCTS.put("7a9b6f9297", "VIP");
        RANK_PRODUCTS.put("bca6e9c816", "Ultimate");
    }

    // メールアドレスのドメイン部分
    private static final String EMAIL_DOMAIN = "rec877.com";
    // 購入用メールの有効期限（15分 = 900秒）
    private static final long EXPIRY_TICKS = 20L * 60 * 15; // 18000 ticks

    // プレイヤー名 → 購入セッション情報
    // キー: MC ユーザー名（小文字統一）
    private final Map<String, PurchaseSession> sessions = new ConcurrentHashMap<>();

    // Ko-Fi APIトークン（config.yml から読み込む）
    private final String kofiToken;

    private final JavaPlugin plugin;
    private final Logger logger;

    // Ko-Fi Webhook から受け取った処理済み取引IDセット（重複防止）
    private final Set<String> processedTransactions = Collections.synchronizedSet(new HashSet<>());

    public RankManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        // config.yml の kofi-token キーから読む
        this.kofiToken = plugin.getConfig().getString("kofi-token", "");
        if (kofiToken.isEmpty()) {
            logger.warning("[RankManager] config.yml に kofi-token が設定されていません。Ko-Fi API連携が無効です。");
        }
    }

    // ─────────────────────────────────────────────
    //  セッション管理
    // ─────────────────────────────────────────────

    /**
     * プレイヤー用の購入セッションを作成（または更新）し、
     * 生成したメールアドレスを返す。
     */
    public String createOrRefreshSession(Player player) {
        String name = player.getName();
        String lowerName = name.toLowerCase();

        // 既存セッションがあればキャンセルして作り直す
        cancelSession(lowerName);

        PurchaseSession session = new PurchaseSession(name);
        sessions.put(lowerName, session);

        // 15分後に自動破棄
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sessions.remove(lowerName);
            logger.info("[RankManager] セッション期限切れ: " + name);
        }, EXPIRY_TICKS).getTaskId();
        session.expireTaskId = taskId;

        logger.info("[RankManager] セッション作成: " + name + " → " + session.getEmail());
        return session.getEmail();
    }

    /**
     * /buyname_change で購入用ユーザー名を変更する。
     * 既存セッションがあれば古い名前を破棄して新しい名前で再作成。
     * セッションが存在しない場合は新規作成する。
     * @return 新しいメールアドレス
     */
    public String changeSessionName(Player player, String newUsername) {
        String lowerNew = newUsername.toLowerCase();

        // 既存セッション（旧名）があれば削除
        cancelSession(player.getName().toLowerCase());

        PurchaseSession session = new PurchaseSession(newUsername);
        sessions.put(lowerNew, session);

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sessions.remove(lowerNew);
            logger.info("[RankManager] セッション期限切れ (nameChange): " + newUsername);
        }, EXPIRY_TICKS).getTaskId();
        session.expireTaskId = taskId;

        // 後で付与先プレイヤーを特定できるようプレイヤーUUIDを持たせる
        session.playerUUID = player.getUniqueId();

        logger.info("[RankManager] セッション名変更: " + player.getName() + " → " + newUsername);
        return session.getEmail();
    }

    /** セッションをキャンセル（タスクも停止） */
    private void cancelSession(String lowerName) {
        PurchaseSession old = sessions.remove(lowerName);
        if (old != null && old.expireTaskId != -1) {
            Bukkit.getScheduler().cancelTask(old.expireTaskId);
        }
    }

    /**
     * プレイヤーに対してアクティブなセッションのメールアドレスを返す。
     * セッションが存在しない場合は null。
     */
    public String getActiveEmail(Player player) {
        PurchaseSession s = sessions.get(player.getName().toLowerCase());
        return s == null ? null : s.getEmail();
    }

    // ─────────────────────────────────────────────
    //  Ko-Fi Webhook 処理
    // ─────────────────────────────────────────────

    /**
     * Ko-Fi Webhook から受け取った JSON ペイロードを処理する。
     * Webhook の data フィールドは URL エンコードされた JSON 文字列。
     *
     * 確認する項目:
     *   - kofi_transaction_id: 重複チェック
     *   - email: セッションのメールアドレスと照合
     *   - shop_items[].variation_name: 商品IDと照合
     *
     * ※ Ko-Fi の Shop Webhook JSON 構造に準拠
     */
    public void processWebhookPayload(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // --- 簡易 JSON パーサ（依存ライブラリなし） ---
                String transactionId = extractJsonString(jsonPayload, "kofi_transaction_id");
                String email         = extractJsonString(jsonPayload, "email");
                String type          = extractJsonString(jsonPayload, "type");

                if (transactionId == null || email == null) {
                    logger.warning("[RankManager] Webhook: 必須フィールドが不足しています。payload=" + jsonPayload);
                    return;
                }

                // ショップ購入以外は無視（Donation / Subscription も受け取る可能性）
                if (!"Shop Order".equalsIgnoreCase(type)) {
                    logger.info("[RankManager] Webhook: type=" + type + " は無視します。");
                    return;
                }

                // 重複処理防止
                if (processedTransactions.contains(transactionId)) {
                    logger.info("[RankManager] Webhook: 処理済みトランザクション: " + transactionId);
                    return;
                }
                processedTransactions.add(transactionId);

                // メールアドレスを小文字に正規化
                String normalizedEmail = email.trim().toLowerCase();

                // shop_items から variation_name を取り出す
                List<String> variationNames = extractShopItemVariations(jsonPayload);

                // セッションと照合
                matchAndGrantRank(normalizedEmail, variationNames, transactionId);

            } catch (Exception e) {
                logger.severe("[RankManager] Webhook処理中にエラーが発生しました: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * メールアドレスとセッションを照合し、ランクを付与する。
     */
    private void matchAndGrantRank(String normalizedEmail, List<String> variationNames, String transactionId) {
        // アクティブセッションをすべて検索
        for (Map.Entry<String, PurchaseSession> entry : sessions.entrySet()) {
            PurchaseSession session = entry.getValue();
            if (!session.getEmail().toLowerCase().equals(normalizedEmail)) continue;

            // メールアドレスが一致 → 商品IDを照合
            String matchedRank = null;
            for (String variation : variationNames) {
                String rankName = RANK_PRODUCTS.get(variation.toLowerCase());
                if (rankName != null) {
                    matchedRank = rankName;
                    break;
                }
            }

            if (matchedRank == null) {
                logger.warning("[RankManager] メールアドレスは一致しましたが、商品IDが不明です。variations=" + variationNames);
                return;
            }

            final String rankToGrant = matchedRank;
            final UUID targetUUID = session.playerUUID;
            final String sessionKey = entry.getKey();

            // セッション破棄
            cancelSession(sessionKey);

            // メインスレッドでランク付与
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player target = (targetUUID != null)
                        ? Bukkit.getPlayer(targetUUID)
                        : Bukkit.getPlayer(entry.getKey());

                if (target != null && target.isOnline()) {
                    grantRankTag(target, rankToGrant);
                    target.sendMessage("§a§l■ランク購入ありがとうございます！");
                    target.sendMessage("§e" + rankToGrant + " §aランクが付与されました！");
                    target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    logger.info("[RankManager] ランク付与: " + target.getName() + " → " + rankToGrant + " (tx=" + transactionId + ")");
                } else {
                    // オフラインの場合はデータファイルに記録（次回ログイン時に付与）
                    savePendingRank(sessionKey, rankToGrant, targetUUID);
                    logger.info("[RankManager] プレイヤーがオフラインのため保留: key=" + sessionKey + " rank=" + rankToGrant);
                }
            });
            return;
        }

        logger.warning("[RankManager] 一致するセッションが見つかりませんでした。email=" + normalizedEmail);
    }

    /**
     * プレイヤーにランクタグを付与する。
     * LuckPerms が存在する場合はコマンドで付与、存在しない場合は scoreboard タグを使用。
     */
    private void grantRankTag(Player player, String rankName) {
        String tag = "rank_" + rankName.toLowerCase(); // 例: rank_plus, rank_ultimate

        // LuckPerms 経由でグループ付与を試みる
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + player.getName() + " group set " + tag);
            logger.info("[RankManager] LuckPermsでグループ付与: " + player.getName() + " → " + tag);
        } else {
            // フォールバック: スコアボードタグ
            player.addScoreboardTag(tag);
            logger.info("[RankManager] スコアボードタグ付与: " + player.getName() + " → " + tag);
        }
    }

    // ─────────────────────────────────────────────
    //  保留ランク（オフラインプレイヤー向け）
    // ─────────────────────────────────────────────

    private void savePendingRank(String playerName, String rankName, UUID uuid) {
        plugin.getConfig().set("pending-ranks." + playerName + ".rank", rankName);
        if (uuid != null) {
            plugin.getConfig().set("pending-ranks." + playerName + ".uuid", uuid.toString());
        }
        plugin.saveConfig();
    }

    /**
     * プレイヤーがログインした際に呼び出し、保留ランクを付与する。
     */
    public void applyPendingRank(Player player) {
        String key = "pending-ranks." + player.getName();
        String rank = plugin.getConfig().getString(key + ".rank");
        if (rank == null) {
            // UUID でも検索
            Set<String> pendingKeys = plugin.getConfig().getConfigurationSection("pending-ranks") != null
                    ? plugin.getConfig().getConfigurationSection("pending-ranks").getKeys(false)
                    : new HashSet<>();
            for (String name : pendingKeys) {
                String uuidStr = plugin.getConfig().getString("pending-ranks." + name + ".uuid");
                if (uuidStr != null && player.getUniqueId().toString().equals(uuidStr)) {
                    rank = plugin.getConfig().getString("pending-ranks." + name + ".rank");
                    plugin.getConfig().set("pending-ranks." + name, null);
                    plugin.saveConfig();
                    if (rank != null) {
                        grantRankTag(player, rank);
                        player.sendMessage("§a§l■保留されていたランクが付与されました: §e" + rank);
                    }
                    return;
                }
            }
            return;
        }
        plugin.getConfig().set(key, null);
        plugin.saveConfig();
        grantRankTag(player, rank);
        player.sendMessage("§a§l■保留されていたランクが付与されました: §e" + rank);
    }

    // ─────────────────────────────────────────────
    //  簡易 JSON パーサ ユーティリティ
    // ─────────────────────────────────────────────

    /** JSON文字列から指定キーの文字列値を取り出す（ネストなし）。 */
    static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon == -1) return null;
        int start = json.indexOf('"', colon + 1);
        if (start == -1) return null;
        int end = json.indexOf('"', start + 1);
        if (end == -1) return null;
        return json.substring(start + 1, end);
    }

    /**
     * Ko-Fi Webhook JSON の shop_items 配列から variation_name の値リストを取り出す。
     * 例: "shop_items":[{"variation_name":"484fde", ...}]
     */
/**
     * Ko-Fi Webhook JSON の shop_items 配列から direct_link_code の値リストを取り出す。
     */
    static List<String> extractShopItemVariations(String json) {
        List<String> result = new ArrayList<>();
        String shopKey = "\"shop_items\"";
        int shopIdx = json.indexOf(shopKey);
        if (shopIdx == -1) return result;

        // 配列の開始 '[' を探す
        int arrStart = json.indexOf('[', shopIdx);
        if (arrStart == -1) return result;
        int arrEnd = json.indexOf(']', arrStart);
        if (arrEnd == -1) return result;

        String itemsBlock = json.substring(arrStart, arrEnd + 1);
        
        // 【修正】抽出するキーを "variation_name" から "direct_link_code" に変更
        String varKey = "\"direct_link_code\"";
        int pos = 0;
        while (true) {
            int vi = itemsBlock.indexOf(varKey, pos);
            if (vi == -1) break;
            int colon = itemsBlock.indexOf(':', vi + varKey.length());
            if (colon == -1) break;
            int qs = itemsBlock.indexOf('"', colon + 1);
            if (qs == -1) break;
            int qe = itemsBlock.indexOf('"', qs + 1);
            if (qe == -1) break;
            result.add(itemsBlock.substring(qs + 1, qe));
            pos = qe + 1;
        }
        return result;
    }

    /** ランクID対応表を返す（読み取り専用） */
    public static Map<String, String> getRankProducts() {
        return Collections.unmodifiableMap(RANK_PRODUCTS);
    }

    // ─────────────────────────────────────────────
    //  内部クラス: 購入セッション
    // ─────────────────────────────────────────────

    static class PurchaseSession {
        /** 購入用に使用するユーザー名（MC_<name>@rec877.com のname部分） */
        final String purchaseUsername;
        /** スケジューラのタスクID */
        int expireTaskId = -1;
        /** 付与先プレイヤーのUUID（buyname_change で設定） */
        UUID playerUUID;

        PurchaseSession(String purchaseUsername) {
            this.purchaseUsername = purchaseUsername;
        }

        String getEmail() {
            return "MC_" + purchaseUsername + "@rec877.com";
        }
    }
}