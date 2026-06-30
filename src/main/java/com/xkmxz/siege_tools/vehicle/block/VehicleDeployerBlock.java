package com.xkmxz.siege_tools.vehicle.block;

import com.mojang.serialization.MapCodec;
import com.xkmxz.siege_tools.vehicle.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 载具部署台方块。
 * 替代 KubeJS kubejs:vehicle_deployer。
 * Java ID: siege_tools:vehicle_deployer
 */
public class VehicleDeployerBlock extends BaseEntityBlock {

    public static final MapCodec<VehicleDeployerBlock> CODEC = simpleCodec(VehicleDeployerBlock::new);

    public VehicleDeployerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VehicleDeployerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.VEHICLE_DEPLOYER.get(), VehicleDeployerBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof VehicleDeployerBlockEntity deployer) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(deployer);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
