package com.xkmxz.siege_tools;

import com.mojang.logging.LogUtils;
import com.xkmxz.siege_tools.entity.AmmoKitEntity;
import com.xkmxz.siege_tools.item.AmmoKitItem;
import com.xkmxz.siege_tools.client.AmmoKitRenderer;
import com.xkmxz.siege_tools.vehicle.registry.ModBlocks;
import com.xkmxz.siege_tools.vehicle.registry.ModBlockEntities;
import com.xkmxz.siege_tools.vehicle.registry.ModMenuTypes;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import com.xkmxz.siege_tools.vehicle.network.VehicleSystemNetworking;

@Mod(siege_tools.MODID)
public class siege_tools {
    public static final String MODID = "siege_tools";
    private static final Logger LOGGER = LogUtils.getLogger();

    // ===== Deferred Registers =====
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ===== 物品 =====
    public static final DeferredHolder<Item, AmmoKitItem> AMMO_KIT_ITEM =
            ITEMS.register("ammo_kit", AmmoKitItem::new);

    // ===== 实体 =====
    public static final DeferredHolder<EntityType<?>, EntityType<AmmoKitEntity>> AMMO_KIT_ENTITY =
            ENTITY_TYPES.register("ammo_kit", () -> EntityType.Builder.<AmmoKitEntity>of(
                            AmmoKitEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .build("ammo_kit")
            );

    // ===== 创造模式标签页 =====
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SIEGE_TOOLS_TAB =
            CREATIVE_MODE_TABS.register("siege_tools_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.siege_tools"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> AMMO_KIT_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(AMMO_KIT_ITEM.get());
                        // 新增：弹药补给站和载具部署台
                        output.accept(ModBlocks.AMMO_STATION.get());
                        output.accept(ModBlocks.VEHICLE_DEPLOYER.get());
                    })
                    .build());

    public siege_tools(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        // 注册原有 Deferred Registers
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // ===== 新增：载具系统注册 =====
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);

        // 注册网络包（NeoForge Payload 系统）
        modEventBus.addListener(VehicleSystemNetworking::register);

        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);

        // 注册配置
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Siege Tools mod initialized");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(AMMO_KIT_ITEM.get());
        }
        // 将新方块添加到对应标签页
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.AMMO_STATION.get());
            event.accept(ModBlocks.VEHICLE_DEPLOYER.get());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Siege Tools server starting");
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("Siege Tools client setup");
            // 注册实体渲染器
            EntityRenderers.register(siege_tools.AMMO_KIT_ENTITY.get(), AmmoKitRenderer::new);
        }
    }
}
