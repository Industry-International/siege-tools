package com.xkmxz.siege_tools.vehicle.deploy;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.xkmxz.siege_tools.vehicle.block.VehicleDeployerBlockEntity;
import com.xkmxz.siege_tools.vehicle.data.VehicleDataManager;
import com.xkmxz.siege_tools.vehicle.util.JsonToNBTConverter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * 载具部署工具。
 * 替代 KubeJS deploy.js 的 spawnVehicleForBlock() + getSpawnPosition() + makeDeployTag()。
 */
public class VehicleDeployerHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 生成部署位置标签（用于实体反查方块） */
    public static String makeDeployTag(BlockPos pos) {
        return "sbw_deploy_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }

    /**
     * 从方块配置计算生成位置
     */
    public static Vec3 getSpawnPosition(VehicleDeployerBlockEntity be, BlockPos blockPos) {
        double x = blockPos.getX() + be.getOffsetX() + 0.5;
        double y = blockPos.getY() + be.getOffsetY();
        double z = blockPos.getZ() + be.getOffsetZ() + 0.5;
        return new Vec3(x, y, z);
    }

    /**
     * 为部署台召唤一辆载具。
     * 替代 KubeJS spawnVehicleForBlock()。
     */
    public static void spawnVehicle(ServerLevel level, BlockPos pos, VehicleDeployerBlockEntity be) {
        String vehicleType = be.getVehicleType();
        if (vehicleType == null || vehicleType.isEmpty()) {
            LOGGER.warn("[Deploy] 方块未配置载具类型，跳过 @[{},{},{}]", pos.getX(), pos.getY(), pos.getZ());
            return;
        }

        Vec3 spawnPos = getSpawnPosition(be, pos);
        double x = spawnPos.x, y = spawnPos.y, z = spawnPos.z;
        float yaw = be.getYaw(), pitch = be.getPitch();
        String tag = makeDeployTag(pos);

        // 从数据库获取车辆数据
        com.xkmxz.siege_tools.vehicle.data.VehicleData vehicleInfo = VehicleDataManager.getVehicle(vehicleType);
        CompoundTag nbt;

        if (vehicleInfo != null) {
            // 使用加载时已生成的完整 spawnNbt（含 Inventory + WeaponState）
            nbt = vehicleInfo.fullSpawnNbt().copy();
            LOGGER.info("[Deploy] 使用数据库模板: {}", vehicleType);
        } else {
            LOGGER.warn("[Deploy] 数据库未找到车辆 {}，使用空白模板", vehicleType);
            nbt = new CompoundTag();
        }

        // 叠加 Rotation
        ListTag rotList = new ListTag();
        rotList.add(FloatTag.valueOf(yaw));
        rotList.add(FloatTag.valueOf(pitch));
        nbt.put("Rotation", rotList);

        // 叠加 Tags
        ListTag tagsList = new ListTag();
        tagsList.add(StringTag.valueOf(tag));
        // 保留旧标签
        if (nbt.contains("Tags", Tag.TAG_LIST)) {
            ListTag oldTags = nbt.getList("Tags", Tag.TAG_STRING);
            for (int i = 0; i < oldTags.size(); i++) {
                String oldTag = oldTags.getString(i);
                if (oldTag.startsWith("sbw_")) {
                    tagsList.add(StringTag.valueOf(oldTag));
                }
            }
        }
        nbt.put("Tags", tagsList);

        // 叠加用户 deployNBT（直接合并 CompoundTag）
        CompoundTag deployNBT = be.getDeployNBT();
        if (deployNBT != null && !deployNBT.isEmpty()) {
            mergeCompoundTag(nbt, deployNBT);
            LOGGER.info("[Deploy] 合并用户 deployNBT");
        }

        // spawnWithAmmo = 0 时清除 Inventory
        if (!be.isSpawnWithAmmo() && nbt.contains("Inventory")) {
            nbt.remove("Inventory");
            LOGGER.info("[Deploy] spawnWithAmmo=0，已清除 Inventory");
        }

        // 使用 summon 命令
        CommandSourceStack source = level.getServer().createCommandSourceStack()
                .withSuppressedOutput()
                .withPermission(2);
        String cmd = "summon " + vehicleType + " " + x + " " + y + " " + z + " " + nbt;
        level.getServer().getCommands().performPrefixedCommand(source, cmd);

        // 1 tick 后捕获 UUID
        final BlockPos fPos = pos.immutable();
        final String fTag = tag;
        level.getServer().execute(() -> {
            captureDeployedUUID(level, fPos, fTag);
        });
    }

    /**
     * 部署后捕获实体的 UUID 并写入方块。
     * 替代 KubeJS deploy.js 的 server.scheduleInTicks(1, ...)。
     */
    private static void captureDeployedUUID(ServerLevel level, BlockPos pos, String tag) {
        for (Entity entity : level.getAllEntities()) {
            if (entity.isRemoved()) continue;
            for (String entityTag : entity.getTags()) {
                if (entityTag.equals(tag)) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof VehicleDeployerBlockEntity deployer) {
                        String uuid = entity.getUUID().toString();
                        deployer.setDeployedUUID(uuid);
                        LOGGER.info("[Deploy] 载具已部署 @[{},{},{}] UUID={}...",
                                pos.getX(), pos.getY(), pos.getZ(),
                                uuid.substring(0, Math.min(8, uuid.length())));
                    }
                    return;
                }
            }
        }
        LOGGER.warn("[Deploy] 部署后未找到标签匹配的实体 @[{},{},{}]", pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * 将 source 中的所有键递归合并到 target 的 CompoundTag 中。
     * 对于同为 CompoundTag 的子节点做深度合并，否则直接覆盖。
     */
    private static void mergeCompoundTag(CompoundTag target, CompoundTag source) {
        if (source == null || source.isEmpty()) return;
        for (String key : source.getAllKeys()) {
            Tag incoming = source.get(key);
            if (incoming == null) continue;
            if (target.contains(key) && target.get(key) instanceof CompoundTag existing
                    && incoming instanceof CompoundTag incomingCompound) {
                // 递归合并子对象
                for (String subKey : incomingCompound.getAllKeys()) {
                    existing.put(subKey, incomingCompound.get(subKey));
                }
            } else {
                target.put(key, incoming);
            }
        }
    }
}
