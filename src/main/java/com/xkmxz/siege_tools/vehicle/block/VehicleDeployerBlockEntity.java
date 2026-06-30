package com.xkmxz.siege_tools.vehicle.block;

import com.mojang.logging.LogUtils;
import com.xkmxz.siege_tools.vehicle.data.VehicleDataManager;
import com.xkmxz.siege_tools.vehicle.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * 载具部署台 BlockEntity。
 * 替代 KubeJS block_main.js 的 ensurePD + tick + 部署生命周期。
 */
public class VehicleDeployerBlockEntity extends BlockEntity implements MenuProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    // === 持久化配置 ===
    private boolean inited = false;
    private String vehicleType = "";
    private int respawnDelay = 600;      // tick
    private boolean autoRespawn = false;
    private double offsetX = 0.0, offsetY = 1.0, offsetZ = 0.0;
    private float yaw = 0.0f, pitch = 0.0f;
    private String deployedUUID = "";
    private long cooldownEnd = 0;
    private CompoundTag deployNBT = new CompoundTag();
    private String displayName = "";
    private boolean spawnWithAmmo = true;

    // === 运行时标记 ===
    private boolean pendingDeploy = false;

    public VehicleDeployerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VEHICLE_DEPLOYER.get(), pos, state);
    }

    // ========== Tick ==========

    public static void tick(Level level, BlockPos pos, BlockState state, VehicleDeployerBlockEntity be) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // 确保初始化
        be.ensureInit();

        // 处理 PendingDeploy 标记
        if (be.pendingDeploy) {
            be.pendingDeploy = false;
            be.setChanged();
            if (!be.vehicleType.isEmpty()) {
                // 防重复部署
                if (!be.deployedUUID.isEmpty()) {
                    Entity existing = serverLevel.getEntity(UUID.fromString(be.deployedUUID));
                    if (existing != null && existing.isAlive()) {
                        return; // 已有存活载具
                    }
                }
                be.cooldownEnd = 0;
                be.deployedUUID = "";
                be.setChanged();
                com.xkmxz.siege_tools.vehicle.deploy.VehicleDeployerHelper.spawnVehicle(serverLevel, pos, be);
            }
            return;
        }

        // 未配置车辆类型
        if (be.vehicleType.isEmpty()) return;

        long gameTime = level.getGameTime();

        // 重启后 cooldownEnd 范围校验
        if (be.cooldownEnd > gameTime + 72000) {
            LOGGER.warn("[DeployerBE] cooldownEnd 异常（重启残留）: {} > {}, 重置为 0", be.cooldownEnd, gameTime + 72000);
            be.cooldownEnd = 0;
            be.setChanged();
        }

        // 有 UUID → 检查实体存活
        if (!be.deployedUUID.isEmpty()) {
            try {
                Entity entity = serverLevel.getEntity(UUID.fromString(be.deployedUUID));
                if (entity != null && entity.isAlive()) {
                    return; // 存活，无事可做
                }
            } catch (Exception ignored) {}

            // 实体死亡 → 清 UUID，开始冷却
            LOGGER.info("[DeployerBE] 载具已消失 @[{},{},{}]", pos.getX(), pos.getY(), pos.getZ());
            be.deployedUUID = "";
            be.cooldownEnd = gameTime + be.respawnDelay;
            be.setChanged();
            return;
        }

        // 无 UUID + 冷却已过 → 自动部署
        if (gameTime >= be.cooldownEnd && be.autoRespawn) {
            com.xkmxz.siege_tools.vehicle.deploy.VehicleDeployerHelper.spawnVehicle(serverLevel, pos, be);
        }
    }

    // ========== 初始化 ==========

    public void ensureInit() {
        if (!inited) {
            inited = true;
            setChanged();
            LOGGER.info("[DeployerBE] 方块初始化完成 @[{},{},{}]", worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        }
    }

    // ========== 配置读写 ==========

    public void setPendingDeploy(boolean pending) { this.pendingDeploy = pending; setChanged(); }
    public boolean isPendingDeploy() { return pendingDeploy; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; setChanged(); }

    public int getRespawnDelay() { return respawnDelay; }
    public void setRespawnDelay(int respawnDelay) { this.respawnDelay = respawnDelay; setChanged(); }

    public boolean isAutoRespawn() { return autoRespawn; }
    public void setAutoRespawn(boolean autoRespawn) { this.autoRespawn = autoRespawn; setChanged(); }

    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public double getOffsetZ() { return offsetZ; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public void setOffsets(double ox, double oy, double oz, float yaw, float pitch) {
        this.offsetX = ox; this.offsetY = oy; this.offsetZ = oz;
        this.yaw = yaw; this.pitch = pitch;
        setChanged();
    }

    public String getDeployedUUID() { return deployedUUID; }
    public void setDeployedUUID(String deployedUUID) { this.deployedUUID = deployedUUID; setChanged(); }

    public long getCooldownEnd() { return cooldownEnd; }
    public void setCooldownEnd(long cooldownEnd) { this.cooldownEnd = cooldownEnd; setChanged(); }

    public CompoundTag getDeployNBT() { return deployNBT; }
    public void setDeployNBT(CompoundTag deployNBT) { this.deployNBT = deployNBT != null ? deployNBT : new CompoundTag(); setChanged(); }

    /** 将 deployNBT 转为标准 JSON 字符串，供 GUI 文本框展示 */
    public String getDeployNBTAsJson() {
        if (deployNBT == null || deployNBT.isEmpty()) return "{}";
        return nbtCompoundToJson(deployNBT);
    }

    /** 递归：CompoundTag → 标准 JSON 对象字符串 */
    public static String nbtCompoundToJson(CompoundTag tag) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String key : tag.getAllKeys()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(jsonEscape(key)).append("\":");
            sb.append(nbtTagToJson(tag.get(key)));
        }
        sb.append("}");
        return sb.toString();
    }

    /** 递归：任意 NBT Tag → JSON 值字符串 */
    private static String nbtTagToJson(Tag t) {
        if (t instanceof CompoundTag ct) return nbtCompoundToJson(ct);
        if (t instanceof net.minecraft.nbt.ListTag lt) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < lt.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(nbtTagToJson(lt.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (t instanceof net.minecraft.nbt.StringTag st) {
            return "\"" + jsonEscape(st.getAsString()) + "\"";
        }
        if (t instanceof net.minecraft.nbt.NumericTag nt) {
            return nt.getAsString(); // 数字直接输出
        }
        return "\"\"";
    }

    /** 转义 JSON 字符串中的特殊字符 */
    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public boolean isSpawnWithAmmo() { return spawnWithAmmo; }
    public void setSpawnWithAmmo(boolean spawnWithAmmo) { this.spawnWithAmmo = spawnWithAmmo; setChanged(); }

    /** 一次性应用来自网络包的配置 */
    public void applyConfig(com.xkmxz.siege_tools.vehicle.network.DeployerConfigData cfg) {
        this.vehicleType = cfg.vehicleType();
        this.respawnDelay = Math.max(20, cfg.respawnDelay());
        this.autoRespawn = cfg.autoRespawn();
        this.spawnWithAmmo = cfg.spawnWithAmmo();
        this.offsetX = cfg.offsetX(); this.offsetY = cfg.offsetY(); this.offsetZ = cfg.offsetZ();
        this.yaw = cfg.yaw(); this.pitch = cfg.pitch();
        this.deployNBT = cfg.deployNBT() != null ? cfg.deployNBT() : new CompoundTag();
        setChanged();
    }

    /** 重置为数据包默认配置 */
    public void resetConfig() {
        CompoundTag def = VehicleDataManager.getDefaultDeployerConfig();
        this.vehicleType = def.contains("vehicleType") ? def.getString("vehicleType") : "";
        this.respawnDelay = def.contains("respawnDelay") ? def.getInt("respawnDelay") : 600;
        this.autoRespawn = def.contains("autoRespawn") && def.getBoolean("autoRespawn");
        this.spawnWithAmmo = !def.contains("spawnWithAmmo") || def.getBoolean("spawnWithAmmo");
        this.offsetX = def.contains("offsetX") ? def.getDouble("offsetX") : 0.0;
        this.offsetY = def.contains("offsetY") ? def.getDouble("offsetY") : 1.0;
        this.offsetZ = def.contains("offsetZ") ? def.getDouble("offsetZ") : 0.0;
        this.yaw = def.contains("yaw") ? def.getFloat("yaw") : 0.0f;
        this.pitch = def.contains("pitch") ? def.getFloat("pitch") : 0.0f;
        this.deployNBT = def.contains("deployNBT") ? new CompoundTag() : new CompoundTag();
        this.deployedUUID = "";
        this.cooldownEnd = 0;
        setChanged();
    }

    // ========== NBT ==========

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("inited", inited);
        tag.putString("vehicleType", vehicleType);
        tag.putInt("respawnDelay", respawnDelay);
        tag.putBoolean("autoRespawn", autoRespawn);
        tag.putDouble("offsetX", offsetX);
        tag.putDouble("offsetY", offsetY);
        tag.putDouble("offsetZ", offsetZ);
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        tag.putString("deployedUUID", deployedUUID);
        tag.putLong("cooldownEnd", cooldownEnd);
        tag.put("deployNBT", deployNBT);
        tag.putString("displayName", displayName);
        tag.putBoolean("spawnWithAmmo", spawnWithAmmo);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inited = tag.getBoolean("inited");
        vehicleType = tag.getString("vehicleType");
        respawnDelay = tag.contains("respawnDelay") ? tag.getInt("respawnDelay") : 600;
        autoRespawn = tag.contains("autoRespawn") ? tag.getBoolean("autoRespawn") : false;
        offsetX = tag.contains("offsetX") ? tag.getDouble("offsetX") : 0.0;
        offsetY = tag.contains("offsetY") ? tag.getDouble("offsetY") : 1.0;
        offsetZ = tag.contains("offsetZ") ? tag.getDouble("offsetZ") : 0.0;
        yaw = tag.contains("yaw") ? tag.getFloat("yaw") : 0.0f;
        pitch = tag.contains("pitch") ? tag.getFloat("pitch") : 0.0f;
        deployedUUID = tag.getString("deployedUUID");
        cooldownEnd = tag.getLong("cooldownEnd");
        deployNBT = tag.contains("deployNBT", Tag.TAG_COMPOUND) ? tag.getCompound("deployNBT") : new CompoundTag();
        displayName = tag.getString("displayName");
        spawnWithAmmo = !tag.contains("spawnWithAmmo") || tag.getBoolean("spawnWithAmmo");
    }

    // ========== MenuProvider ==========

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.siege_tools.vehicle_deployer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return null; // UI handled by BlockUI interface on the block
    }
}
