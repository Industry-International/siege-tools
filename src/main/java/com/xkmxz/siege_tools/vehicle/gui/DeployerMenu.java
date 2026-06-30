package com.xkmxz.siege_tools.vehicle.gui;

import com.xkmxz.siege_tools.vehicle.block.VehicleDeployerBlockEntity;
import com.xkmxz.siege_tools.vehicle.registry.ModBlocks;
import com.xkmxz.siege_tools.vehicle.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 载具部署台配置菜单。
 * DataSlots: [x, y, z, respawnDelay, autoRespawn(0/1), spawnWithAmmo(0/1),
 *             offsetX*100, offsetY*100, offsetZ*100, yaw*10, pitch*10] = 12个
 */
public class DeployerMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 12;
    public static final int POS_X = 0, POS_Y = 1, POS_Z = 2;
    public static final int RESPAWN_DELAY = 3, AUTO_RESPAWN = 4, SPAWN_WITH_AMMO = 5;
    public static final int OFFSET_X = 6, OFFSET_Y = 7, OFFSET_Z = 8;
    public static final int YAW = 9, PITCH = 10;

    public final ContainerData data;
    private ContainerLevelAccess access;

    public DeployerMenu(int containerId, Inventory playerInv) {
        super(ModMenuTypes.VEHICLE_DEPLOYER.get(), containerId);
        this.data = new SimpleContainerData(DATA_COUNT);
        this.access = ContainerLevelAccess.NULL;
        addDataSlots(this.data);
        addPlayerSlots(playerInv);
    }

    public DeployerMenu(int containerId, Inventory playerInv, BlockEntity be) {
        this(containerId, playerInv);
        BlockPos pos = be.getBlockPos();
        data.set(POS_X, pos.getX());
        data.set(POS_Y, pos.getY());
        data.set(POS_Z, pos.getZ());
        if (be instanceof VehicleDeployerBlockEntity d) {
            data.set(RESPAWN_DELAY, d.getRespawnDelay());
            data.set(AUTO_RESPAWN, d.isAutoRespawn() ? 1 : 0);
            data.set(SPAWN_WITH_AMMO, d.isSpawnWithAmmo() ? 1 : 0);
            data.set(OFFSET_X, (int)(d.getOffsetX() * 100));
            data.set(OFFSET_Y, (int)(d.getOffsetY() * 100));
            data.set(OFFSET_Z, (int)(d.getOffsetZ() * 100));
            data.set(YAW, (int)(d.getYaw() * 10));
            data.set(PITCH, (int)(d.getPitch() * 10));
        }
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
    }

    private void addPlayerSlots(Inventory playerInv) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
    }

    public BlockPos getBlockPos() {
        return new BlockPos(data.get(POS_X), data.get(POS_Y), data.get(POS_Z));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.VEHICLE_DEPLOYER.get());
    }
}
