package com.xkmxz.siege_tools.vehicle.event;

import com.mojang.logging.LogUtils;
import com.xkmxz.siege_tools.vehicle.data.VehicleDataManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * 服务器生命周期处理器。
 * 替代 KubeJS main.js 的 ServerEvents.loaded/unloaded。
 */
@EventBusSubscriber
public class ServerLifecycleHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // 加载载具数据库
        VehicleDataManager.load(event.getServer().getResourceManager());
        var db = VehicleDataManager.getDatabase();
        if (db.isLoaded()) {
            LOGGER.info("[VehicleSystem] 载具数据库已加载: {} 种载具, {} 个分类",
                    db.getVehicleCount(), db.getCategories().size());
        }

        // 确保全局系统开关
        var serverData = event.getServer().getWorldData().getGameRules();
        if (!event.getServer().getPlayerList().getPlayers().isEmpty()) {
            LOGGER.info("[VehicleSystem] 系统已激活");
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        VehicleDataManager.reset();
        LOGGER.info("[VehicleSystem] 服务器关闭，清理完成");
    }
}
