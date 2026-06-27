package com.xkmxz.siege_tools.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * SiegeTools API — KubeJS ↔ Java 桥接类
 *
 * KubeJS 在启动时通过 Java.loadClass() 调用本类的静态方法，
 * 将所有武器的弹药配置注册到 Java 侧的静态 Map 中。
 * Java 侧的 AmmoKitItem / AmmoKitEntity 通过本类查询和发放弹药。
 *
 * == KubeJS 调用示例（在 z_tacz_config_build.js 最后添加）： ==
 *
 * // 1. 构建弹药配置映射表
 * var ammoMap = {};
 * for each weapon in PROF_CONFIGS:
 *   ammoMap[weaponId] = {
 *     type: "tacz" 或 "vanilla",
 *     ammoId: "...", main: 210, level: 2, gunId: "...",
 *     item: "...", count: 16
 *   };
 *
 * // 2. 调用 Java API 注册
 * var $SiegeToolsAPI = Java.loadClass('com.xkmxz.siege_tools.api.SiegeToolsAPI');
 * $SiegeToolsAPI.registerAmmoConfigs(JSON.stringify(ammoMap));
 */
public class SiegeToolsAPI {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    /** 武器ID → 弹药配置 的映射表（由 KubeJS 注册） */
    private static final Map<String, AmmoConfig> AMMO_CONFIG_MAP = new HashMap<>();

    /** 是否已注册 */
    private static volatile boolean initialized = false;

    /**
     * 弹药配置记录
     */
    public static class AmmoConfig {
        public String type;      // "tacz" 或 "vanilla"
        public String ammoId;    // TACZ 弹药 ID（如 "tacz:762x39"）
        public int main;         // 备弹量（如 210）
        public int level;        // 弹药等级（有则发弹药盒，否则直接发弹药物品）
        public String gunId;     // TACZ 枪械 ID（如 "tacz:ak47"）
        public String item;      // 非 TACZ 物品 ID（如 "minecraft:snowball"）
        public int count;        // 非 TACZ 物品数量
    }

    /**
     * 由 KubeJS 调用：批量注册所有武器的弹药配置
     */
    public static void registerAmmoConfigs(String json) {
        try {
            Type type = new TypeToken<Map<String, AmmoConfig>>() {}.getType();
            Map<String, AmmoConfig> configs = GSON.fromJson(json, type);
            if (configs == null) return;
            AMMO_CONFIG_MAP.putAll(configs);
            initialized = true;
            LOGGER.info("[SiegeToolsAPI] 已注册 {} 个武器的弹药配置", AMMO_CONFIG_MAP.size());
        } catch (Exception e) {
            LOGGER.error("[SiegeToolsAPI] 注册弹药配置失败: {}", e.getMessage());
        }
    }

    /**
     * 清空并重新注册（用于 /kubejs reload 后的重新注册）
     */
    public static void clearAndRegister(String json) {
        AMMO_CONFIG_MAP.clear();
        registerAmmoConfigs(json);
    }

    /**
     * 查询指定武器的弹药配置
     */
    public static AmmoConfig getAmmoConfig(String weaponId) {
        return AMMO_CONFIG_MAP.get(weaponId);
    }

    /**
     * 检查是否已初始化
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * 为玩家补充指定武器的弹药（放入背包）
     */
    public static boolean giveAmmoToPlayer(ServerPlayer player, String weaponId) {
        AmmoConfig cfg = getAmmoConfig(weaponId);
        if (cfg == null) return false;

        if ("tacz".equals(cfg.type)) {
            return giveTaczAmmo(player, cfg);
        } else if ("vanilla".equals(cfg.type)) {
            return giveVanillaAmmo(player, cfg);
        }
        return false;
    }

    /**
     * 为玩家补充全部已配置武器的弹药
     */
    public static boolean refillPlayerAmmo(ServerPlayer player, boolean primary, boolean secondary, boolean tertiary) {
        var data = player.getPersistentData();
        boolean anyRefilled = false;

        if (primary) {
            String weaponId = data.getString("mainWeapon");
            if (weaponId != null && !weaponId.isEmpty() && !"\"\"".equals(weaponId)) {
                weaponId = cleanId(weaponId);
                if (giveAmmoToPlayer(player, weaponId)) anyRefilled = true;
            }
        }
        if (secondary) {
            String weaponId = data.getString("offhandWeapon");
            if (weaponId != null && !weaponId.isEmpty() && !"\"\"".equals(weaponId)) {
                weaponId = cleanId(weaponId);
                if (giveAmmoToPlayer(player, weaponId)) anyRefilled = true;
            }
        }
        if (tertiary) {
            String weaponId = data.getString("specialWeapon");
            if (weaponId != null && !weaponId.isEmpty() && !"\"\"".equals(weaponId)) {
                weaponId = cleanId(weaponId);
                if (giveAmmoToPlayer(player, weaponId)) anyRefilled = true;
            }
        }

        return anyRefilled;
    }

    /**
     * 检查玩家指定武器是否弹药已满
     */
    public static boolean isAmmoFull(ServerPlayer player, String weaponId) {
        AmmoConfig cfg = getAmmoConfig(weaponId);
        if (cfg == null) return true;

        if ("tacz".equals(cfg.type)) {
            return hasEnoughTaczAmmo(player, cfg);
        } else if ("vanilla".equals(cfg.type)) {
            return hasEnoughVanillaAmmo(player, cfg);
        }
        return true;
    }

    /**
     * 检查玩家所有已配置武器的弹药是否都已满
     */
    public static boolean isPlayerFullySupplied(ServerPlayer player) {
        var data = player.getPersistentData();

        String main = cleanId(data.getString("mainWeapon"));
        if (!main.isEmpty() && !isAmmoFull(player, main)) return false;

        String offhand = cleanId(data.getString("offhandWeapon"));
        if (!offhand.isEmpty() && !isAmmoFull(player, offhand)) return false;

        String special = cleanId(data.getString("specialWeapon"));
        if (!special.isEmpty() && !isAmmoFull(player, special)) return false;

        return true;
    }

    // ========== 内部实现 ==========

    /** 发放 TACZ 弹药 */
    private static boolean giveTaczAmmo(ServerPlayer player, AmmoConfig cfg) {
        if (cfg.ammoId == null || cfg.main <= 0) return false;

        if (cfg.level > 0) {
            // 使用弹药盒（tacz:ammo_box + 组件数据）
            Item boxItem = findItem("tacz", "ammo_box");
            if (boxItem == Items.AIR) return false;

            ItemStack ammoStack = new ItemStack(boxItem, 1);
            if (ammoStack.isEmpty()) return false;

            // 使用 DataComponent API 设置弹药盒数据
            CompoundTag tag = new CompoundTag();
            tag.putString("GunId", cfg.gunId != null ? cfg.gunId : "");
            tag.putInt("AmmoCount", cfg.main);
            tag.putInt("AmmoLevel", cfg.level);
            ammoStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            ItemHandlerHelper.giveItemToPlayer(player, ammoStack);
            return true;
        } else {
            // 直接发放弹药物品
            Item ammoItem = findItem(cfg.ammoId);
            if (ammoItem == Items.AIR) return false;

            int maxStack = ammoItem.getDefaultInstance().getMaxStackSize();
            int remaining = cfg.main;

            while (remaining > 0) {
                int count = Math.min(remaining, maxStack);
                ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ammoItem, count));
                remaining -= count;
            }
            return true;
        }
    }

    /** 发放非 TACZ 弹药 */
    private static boolean giveVanillaAmmo(ServerPlayer player, AmmoConfig cfg) {
        if (cfg.item == null || cfg.count <= 0) return false;

        Item item = findItem(cfg.item);
        if (item == Items.AIR) return false;

        int maxStack = item.getDefaultInstance().getMaxStackSize();
        int remaining = cfg.count;

        while (remaining > 0) {
            int count = Math.min(remaining, maxStack);
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(item, count));
            remaining -= count;
        }
        return true;
    }

    /** 检查玩家背包中是否有足够的 TACZ 弹药 */
    private static boolean hasEnoughTaczAmmo(ServerPlayer player, AmmoConfig cfg) {
        Inventory inv = player.getInventory();
        int totalAmmo = 0;

        if (cfg.level > 0) {
            // 检查弹药盒（使用 DataComponent 读取）
            Item boxItem = findItem("tacz", "ammo_box");
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;

                if (stack.getItem() == boxItem) {
                    CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
                    if (customData != null) {
                        CompoundTag tag = customData.copyTag();
                        String gunId = tag.getString("GunId");
                        int ammoCount = tag.getInt("AmmoCount");
                        if (gunId.equals(cfg.gunId) && ammoCount >= cfg.main) {
                            return true;
                        }
                        totalAmmo += ammoCount;
                    }
                }
            }
        } else {
            // 检查直接弹药
            Item ammoItem = findItem(cfg.ammoId);
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && stack.getItem() == ammoItem) {
                    totalAmmo += stack.getCount();
                }
            }
        }

        return totalAmmo >= cfg.main;
    }

    /** 检查玩家背包中是否有足够的非 TACZ 弹药 */
    private static boolean hasEnoughVanillaAmmo(ServerPlayer player, AmmoConfig cfg) {
        if (cfg.item == null || cfg.count <= 0) return true;

        Item item = findItem(cfg.item);
        int total = 0;

        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                total += stack.getCount();
            }
        }

        return total >= cfg.count;
    }

    /** 通过完整 ResourceLocation 查找物品 */
    private static Item findItem(String id) {
        ResourceLocation rl = ResourceLocation.parse(id);
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
    }

    /** 通过 namespace:path 查找物品 */
    private static Item findItem(String namespace, String path) {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(namespace, path);
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
    }

    /**
     * 清洗武器 ID（去除 persistentData 读取时可能带有的引号）
     */
    private static String cleanId(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String cleaned = raw.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }
}
