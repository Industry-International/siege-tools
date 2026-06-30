package com.xkmxz.siege_tools.vehicle.registry;

import com.xkmxz.siege_tools.siege_tools;
import com.xkmxz.siege_tools.vehicle.block.AmmoCrateBlockEntity;
import com.xkmxz.siege_tools.vehicle.block.VehicleDeployerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * BlockEntity 类型注册。
 */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, siege_tools.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AmmoCrateBlockEntity>> AMMO_STATION =
            BLOCK_ENTITIES.register("ammo_station", () ->
                    BlockEntityType.Builder.of(AmmoCrateBlockEntity::new, ModBlocks.AMMO_STATION.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VehicleDeployerBlockEntity>> VEHICLE_DEPLOYER =
            BLOCK_ENTITIES.register("vehicle_deployer", () ->
                    BlockEntityType.Builder.of(VehicleDeployerBlockEntity::new, ModBlocks.VEHICLE_DEPLOYER.get()).build(null));
}
