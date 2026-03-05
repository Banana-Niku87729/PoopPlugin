package com.example.poopplugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.geysermc.floodgate.api.FloodgateApi;

import java.text.SimpleDateFormat;
import java.util.*;

public class WatermarkManager {

    private final PoopPlugin plugin;
    private final Map<UUID, String> watermarkModes; // プレイヤーのウォーターマークモード
    private final Map<UUID, String> customWatermarks; // カスタムウォーターマーク
    private final Map<UUID, Long> lastColorChange; // 最後の色変更時刻
    private final Map<UUID, ChatColor> currentColors; // 現在の色
    private final Random random;
    private Scoreboard mainScoreboard;
    private Objective coinObjective;

    // 使用可能な色リスト
    private final List<ChatColor> colors = Arrays.asList(
            ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW,
            ChatColor.GREEN, ChatColor.AQUA, ChatColor.BLUE,
            ChatColor.LIGHT_PURPLE, ChatColor.DARK_RED, ChatColor.DARK_GREEN,
            ChatColor.DARK_AQUA, ChatColor.DARK_BLUE, ChatColor.DARK_PURPLE);

    public WatermarkManager(PoopPlugin plugin) {
        this.plugin = plugin;
        this.watermarkModes = new HashMap<>();
        this.customWatermarks = new HashMap<>();
        this.lastColorChange = new HashMap<>();
        this.currentColors = new HashMap<>();
        this.random = new Random();

        // Scoreboardの初期化
        initializeScoreboard();

        startWatermarkTask();

        // 既存のプレイヤーのデータをロード
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadData(player);
        }
    }

    /**
     * Scoreboardを初期化
     */
    private void initializeScoreboard() {
        mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Coin Objectiveが存在するか確認
        coinObjective = mainScoreboard.getObjective("coin");

        if (coinObjective == null) {
            // 存在しない場合は作成
            coinObjective = mainScoreboard.registerNewObjective("coin", "dummy", "§6Coin");
            plugin.getLogger().info("Coin Scoreboard Objectiveを作成しました!");
        }
    }

    /**
     * プレイヤーのCoin残高を取得
     */
    public int getCoins(Player player) {
        if (coinObjective == null) {
            initializeScoreboard();
        }

        Score score = coinObjective.getScore(player.getName());
        return score.getScore();
    }

    /**
     * プレイヤーのCoin残高を設定
     */
    public void setCoins(Player player, int amount) {
        if (coinObjective == null) {
            initializeScoreboard();
        }

        Score score = coinObjective.getScore(player.getName());
        score.setScore(amount);
    }

    /**
     * プレイヤーのCoinを追加
     */
    public void addCoins(Player player, int amount) {
        setCoins(player, getCoins(player) + amount);
    }

    /**
     * プレイヤーのCoinを減らす
     */
    public boolean removeCoins(Player player, int amount) {
        int currentCoins = getCoins(player);
        if (currentCoins >= amount) {
            setCoins(player, currentCoins - amount);
            return true;
        }
        return false;
    }

    /**
     * プレイヤーが十分なCoinを持っているか確認
     */
    public boolean hasCoins(Player player, int amount) {
        return getCoins(player) >= amount;
    }

    /**
     * ウォーターマーク表示タスクを開始
     */
    private void startWatermarkTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateWatermark(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // 0.5秒ごとに更新
    }

    /**
     * プレイヤーのウォーターマークを更新
     */
    private void updateWatermark(Player player) {
        UUID uuid = player.getUniqueId();

        // ランダムな時間で色を変更 (5-15秒)
        long currentTime = System.currentTimeMillis();
        if (!lastColorChange.containsKey(uuid)) {
            lastColorChange.put(uuid, currentTime);
            currentColors.put(uuid, colors.get(random.nextInt(colors.size())));
        }

        long timeSinceLastChange = currentTime - lastColorChange.get(uuid);
        long nextChangeTime = 5000 + random.nextInt(10000); // 5-15秒

        if (timeSinceLastChange > nextChangeTime) {
            currentColors.put(uuid, colors.get(random.nextInt(colors.size())));
            lastColorChange.put(uuid, currentTime);
        }

        ChatColor color = currentColors.get(uuid);
        String watermarkText = getWatermarkText(player);

        // アクションバーに表示
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(color + watermarkText));
    }

    /**
     * ウォーターマークテキストを取得
     */
    private String getWatermarkText(Player player) {
        UUID uuid = player.getUniqueId();
        String mode = watermarkModes.getOrDefault(uuid, "default");
        boolean isBedrock = isBedrockPlayer(player);

        // デフォルト表示
        if (mode.equals("default")) {
            if (isBedrock) {
                return "POOPMC playmc.rec877.com 3202";
            } else {
                return "POOPMC playmc.rec877.com";
            }
        }

        // モード別表示
        switch (mode) {
            case "1":
                int coins = getCoins(player);
                return "POOPMC " + coins + " Coin";

            case "2":
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                sdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
                return "POOPMC JST " + sdf.format(new Date());

            case "3":
                if (isBedrock) {
                    return "POOPMC .Bananakundao";
                } else {
                    return "POOPMC Rec877";
                }

            case "4":
                String custom = customWatermarks.get(uuid);
                if (custom != null && !custom.isEmpty()) {
                    return "POOPMC " + custom;
                }
                return "POOPMC";

            default:
                if (isBedrock) {
                    return "POOPMC playmc.rec877.com 3202";
                } else {
                    return "POOPMC playmc.rec877.com";
                }
        }
    }

    /**
     * Bedrock版プレイヤーかどうかを判定
     */
    private boolean isBedrockPlayer(Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ウォーターマークモードを設定
     */
    /**
     * ウォーターマークモードを設定
     */
    public void setWatermarkMode(Player player, String mode) {
        watermarkModes.put(player.getUniqueId(), mode);
        plugin.getConfigManager().setWatermarkMode(player, mode);
    }

    /**
     * カスタムウォーターマークを設定
     */
    public void setCustomWatermark(Player player, String text) {
        customWatermarks.put(player.getUniqueId(), text);
        watermarkModes.put(player.getUniqueId(), "4");

        plugin.getConfigManager().setCustomWatermark(player, text);
        plugin.getConfigManager().setWatermarkMode(player, "4");
    }

    /**
     * 現在のウォーターマークモードを取得
     */
    public String getWatermarkMode(Player player) {
        return watermarkModes.getOrDefault(player.getUniqueId(), "default");
    }

    /**
     * プレイヤーのデータをロード
     */
    public void loadData(Player player) {
        String mode = plugin.getConfigManager().getWatermarkMode(player);
        String custom = plugin.getConfigManager().getCustomWatermark(player);

        watermarkModes.put(player.getUniqueId(), mode);
        if (custom != null && !custom.isEmpty()) {
            customWatermarks.put(player.getUniqueId(), custom);
        }
    }

    /**
     * プレイヤーのデータをクリーンアップ (メモリから削除)
     */
    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        watermarkModes.remove(uuid);
        customWatermarks.remove(uuid);
        lastColorChange.remove(uuid);
        currentColors.remove(uuid);
    }
}