package com.xkmxz.siege_tools;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = siege_tools.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ========== 旧示例配置（保留兼容） ==========
    private static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    private static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    private static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    // ========== AmmoKit 弹药补给包配置 ==========
    // 放置实体（范围补给）
    private static final ModConfigSpec.IntValue PLACED_SCAN_RANGE = BUILDER
            .comment("放置后的弹药箱扫描范围（方块）")
            .defineInRange("ammo_kit.placed.scanRange", 6, 1, 32);

    private static final ModConfigSpec.IntValue PLACED_SCAN_INTERVAL = BUILDER
            .comment("放置后的弹药箱每次扫描间隔（tick，20 tick = 1 秒）")
            .defineInRange("ammo_kit.placed.scanInterval", 40, 10, 200);

    private static final ModConfigSpec.IntValue PLACED_MAX_LIFETIME = BUILDER
            .comment("放置后的弹药箱最大存活时间（tick，0 = 无限）")
            .defineInRange("ammo_kit.placed.maxLifetime", 0, 0, 72000);

    private static final ModConfigSpec.IntValue PLACED_IDLE_DISCARD_DELAY = BUILDER
            .comment("放置后所有玩家弹药已满后，多少 tick 后自动消失（0 = 不自动消失）")
            .defineInRange("ammo_kit.placed.idleDiscardDelay", 200, 0, 72000);

    // 直接右键补给
    private static final ModConfigSpec.IntValue DIRECT_COOLDOWN = BUILDER
            .comment("直接右键玩家补充弹药后的冷却时间（tick）")
            .defineInRange("ammo_kit.direct.cooldown", 25, 0, 200);

    // 补给类别开关
    private static final ModConfigSpec.BooleanValue SUPPLY_PRIMARY = BUILDER
            .comment("是否补充主武器弹药")
            .define("ammo_kit.supply.primary", true);

    private static final ModConfigSpec.BooleanValue SUPPLY_SECONDARY = BUILDER
            .comment("是否补充副武器弹药")
            .define("ammo_kit.supply.secondary", true);

    private static final ModConfigSpec.BooleanValue SUPPLY_TERTIARY = BUILDER
            .comment("是否补充特殊武器弹药")
            .define("ammo_kit.supply.tertiary", true);

    // 物品属性
    private static final ModConfigSpec.IntValue MAX_STACK_SIZE = BUILDER
            .comment("弹药补给包的最大堆叠数")
            .defineInRange("ammo_kit.item.maxStackSize", 16, 1, 64);

    static final ModConfigSpec SPEC = BUILDER.build();

    // ========== 运行时值（旧示例） ==========
    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;

    // ========== 运行时值（AmmoKit） ==========
    public static int ammoKitPlacedScanRange;
    public static int ammoKitPlacedScanInterval;
    public static int ammoKitPlacedMaxLifetime;
    public static int ammoKitPlacedIdleDiscardDelay;
    public static int ammoKitDirectCooldown;
    public static boolean ammoKitSupplyPrimary;
    public static boolean ammoKitSupplySecondary;
    public static boolean ammoKitSupplyTertiary;
    public static int ammoKitMaxStackSize;

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // 旧示例
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();
        items = ITEM_STRINGS.get().stream()
                .map(itemName -> BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemName)))
                .collect(Collectors.toSet());

        // AmmoKit
        ammoKitPlacedScanRange = PLACED_SCAN_RANGE.get();
        ammoKitPlacedScanInterval = PLACED_SCAN_INTERVAL.get();
        ammoKitPlacedMaxLifetime = PLACED_MAX_LIFETIME.get();
        ammoKitPlacedIdleDiscardDelay = PLACED_IDLE_DISCARD_DELAY.get();
        ammoKitDirectCooldown = DIRECT_COOLDOWN.get();
        ammoKitSupplyPrimary = SUPPLY_PRIMARY.get();
        ammoKitSupplySecondary = SUPPLY_SECONDARY.get();
        ammoKitSupplyTertiary = SUPPLY_TERTIARY.get();
        ammoKitMaxStackSize = MAX_STACK_SIZE.get();
    }
}
