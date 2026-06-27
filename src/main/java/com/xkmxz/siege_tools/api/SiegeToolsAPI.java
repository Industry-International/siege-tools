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
 * SiegeTools API — 弹药配置管理与弹药发放逻辑
 *
 * === 数据流 ===
 *
 * 1. 服务端启动时：
 *    KubeJS  →  clearAndRegister(ammoConfigJson)  注册全局弹药配置表
 *
 * 2. 玩家交互时（AmmoKitItem / AmmoKitEntity）：
 *    Java    →  从 player.persistentData 直接读取武器 ID 和队伍
 *               （由 KubeJS 写入，无推送延迟问题）
 *    Java    →  查 AMMO_CONFIG_MAP 获得弹药配置 → 发放弹药
 *
 * === 设计原则 ===
 *
 * - 弹药配置表（全局）由 KubeJS 通过 JSON 注册，Java 侧只做查表。
 * - 玩家武器/队伍数据不从 Java 侧管理，直接从 player.persistentData 读取。
 *   KubeJS 写入 persistentData，Java 同步读取（消除了旧版推送机制的时序问题）。
 * - Java 只负责：查弹药配置 + 背包操作 + 弹药发放。
 * - KubeJS 只负责：维护玩家武器/队伍/职业等业务状态。
 */
public class SiegeToolsAPI {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    // ======================================================================
    //  弹药配置表（全局，KubeJS 在服务端启动时注册）
    // ======================================================================

    private static final Map<String, AmmoConfig> AMMO_CONFIG_MAP = new HashMap<>();
    private static volatile boolean initialized = false;

    public static class AmmoConfig {
        public String type;      // "tacz" 或 "vanilla"
        public String ammoId;    // TACZ 弹药 ID
        public int main;         // 主武器备弹量
        public int offhand;      // 副武器备弹量（0 时回退到 main）
        public int level;        // 弹药等级（>0 发弹药盒）
        public String gunId;     // TACZ 枪械 ID（弹药盒标记用）
        public String item;      // 非 TACZ 物品 ID
        public int count;        // 非 TACZ 物品数量
    }

    // ======================================================================
    //  弹药配置注册
    // ======================================================================

    /**
     * 注册弹药配置表（全局覆盖）。
     * 由 KubeJS 在服务端启动时调用，支持 /kubejs reload 重新注册。
     *
     * JSON 格式（Map<String, AmmoConfig> 序列化）：
     * {
     *   "ak47": {
     *     "type": "tacz",
     *     "ammoId": "tacz:762x39",
     *     "main": 210,
     *     "offhand": 0,
     *     "level": 2,
     *     "gunId": "tacz:ak47"
     *   },
     *   "snowball": {
     *     "type": "vanilla",
     *     "item": "minecraft:snowball",
     *     "count": 16
     *   }
     * }
     */
    public static void clearAndRegister(String json) {
        AMMO_CONFIG_MAP.clear();
        initialized = false;
        try {
            Type type = new TypeToken<Map<String, AmmoConfig>>() {}.getType();
            Map<String, AmmoConfig> configs = GSON.fromJson(json, type);
            if (configs == null || configs.isEmpty()) {
                LOGGER.warn("[SiegeToolsAPI] clearAndRegister: 配置为空或解析失败");
                return;
            }
            AMMO_CONFIG_MAP.putAll(configs);
            initialized = true;
            LOGGER.info("[SiegeToolsAPI] 已注册 {} 个武器的弹药配置", AMMO_CONFIG_MAP.size());
        } catch (Exception e) {
            LOGGER.error("[SiegeToolsAPI] 注册弹药配置失败: {}", e.getMessage());
        }
    }

    public static AmmoConfig getAmmoConfig(String weaponId) {
        return AMMO_CONFIG_MAP.get(weaponId);
    }

    /** 弹药配置表是否已成功初始化 */
    public static boolean isInitialized() {
        return initialized;
    }

    // ======================================================================
    //  玩家数据读取（从 player.persistentData，由 KubeJS 写入）
    // ======================================================================

    /**
     * 从玩家的 persistentData 读取队伍名。
     * KubeJS 在 team_selector_gui.js 中写入 player.persistentData.team。
     */
    public static String getPlayerTeam(ServerPlayer player) {
        return player.getPersistentData().getString("team");
    }

    /**
     * 从玩家的 persistentData 读取指定类别的武器 ID。
     *
     * @param player   目标玩家
     * @param category 类别："primary"（mainWeapon）, "secondary"（offhandWeapon）, "tertiary"（specialWeapon）
     * @return 武器 ID 字符串（空串表示无）
     */
    public static String getPlayerWeaponId(ServerPlayer player, String category) {
        String key = switch (category) {
            case "primary" -> "mainWeapon";
            case "secondary" -> "offhandWeapon";
            case "tertiary" -> "specialWeapon";
            default -> "";
        };
        if (key.isEmpty()) return "";
        String raw = player.getPersistentData().getString(key);
        // 清理可能的引号（兼容 KubeJS 某些 API 带引号存储的情况）
        if (raw != null) {
            raw = raw.replace("\"", "").trim();
        }
        return raw != null ? raw : "";
    }

    // ======================================================================
    //  弹药发放与检查
    // ======================================================================

    /**
     * 补充玩家全部武器的弹药。
     * 武器 ID 从 player.persistentData 的 mainWeapon/offhandWeapon/specialWeapon 读取。
     *
     * @param player   目标玩家
     * @param primary   是否补充主武器
     * @param secondary 是否补充副武器
     * @param tertiary  是否补充特殊武器
     * @return 是否补充了任何弹药
     */
    public static boolean refillPlayerAmmo(ServerPlayer player, boolean primary, boolean secondary, boolean tertiary) {
        String mainWeapon = primary ? getPlayerWeaponId(player, "primary") : "";
        String offhandWeapon = secondary ? getPlayerWeaponId(player, "secondary") : "";
        String specialWeapon = tertiary ? getPlayerWeaponId(player, "tertiary") : "";

        LOGGER.info("[SiegeToolsAPI] refillPlayerAmmo: main=[{}] off=[{}] sp=[{}]",
                mainWeapon, offhandWeapon, specialWeapon);

        boolean anyRefilled = false;
        if (!mainWeapon.isEmpty()) {
            if (giveAmmoToPlayer(player, mainWeapon, "primary")) anyRefilled = true;
        }
        if (!offhandWeapon.isEmpty()) {
            if (giveAmmoToPlayer(player, offhandWeapon, "secondary")) anyRefilled = true;
        }
        if (!specialWeapon.isEmpty()) {
            if (giveAmmoToPlayer(player, specialWeapon, "tertiary")) anyRefilled = true;
        }
        return anyRefilled;
    }

    /**
     * 检查玩家所有武器弹药是否已满。
     * 武器 ID 从 player.persistentData 读取。
     */
    public static boolean isPlayerFullySupplied(ServerPlayer player) {
        String mainWeapon = getPlayerWeaponId(player, "primary");
        String offhandWeapon = getPlayerWeaponId(player, "secondary");
        String specialWeapon = getPlayerWeaponId(player, "tertiary");

        if (!mainWeapon.isEmpty() && !isAmmoFull(player, mainWeapon, "primary")) return false;
        if (!offhandWeapon.isEmpty() && !isAmmoFull(player, offhandWeapon, "secondary")) return false;
        if (!specialWeapon.isEmpty() && !isAmmoFull(player, specialWeapon, "tertiary")) return false;
        return true;
    }

    /**
     * 为玩家发放某种武器的弹药。
     *
     * @param player   目标玩家
     * @param weaponId 武器 ID（如 "ak47"）
     * @param category 类别："primary"（用 main）、"secondary"（优先 offhand，回退 main）、"tertiary"（用 main）
     * @return 是否成功发放了弹药
     */
    public static boolean giveAmmoToPlayer(ServerPlayer player, String weaponId, String category) {
        if (!initialized) {
            LOGGER.warn("[SiegeToolsAPI] giveAmmoToPlayer: 弹药配置未初始化");
            return false;
        }
        AmmoConfig cfg = getAmmoConfig(weaponId);
        if (cfg == null) {
            LOGGER.warn("[SiegeToolsAPI] 武器 [{}] 未在配置表中", weaponId);
            return false;
        }
        if ("tacz".equals(cfg.type)) return giveTaczAmmo(player, cfg, category);
        if ("vanilla".equals(cfg.type)) return giveVanillaAmmo(player, cfg);
        return false;
    }

    /**
     * 检查玩家某种武器的弹药是否已满。
     */
    public static boolean isAmmoFull(ServerPlayer player, String weaponId, String category) {
        AmmoConfig cfg = getAmmoConfig(weaponId);
        if (cfg == null) return true;
        if ("tacz".equals(cfg.type)) return hasEnoughTaczAmmo(player, cfg, category);
        if ("vanilla".equals(cfg.type)) return hasEnoughVanillaAmmo(player, cfg);
        return true;
    }

    // ======================================================================
    //  内部实现
    // ======================================================================

    /** 根据类别获取弹药需求量 */
    private static int getAmmoCount(AmmoConfig cfg, String category) {
        if ("secondary".equals(category) && cfg.offhand > 0) return cfg.offhand;
        return cfg.main;
    }

    /** 发放 TACZ 弹药（弹药盒模式或直接发放模式） */
    private static boolean giveTaczAmmo(ServerPlayer player, AmmoConfig cfg, String category) {
        int ammoNeeded = getAmmoCount(cfg, category);
        if (cfg.ammoId == null || cfg.ammoId.isEmpty() || ammoNeeded <= 0) return false;

        if (cfg.level > 0) {
            // 弹药盒模式
            Item boxItem = findItem("tacz", "ammo_box");
            if (boxItem == Items.AIR) {
                LOGGER.warn("[SiegeToolsAPI] tacz:ammo_box 不存在，降级为直接发放");
                return giveDirectTaczAmmo(player, cfg, ammoNeeded);
            }
            ItemStack ammoStack = new ItemStack(boxItem, 1);
            CompoundTag tag = new CompoundTag();
            tag.putString("GunId", cfg.gunId != null ? cfg.gunId : "");
            tag.putInt("AmmoCount", ammoNeeded);
            tag.putInt("AmmoLevel", cfg.level);
            ammoStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            ItemHandlerHelper.giveItemToPlayer(player, ammoStack);
            LOGGER.info("[SiegeToolsAPI] 发放弹药盒: GunId={}, AmmoCount={}, AmmoLevel={}", cfg.gunId, ammoNeeded, cfg.level);
            return true;
        }
        // 直接发放模式
        return giveDirectTaczAmmo(player, cfg, ammoNeeded);
    }

    /** 直接发放 TACZ 弹药物品（按最大堆叠拆分） */
    private static boolean giveDirectTaczAmmo(ServerPlayer player, AmmoConfig cfg, int ammoNeeded) {
        Item ammoItem = findItem(cfg.ammoId);
        if (ammoItem == Items.AIR) {
            LOGGER.warn("[SiegeToolsAPI] 弹药物品 [{}] 不存在", cfg.ammoId);
            return false;
        }
        int maxStack = ammoItem.getDefaultInstance().getMaxStackSize();
        int remaining = ammoNeeded;
        while (remaining > 0) {
            int count = Math.min(remaining, maxStack);
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ammoItem, count));
            remaining -= count;
        }
        LOGGER.info("[SiegeToolsAPI] 直接发放弹药: {} x{}", cfg.ammoId, ammoNeeded);
        return true;
    }

    /** 发放非 TACZ 弹药（如雪球） */
    private static boolean giveVanillaAmmo(ServerPlayer player, AmmoConfig cfg) {
        if (cfg.item == null || cfg.item.isEmpty() || cfg.count <= 0) return false;
        Item item = findItem(cfg.item);
        if (item == Items.AIR) {
            LOGGER.warn("[SiegeToolsAPI] 非 TACZ 物品 [{}] 不存在", cfg.item);
            return false;
        }
        int maxStack = item.getDefaultInstance().getMaxStackSize();
        int remaining = cfg.count;
        while (remaining > 0) {
            int count = Math.min(remaining, maxStack);
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(item, count));
            remaining -= count;
        }
        LOGGER.info("[SiegeToolsAPI] 发放非 TACZ 物品: {} x{}", cfg.item, cfg.count);
        return true;
    }

    /** 检查 TACZ 弹药是否充足（弹药盒用 GunId + AmmoCount 匹配，散装累加） */
    private static boolean hasEnoughTaczAmmo(ServerPlayer player, AmmoConfig cfg, String category) {
        int ammoNeeded = getAmmoCount(cfg, category);
        Inventory inv = player.getInventory();
        int totalAmmo = 0;

        if (cfg.level > 0) {
            // 弹药盒模式：查找 GunId 匹配、AmmoCount 累加 >= 需要量
            Item boxItem = findItem("tacz", "ammo_box");
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;
                if (stack.getItem() == boxItem) {
                    CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
                    if (cd != null) {
                        CompoundTag tag = cd.copyTag();
                        String gunId = tag.getString("GunId");
                        int ammoCount = tag.getInt("AmmoCount");
                        if (gunId.equals(cfg.gunId) && ammoCount >= ammoNeeded) return true;
                        if (gunId.equals(cfg.gunId)) totalAmmo += ammoCount;
                    }
                }
            }
        } else {
            // 直接发放模式：散装弹药累加
            Item ammoItem = findItem(cfg.ammoId);
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && stack.getItem() == ammoItem) {
                    totalAmmo += stack.getCount();
                }
            }
        }
        return totalAmmo >= ammoNeeded;
    }

    /** 检查非 TACZ 弹药是否充足 */
    private static boolean hasEnoughVanillaAmmo(ServerPlayer player, AmmoConfig cfg) {
        if (cfg.item == null || cfg.item.isEmpty() || cfg.count <= 0) return true;
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

    // ======================================================================
    //  工具方法
    // ======================================================================

    private static Item findItem(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static Item findItem(String namespace, String path) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
