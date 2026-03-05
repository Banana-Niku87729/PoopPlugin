package com.example.poopplugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class PoopPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private PlatformDisplayListener platformListener;
    private CPSKickListener cpsKickListener;
    private WatermarkManager watermarkManager;
    private EnderChestManager enderChestManager;
    private HardcoreManager hardcoreManager;
    private ProtectionManager protectionManager;

    // ── 追加 ──
    private RankManager rankManager;
    private KofiWebhookListener kofiWebhookListener;

    // ── Bedrockスキン ──
    private BedrockSkinListener bedrockSkinListener;  // ★ 追加

    private WorldManager worldManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        worldManager = new WorldManager(this);
        OwnListGUI.init(this);
        DeleteConfirmGUI.init(this);
        DisableCodeConfirmGUI.init(this);
        // プラグインが有効化された時の処理
        getLogger().info("PoopPlugin が有効化されました!");

        // Floodgateの存在確認
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) {
            getLogger().warning("Floodgateが見つかりません。Bedrock版プレイヤーのプラットフォーム情報は表示されません。");
        }

        // ProtocolLibの存在確認
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            getLogger().info("ProtocolLibが検出されました。ハードコアハート表示が有効です。");
        } else {
            getLogger().warning("ProtocolLibが見つかりません。ハードコアハート表示は制限されます。");
        }

        // ConfigManagerの初期化
        configManager = new ConfigManager(this);

        // WatermarkManagerの初期化
        watermarkManager = new WatermarkManager(this);

        // EnderChestManagerの初期化
        enderChestManager = new EnderChestManager(this);

        // HardcoreManagerの初期化
        hardcoreManager = new HardcoreManager(this);

        // ProtectionManagerの初期化
        protectionManager = new ProtectionManager(this);

        // ── RankManagerの初期化 ──
        rankManager = new RankManager(this);

        // ── Ko-Fi Webhook HTTPサーバーの起動 ──
        kofiWebhookListener = new KofiWebhookListener(this);
        kofiWebhookListener.start();

        // ── /own コマンド登録 ──
        OwnCommand ownCommand = new OwnCommand(this);
        this.getCommand("own").setExecutor(ownCommand);
        this.getCommand("own").setTabCompleter(ownCommand);

        // ── /join コマンド登録 ──
        this.getCommand("join").setExecutor(new JoinCommand(this));

        // ── /account コマンド登録 ──
        AccountCommand accountCommand = new AccountCommand(this);
        this.getCommand("account").setExecutor(accountCommand);
        this.getCommand("account").setTabCompleter(accountCommand);

        // ── /settings コマンド登録（リスナーも兼ねる） ──
        SettingsCommand settingsCommand = new SettingsCommand(this);
        this.getCommand("settings").setExecutor(settingsCommand);
        // SettingsCommand のコンストラクタ内で Bukkit.getPluginManager().registerEvents() を呼んでいるので不要

        // ── OwnWorld イベントリスナー登録 ──
        OwnWorldListener ownWorldListener = new OwnWorldListener(this);
        Bukkit.getPluginManager().registerEvents(ownWorldListener, this);

        // コマンドを登録
        this.getCommand("ec").setExecutor(new EnderChestCommand(this));
        this.getCommand("enderchest").setExecutor(new EnderChestCommand(this));

        ToggleCommand toggleCommand = new ToggleCommand(this);
        this.getCommand("toggle").setExecutor(toggleCommand);
        this.getCommand("toggle").setTabCompleter(toggleCommand);

        WatermarkCommand watermarkCommand = new WatermarkCommand(this);
        this.getCommand("watermark").setExecutor(watermarkCommand);
        this.getCommand("watermark").setTabCompleter(watermarkCommand);

        CoinCommand coinCommand = new CoinCommand(this);
        this.getCommand("coin").setExecutor(coinCommand);
        this.getCommand("coin").setTabCompleter(coinCommand);

        HardcoreCommand hardcoreCommand = new HardcoreCommand(this);
        this.getCommand("hardcore").setExecutor(hardcoreCommand);
        this.getCommand("hardcore").setTabCompleter(hardcoreCommand);

        // 保護コマンドを登録
        ProtectCommand protectCommand = new ProtectCommand(this);
        this.getCommand("protect").setExecutor(protectCommand);
        this.getCommand("protect").setTabCompleter(protectCommand);

        // ── ランクコマンドを登録 ──
        this.getCommand("rank_buy").setExecutor(new RankBuyCommand(this));
        this.getCommand("buyname_change").setExecutor(new BuynameChangeCommand(this));

        // イベントリスナーを登録
        platformListener = new PlatformDisplayListener(this);
        Bukkit.getPluginManager().registerEvents(platformListener, this);

        // CPS制限リスナーを登録
        cpsKickListener = new CPSKickListener(this);
        Bukkit.getPluginManager().registerEvents(cpsKickListener, this);

        // Watermarkリスナーを登録
        WatermarkListener watermarkListener = new WatermarkListener(this);
        Bukkit.getPluginManager().registerEvents(watermarkListener, this);

        // EnderChestリスナーを登録
        EnderChestListener enderChestListener = new EnderChestListener(this);
        Bukkit.getPluginManager().registerEvents(enderChestListener, this);

        // Hardcoreリスナーを登録
        HardcoreListener hardcoreListener = new HardcoreListener(this);
        Bukkit.getPluginManager().registerEvents(hardcoreListener, this);

        // 保護リスナーを登録
        ProtectionListener protectionListener = new ProtectionListener(this);
        Bukkit.getPluginManager().registerEvents(protectionListener, this);

        // Coin自動付与・減算リスナーを登録
        CoinRewardListener coinRewardListener = new CoinRewardListener(this);
        Bukkit.getPluginManager().registerEvents(coinRewardListener, this);

        // 範囲選択の杖リスナーを登録
        WandListener wandListener = new WandListener(this,
                protectCommand.getPos1Selections(),
                protectCommand.getPos2Selections());
        Bukkit.getPluginManager().registerEvents(wandListener, this);

        // ── ランクログインリスナーを登録 ──
        RankLoginListener rankLoginListener = new RankLoginListener(this);
        Bukkit.getPluginManager().registerEvents(rankLoginListener, this);

        // ── Bedrockスキンリスナーを登録 ──               ★ 追加
        bedrockSkinListener = new BedrockSkinListener(this);
        Bukkit.getPluginManager().registerEvents(bedrockSkinListener, this);

        // 既存のオンラインプレイヤーの表示名を更新
        Bukkit.getScheduler().runTaskLater(this, () -> {
            platformListener.updateAllPlayerDisplayNames();
        }, 40L); // 2秒後

        getLogger().info("全機能が正常に登録されました!");
    }

    @Override
    public void onDisable() {
        // プラグインが無効化された時の処理
        if (hardcoreManager != null) {
            hardcoreManager.saveConfig();
        }
        if (protectionManager != null) {
            protectionManager.saveRegions();
        }
        // ── Ko-Fi Webhook HTTPサーバーを停止 ──
        if (kofiWebhookListener != null) {
            kofiWebhookListener.stop();
        }
        getLogger().info("PoopPlugin が無効化されました!");
    }

    // ── Getter 群 ──────────────────────────────────────────────

    public ConfigManager getConfigManager() { return configManager; }
    public PlatformDisplayListener getPlatformListener() { return platformListener; }
    public CPSKickListener getCpsKickListener() { return cpsKickListener; }
    public WatermarkManager getWatermarkManager() { return watermarkManager; }
    public EnderChestManager getEnderChestManager() { return enderChestManager; }
    public HardcoreManager getHardcoreManager() { return hardcoreManager; }
    public ProtectionManager getProtectionManager() { return protectionManager; }

    /** RankManagerを取得 */
    public RankManager getRankManager() { return rankManager; }

    public WorldManager getWorldManager() {
    return worldManager;
    }
}
