package com.xkmxz.siege_tools.vehicle.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 载具数据管理器 — 全局单例。
 * 持有 VehicleDatabase、AmmoTypeRegistry 以及方块默认配置。
 * 在服务器启动时初始化。
 */
public class VehicleDataManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private static VehicleDatabase currentDatabase = new VehicleDatabase();
    private static AmmoTypeRegistry currentAmmoTypes = new AmmoTypeRegistry();

    // 默认配置（从数据包加载）
    private static CompoundTag defaultDeployerConfig = new CompoundTag();
    private static CompoundTag defaultAmmoConfig = new CompoundTag();

    public static void load(ResourceManager resourceManager) {
        currentDatabase = VehicleDataLoader.load(resourceManager);
        currentAmmoTypes = VehicleDataLoader.loadAmmoTypes(resourceManager);
        defaultDeployerConfig = loadDefaultConfig(resourceManager, "sbw_vehicle_db/_default_deployer.json");
        defaultAmmoConfig = loadDefaultConfig(resourceManager, "sbw_vehicle_db/_default_ammo.json");
    }

    public static void reset() {
        currentDatabase = new VehicleDatabase();
        currentAmmoTypes = new AmmoTypeRegistry();
        defaultDeployerConfig = new CompoundTag();
        defaultAmmoConfig = new CompoundTag();
    }

    public static VehicleDatabase getDatabase() { return currentDatabase; }
    public static AmmoTypeRegistry getAmmoTypes() { return currentAmmoTypes; }

    /** 部署台默认配置（CompoundTag） */
    public static CompoundTag getDefaultDeployerConfig() { return defaultDeployerConfig.copy(); }

    /** 弹药补给站默认配置（CompoundTag） */
    public static CompoundTag getDefaultAmmoConfig() { return defaultAmmoConfig.copy(); }

    /** 通过车辆 ID 获取车辆数据 */
    @Nullable
    public static VehicleData getVehicle(String vehicleId) {
        return currentDatabase.getVehicle(vehicleId);
    }

    /** 弹药短名查询工具 */
    @Nullable
    public static String getAmmoShortName(String fullId) {
        return currentAmmoTypes.getShortName(fullId);
    }

    @Nullable
    public static AmmoTypeRegistry.AmmoTypeInfo getAmmoType(String shortName) {
        return currentAmmoTypes.getAmmoType(shortName);
    }

    /** 判断实体是否为 SBW/MCSP 载具 */
    public static boolean isSBWVehicle(String entityType) {
        return entityType != null && (entityType.startsWith("superbwarfare:") || entityType.startsWith("mcsp:"));
    }

    // ========== 内部：加载默认配置 ==========

    private static CompoundTag loadDefaultConfig(ResourceManager resourceManager, String path) {
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("siege_tools", path);
        try {
            Optional<net.minecraft.server.packs.resources.Resource> resourceOpt = resourceManager.getResource(loc);
            if (resourceOpt.isEmpty()) {
                LOGGER.warn("[VehicleDataManager] 默认配置不存在: {}", loc);
                return new CompoundTag();
            }
            try (InputStream is = resourceOpt.get().open();
                 Reader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                return jsonToCompoundTag(json);
            }
        } catch (Exception e) {
            LOGGER.warn("[VehicleDataManager] 加载默认配置失败: {} - {}", loc, e.getMessage());
            return new CompoundTag();
        }
    }

    private static CompoundTag jsonToCompoundTag(JsonObject json) {
        CompoundTag tag = new CompoundTag();
        for (String key : json.keySet()) {
            var element = json.get(key);
            if (element == null || element.isJsonNull()) continue;
            if (element.isJsonObject()) {
                tag.put(key, jsonToCompoundTag(element.getAsJsonObject()));
            } else if (element.isJsonPrimitive()) {
                var prim = element.getAsJsonPrimitive();
                if (prim.isNumber()) {
                    double d = prim.getAsDouble();
                    if (d == (int) d) tag.putInt(key, prim.getAsInt());
                    else tag.putDouble(key, d);
                } else if (prim.isBoolean()) {
                    tag.putBoolean(key, prim.getAsBoolean());
                } else {
                    tag.putString(key, prim.getAsString());
                }
            }
        }
        return tag;
    }
}
