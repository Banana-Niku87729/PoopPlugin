package com.example.poopplugin;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * 保護エリアを表すクラス
 */
public class ProtectedRegion {

    private final String name;
    private final Location pos1;
    private final Location pos2;
    private final UUID owner;
    private final Set<UUID> members;
    private int priority;
    private final Map<String, Boolean> flags;

    // 利用可能なフラグ
    public static final String FLAG_PVP = "pvp";
    public static final String FLAG_BUILD = "build";
    public static final String FLAG_INTERACT = "interact";
    public static final String FLAG_MOB_SPAWN = "mob-spawn";
    public static final String FLAG_EXPLOSION = "explosion";
    public static final String FLAG_FIRE_SPREAD = "fire-spread";
    public static final String FLAG_GREETING = "greeting";
    public static final String FLAG_FAREWELL = "farewell";

    public ProtectedRegion(String name, Location pos1, Location pos2, UUID owner) {
        this.name = name;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.owner = owner;
        this.members = new HashSet<>();
        this.priority = 0;
        this.flags = new HashMap<>();

        // デフォルトフラグ
        flags.put(FLAG_BUILD, false); // メンバー以外は建築不可
        flags.put(FLAG_INTERACT, false); // メンバー以外は相互作用不可
        flags.put(FLAG_PVP, false); // PVP無効
        flags.put(FLAG_MOB_SPAWN, true); // モブスポーン有効
        flags.put(FLAG_EXPLOSION, false); // 爆発無効
        flags.put(FLAG_FIRE_SPREAD, false); // 炎の延焼無効
    }

    /**
     * 指定位置がこの保護エリア内にあるかチェック
     */
    public boolean contains(Location location) {
        if (!location.getWorld().equals(pos1.getWorld())) {
            return false;
        }

        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());

        return location.getX() >= minX && location.getX() <= maxX &&
                location.getY() >= minY && location.getY() <= maxY &&
                location.getZ() >= minZ && location.getZ() <= maxZ;
    }

    /**
     * プレイヤーが建築できるかチェック
     */
    public boolean canBuild(Player player) {
        UUID uuid = player.getUniqueId();

        // オーナーまたはメンバーなら許可
        if (uuid.equals(owner) || members.contains(uuid)) {
            return true;
        }

        // buildフラグで判定
        return flags.getOrDefault(FLAG_BUILD, false);
    }

    /**
     * プレイヤーが相互作用できるかチェック
     */
    public boolean canInteract(Player player) {
        UUID uuid = player.getUniqueId();

        // オーナーまたはメンバーなら許可
        if (uuid.equals(owner) || members.contains(uuid)) {
            return true;
        }

        // interactフラグで判定
        return flags.getOrDefault(FLAG_INTERACT, false);
    }

    /**
     * メンバーを追加
     */
    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    /**
     * メンバーを削除
     */
    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    /**
     * プレイヤーがメンバーかチェック
     */
    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    /**
     * プレイヤーがオーナーかチェック
     */
    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    /**
     * フラグを設定
     */
    public void setFlag(String flag, boolean value) {
        flags.put(flag, value);
    }

    /**
     * フラグを取得
     */
    public boolean getFlag(String flag) {
        return flags.getOrDefault(flag, false);
    }

    /**
     * 保護エリアの体積を計算
     */
    public int getVolume() {
        int sizeX = (int) Math.abs(pos2.getX() - pos1.getX()) + 1;
        int sizeY = (int) Math.abs(pos2.getY() - pos1.getY()) + 1;
        int sizeZ = (int) Math.abs(pos2.getZ() - pos1.getZ()) + 1;
        return sizeX * sizeY * sizeZ;
    }

    // Getter/Setter
    public String getName() {
        return name;
    }

    public Location getPos1() {
        return pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public UUID getOwner() {
        return owner;
    }

    public Set<UUID> getMembers() {
        return new HashSet<>(members);
    }

    public void setMembers(Set<UUID> members) {
        this.members.clear();
        this.members.addAll(members);
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Map<String, Boolean> getFlags() {
        return new HashMap<>(flags);
    }

    public void setFlags(Map<String, Boolean> flags) {
        this.flags.clear();
        this.flags.putAll(flags);
    }
}