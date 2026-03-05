package com.example.poopplugin;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /own システム全体のデータ・ロジック管理クラス
 * ワールドのCRUD、スロット管理、ランク制限、AntiAFK等を担う
 */
public class WorldManager {

    // ─── ランク別制限定義 ───────────────────────────────────────
    public enum RankTier { DIARRHEA, STOOL, POOP }

    public static class TierConfig {
        public final int slots;
        public final long maxSizeBytes;        // バイト単位
        public final int maxConcurrent;        // 同接上限
        public final int maxJoin;              // 参加上限 (-1=無制限)
        public final boolean allowCreative;
        public final boolean allowCommands;
        public final long sleepDelayMs;        // 起動遅延ミリ秒 (0=即時)

        public TierConfig(int slots, long maxSizeBytes, int maxConcurrent, int maxJoin,
                          boolean allowCreative, boolean allowCommands, long sleepDelayMs) {
            this.slots          = slots;
            this.maxSizeBytes   = maxSizeBytes;
            this.maxConcurrent  = maxConcurrent;
            this.maxJoin        = maxJoin;
            this.allowCreative  = allowCreative;
            this.allowCommands  = allowCommands;
            this.sleepDelayMs   = sleepDelayMs;
        }
    }

    public static final Map<RankTier, TierConfig> TIER_CONFIGS = new EnumMap<>(RankTier.class);
    static {
        // Diarrhea (無料): 1スロット / 250MB / 同接3 / 上限5 / サバイバルのみ / コマンド不可 / 起動3分
        TIER_CONFIGS.put(RankTier.DIARRHEA, new TierConfig(
                1, 250L * 1024 * 1024, 3, 5, false, false, 3 * 60 * 1000L));
        // Stool (VIP): 3スロット / 3GB / 同接50 / 上限300 / 全モード / コマンド可 / 起動10秒
        TIER_CONFIGS.put(RankTier.STOOL, new TierConfig(
                3, 3L * 1024 * 1024 * 1024, 50, 300, true, true, 10 * 1000L));
        // POOP (Ultimate): 5スロット / 10GB / 同接1000 / 無制限 / 全モード / コマンド可 / 即時
        TIER_CONFIGS.put(RankTier.POOP, new TierConfig(
                5, 10L * 1024 * 1024 * 1024, 1000, -1, true, true, 0L));
    }

    // 予約禁止ワールドコード
    private static final Set<String> RESERVED_CODES =
            new HashSet<>(Arrays.asList("hub", "lobby", "survival", "creative"));

    // ─── データ ────────────────────────────────────────────────
    // key: worldCode (小文字) → OwnWorld
    private final Map<String, OwnWorld> worldsByCode = new ConcurrentHashMap<>();
    // key: worldName (小文字) → OwnWorld (高速検索用)
    private final Map<String, OwnWorld> worldsByName = new ConcurrentHashMap<>();
    // key: ownerUUID → List<worldCode>
    private final Map<UUID, List<String>> ownerWorlds = new ConcurrentHashMap<>();

    // スリープ中ワールド: worldCode → true
    private final Set<String> sleepingWorlds = Collections.synchronizedSet(new HashSet<>());
    // 起動待ちキュー: worldCode → List<pending player UUID>
    private final Map<String, List<UUID>> wakeQueue = new ConcurrentHashMap<>();

    // AntiAFKチェック用: playerUUID → last move location hash + timestamp
    private final Map<UUID, Long> lastMoveTime = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastMoveLocation = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    // ─── 定数 ──────────────────────────────────────────────────
    private static final long AFK_CHECK_INTERVAL_TICKS = 20L * 60; // 1分毎チェック
    private static final long AFK_THRESHOLD_MS         = 5L * 60 * 1000; // 5分動かなければAFK

    public WorldManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadData();
        startSleepChecker();
        startAfkChecker();
    }

    // ─── データ永続化 ──────────────────────────────────────────

    private File getDataFile() {
        return new File(plugin.getDataFolder(), "own_worlds.yml");
    }

    public void loadData() {
        dataFile = getDataFile();
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection sec = dataConfig.getConfigurationSection("worlds");
        if (sec == null) return;

        for (String code : sec.getKeys(false)) {
            ConfigurationSection ws = sec.getConfigurationSection(code);
            if (ws == null) continue;

            OwnWorld ow = new OwnWorld();
            ow.code          = code;
            ow.worldName     = ws.getString("worldName", "");
            ow.ownerUUID     = UUID.fromString(ws.getString("ownerUUID", UUID.randomUUID().toString()));
            ow.ownerName     = ws.getString("ownerName", "");
            ow.defaultGameMode = parseGameMode(ws.getString("defaultGameMode", "SURVIVAL"));
            ow.isLoaded      = false; // 再起動時はアンロード扱い
            ow.sleeping      = true;
            ow.worldType     = ws.getString("worldType", "normal");
            ow.hasCustomCode = ws.getBoolean("hasCustomCode", false);
            ow.autoCode      = ws.getString("autoCode", null);

            // メンバー権限
            ConfigurationSection permSec = ws.getConfigurationSection("permissions");
            if (permSec != null) {
                for (String uuidStr : permSec.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        String perm = permSec.getString(uuidStr, "member");
                        ow.memberPermissions.put(uuid, perm);
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            // BANリスト
            List<String> bannedList = ws.getStringList("banned");
            for (String uuidStr : bannedList) {
                try { ow.bannedPlayers.add(UUID.fromString(uuidStr)); }
                catch (IllegalArgumentException ignored) {}
            }

            worldsByCode.put(code, ow);
            worldsByName.put(ow.worldName.toLowerCase(), ow);
            ownerWorlds.computeIfAbsent(ow.ownerUUID, k -> new ArrayList<>()).add(code);
            sleepingWorlds.add(code);
        }
    }

    public void saveData() {
        dataConfig.set("worlds", null);
        for (OwnWorld ow : worldsByCode.values()) {
            String path = "worlds." + ow.code;
            dataConfig.set(path + ".worldName",      ow.worldName);
            dataConfig.set(path + ".ownerUUID",      ow.ownerUUID.toString());
            dataConfig.set(path + ".ownerName",      ow.ownerName);
            dataConfig.set(path + ".defaultGameMode", ow.defaultGameMode.name());
            dataConfig.set(path + ".worldType",      ow.worldType != null ? ow.worldType : "normal");
            dataConfig.set(path + ".hasCustomCode",  ow.hasCustomCode);
            if (ow.autoCode != null) dataConfig.set(path + ".autoCode", ow.autoCode);
            // permissions
            dataConfig.set(path + ".permissions", null);
            for (Map.Entry<UUID, String> e : ow.memberPermissions.entrySet()) {
                dataConfig.set(path + ".permissions." + e.getKey().toString(), e.getValue());
            }
            // banned
            List<String> bannedStr = new ArrayList<>();
            for (UUID u : ow.bannedPlayers) bannedStr.add(u.toString());
            dataConfig.set(path + ".banned", bannedStr);
        }
        try { dataConfig.save(dataFile); }
        catch (IOException e) { plugin.getLogger().severe("[WorldManager] データ保存失敗: " + e.getMessage()); }
    }

    // ─── ワールド作成 ──────────────────────────────────────────

    /**
     * ワールドを作成する。
     * @return 作成したOwnWorld / null=失敗
     */
    /**
     * ワールドを作成する (後方互換用オーバーロード: worldType = "normal")
     */
    public OwnWorld createWorld(Player owner, String worldName) {
        return createWorld(owner, worldName, "normal");
    }

    public OwnWorld createWorld(Player owner, String worldName, String worldType) {
        if (worldType == null || (!worldType.equals("flat") && !worldType.equals("normal"))) {
            worldType = "normal";
        }
        RankTier tier = getTier(owner);
        TierConfig cfg = TIER_CONFIGS.get(tier);

        // スロットチェック
        List<String> owned = ownerWorlds.getOrDefault(owner.getUniqueId(), Collections.emptyList());
        if (owned.size() >= cfg.slots) {
            owner.sendMessage("§c現在のランクで作成できるワールド数の上限（" + cfg.slots + "個）に達しています。");
            return null;
        }

        // 名前の重複チェック
        if (worldsByName.containsKey(worldName.toLowerCase())) {
            owner.sendMessage("§cそのワールド名はすでに使用されています。");
            return null;
        }

        // 名前の長さ・文字制限
        if (!worldName.matches("[a-zA-Z0-9_\\-]{3,32}")) {
            owner.sendMessage("§cワールド名は英数字・アンダースコア・ハイフン（3〜32文字）で指定してください。");
            return null;
        }

        // ユニークコード生成
        String code = generateUniqueCode();

        OwnWorld ow = new OwnWorld();
        ow.code            = code;
        ow.worldName       = worldName;
        ow.ownerUUID       = owner.getUniqueId();
        ow.ownerName       = owner.getName();
        ow.defaultGameMode = GameMode.SURVIVAL;
        ow.isLoaded        = false;
        ow.sleeping        = true;
        ow.worldType       = worldType;
        ow.autoCode        = code; // 初回自動生成コードを保存
        ow.memberPermissions.put(owner.getUniqueId(), "admin");

        worldsByCode.put(code, ow);
        worldsByName.put(worldName.toLowerCase(), ow);
        ownerWorlds.computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>()).add(code);
        sleepingWorlds.add(code);
        saveData();

        owner.sendMessage("");
        owner.sendMessage("§a§l■ ワールドを作成しました！");
        owner.sendMessage("§eワールド名: §f" + worldName);
        owner.sendMessage("§eワールドコード: §b" + code);
        owner.sendMessage("§7ワールド参加後、/settings コマンドでワールドの設定を変更できます。");
        owner.sendMessage("§7/own list からワールドの参加が可能です。");
        owner.sendMessage("");

        return ow;
    }

    // ─── ワールドへの参加（スリープ→ウェイク含む） ───────────

    public void joinWorld(Player player, String code) {
        code = code.toLowerCase();
        OwnWorld ow = worldsByCode.get(code);
        if (ow == null) {
            player.sendMessage("§cワールドコード §e" + code + " §cは存在しません。");
            return;
        }

        // BANチェック
        if (ow.bannedPlayers.contains(player.getUniqueId())) {
            player.sendMessage("§cあなたはこのワールドからBANされています。");
            return;
        }

        TierConfig cfg = getTierConfig(ow);

        // 参加上限チェック
        World w = Bukkit.getWorld(ow.worldName);
        if (w != null) {
            int cur = w.getPlayers().size();
            if (cfg.maxJoin != -1 && cur >= cfg.maxJoin) {
                player.sendMessage("§cこのワールドは参加上限に達しています。");
                return;
            }
            if (cur >= cfg.maxConcurrent) {
                player.sendMessage("§cこのワールドは現在の同接上限に達しています。");
                return;
            }
        }

        // スリープ中なら起動キューに追加
        if (sleepingWorlds.contains(code) || w == null) {
            wakeQueue.computeIfAbsent(code, k -> new ArrayList<>()).add(player.getUniqueId());
            long delaySec = cfg.sleepDelayMs / 1000;
            if (delaySec == 0) {
                player.sendMessage("§eワールドを起動しています...");
            } else {
                player.sendMessage("§eワールドを起動しています... (約" + delaySec + "秒)");
            }
            wakeWorld(ow, cfg);
            return;
        }

        teleportToWorld(player, ow, w);
    }

    private void wakeWorld(OwnWorld ow, TierConfig cfg) {
        String code = ow.code;
        // 既に起動処理中なら二重起動しない
        if (!sleepingWorlds.contains(code)) return;

        long delayTicks = cfg.sleepDelayMs / 50; // ms → ticks

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // MultiverseCore経由でワールドをロード
            if (!loadMultiverseWorld(ow.worldName)) {
                // フォールバック: Bukkit直接ロード
                WorldCreator creator = new WorldCreator(ow.worldName);
                creator.createWorld();
            }
            World loaded = Bukkit.getWorld(ow.worldName);
            if (loaded == null) {
                plugin.getLogger().warning("[WorldManager] ワールドのロードに失敗: " + ow.worldName);
                // キュー内プレイヤーに通知
                notifyWakeFailure(code);
                return;
            }

            ow.isLoaded = true;
            sleepingWorlds.remove(code);
            ow.sleeping = false;

            // キュー内プレイヤーをテレポート
            List<UUID> queue = wakeQueue.remove(code);
            if (queue != null) {
                for (UUID uuid : queue) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        teleportToWorld(p, ow, loaded);
                    }
                }
            }
        }, Math.max(1L, delayTicks));
    }

    private boolean loadMultiverseWorld(String worldName) {
        try {
            Object mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
            if (mv == null) return false;
            // MultiverseCore API経由でロード
            return (boolean) mv.getClass()
                    .getMethod("loadWorld", String.class)
                    .invoke(mv, worldName);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean createMultiverseWorld(String worldName, String worldType) {
        try {
            Object mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
            if (mv == null) return false;
            // mvcreate コマンド経由  (normal or flat)
            String mvType = "flat".equals(worldType) ? "flat" : "normal";
            String gen    = "flat".equals(worldType) ? " -g VoidGenerator" : "";
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "mv create " + worldName + " " + mvType + gen);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void notifyWakeFailure(String code) {
        List<UUID> queue = wakeQueue.remove(code);
        if (queue == null) return;
        for (UUID uuid : queue) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage("§cワールドの起動に失敗しました。管理者に連絡してください。");
        }
    }

    public void teleportToWorld(Player player, OwnWorld ow, World world) {
        TierConfig cfg = getTierConfig(ow);

        // コマンド制限適用（Diarrheaはコマンド禁止）
        // → PlayerCommandPreprocessEvent で処理（OwnWorldCommandListener参照）

        // ゲームモード設定
        player.setGameMode(ow.defaultGameMode);
        player.teleport(world.getSpawnLocation());
        player.sendMessage("§aワールド §e" + ow.worldName + " §aに参加しました！");

        if (!cfg.allowCommands) {
            player.sendMessage("§7このワールドではコマンドの使用が制限されています。");
        }

        // AFKトラッキング開始
        lastMoveTime.put(player.getUniqueId(), System.currentTimeMillis());
        lastMoveLocation.put(player.getUniqueId(), player.getLocation());
    }

    // ─── スリープチェッカー ─────────────────────────────────────

    private void startSleepChecker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (OwnWorld ow : worldsByCode.values()) {
                if (ow.sleeping) continue;
                World w = Bukkit.getWorld(ow.worldName);
                if (w == null) continue;
                if (w.getPlayers().isEmpty()) {
                    // プレイヤーがゼロ → スリープ
                    sleepWorld(ow);
                }
            }
        }, 20L * 30, 20L * 30); // 30秒ごとチェック
    }

    private void sleepWorld(OwnWorld ow) {
        ow.sleeping = true;
        ow.isLoaded = false;
        sleepingWorlds.add(ow.code);

        // MultiverseCore経由でアンロード
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv unload " + ow.worldName);
        } catch (Exception e) {
            // フォールバック
            World w = Bukkit.getWorld(ow.worldName);
            if (w != null) Bukkit.unloadWorld(w, true);
        }
        plugin.getLogger().info("[WorldManager] ワールドをスリープ: " + ow.worldName);
    }

    // ─── AntiAFKチェッカー ──────────────────────────────────────

    private void startAfkChecker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                World w = player.getWorld();
                OwnWorld ow = worldsByName.get(w.getName().toLowerCase());
                if (ow == null) continue; // own管理ワールド以外は無視

                Location lastLoc = lastMoveLocation.get(player.getUniqueId());
                Long lastTime    = lastMoveTime.get(player.getUniqueId());
                if (lastLoc == null || lastTime == null) continue;

                long elapsed = System.currentTimeMillis() - lastTime;

                // 現在位置と前回位置を比較
                Location curLoc = player.getLocation();
                if (curLoc.distanceSquared(lastLoc) < 0.1) {
                    // 動いていない
                    if (elapsed > AFK_THRESHOLD_MS) {
                        // AntiAFK疑い → Hubへキック
                        String hubWorld = plugin.getConfig().getString("hub-world", "world");
                        World hub = Bukkit.getWorld(hubWorld);
                        if (hub != null) {
                            player.teleport(hub.getSpawnLocation());
                            player.sendMessage("§e長時間動きがなかったため、Hubに転送されました。");
                        }
                        lastMoveTime.remove(player.getUniqueId());
                        lastMoveLocation.remove(player.getUniqueId());
                    }
                } else {
                    // 動いた
                    lastMoveTime.put(player.getUniqueId(), System.currentTimeMillis());
                    lastMoveLocation.put(player.getUniqueId(), curLoc);
                }
            }
        }, AFK_CHECK_INTERVAL_TICKS, AFK_CHECK_INTERVAL_TICKS);
    }

    public void updatePlayerMove(Player player) {
        lastMoveTime.put(player.getUniqueId(), System.currentTimeMillis());
        lastMoveLocation.put(player.getUniqueId(), player.getLocation());
    }

    // ─── BAN管理 ───────────────────────────────────────────────

    /** プレイヤーが現在いるワールドのオーナーとして対象をBAN */
    public boolean banPlayerFromCurrentWorld(Player owner, Player target) {
        World w = owner.getWorld();
        OwnWorld ow = worldsByName.get(w.getName().toLowerCase());
        if (ow == null) {
            owner.sendMessage("§cあなたは現在、own管理ワールドにいません。");
            return false;
        }
        return banPlayerFromWorld(owner, ow, target.getUniqueId(), target.getName());
    }

    public boolean banPlayerFromWorld(Player actor, OwnWorld ow, UUID targetUUID, String targetName) {
        if (!hasPermission(actor, ow, "admin")) {
            actor.sendMessage("§cこの操作にはadmin権限が必要です。");
            return false;
        }
        if (ow.ownerUUID.equals(targetUUID)) {
            actor.sendMessage("§cオーナー自身をBANすることはできません。");
            return false;
        }
        ow.bannedPlayers.add(targetUUID);
        saveData();

        // ワールドにいれば追い出す
        Player target = Bukkit.getPlayer(targetUUID);
        if (target != null && target.isOnline() &&
                target.getWorld().getName().equalsIgnoreCase(ow.worldName)) {
            String hub = plugin.getConfig().getString("hub-world", "world");
            World hubWorld = Bukkit.getWorld(hub);
            if (hubWorld != null) target.teleport(hubWorld.getSpawnLocation());
            target.sendMessage("§cワールド §e" + ow.worldName + " §cからBANされました。");
        }

        actor.sendMessage("§a" + targetName + " をワールド §e" + ow.worldName + " §aからBANしました。");
        return true;
    }

    public boolean unbanPlayerFromWorld(Player actor, OwnWorld ow, UUID targetUUID, String targetName) {
        if (!hasPermission(actor, ow, "admin")) {
            actor.sendMessage("§cこの操作にはadmin権限が必要です。");
            return false;
        }
        boolean removed = ow.bannedPlayers.remove(targetUUID);
        if (removed) {
            saveData();
            actor.sendMessage("§a" + targetName + " のBANをワールド §e" + ow.worldName + " §aから解除しました。");
        } else {
            actor.sendMessage("§c" + targetName + " はBANされていません。");
        }
        return removed;
    }

    // ─── ワールドコード変更 ────────────────────────────────────

    public boolean changeWorldCode(Player actor, OwnWorld ow, String newCode) {
        if (!hasPermission(actor, ow, "admin")) {
            actor.sendMessage("§cこの操作にはadmin権限が必要です。");
            return false;
        }
        newCode = newCode.toLowerCase();

        if (RESERVED_CODES.contains(newCode)) {
            actor.sendMessage("§cそのコードは使用できません。");
            return false;
        }
        if (!newCode.matches("[a-z0-9]{4,16}")) {
            actor.sendMessage("§cコードは英小文字・数字（4〜16文字）で指定してください。");
            return false;
        }
        if (worldsByCode.containsKey(newCode)) {
            actor.sendMessage("§cそのコードはすでに使用されています。");
            return false;
        }

        // 変更前に autoCode を保存（カスタムコード設定前の自動コード）
        if (!ow.hasCustomCode && ow.autoCode == null) {
            ow.autoCode = ow.code; // 現在のコードが自動生成コードなので保存
        }

        // 変更
        worldsByCode.remove(ow.code);
        List<String> ownerList = ownerWorlds.getOrDefault(ow.ownerUUID, new ArrayList<>());
        ownerList.remove(ow.code);
        boolean wasSleeping = sleepingWorlds.remove(ow.code);
        ow.code          = newCode;
        ow.hasCustomCode = true;
        ownerList.add(newCode);
        worldsByCode.put(newCode, ow);
        if (wasSleeping) sleepingWorlds.add(newCode);

        saveData();
        actor.sendMessage("§aワールドコードを §e" + newCode + " §aに変更しました。");
        return true;
    }

    // ─── ワールド削除 ──────────────────────────────────────────

    /**
     * ワールドを完全削除する。
     * ファイルシステム上のワールドフォルダも削除する。
     * @return true=成功, false=失敗
     */
    public boolean deleteWorld(Player actor, OwnWorld ow) {
        if (!hasPermission(actor, ow, "admin")) {
            actor.sendMessage("§cこの操作にはadmin権限が必要です。");
            return false;
        }
        // オーナーのみ削除可能
        if (!ow.ownerUUID.equals(actor.getUniqueId()) &&
                !actor.hasPermission("poopplugin.world.admin")) {
            actor.sendMessage("§cワールドを削除できるのはオーナーのみです。");
            return false;
        }

        String worldName = ow.worldName;
        String code      = ow.code;

        // ワールドにいるプレイヤーをHubへ
        World w = Bukkit.getWorld(worldName);
        String hub = plugin.getConfig().getString("hub-world", "world");
        World hubWorld = Bukkit.getWorld(hub);
        if (w != null) {
            for (Player p : new ArrayList<>(w.getPlayers())) {
                if (hubWorld != null) p.teleport(hubWorld.getSpawnLocation());
                p.sendMessage("§cワールド §e" + worldName + " §cは削除されました。");
            }
            // アンロード
            try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv remove " + worldName); }
            catch (Exception ignored) {}
            Bukkit.unloadWorld(w, false);
        }

        // ファイル削除
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File worldDir = new File(Bukkit.getWorldContainer(), worldName);
            if (worldDir.exists()) deleteFolder(worldDir);
            plugin.getLogger().info("[WorldManager] ワールドフォルダを削除: " + worldName);
        });

        // マップから削除
        worldsByCode.remove(code);
        worldsByName.remove(worldName.toLowerCase());
        sleepingWorlds.remove(code);
        wakeQueue.remove(code);
        List<String> ownerList = ownerWorlds.getOrDefault(ow.ownerUUID, new ArrayList<>());
        ownerList.remove(code);
        if (ownerList.isEmpty()) ownerWorlds.remove(ow.ownerUUID);

        saveData();
        actor.sendMessage("§aワールド §e" + worldName + " §aを削除しました。");
        return true;
    }

    private void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) for (File f : files) {
            if (f.isDirectory()) deleteFolder(f);
            else f.delete();
        }
        folder.delete();
    }

    // ─── カスタムコード無効化 ──────────────────────────────────

    /**
     * カスタムコードを無効化し、元の自動生成コードに戻す。
     * @return true=成功, false=失敗
     */
    public boolean disableCustomCode(Player actor, OwnWorld ow) {
        if (!hasPermission(actor, ow, "admin")) {
            actor.sendMessage("§cこの操作にはadmin権限が必要です。");
            return false;
        }
        if (!ow.hasCustomCode) {
            actor.sendMessage("§cこのワールドにはカスタムコードが設定されていません。");
            return false;
        }

        // autoCodeが保存されていなければ新規生成
        String defaultCode = (ow.autoCode != null && !ow.autoCode.isEmpty())
                ? ow.autoCode
                : generateUniqueCode();

        // コード変更
        worldsByCode.remove(ow.code);
        List<String> ownerList = ownerWorlds.getOrDefault(ow.ownerUUID, new ArrayList<>());
        ownerList.remove(ow.code);
        boolean wasSleeping = sleepingWorlds.remove(ow.code);

        ow.code          = defaultCode;
        ow.hasCustomCode = false;
        ow.autoCode      = null;

        ownerList.add(defaultCode);
        worldsByCode.put(defaultCode, ow);
        if (wasSleeping) sleepingWorlds.add(defaultCode);

        saveData();
        actor.sendMessage("§aカスタムコードを無効化しました。新しいコード: §b" + defaultCode);
        return true;
    }

    // ─── プランアップグレード後の制限解除 ─────────────────────

    /**
     * プレイヤーのランクを再評価し、現在のTierに合わせてワールド制限を更新する。
     * ランクアップ時にRankManagerなどから呼び出すこと。
     */
    public void refreshPlayerTier(Player player) {
        RankTier newTier = getTier(player);
        TierConfig cfg   = TIER_CONFIGS.get(newTier);
        List<OwnWorld> worlds = getWorldsByOwner(player.getUniqueId());

        int unlocked = 0;
        for (OwnWorld ow : worlds) {
            // スロット内に収まっているものはそのまま有効とする
            // (スロット数オーバーのワールドは削除せず保留 → 超過分はアクセス不可にしない)
            // ゲームモードをSURVIVALに戻す必要がある場合はそのままにする
            // (ランクダウン時の強制変更は別メソッドで処理)

            World w = Bukkit.getWorld(ow.worldName);
            if (w != null) {
                // ロード中のワールドにいるプレイヤーに新制限を通知
                for (Player p : w.getPlayers()) {
                    p.sendMessage("§aこのワールドのプランが更新されました！新しい制限が適用されています。");
                }
            }
            unlocked++;
        }

        player.sendMessage("§a§lプランアップグレード完了！");
        player.sendMessage("§eランク: §f" + tierDisplayName(newTier));
        player.sendMessage("§eワールドスロット: §f" + cfg.slots);
        player.sendMessage("§eコマンド使用: §f" + (cfg.allowCommands ? "§a有効" : "§c無効"));
        player.sendMessage("§eクリエイティブ: §f" + (cfg.allowCreative ? "§a有効" : "§c無効"));
        player.sendMessage("§e最大サイズ: §f" + formatSizeStatic(cfg.maxSizeBytes));
        player.sendMessage("§e同接上限: §f" + (cfg.maxConcurrent < 0 ? "無制限" : cfg.maxConcurrent));
        player.sendMessage("§7" + unlocked + " 個のワールドに新しい制限が適用されました。");
    }

    private static String formatSizeStatic(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String tierDisplayName(RankTier tier) {
        return switch (tier) {
            case POOP     -> "POOP (Ultimate)";
            case STOOL    -> "Stool (VIP)";
            case DIARRHEA -> "Diarrhea (無料)";
        };
    }


    // ─── ゲームモード変更 ─────────────────────────────────────

    public boolean changeDefaultGameMode(Player actor, OwnWorld ow, GameMode mode) {
        RankTier tier = getTierByOwner(ow.ownerUUID);
        if (tier == RankTier.DIARRHEA) {
            actor.sendMessage("§c無料ランクではゲームモードを変更できません。");
            return false;
        }
        if (!hasPermission(actor, ow, "admin")) {
            actor.sendMessage("§cこの操作にはadmin権限が必要です。");
            return false;
        }
        ow.defaultGameMode = mode;
        saveData();
        actor.sendMessage("§aデフォルトゲームモードを §e" + mode.name() + " §aに変更しました。");
        return true;
    }

    // ─── 権限管理 ─────────────────────────────────────────────

    public boolean hasPermission(Player player, OwnWorld ow, String required) {
        if (player.hasPermission("poopplugin.world.admin")) return true;
        if (ow.ownerUUID.equals(player.getUniqueId())) return true;
        String perm = ow.memberPermissions.getOrDefault(player.getUniqueId(), "member");
        if (required.equals("admin")) return "admin".equals(perm);
        if (required.equals("mod"))   return "admin".equals(perm) || "mod".equals(perm);
        return true; // member以上
    }

    public void setMemberPermission(Player actor, OwnWorld ow, UUID targetUUID, String targetName, String perm) {
        if (!hasPermission(actor, ow, "admin")) {
            actor.sendMessage("§cこの操作にはadmin権限が必要です。");
            return;
        }
        ow.memberPermissions.put(targetUUID, perm);
        saveData();
        actor.sendMessage("§a" + targetName + " の権限を §e" + perm + " §aに設定しました。");
    }

    // ─── ユーティリティ ───────────────────────────────────────

    public OwnWorld getWorldByCode(String code) {
        return worldsByCode.get(code.toLowerCase());
    }

    public OwnWorld getWorldByName(String name) {
        return worldsByName.get(name.toLowerCase());
    }

    public List<OwnWorld> getWorldsByOwner(UUID ownerUUID) {
        List<String> codes = ownerWorlds.getOrDefault(ownerUUID, Collections.emptyList());
        List<OwnWorld> result = new ArrayList<>();
        for (String c : codes) {
            OwnWorld ow = worldsByCode.get(c);
            if (ow != null) result.add(ow);
        }
        return result;
    }

    public RankTier getTier(Player player) {
        if (player.hasPermission("rank.ultimate") || player.hasPermission("rank.poop")) return RankTier.POOP;
        if (player.hasPermission("rank.vip")      || player.hasPermission("rank.stool")) return RankTier.STOOL;
        return RankTier.DIARRHEA;
    }

    public RankTier getTierByOwner(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return getTier(p);
        return RankTier.DIARRHEA; // オフラインはデフォルト
    }

    public TierConfig getTierConfig(OwnWorld ow) {
        return TIER_CONFIGS.get(getTierByOwner(ow.ownerUUID));
    }

    private String generateUniqueCode() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random rnd = new Random();
        while (true) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
            String code = sb.toString();
            if (!worldsByCode.containsKey(code) && !RESERVED_CODES.contains(code)) return code;
        }
    }

    private GameMode parseGameMode(String s) {
        try { return GameMode.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return GameMode.SURVIVAL; }
    }

    /** ワールドの現在のフォルダサイズをバイトで返す */
    public long getWorldSize(String worldName) {
        File worldDir = new File(Bukkit.getWorldContainer(), worldName);
        if (!worldDir.exists()) return 0;
        return folderSize(worldDir);
    }

    private long folderSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            size += f.isDirectory() ? folderSize(f) : f.length();
        }
        return size;
    }

    // ─── データクラス ──────────────────────────────────────────

    public static class OwnWorld {
        public String   code;
        public String   worldName;
        public UUID     ownerUUID;
        public String   ownerName;
        public GameMode defaultGameMode = GameMode.SURVIVAL;
        public boolean  isLoaded  = false;
        public boolean  sleeping  = true;

        /** "normal" or "flat" */
        public String   worldType    = "normal";
        /** カスタムコードが設定されているか (falseの場合はauto生成コードを使用) */
        public boolean  hasCustomCode = false;
        /** カスタムコード無効化前のオリジナルコードを復元するためのバックアップ (未使用時null) */
        public String   autoCode     = null;  // 自動生成コード（カスタム前）を保持

        // UUID → "admin" / "mod" / "member"
        public final Map<UUID, String> memberPermissions = new ConcurrentHashMap<>();
        public final Set<UUID>         bannedPlayers     = Collections.synchronizedSet(new HashSet<>());
    }
}
