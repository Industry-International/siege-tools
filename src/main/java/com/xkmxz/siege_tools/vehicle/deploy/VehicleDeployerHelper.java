package com.xkmxz.siege_tools.vehicle.deploy;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
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
    private static final Gson GSON = new Gson();

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

        // 从数据库获取车辆信息的 nbtTemplate
        JsonObject vehicleInfo = VehicleDataManager.getVehicle(vehicleType);
        CompoundTag nbt = new CompoundTag();

        if (vehicleInfo != null && vehicleInfo.has("nbtTemplate")) {
            JsonObject template = vehicleInfo.getAsJsonObject("nbtTemplate");
            nbt = JsonToNBTConverter.toCompoundTag(template);
            LOGGER.info("[Deploy] 使用数据库模板: {}", vehicleType);

            // 写入分类信息
            if (vehicleInfo.has("category")) {
                // 存到 persistentData 或直接作为 NBT 的一部分
            }
        } else {
            LOGGER.warn("[Deploy] 数据库未找到车辆 {}，使用空白模板", vehicleType);
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

        // 叠加用户 deployNBT
        String deployNBTStr = be.getDeployNBT();
        if (deployNBTStr != null && !deployNBTStr.isEmpty() && !"{}".equals(deployNBTStr)) {
            try {
                JsonObject deployObj = GSON.fromJson(deployNBTStr, JsonObject.class);
                if (deployObj != null) {
                    JsonToNBTConverter.mergeDeployNBT(nbt, deployObj);
                    LOGGER.info("[Deploy] 合并用户 deployNBT");
                }
            } catch (Exception e) {
                LOGGER.warn("[Deploy] deployNBT JSON 解析失败: {}", e.getMessage());
            }
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
}
