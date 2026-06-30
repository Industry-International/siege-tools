package com.xkmxz.siege_tools.vehicle.gui;

import com.xkmxz.siege_tools.vehicle.block.AmmoCrateBlockEntity;
import com.xkmxz.siege_tools.vehicle.registry.ModBlocks;
import com.xkmxz.siege_tools.vehicle.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

/**
 * 弹药补给站配置菜单。
 * DataSlots 布局: [x, y, z, scanRange, cooldown, enterDelay, slot0, slot1, ..., slot28] = 35个
 */
public class AmmoStationMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 35;
    public static final int POS_X = 0, POS_Y = 1, POS_Z = 2;
    public static final int DATA_SCAN_RANGE = 3, DATA_COOLDOWN = 4, DATA_ENTER_DELAY = 5;
    public static final int DATA_SLOTS_START = 6;

    public static final List<String> AMMO_SLOT_KEYS = List.of(
        "large_shell_ap", "large_shell_he", "large_shell_gs",
        "mortar_shell",
        "small_shell_ap", "small_shell_he", "small_shell_gs", "small_shell_aa",
        "rifle_ammo", "heavy_ammo", "small_rocket", "rocket",
        "missile", "medium_anti_ground_missile", "large_anti_ground_missile",
        "medium_anti_air_missile", "medium_aerial_bomb", "small_aerial_bomb",
        "mcsp_125mm_ap", "mcsp_125mm_he", "mcsp_120mm_bulletmortar", "mcsp_tow_2", "mcsp_mlrs_shells",
        "mcsp_25mm_ap", "mcsp_30mm_ap", "mcsp_40mm_explosive", "mcsp_40mm_smoke",
        "mcsp_bullet762", "mcsp_smallarmscartridge"
    );

    public final ContainerData data;
    private ContainerLevelAccess access;

    /** 客户端构造 */
    public AmmoStationMenu(int containerId, Inventory playerInv) {
        super(ModMenuTypes.AMMO_STATION.get(), containerId);
        this.data = new SimpleContainerData(DATA_COUNT);
        this.access = ContainerLevelAccess.NULL;
        addDataSlots(this.data);
        addPlayerSlots(playerInv);
    }

    /** 服务端构造 */
    public AmmoStationMenu(int containerId, Inventory playerInv, BlockEntity be) {
        this(containerId, playerInv);
        BlockPos pos = be.getBlockPos();
        data.set(POS_X, pos.getX());
        data.set(POS_Y, pos.getY());
        data.set(POS_Z, pos.getZ());
        if (be instanceof AmmoCrateBlockEntity station) {
            data.set(DATA_SCAN_RANGE, station.getScanRange());
            data.set(DATA_COOLDOWN, station.getCooldownSec());
            data.set(DATA_ENTER_DELAY, station.getEnterDelay());
            var slots = station.getSlots();
            for (int i = 0; i < AMMO_SLOT_KEYS.size(); i++) {
                data.set(DATA_SLOTS_START + i, slots.getOrDefault(AMMO_SLOT_KEYS.get(i), 0));
            }
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
        return stillValid(this.access, player, ModBlocks.AMMO_STATION.get());
    }
}
