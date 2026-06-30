package com.xkmxz.siege_tools.vehicle.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;

/**
 * 弹药类型注册表。
 * 从 _ammo_types.json 加载，维护短名↔完整ID映射。
 * 替代 KubeJS tools/database.js 中的 getAmmoType / getAmmoShortName 等函数。
 */
public class AmmoTypeRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final boolean loaded;
    private final Map<String, AmmoTypeInfo> byShortName;
    private final Map<String, String> byFullId;  // fullId → shortName

    public AmmoTypeRegistry() {
        this.loaded = false;
        this.byShortName = Collections.emptyMap();
        this.byFullId = Collections.emptyMap();
    }

    public AmmoTypeRegistry(JsonObject json) {
        Map<String, AmmoTypeInfo> shortNameMap = new HashMap<>();
        Map<String, String> fullIdMap = new HashMap<>();

        if (json == null || !json.has("ammoTypes")) {
            LOGGER.warn("[AmmoTypeRegistry] _ammo_types.json 缺失或格式错误");
            this.loaded = false;
            this.byShortName = Collections.emptyMap();
            this.byFullId = Collections.emptyMap();
            return;
        }

        JsonObject ammoTypes = json.getAsJsonObject("ammoTypes");
        for (Map.Entry<String, JsonElement> entry : ammoTypes.entrySet()) {
            String shortName = entry.getKey();
            JsonObject info = entry.getValue().getAsJsonObject();
            if (info == null || !info.has("id")) continue;

            String id = info.get("id").getAsString();
            String displayName = info.has("displayName") ? info.get("displayName").getAsString() : shortName;
            String enName = info.has("enName") ? info.get("enName").getAsString() : shortName;
            int maxStack = info.has("maxStack") ? info.get("maxStack").getAsInt() : 64;
            String category = info.has("category") ? info.get("category").getAsString() : "";

            AmmoTypeInfo typeInfo = new AmmoTypeInfo(id, displayName, enName, maxStack, category);
            shortNameMap.put(shortName, typeInfo);
            fullIdMap.put(id, shortName);
        }

        this.loaded = true;
        this.byShortName = Collections.unmodifiableMap(shortNameMap);
        this.byFullId = Collections.unmodifiableMap(fullIdMap);

        LOGGER.info("[AmmoTypeRegistry] 已加载 {} 种弹药类型", shortNameMap.size());
    }

    public boolean isLoaded() { return loaded; }

    /** 通过短名获取弹药类型信息 */
    public AmmoTypeInfo getAmmoType(String shortName) {
        return byShortName.get(shortName);
    }

    /** 通过完整物品 ID 获取弹药短名 */
    public String getShortName(String fullId) {
        return byFullId.get(fullId);
    }

    /** 通过完整物品 ID 获取弹药显示名 */
    public String getDisplayName(String fullId) {
        String shortName = byFullId.get(fullId);
        if (shortName == null) return null;
        AmmoTypeInfo info = byShortName.get(shortName);
        return info != null ? info.displayName() : null;
    }

    /** 获取所有弹药短名列表 */
    public Set<String> getAllShortNames() {
        return byShortName.keySet();
    }

    /** 获取所有分类名 */
    public Set<String> getAllCategories() {
        Set<String> cats = new LinkedHashSet<>();
        for (AmmoTypeInfo info : byShortName.values()) {
            if (info.category() != null && !info.category().isEmpty()) {
                cats.add(info.category());
            }
        }
        return cats;
    }

    /** 获取指定分类下的所有弹药短名（排序） */
    public List<String> getShortNamesByCategory(String category) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, AmmoTypeInfo> e : byShortName.entrySet()) {
            if (category.equals(e.getValue().category())) {
                result.add(e.getKey());
            }
        }
        Collections.sort(result);
        return result;
    }

    /** 获取所有完整ID→短名的映射 */
    public Map<String, String> getFullIdMap() {
        return new HashMap<>(byFullId);
    }

    public record AmmoTypeInfo(String id, String displayName, String enName, int maxStack, String category) {}
}
