package com.xkmxz.siege_tools.config;

import com.xkmxz.siege_tools.siege_tools;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 弹药补给包（AmmoKit）配置
 */
@EventBusSubscriber(modid = siege_tools.MODID, bus = EventBusSubscriber.Bus.MOD)
public class AmmoKitConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ===== 放置实体（范围补给）配置 =====
    private static final ModConfigSpec.IntValue PLACED_SCAN_RANGE = BUILDER
            .comment("放置后的弹药箱扫描范围（方块）")
            .defineInRange("placed.scanRange", 6, 1, 32);

    private static final ModConfigSpec.IntValue PLACED_SCAN_INTERVAL = BUILDER
            .comment("放置后的弹药箱每次扫描间隔（tick，20 tick = 1 秒）")
            .defineInRange("placed.scanInterval", 40, 10, 200);

    private static final ModConfigSpec.IntValue PLACED_MAX_LIFETIME = BUILDER
            .comment("放置后的弹药箱最大存活时间（tick，0 = 无限）")
            .defineInRange("placed.maxLifetime", 0, 0, 72000);

    private static final ModConfigSpec.IntValue PLACED_IDLE_DISCARD_DELAY = BUILDER
            .comment("放置后所有玩家弹药已满后，多少 tick 后自动消失（0 = 不自动消失）")
            .defineInRange("placed.idleDiscardDelay", 200, 0, 72000);

    // ===== 直接右键补给配置 =====
    private static final ModConfigSpec.IntValue DIRECT_COOLDOWN = BUILDER
            .comment("直接右键玩家补充弹药后的冷却时间（tick）")
            .defineInRange("direct.cooldown", 25, 0, 200);

    // ===== 补给类别开关 =====
    private static final ModConfigSpec.BooleanValue SUPPLY_PRIMARY = BUILDER
            .comment("是否补充主武器弹药")
            .define("supply.primary", true);

    private static final ModConfigSpec.BooleanValue SUPPLY_SECONDARY = BUILDER
            .comment("是否补充副武器弹药")
            .define("supply.secondary", true);

    private static final ModConfigSpec.BooleanValue SUPPLY_TERTIARY = BUILDER
            .comment("是否补充特殊武器弹药")
            .define("supply.tertiary", true);

    // ===== 物品基础属性 =====
    private static final ModConfigSpec.IntValue MAX_STACK_SIZE = BUILDER
            .comment("弹药补给包的最大堆叠数")
            .defineInRange("item.maxStackSize", 16, 1, 64);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ===== 运行时值 =====
    public static int placedScanRange;
    public static int placedScanInterval;
    public static int placedMaxLifetime;
    public static int placedIdleDiscardDelay;
    public static int directCooldown;
    public static boolean supplyPrimary;
    public static boolean supplySecondary;
    public static boolean supplyTertiary;
    public static int maxStackSize;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        placedScanRange = PLACED_SCAN_RANGE.get();
        placedScanInterval = PLACED_SCAN_INTERVAL.get();
        placedMaxLifetime = PLACED_MAX_LIFETIME.get();
        placedIdleDiscardDelay = PLACED_IDLE_DISCARD_DELAY.get();
        directCooldown = DIRECT_COOLDOWN.get();
        supplyPrimary = SUPPLY_PRIMARY.get();
        supplySecondary = SUPPLY_SECONDARY.get();
        supplyTertiary = SUPPLY_TERTIARY.get();
        maxStackSize = MAX_STACK_SIZE.get();
    }
}
