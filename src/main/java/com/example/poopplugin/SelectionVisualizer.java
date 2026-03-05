package com.example.poopplugin;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 選択範囲を視覚化するクラス
 */
public class SelectionVisualizer {

    private final PoopPlugin plugin;

    public SelectionVisualizer(PoopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 2つの位置を結ぶ線を表示
     */
    public void showLine(Player player, Location loc1, Location loc2, Particle particle) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 40 || !player.isOnline()) { // 2秒間表示
                    cancel();
                    return;
                }

                drawLine(loc1, loc2, particle, player);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 選択範囲の立方体を表示
     */
    public void showCuboid(Player player, Location pos1, Location pos2) {
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            return;
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 60 || !player.isOnline()) { // 3秒間表示
                    cancel();
                    return;
                }

                drawCuboid(pos1, pos2, player);
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * 立方体の枠線を描画
     */
    private void drawCuboid(Location pos1, Location pos2, Player player) {
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;

        // 下の4辺
        drawLine(new Location(pos1.getWorld(), minX, minY, minZ),
                new Location(pos1.getWorld(), maxX, minY, minZ), Particle.HAPPY_VILLAGER, player);
        drawLine(new Location(pos1.getWorld(), minX, minY, maxZ),
                new Location(pos1.getWorld(), maxX, minY, maxZ), Particle.HAPPY_VILLAGER, player);
        drawLine(new Location(pos1.getWorld(), minX, minY, minZ),
                new Location(pos1.getWorld(), minX, minY, maxZ), Particle.HAPPY_VILLAGER, player);
        drawLine(new Location(pos1.getWorld(), maxX, minY, minZ),
                new Location(pos1.getWorld(), maxX, minY, maxZ), Particle.HAPPY_VILLAGER, player);

        // 上の4辺
        drawLine(new Location(pos1.getWorld(), minX, maxY, minZ),
                new Location(pos1.getWorld(), maxX, maxY, minZ), Particle.HAPPY_VILLAGER, player);
        drawLine(new Location(pos1.getWorld(), minX, maxY, maxZ),
                new Location(pos1.getWorld(), maxX, maxY, maxZ), Particle.HAPPY_VILLAGER, player);
        drawLine(new Location(pos1.getWorld(), minX, maxY, minZ),
                new Location(pos1.getWorld(), minX, maxY, maxZ), Particle.HAPPY_VILLAGER, player);
        drawLine(new Location(pos1.getWorld(), maxX, maxY, minZ),
                new Location(pos1.getWorld(), maxX, maxY, maxZ), Particle.HAPPY_VILLAGER, player);

        // 縦の4辺
        drawLine(new Location(pos1.getWorld(), minX, minY, minZ),
                new Location(pos1.getWorld(), minX, maxY, minZ), Particle.HAPPY_VILLAGER, player);
        drawLine(new Location(pos1.getWorld(), maxX, minY, minZ),
                new Location(pos1.getWorld(), maxX, maxY, minZ), Particle.HAPPY_VILLAGER, player);
        drawLine(new Location(pos1.getWorld(), minX, minY, maxZ),
                new Location(pos1.getWorld(), minX, maxY, maxZ), Particle.HAPPY_VILLAGER, player);
        drawLine(new Location(pos1.getWorld(), maxX, minY, maxZ),
                new Location(pos1.getWorld(), maxX, maxY, maxZ), Particle.HAPPY_VILLAGER, player);
    }

    /**
     * 2点間に線を描画
     */
    private void drawLine(Location start, Location end, Particle particle, Player player) {
        double distance = start.distance(end);
        double step = 0.3; // パーティクル間の距離

        double dx = (end.getX() - start.getX()) / distance;
        double dy = (end.getY() - start.getY()) / distance;
        double dz = (end.getZ() - start.getZ()) / distance;

        for (double d = 0; d <= distance; d += step) {
            double x = start.getX() + dx * d;
            double y = start.getY() + dy * d;
            double z = start.getZ() + dz * d;

            Location particleLoc = new Location(start.getWorld(), x, y, z);
            player.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    /**
     * 単一のポイントを強調表示
     */
    public void showPoint(Player player, Location location, Particle particle) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 40 || !player.isOnline()) {
                    cancel();
                    return;
                }

                // 十字の形でパーティクルを表示
                for (int i = -2; i <= 2; i++) {
                    player.spawnParticle(particle, location.clone().add(i * 0.2, 0, 0), 1, 0, 0, 0, 0);
                    player.spawnParticle(particle, location.clone().add(0, i * 0.2, 0), 1, 0, 0, 0, 0);
                    player.spawnParticle(particle, location.clone().add(0, 0, i * 0.2), 1, 0, 0, 0, 0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}