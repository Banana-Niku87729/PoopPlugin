package com.example.poopplugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.geysermc.floodgate.api.FloodgateApi;

public class PlatformDisplayListener implements Listener {

    private final PoopPlugin plugin;

    // Unicodeプライベート領域の文字マッピング
    private static final String ICON_JAVA = "\uE000";
    private static final String ICON_ANDROID = "\uE001";
    private static final String ICON_IOS = "\uE002";
    private static final String ICON_WINDOWS = "\uE003";
    private static final String ICON_SWITCH = "\uE004";
    private static final String ICON_XBOX = "\uE005";
    private static final String ICON_PLAYSTATION = "\uE006";
    private static final String ICON_MACOS = "\uE007";
    private static final String ICON_UNKNOWN = "\uE008";

    public PlatformDisplayListener(PoopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 少し遅延させてから処理（他のプラグインとの競合を避けるため）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateAllPlayerDisplayNames();
        }, 20L); // 1秒後
    }

    /**
     * 全プレイヤーの表示名を更新
     */
    public void updateAllPlayerDisplayNames() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                updatePlayerDisplayName(viewer, target);
            }
        }
    }

    /**
     * 特定のプレイヤーに対する表示名を更新
     */
    public void updatePlayerDisplayName(Player viewer, Player target) {
        Scoreboard scoreboard = viewer.getScoreboard();
        if (scoreboard == Bukkit.getScoreboardManager().getMainScoreboard()) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            viewer.setScoreboard(scoreboard);
        }

        String teamName = "platform_" + target.getName().substring(0, Math.min(10, target.getName().length()));
        Team team = scoreboard.getTeam(teamName);

        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        // チームにプレイヤーを追加
        if (!team.hasEntry(target.getName())) {
            team.addEntry(target.getName());
        }

        // プレフィックスを設定
        String prefix = getPlatformPrefix(viewer, target);
        team.setPrefix(prefix);
    }

    /**
     * プラットフォームプレフィックスを取得
     */
    private String getPlatformPrefix(Player viewer, Player target) {
        // 視聴者が非表示設定にしている場合
        if (!plugin.getConfigManager().isViewPlatformEnabled(viewer)) {
            return "";
        }

        // ターゲットが自分のアイコンを拒否している場合
        if (plugin.getConfigManager().isDenyPlatformIcon(target)) {
            return "";
        }

        // Floodgate APIを使用してプラットフォームを判定
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            if (api.isFloodgatePlayer(target.getUniqueId())) {
                String deviceOs = api.getPlayer(target.getUniqueId()).getDeviceOs().name();
                return getPlatformIcon(deviceOs) + " ";
            } else {
                // Java版プレイヤー
                return ICON_JAVA + " ";
            }
        } catch (Exception e) {
            // Floodgate APIが利用できない場合、Java版として扱う
            return ICON_JAVA + " ";
        }
    }

    /**
     * プラットフォーム名からUnicode文字アイコンを取得
     */
    private String getPlatformIcon(String platform) {
        switch (platform.toUpperCase()) {
            case "ANDROID":
                return ICON_ANDROID;
            case "IOS":
                return ICON_IOS;
            case "WINDOWS":
            case "WIN32":
                return ICON_WINDOWS;
            case "MACOS":
            case "OSX":
                return ICON_MACOS;
            case "NINTENDO":
            case "NX":
                return ICON_SWITCH;
            case "XBOX":
                return ICON_XBOX;
            case "PLAYSTATION":
            case "PS4":
            case "PS5":
                return ICON_PLAYSTATION;
            default:
                return ICON_UNKNOWN;
        }
    }

    /**
     * プレイヤーのスコアボードをリセット
     */
    public void resetPlayerScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }
}