package com.xkmxz.siege_tools.vehicle.data;

import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 载具数据管理器 — 全局单例。
 * 持有 VehicleDatabase 和 AmmoTypeRegistry 实例。
 * 在服务器启动时初始化。
 */
public class VehicleDataManager {

    private static VehicleDatabase currentDatabase = new VehicleDatabase();
    private static AmmoTypeRegistry currentAmmoTypes = new AmmoTypeRegistry();

    public static void load(ResourceManager resourceManager) {
        currentDatabase = VehicleDataLoader.load(resourceManager);
        currentAmmoTypes = VehicleDataLoader.loadAmmoTypes(resourceManager);
    }

    public static void reset() {
        currentDatabase = new VehicleDatabase();
        currentAmmoTypes = new AmmoTypeRegistry();
    }

    public static VehicleDatabase getDatabase() {
        return currentDatabase;
    }

    public static AmmoTypeRegistry getAmmoTypes() {
        return currentAmmoTypes;
    }

    /** 通过车辆 ID 获取车辆数据 */
    public static com.google.gson.JsonObject getVehicle(String vehicleId) {
        return currentDatabase.getVehicle(vehicleId);
    }

    /** 弹药短名查询工具 */
    public static String getAmmoShortName(String fullId) {
        return currentAmmoTypes.getShortName(fullId);
    }

    public static AmmoTypeRegistry.AmmoTypeInfo getAmmoType(String shortName) {
        return currentAmmoTypes.getAmmoType(shortName);
    }

    /** 判断实体是否为 SBW/MCSP 载具 */
    public static boolean isSBWVehicle(String entityType) {
        return entityType != null && (entityType.startsWith("superbwarfare:") || entityType.startsWith("mcsp:"));
    }
}
