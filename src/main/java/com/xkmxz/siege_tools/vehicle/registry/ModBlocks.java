package com.xkmxz.siege_tools.vehicle.registry;

import com.xkmxz.siege_tools.siege_tools;
import com.xkmxz.siege_tools.vehicle.block.AmmoCrateBlock;
import com.xkmxz.siege_tools.vehicle.block.VehicleDeployerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块注册。
 * Java ID: siege_tools:ammo_station, siege_tools:vehicle_deployer
 */
public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(siege_tools.MODID);

    public static final DeferredBlock<AmmoCrateBlock> AMMO_STATION = BLOCKS.registerBlock("ammo_station",
            AmmoCrateBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.WOOD)
                    .noLootTable()
                    .noOcclusion());

    public static final DeferredBlock<VehicleDeployerBlock> VEHICLE_DEPLOYER = BLOCKS.registerBlock("vehicle_deployer",
            VehicleDeployerBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(3.0f, 8.0f)
                    .sound(SoundType.METAL)
                    .noLootTable()
                    .noOcclusion());
}
