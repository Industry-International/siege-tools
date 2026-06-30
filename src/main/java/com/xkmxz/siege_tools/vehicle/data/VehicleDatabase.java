package com.xkmxz.siege_tools.vehicle.data;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 运行时载具数据库 — 内存缓存。
 * 数据从 {@link VehicleDataLoader} 加载，存储为强类型的 {@link VehicleData}。
 */
public class VehicleDatabase {

    private final boolean loaded;
    private final int vehicleCount;
    private final Map<String, CategoryInfo> categories;
    private final Map<String, VehicleData> byId;
    private final Map<String, List<String>> byCategory;

    /** 空数据库（加载失败或尚未加载） */
    public VehicleDatabase() {
        this.loaded = false;
        this.vehicleCount = 0;
        this.categories = Collections.emptyMap();
        this.byId = Collections.emptyMap();
        this.byCategory = Collections.emptyMap();
    }

    public VehicleDatabase(
            Map<String, CategoryInfo> categories,
            Map<String, VehicleData> byId,
            Map<String, List<String>> byCategory
    ) {
        this.loaded = true;
        this.categories = Collections.unmodifiableMap(new LinkedHashMap<>(categories));
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
        this.byCategory = Collections.unmodifiableMap(new LinkedHashMap<>(byCategory));
        this.vehicleCount = byId.size();
    }

    public boolean isLoaded() { return loaded; }
    public int getVehicleCount() { return vehicleCount; }
    public Map<String, CategoryInfo> getCategories() { return categories; }
    public Map<String, VehicleData> getById() { return byId; }
    public Map<String, List<String>> getByCategory() { return byCategory; }

    /** 通过 vehicleId 获取载具数据 */
    @Nullable
    public VehicleData getVehicle(String vehicleId) {
        return byId.get(vehicleId);
    }

    /** 获取指定分类下的所有载具 ID 列表 */
    public List<String> getVehiclesByCategory(String categoryKey) {
        return byCategory.getOrDefault(categoryKey, Collections.emptyList());
    }

    public Set<String> getAllVehicleIds() { return byId.keySet(); }
    public Set<String> getAllCategoryKeys() { return categories.keySet(); }

    /** 分类元信息 */
    public record CategoryInfo(
            String key,
            String displayName,
            String description
    ) {}
}
