package com.example.poopplugin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 統合版（Bedrock）プレイヤーのスキンをJava版プレイヤーに反映するリスナー。
 *
 * GeyserMC のスキンキャッシュは参加後しばらくしないと更新されないため、
 * 前回ログイン時の texture_id を記憶しておき、それと異なる値が返るまで
 * 最大 MAX_RETRY 回リトライする。
 */
public class BedrockSkinListener implements Listener {

    private static final String GEYSER_XUID_API = "https://api.geysermc.org/v2/xbox/xuid/%s";
    private static final String GEYSER_SKIN_API = "https://api.geysermc.org/v2/skin/%s";
    private static final String MC_TEXTURE_URL  = "https://textures.minecraft.net/texture/%s";
    private static final String SAMPLE_SKIN_URL = "https://get.rec877.com/get/poopserver/sample.png";
    private static final boolean SAMPLE_IS_SLIM = true;
    private static final String BE_PREFIX       = "[BE]";

    /** 初回取得までの待機時間(tick) */
    private static final long FIRST_DELAY_TICKS  = 60L;  // 3秒
    /** リトライ間隔(tick) */
    private static final long RETRY_INTERVAL_TICKS = 40L; // 2秒
    /** 最大リトライ回数 */
    private static final int MAX_RETRY = 10;

    private final PoopPlugin plugin;

    /**
     * プレイヤー名 → 前回適用した texture_id を保存。
     * 再ログイン時に「前回と同じIDが返ってきたらまだキャッシュ中」と判断する。
     */
    private final Map<String, String> lastTextureIdMap = new ConcurrentHashMap<>();

    public BedrockSkinListener(PoopPlugin plugin) {
        this.plugin = plugin;
    }

    // ──────────────────────────────────────────────
    // イベント
    // ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isBedrock(player)) return;

        // 1. 参加直後(メインスレッド): 仮スキンを即時適用
        String sampleValue = buildTextureValue(SAMPLE_SKIN_URL, SAMPLE_IS_SLIM);
        applyProfileSkin(player, sampleValue);
        plugin.getLogger().info("[BedrockSkin] サンプルスキン適用: " + player.getName());

        // 2. 3秒後から非同期リトライループ開始
        scheduleRetry(player, 0, FIRST_DELAY_TICKS);
    }

    // ──────────────────────────────────────────────
    // リトライスケジューラ
    // ──────────────────────────────────────────────

    private void scheduleRetry(Player player, int attempt, long delayTicks) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (!player.isOnline()) return;

            try {
                boolean done = tryFetchAndApply(player, attempt);
                if (!done && attempt < MAX_RETRY) {
                    // まだキャッシュ中 or 失敗 → リトライ
                    plugin.getLogger().info("[BedrockSkin] リトライ " + (attempt + 1)
                            + "/" + MAX_RETRY + ": " + player.getName());
                    scheduleRetry(player, attempt + 1, RETRY_INTERVAL_TICKS);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[BedrockSkin] スキン取得失敗 (attempt=" + attempt + "): "
                                + player.getName(), e);
                if (attempt < MAX_RETRY) {
                    scheduleRetry(player, attempt + 1, RETRY_INTERVAL_TICKS);
                }
            }
        }, delayTicks);
    }

    /**
     * スキンを取得して適用する。
     * @return true=新しいスキンを適用できた / false=まだキャッシュ中または失敗
     */
    private boolean tryFetchAndApply(Player player, int attempt) throws Exception {
        String rawName     = player.getName();
        String bedrockName = rawName.startsWith(BE_PREFIX)
                ? rawName.substring(BE_PREFIX.length()) : rawName;

        // XUID 取得
        String xuid = fetchXuid(bedrockName);
        if (xuid == null) return false;

        // スキン情報取得（キャッシュ回避のためタイムスタンプ付与）
        String url = String.format(GEYSER_SKIN_API, xuid)
                + "?t=" + System.currentTimeMillis();
        JsonObject skinJson = fetchJson(url);
        if (skinJson == null || !skinJson.has("texture_id")) return false;

        String newTextureId = skinJson.get("texture_id").getAsString();
        String lastTextureId = lastTextureIdMap.get(bedrockName);

        plugin.getLogger().info("[BedrockSkin] texture_id取得 (attempt=" + attempt + "): "
                + newTextureId + (lastTextureId != null ? " (前回: " + lastTextureId + ")" : ""));

        // 前回と同じIDならGeyserのキャッシュがまだ古い → リトライ
        // ただし attempt が MAX_RETRY に達したら諦めて適用する
        if (newTextureId.equals(lastTextureId) && attempt < MAX_RETRY) {
            plugin.getLogger().info("[BedrockSkin] 前回と同じtexture_id、キャッシュ更新待ち: "
                    + bedrockName);
            return false;
        }

        boolean isSlim      = detectSlim(skinJson);
        String textureUrl   = String.format(MC_TEXTURE_URL, newTextureId);
        String textureValue = buildTextureValue(textureUrl, isSlim);

        // メインスレッドで適用
        final String finalTextureId = newTextureId;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            applyProfileSkin(player, textureValue);
            // 今回のtexture_idを記憶
            lastTextureIdMap.put(bedrockName, finalTextureId);
            plugin.getLogger().info("[BedrockSkin] スキン適用完了: " + bedrockName
                    + " | slim=" + isSlim + " | textureId=" + finalTextureId);
        });

        return true;
    }

    // ──────────────────────────────────────────────
    // PlayerProfile へのスキンセット（メインスレッド専用）
    // ──────────────────────────────────────────────

    private void applyProfileSkin(Player player, String textureValue) {
        PlayerProfile profile = player.getPlayerProfile();
        profile.removeProperty("textures");
        profile.setProperty(new ProfileProperty("textures", textureValue));
        player.setPlayerProfile(profile);
        refreshForOthers(player);
    }

    private void refreshForOthers(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) continue;
            online.hidePlayer(plugin, player);
            online.showPlayer(plugin, player);
        }
    }

    // ──────────────────────────────────────────────
    // テクスチャ値生成
    // ──────────────────────────────────────────────

    private String buildTextureValue(String textureUrl, boolean slim) {
        String modelMeta = slim ? ",\"metadata\":{\"model\":\"slim\"}" : "";
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl + "\"" + modelMeta + "}}}";
        return Base64.getEncoder().encodeToString(json.getBytes());
    }

    // ──────────────────────────────────────────────
    // Slim 判定
    // ──────────────────────────────────────────────

    private boolean detectSlim(JsonObject skinJson) {
        if (skinJson.has("is_slim")) {
            return skinJson.get("is_slim").getAsBoolean();
        }
        if (skinJson.has("geometry_name")) {
            String geo = skinJson.get("geometry_name").getAsString().toLowerCase();
            if (geo.contains("slim") || geo.contains("alex")) return true;
        }
        if (skinJson.has("texture_id")) {
            try {
                String url = String.format(MC_TEXTURE_URL,
                        skinJson.get("texture_id").getAsString());
                return detectSlimFromImage(url);
            } catch (Exception ignored) {}
        }
        return false;
    }

    private boolean detectSlimFromImage(String imageUrl) throws IOException {
        HttpURLConnection conn = openConnection(imageUrl);
        try (InputStream is = conn.getInputStream()) {
            BufferedImage img = ImageIO.read(is);
            if (img == null) return false;
            int alpha = (img.getRGB(50, 16) >> 24) & 0xFF;
            return alpha == 0;
        }
    }

    // ──────────────────────────────────────────────
    // HTTP ユーティリティ
    // ──────────────────────────────────────────────

    private String fetchXuid(String bedrockName) throws IOException {
        JsonObject json = fetchJson(String.format(GEYSER_XUID_API, bedrockName));
        if (json == null || !json.has("xuid")) return null;
        return json.get("xuid").getAsString();
    }

    private JsonObject fetchJson(String urlStr) throws IOException {
        HttpURLConnection conn = openConnection(urlStr);
        if (conn.getResponseCode() != 200) return null;
        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private HttpURLConnection openConnection(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setUseCaches(false);
        conn.setRequestProperty("Cache-Control", "no-cache, no-store");
        conn.setRequestProperty("Pragma", "no-cache");
        conn.setRequestProperty("User-Agent", "PoopPlugin/1.0");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return conn;
    }

    // ──────────────────────────────────────────────
    // Bedrock 判定
    // ──────────────────────────────────────────────

    private boolean isBedrock(Player player) {
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object api = apiClass.getMethod("getInstance").invoke(null);
                return (boolean) apiClass.getMethod("isFloodgatePlayer", UUID.class)
                        .invoke(api, player.getUniqueId());
            } catch (Exception ignored) {}
        }
        return player.getName().startsWith(BE_PREFIX);
    }
}