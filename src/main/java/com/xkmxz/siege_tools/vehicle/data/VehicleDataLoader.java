package com.xkmxz.siege_tools.vehicle.data;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 载具数据库加载器。
 * 从 ResourceManager 读取 data/siege_tools/sbw_vehicle_db/ 下的数据文件，
 * 构建 VehicleDatabase 运行时实例。
 *
 * 替代 KubeJS tools/database.js 的 loadVehicleDB()。
 */
public class VehicleDataLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    /** 数据文件在 resources 中的根路径 */
    private static final String DATA_ROOT = "sbw_vehicle_db";

    /**
     * 从 ResourceManager 加载载具数据库。
     * 应在 ServerLifecycleHandler 服务端启动时调用。
     */
    public static VehicleDatabase load(ResourceManager resourceManager) {
        LOGGER.info("[VehicleDataLoader] 开始加载载具数据库...");

        // 1. 读取 _registry.json
        JsonObject registry = loadJson(resourceManager, DATA_ROOT + "/_registry.json");
        if (registry == null || !registry.has("categories")) {
            LOGGER.warn("[VehicleDataLoader] _registry.json 读取失败或格式错误");
            return new VehicleDatabase();
        }

        JsonObject categoriesObj = registry.getAsJsonObject("categories");
        Map<String, VehicleDatabase.CategoryInfo> categories = new HashMap<>();
        Map<String, JsonObject> byId = new HashMap<>();
        Map<String, List<String>> byCategory = new HashMap<>();

        // 2. 遍历分类
        for (Map.Entry<String, JsonElement> catEntry : categoriesObj.entrySet()) {
            String catKey = catEntry.getKey();
            JsonObject catInfo = catEntry.getValue().getAsJsonObject();

            // 跳过未启用的分类
            if (catInfo.has("enabled") && !catInfo.get("enabled").getAsBoolean()) {
                LOGGER.info("[VehicleDataLoader] 跳过已禁用分类: {}", catKey);
                continue;
            }

            String displayName = catInfo.has("displayName") ? catInfo.get("displayName").getAsString() : catKey;
            String description = catInfo.has("description") ? catInfo.get("description").getAsString() : "";

            categories.put(catKey, new VehicleDatabase.CategoryInfo(catKey, displayName, description, true));
            List<String> vehicleList = new ArrayList<>();
            byCategory.put(catKey, vehicleList);

            // 3. 读取该分类下的载具文件
            if (!catInfo.has("files")) {
                LOGGER.warn("[VehicleDataLoader] 分类 {} 无文件列表", catKey);
                continue;
            }

            JsonArray files = catInfo.getAsJsonArray("files");
            for (JsonElement fileElem : files) {
                String filename = fileElem.getAsString();
                String filePath = DATA_ROOT + "/" + catKey + "/" + filename;

                try {
                    JsonObject vehicleData = loadJson(resourceManager, filePath);
                    if (vehicleData == null || !vehicleData.has("vehicleId")) {
                        LOGGER.warn("[VehicleDataLoader] 文件格式错误: {}", filePath);
                        continue;
                    }

                    String vid = vehicleData.get("vehicleId").getAsString();
                    byId.put(vid, vehicleData);
                    vehicleList.add(vid);

                } catch (Exception e) {
                    LOGGER.warn("[VehicleDataLoader] 读取失败: {} - {}", filePath, e.getMessage());
                }
            }
        }

        VehicleDatabase db = new VehicleDatabase(registry, categories, byId, byCategory);
        LOGGER.info("[VehicleDataLoader] 加载完成: {} 辆载具, {} 个分类",
                db.getVehicleCount(), categories.size());
        return db;
    }

    /**
     * 从 ResourceManager 加载弹药类型注册表。
     */
    public static AmmoTypeRegistry loadAmmoTypes(ResourceManager resourceManager) {
        JsonObject json = loadJson(resourceManager, DATA_ROOT + "/_ammo_types.json");
        return new AmmoTypeRegistry(json);
    }

    /**
     * 从 ResourceManager 读取并解析 JSON 文件。
     */
    private static JsonObject loadJson(ResourceManager resourceManager, String path) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("siege_tools", path);
        try {
            Optional<Resource> resourceOpt = resourceManager.getResource(location);
            if (resourceOpt.isEmpty()) {
                LOGGER.warn("[VehicleDataLoader] 资源不存在: {}", location);
                return null;
            }
            try (InputStream is = resourceOpt.get().open();
                 Reader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return GSON.fromJson(reader, JsonObject.class);
            }
        } catch (Exception e) {
            LOGGER.warn("[VehicleDataLoader] 读取 JSON 失败: {} - {}", location, e.getMessage());
            return null;
        }
    }
}
