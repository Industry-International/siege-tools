package com.xkmxz.siege_tools.vehicle.data;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 载具数据库加载器。
 * 从 ResourceManager 读取 data/siege_tools/sbw_vehicle_db/ 下的数据文件，
 * 自动发现分类目录，解析新格式 JSON，生成完整 spawnNBT。
 */
public class VehicleDataLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final String DATA_ROOT = "sbw_vehicle_db";

    /**
     * 从 ResourceManager 加载载具数据库。
     */
    public static VehicleDatabase load(ResourceManager resourceManager) {
        LOGGER.info("[VehicleDataLoader] 开始加载载具数据库...");

        // 1. 读取 _registry.json 获取分类元信息
        JsonObject registry = loadJson(resourceManager, DATA_ROOT + "/_registry.json");
        if (registry == null || !registry.has("categories")) {
            LOGGER.warn("[VehicleDataLoader] _registry.json 读取失败");
            return new VehicleDatabase();
        }

        JsonObject categoriesDef = registry.getAsJsonObject("categories");
        Map<String, VehicleDatabase.CategoryInfo> categories = new LinkedHashMap<>();
        Map<String, VehicleData> byId = new LinkedHashMap<>();
        Map<String, List<String>> byCategory = new LinkedHashMap<>();

        // 2. 遍历分类目录自动发现
        for (Map.Entry<String, JsonElement> catEntry : categoriesDef.entrySet()) {
            String catKey = catEntry.getKey();
            JsonObject catInfo = catEntry.getValue().getAsJsonObject();

            String displayName = catInfo.has("displayName") ? catInfo.get("displayName").getAsString() : catKey;
            String description = catInfo.has("description") ? catInfo.get("description").getAsString() : "";

            categories.put(catKey, new VehicleDatabase.CategoryInfo(catKey, displayName, description));
            List<String> vehicleList = new ArrayList<>();
            byCategory.put(catKey, vehicleList);

            // 3. 自动发现该分类目录下的所有 .json 文件
            String dirPath = DATA_ROOT + "/" + catKey;
            List<String> fileNames = listDirectory(resourceManager, dirPath);

            for (String fileName : fileNames) {
                if (!fileName.endsWith(".json")) continue;

                String filePath = dirPath + "/" + fileName;
                try {
                    JsonObject vehicleJson = loadJson(resourceManager, filePath);
                    if (vehicleJson == null || !vehicleJson.has("vehicleId")) {
                        LOGGER.warn("[VehicleDataLoader] 文件格式错误: {}", filePath);
                        continue;
                    }

                    VehicleData vehicleData = parseVehicle(vehicleJson, catKey);
                    if (vehicleData != null) {
                        String vid = vehicleData.vehicleId();
                        byId.put(vid, vehicleData);
                        vehicleList.add(vid);
                    }
                } catch (Exception e) {
                    LOGGER.warn("[VehicleDataLoader] 读取失败: {} - {}", filePath, e.getMessage());
                }
            }

            if (vehicleList.isEmpty()) {
                LOGGER.warn("[VehicleDataLoader] 分类 '{}' 下无载具文件", catKey);
            }
        }

        VehicleDatabase db = new VehicleDatabase(categories, byId, byCategory);
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
     * 解析单辆载具的 JSON → VehicleData record，并生成完整 spawnNBT。
     */
    @Nullable
    private static VehicleData parseVehicle(JsonObject json, String category) {
        try {
            int formatVersion = json.has("formatVersion") ? json.get("formatVersion").getAsInt() : 1;
            String vehicleId = json.get("vehicleId").getAsString();
            String mod = json.has("mod") ? json.get("mod").getAsString() : null;
            String displayType = json.has("displayType") ? json.get("displayType").getAsString() : null;
            String hudType = json.has("hudType") ? json.get("hudType").getAsString() : null;

            // stats
            VehicleData.VehicleStats stats = null;
            if (json.has("stats")) {
                JsonObject s = json.getAsJsonObject("stats");
                stats = new VehicleData.VehicleStats(
                        getInt(s, "maxHealth"),
                        getInt(s, "maxEnergy"),
                        getDouble(s, "mass"),
                        getDouble(s, "upStep"),
                        getInt(s, "seatCount"),
                        getString(s, "containerType"),
                        getString(s, "engineType"),
                        s.has("hasDecoy") ? s.get("hasDecoy").getAsBoolean() : null
                );
            }

            // parts
            List<String> parts = null;
            if (json.has("parts")) {
                JsonArray arr = json.getAsJsonArray("parts");
                parts = new ArrayList<>();
                for (JsonElement e : arr) parts.add(e.getAsString());
            }

            // weapons
            List<VehicleData.WeaponData> weapons = null;
            if (json.has("weapons")) {
                JsonArray arr = json.getAsJsonArray("weapons");
                weapons = new ArrayList<>();
                for (JsonElement e : arr) {
                    JsonObject w = e.getAsJsonObject();
                    String key = w.get("key").getAsString();
                    String displayKey = w.has("displayKey") ? w.get("displayKey").getAsString() : null;
                    List<String> ammoTypes = new ArrayList<>();
                    for (JsonElement at : w.getAsJsonArray("ammoTypes")) ammoTypes.add(at.getAsString());
                    int magazine = w.has("magazine") ? w.get("magazine").getAsInt() : 1;
                    Integer rpm = w.has("rpm") ? w.get("rpm").getAsInt() : null;
                    Double damage = w.has("damage") ? w.get("damage").getAsDouble() : null;
                    weapons.add(new VehicleData.WeaponData(key, displayKey, ammoTypes, magazine, rpm, damage));
                }
            }

            // defaultAmmo
            Map<String, Integer> defaultAmmo = null;
            if (json.has("defaultAmmo")) {
                JsonObject am = json.getAsJsonObject("defaultAmmo");
                defaultAmmo = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : am.entrySet()) {
                    defaultAmmo.put(e.getKey(), e.getValue().getAsInt());
                }
            }

            // rawSpawnNbt
            JsonObject rawSpawnNbt = json.has("spawnNbt") ? json.getAsJsonObject("spawnNbt") : null;

            // 生成完整 spawnNbt
            CompoundTag fullSpawnNbt = generateFullSpawnNbt(rawSpawnNbt, weapons, defaultAmmo);

            return new VehicleData(formatVersion, vehicleId, mod, displayType, hudType,
                    stats, parts, weapons, defaultAmmo, rawSpawnNbt, fullSpawnNbt);

        } catch (Exception e) {
            LOGGER.warn("[VehicleDataLoader] 解析载具数据失败 ({}): {}", category, e.getMessage());
            return null;
        }
    }

    /**
     * 生成完整的 spawnNbt：
     * 1. 从 rawSpawnNbt 复制基础字段
     * 2. 从 weapons 生成 WeaponState
     * 3. 从 defaultAmmo 生成 Inventory.Items
     */
    private static CompoundTag generateFullSpawnNbt(
            @Nullable JsonObject rawSpawnNbt,
            @Nullable List<VehicleData.WeaponData> weapons,
            @Nullable Map<String, Integer> defaultAmmo
    ) {
        CompoundTag nbt;

        // 1. 基础字段
        if (rawSpawnNbt != null) {
            nbt = toCompoundTag(rawSpawnNbt);
        } else {
            nbt = new CompoundTag();
        }

        // 2. WeaponState
        if (weapons != null && !weapons.isEmpty()) {
            CompoundTag weaponState = new CompoundTag();
            for (VehicleData.WeaponData w : weapons) {
                CompoundTag gunData = new CompoundTag();
                CompoundTag customData = new CompoundTag();
                CompoundTag gunDataInner = new CompoundTag();
                gunDataInner.putInt("Ammo", 1);
                customData.put("GunData", gunDataInner);
                gunData.put("components", new CompoundTag());
                gunData.getCompound("components").put("minecraft:custom_data", customData);
                weaponState.put(w.key(), gunData);
            }
            nbt.put("WeaponState", weaponState);
        }

        // 3. Inventory.Items
        if (defaultAmmo != null && !defaultAmmo.isEmpty()) {
            ListTag items = new ListTag();
            int slot = 0;
            for (Map.Entry<String, Integer> entry : defaultAmmo.entrySet()) {
                String itemId = entry.getKey();
                int count = entry.getValue();
                if (count <= 0) continue;

                CompoundTag item = new CompoundTag();
                item.putString("id", itemId);
                item.putInt("count", count);
                item.putInt("Slot", slot);
                items.add(item);
                slot++;
            }
            if (!items.isEmpty()) {
                CompoundTag inventory = new CompoundTag();
                inventory.put("Items", items);
                nbt.put("Inventory", inventory);
            }
        }

        return nbt;
    }

    // ========== 工具方法 ==========

    /** 从 ResourceManager 读取并解析 JSON 文件 */
    @Nullable
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

    /** 通过 ResourceManager 列出指定路径下的所有 .json 文件 */
    private static List<String> listDirectory(ResourceManager resourceManager, String pathPrefix) {
        Set<String> files = new LinkedHashSet<>();
        Map<ResourceLocation, Resource> map = resourceManager.listResources("siege_tools",
                loc -> loc.getPath().startsWith(pathPrefix + "/") && loc.getPath().endsWith(".json"));
        for (ResourceLocation loc : map.keySet()) {
            String fullPath = loc.getPath();
            // 从 fullPath 提取文件名
            if (fullPath.startsWith(pathPrefix + "/")) {
                String fileName = fullPath.substring(pathPrefix.length() + 1);
                if (!fileName.startsWith("_") && !fileName.isEmpty()) {
                    files.add(fileName);
                }
            }
        }
        return new ArrayList<>(files);
    }

    private static CompoundTag toCompoundTag(JsonObject obj) {
        return com.xkmxz.siege_tools.vehicle.util.JsonToNBTConverter.toCompoundTag(obj);
    }

    // ========== JSON 安全读取 ==========

    @Nullable
    private static Integer getInt(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsInt() : null;
    }

    @Nullable
    private static Double getDouble(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsDouble() : null;
    }

    @Nullable
    private static String getString(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsString() : null;
    }
}
