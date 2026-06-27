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
import java.util.UUID;

/**
 * SiegeTools API — KubeJS ↔ Java 桥接类
 *
 * 数据流：
 *   KubeJS z_ammo_kit_bridge.js → clearAndRegister(json)  注册弹药配置表
 *   KubeJS profession_gui.js    → setPlayerWeapons(...)    告知 Java 玩家武器（可选）
 *
 * 武器 ID 读取优先级：
 *   1. 内存映射表（PLAYER_WEAPONS_MAP，由 setPlayerWeapons 写入）
 *   2. persistentData 回退（直接从 player.getPersistentData() 读取）
 */
public class SiegeToolsAPI {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    private static final Map<String, AmmoConfig> AMMO_CONFIG_MAP = new HashMap<>();
    private static volatile boolean initialized = false;

    /**
     * 玩家武器内存映射表（可选，由 KubeJS setPlayerWeapons 写入）
     * 优先级高于 persistentData 读取
     */
    private static final Map<UUID, PlayerWeapons> PLAYER_WEAPONS_MAP = new HashMap<>();

    public static class AmmoConfig {
        public String type;      // "tacz" 或 "vanilla"
        public String ammoId;    // TACZ 弹药 ID
        public int main;         // 备弹量
        public int offhand;      // 副武器备弹量
        public int level;        // 弹药等级（有则发弹药盒）
        public String gunId;     // TACZ 枪械 ID
        public String item;      // 非 TACZ 物品 ID
        public int count;        // 非 TACZ 物品数量
    }

    public static class PlayerWeapons {
        public String main;
        public String offhand;
        public String special;
        public PlayerWeapons(String main, String offhand, String special) {
            this.main = main != null ? main : "";
            this.offhand = offhand != null ? offhand : "";
            this.special = special != null ? special : "";
        }
    }

    // ========== 弹药配置注册 ==========

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

    public static void clearAndRegister(String json) {
        AMMO_CONFIG_MAP.clear();
        registerAmmoConfigs(json);
    }

    public static AmmoConfig getAmmoConfig(String weaponId) {
        return AMMO_CONFIG_MAP.get(weaponId);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    // ========== 玩家武器注册（可选，供 KubeJS 调用） ==========

    /**
     * 设置玩家当前的武器选择。
     * 调用后 Java 从此内存映射表读取武器 ID，而非 persistentData。
     */
    public static void setPlayerWeapons(String playerUUID, String mainWeapon, String offhandWeapon, String specialWeapon) {
        try {
            UUID uuid = UUID.fromString(playerUUID);
            PLAYER_WEAPONS_MAP.put(uuid, new PlayerWeapons(mainWeapon, offhandWeapon, specialWeapon));
            LOGGER.info("[SiegeToolsAPI] setPlayerWeapons: {}", playerUUID);
        } catch (Exception e) {
            LOGGER.error("[SiegeToolsAPI] setPlayerWeapons 失败: {}", e.getMessage());
        }
    }

    /**
     * 读取玩家的武器选择。
     * 优先级：内存映射表 → persistentData 回退
     */
    private static PlayerWeapons getPlayerWeapons(ServerPlayer player) {
        // 1. 尝试内存映射表
        PlayerWeapons pw = PLAYER_WEAPONS_MAP.get(player.getUUID());
        if (pw != null) return pw;

        // 2. 回退：从 persistentData 读取
        var data = player.getPersistentData();
        return new PlayerWeapons(
                cleanId(data.getString("mainWeapon")),
                cleanId(data.getString("offhandWeapon")),
                cleanId(data.getString("specialWeapon"))
        );
    }

    // ========== 弹药发放 ==========

    public static boolean giveAmmoToPlayer(ServerPlayer player, String weaponId, String category) {
        if (!initialized) return false;
        AmmoConfig cfg = getAmmoConfig(weaponId);
        if (cfg == null) {
            LOGGER.warn("[SiegeToolsAPI] 武器 [{}] 未在配置表中", weaponId);
            return false;
        }
        if ("tacz".equals(cfg.type)) return giveTaczAmmo(player, cfg, category);
        if ("vanilla".equals(cfg.type)) return giveVanillaAmmo(player, cfg);
        return false;
    }

    /** 兼容旧调用 */
    public static boolean giveAmmoToPlayer(ServerPlayer player, String weaponId) {
        return giveAmmoToPlayer(player, weaponId, "primary");
    }

    /**
     * 补充玩家全部武器的弹药
     * 武器 ID 来源：内存映射表（优先）→ persistentData（回退）
     */
    public static boolean refillPlayerAmmo(ServerPlayer player, boolean primary, boolean secondary, boolean tertiary) {
        PlayerWeapons pw = getPlayerWeapons(player);
        LOGGER.info("[SiegeToolsAPI] refillPlayerAmmo: main=[{}] off=[{}] sp=[{}]", pw.main, pw.offhand, pw.special);

        boolean anyRefilled = false;
        if (primary && !pw.main.isEmpty()) {
            if (giveAmmoToPlayer(player, pw.main, "primary")) anyRefilled = true;
        }
        if (secondary && !pw.offhand.isEmpty()) {
            if (giveAmmoToPlayer(player, pw.offhand, "secondary")) anyRefilled = true;
        }
        if (tertiary && !pw.special.isEmpty()) {
            if (giveAmmoToPlayer(player, pw.special, "tertiary")) anyRefilled = true;
        }
        return anyRefilled;
    }

    public static boolean isAmmoFull(ServerPlayer player, String weaponId, String category) {
        AmmoConfig cfg = getAmmoConfig(weaponId);
        if (cfg == null) return true;
        if ("tacz".equals(cfg.type)) return hasEnoughTaczAmmo(player, cfg, category);
        if ("vanilla".equals(cfg.type)) return hasEnoughVanillaAmmo(player, cfg);
        return true;
    }

    /** 兼容旧调用 */
    public static boolean isAmmoFull(ServerPlayer player, String weaponId) {
        return isAmmoFull(player, weaponId, "primary");
    }

    /**
     * 检查玩家所有武器弹药是否已满
     * 武器 ID 来源：内存映射表（优先）→ persistentData（回退）
     */
    public static boolean isPlayerFullySupplied(ServerPlayer player) {
        PlayerWeapons pw = getPlayerWeapons(player);
        if (!pw.main.isEmpty() && !isAmmoFull(player, pw.main, "primary")) return false;
        if (!pw.offhand.isEmpty() && !isAmmoFull(player, pw.offhand, "secondary")) return false;
        if (!pw.special.isEmpty() && !isAmmoFull(player, pw.special, "tertiary")) return false;
        return true;
    }

    // ========== 内部实现 ==========

    private static int getAmmoCount(AmmoConfig cfg, String category) {
        if ("secondary".equals(category) && cfg.offhand > 0) return cfg.offhand;
        return cfg.main;
    }

    private static boolean giveTaczAmmo(ServerPlayer player, AmmoConfig cfg, String category) {
        int ammoNeeded = getAmmoCount(cfg, category);
        if (cfg.ammoId == null || ammoNeeded <= 0) return false;

        if (cfg.level > 0) {
            Item boxItem = findItem("tacz", "ammo_box");
            if (boxItem == Items.AIR) {
                LOGGER.warn("[SiegeToolsAPI] tacz:ammo_box 不存在");
                return giveDirectTaczAmmo(player, cfg, ammoNeeded);
            }
            ItemStack ammoStack = new ItemStack(boxItem, 1);
            CompoundTag tag = new CompoundTag();
            tag.putString("GunId", cfg.gunId != null ? cfg.gunId : "");
            tag.putInt("AmmoCount", ammoNeeded);
            tag.putInt("AmmoLevel", cfg.level);
            ammoStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            ItemHandlerHelper.giveItemToPlayer(player, ammoStack);
            return true;
        }
        return giveDirectTaczAmmo(player, cfg, ammoNeeded);
    }

    private static boolean giveDirectTaczAmmo(ServerPlayer player, AmmoConfig cfg, int ammoNeeded) {
        Item ammoItem = findItem(cfg.ammoId);
        if (ammoItem == Items.AIR) return false;
        int maxStack = ammoItem.getDefaultInstance().getMaxStackSize();
        int remaining = ammoNeeded;
        while (remaining > 0) {
            int count = Math.min(remaining, maxStack);
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ammoItem, count));
            remaining -= count;
        }
        return true;
    }

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

    private static boolean hasEnoughTaczAmmo(ServerPlayer player, AmmoConfig cfg, String category) {
        int ammoNeeded = getAmmoCount(cfg, category);
        Inventory inv = player.getInventory();
        int totalAmmo = 0;

        if (cfg.level > 0) {
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
                        totalAmmo += ammoCount;
                    }
                }
            }
        } else {
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

    private static Item findItem(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static Item findItem(String namespace, String path) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private static String cleanId(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String cleaned = raw.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }
}
