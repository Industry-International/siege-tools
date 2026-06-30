package com.xkmxz.siege_tools.vehicle.data;

import com.google.gson.JsonObject;

import java.util.*;

/**
 * 运行时载具数据库 — 内存缓存。
 * 包含所有已加载载具的 byId / byCategory 索引。
 */
public class VehicleDatabase {

    private final boolean loaded;
    private final int vehicleCount;
    private final JsonObject registry;
    private final Map<String, CategoryInfo> categories;
    private final Map<String, JsonObject> byId;
    private final Map<String, List<String>> byCategory;

    public VehicleDatabase() {
        this.loaded = false;
        this.vehicleCount = 0;
        this.registry = null;
        this.categories = Collections.emptyMap();
        this.byId = Collections.emptyMap();
        this.byCategory = Collections.emptyMap();
    }

    public VehicleDatabase(JsonObject registry,
                           Map<String, CategoryInfo> categories,
                           Map<String, JsonObject> byId,
                           Map<String, List<String>> byCategory) {
        this.loaded = true;
        this.registry = registry;
        this.categories = Collections.unmodifiableMap(new HashMap<>(categories));
        this.byId = Collections.unmodifiableMap(new HashMap<>(byId));
        this.byCategory = Collections.unmodifiableMap(new HashMap<>(byCategory));

        int count = 0;
        for (Map.Entry<String, JsonObject> entry : byId.entrySet()) {
            if (entry.getValue() != null) count++;
        }
        this.vehicleCount = count;
    }

    public boolean isLoaded() { return loaded; }
    public int getVehicleCount() { return vehicleCount; }
    public JsonObject getRegistry() { return registry; }
    public Map<String, CategoryInfo> getCategories() { return categories; }
    public Map<String, JsonObject> getById() { return byId; }
    public Map<String, List<String>> getByCategory() { return byCategory; }

    public JsonObject getVehicle(String vehicleId) {
        return byId.get(vehicleId);
    }

    public List<String> getVehiclesByCategory(String category) {
        return byCategory.getOrDefault(category, Collections.emptyList());
    }

    public Set<String> getAllVehicleIds() {
        return byId.keySet();
    }

    public Set<String> getAllCategoryKeys() {
        return categories.keySet();
    }

    public static class CategoryInfo {
        private final String key;
        private final String displayName;
        private final String description;
        private final boolean enabled;

        public CategoryInfo(String key, String displayName, String description, boolean enabled) {
            this.key = key;
            this.displayName = displayName;
            this.description = description;
            this.enabled = enabled;
        }

        public String getKey() { return key; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public boolean isEnabled() { return enabled; }
    }
}
