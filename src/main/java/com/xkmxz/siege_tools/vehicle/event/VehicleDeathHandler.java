package com.xkmxz.siege_tools.vehicle.event;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;

import com.xkmxz.siege_tools.vehicle.block.VehicleDeployerBlockEntity;

/**
 * 载具死亡事件处理器。
 * 替代 KubeJS main.js 的 EntityEvents.death。
 */
@EventBusSubscriber
public class VehicleDeathHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        // 检查标签 sbw_deploy_ 开头
        String deployTag = null;
        for (String tag : entity.getTags()) {
            if (tag.startsWith("sbw_deploy_")) {
                deployTag = tag;
                break;
            }
        }
        if (deployTag == null) return; // 不是由部署台生成的载具

        // 解析部署台位置: sbw_deploy_x_y_z
        String[] parts = deployTag.split("_");
        if (parts.length < 5) {
            LOGGER.warn("[VehicleDeath] 部署标签格式异常: {}", deployTag);
            return;
        }

        try {
            int bx = Integer.parseInt(parts[2]);
            int by = Integer.parseInt(parts[3]);
            int bz = Integer.parseInt(parts[4]);

            // 找到部署台方块
            BlockPos deployerPos = new BlockPos(bx, by, bz);
            BlockEntity be = entity.level().getBlockEntity(deployerPos);

            if (be instanceof VehicleDeployerBlockEntity deployer) {
                String deployedUUID = deployer.getDeployedUUID();
                String entityUUID = entity.getUUID().toString();

                if (deployedUUID != null && deployedUUID.equals(entityUUID)) {
                    LOGGER.info("[VehicleDeath] 载具被摧毁 @[{},{},{}] {}", bx, by, bz, entity.getType().builtInRegistryHolder().key().location());

                    deployer.setDeployedUUID("");
                    long gameTime = entity.level().getGameTime();
                    deployer.setCooldownEnd(gameTime + deployer.getRespawnDelay());
                } else {
                    LOGGER.warn("[VehicleDeath] 载具 UUID 不匹配，忽略: 实体={}... 方块={}...",
                            entityUUID.substring(0, Math.min(8, entityUUID.length())),
                            deployedUUID != null ? deployedUUID.substring(0, Math.min(8, deployedUUID.length())) : "空");
                }
            } else {
                LOGGER.warn("[VehicleDeath] 未找到部署台方块 @[{},{},{}]", bx, by, bz);
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("[VehicleDeath] 部署标签坐标解析失败: {}", deployTag);
        }
    }
}
