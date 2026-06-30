package com.xkmxz.siege_tools.vehicle.deploy;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.xkmxz.siege_tools.vehicle.block.VehicleDeployerBlockEntity;
import com.xkmxz.siege_tools.vehicle.data.VehicleDataManager;
import com.xkmxz.siege_tools.vehicle.util.JsonToNBTConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * 载具部署工具。
 * 替代 KubeJS deploy.js 的 spawnVehicleForBlock() + getSpawnPosition() + makeDeployTag()。
 */
public class VehicleDeployerHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

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

        // 直接创建实体（不用 /summon 命令，直接获取 UUID）
        Entity entity;
        try {
            var resourceKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .get(net.minecraft.resources.ResourceLocation.parse(vehicleType));
            if (resourceKey == null) {
                LOGGER.error("[Deploy] 未知实体类型: {}", vehicleType);
                return;
            }
            entity = resourceKey.create(level);
            if (entity == null) {
                LOGGER.error("[Deploy] 创建实体失败: {}", vehicleType);
                return;
            }
        } catch (Exception e) {
            LOGGER.error("[Deploy] 实体类型解析失败: {}", vehicleType, e);
            return;
        }

        // 加载 NBT
        entity.load(nbt);

        // 设置位置和旋转
        entity.setPos(x, y, z);
        entity.setYRot(yaw);
        entity.setXRot(pitch);

        // 添加至世界
        level.addFreshEntity(entity);

        // 立即捕获 UUID（无需 1 tick 延迟，也无需 tag）
        String uuid = entity.getUUID().toString();
        be.setDeployedUUID(uuid);
        LOGGER.info("[Deploy] 载具已部署 @[{},{},{}] UUID={}... type={}",
                pos.getX(), pos.getY(), pos.getZ(),
                uuid.substring(0, Math.min(8, uuid.length())), vehicleType);
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
