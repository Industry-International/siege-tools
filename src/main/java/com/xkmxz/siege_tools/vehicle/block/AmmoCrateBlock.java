package com.xkmxz.siege_tools.vehicle.block;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.mojang.serialization.MapCodec;
import com.xkmxz.siege_tools.vehicle.data.VehicleDataManager;
import com.xkmxz.siege_tools.vehicle.network.C2SVehiclePacket;
import com.xkmxz.siege_tools.vehicle.network.S2CVehiclePacket;
import com.xkmxz.siege_tools.vehicle.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AmmoCrateBlock extends BaseEntityBlock implements BlockUIMenuType.BlockUI {

    public static final MapCodec<AmmoCrateBlock> CODEC = simpleCodec(AmmoCrateBlock::new);

    public AmmoCrateBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AmmoCrateBlockEntity(pos, state); }

    @Nullable @Override @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (BlockEntityTicker<T>) createTickerHelper(type, ModBlockEntities.AMMO_STATION.get(), AmmoCrateBlockEntity::tick);
    }

    private static Component tl(String key) { return Component.translatable(key); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            BlockUIMenuType.openUI(sp, pos);
            BlockEntity raw = level.getBlockEntity(pos);
            if (raw instanceof AmmoCrateBlockEntity be) {
                net.minecraft.nbt.CompoundTag slotsTag = new net.minecraft.nbt.CompoundTag();
                for (Map.Entry<String, Integer> entry : be.getSlots().entrySet()) {
                    slotsTag.putInt(entry.getKey(), entry.getValue());
                }
                net.minecraft.nbt.CompoundTag initData = new net.minecraft.nbt.CompoundTag();
                initData.putInt("scanRange", be.getScanRange());
                initData.putInt("cooldown", be.getCooldownSec());
                initData.putInt("enterDelay", be.getEnterDelay());
                initData.put("slots", slotsTag);
                PacketDistributor.sendToPlayer(sp, S2CVehiclePacket.initAmmo(pos, initData));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        Level level = holder.player.level();
        BlockPos pos = holder.pos;

        var ammoReg = VehicleDataManager.getAmmoTypes();
        // 从注册表获取分类，每分类一个 tab
        var categories = new ArrayList<String>();
        var catAmmoMap = new LinkedHashMap<String, List<String>>();
        var ammoDisplayMap = new LinkedHashMap<String, String>();
        if (ammoReg != null && ammoReg.isLoaded()) {
            categories.addAll(ammoReg.getAllCategories());
            for (String cat : categories) {
                List<String> keys = ammoReg.getShortNamesByCategory(cat);
                catAmmoMap.put(cat, keys);
                for (String key : keys) {
                    var info = ammoReg.getAmmoType(key);
                    ammoDisplayMap.put(key, info != null ? "§f" + info.displayName() : "§f" + key);
                }
            }
        }
        // 无分类的弹药（兜底）
        if (categories.isEmpty()) {
            categories.add("弹药");
            var all = new ArrayList<>(ammoReg != null ? ammoReg.getAllShortNames() : List.of());
            Collections.sort(all);
            catAmmoMap.put("弹药", all);
            for (String key : all) {
                ammoDisplayMap.put(key, "§f" + key);
            }
        }


        // 最多显示 MAX_TABS 个 tab 按钮，多余用箭头翻页
        final int MAX_TABS = 5; // 每页显示 5 个 tab 按钮
        final int[] tabOffset = {0}; // 当前 tab 页偏移

        // 收集所有弹药短名（用于遍历）
        var allAmmoKeys = new ArrayList<>(ammoDisplayMap.keySet());

        TextField fieldScan = new TextField().setNumbersOnlyInt(0, 999999).setText("12"); fieldScan.setId("ammo_scanRange"); fieldScan.lss("width", 50);
        TextField fieldCool = new TextField().setNumbersOnlyInt(0, 999999).setText("5"); fieldCool.setId("ammo_cooldown"); fieldCool.lss("width", 50);
        TextField fieldEnter = new TextField().setNumbersOnlyInt(1, 999999).setText("3"); fieldEnter.setId("ammo_enterDelay"); fieldEnter.lss("width", 50);

        Map<String, TextField> slotFields = new LinkedHashMap<>();
        for (String sn : allAmmoKeys) {
            TextField f = new TextField().setNumbersOnlyInt(0, 999999).setText("0");
            f.setId("ammo_slot_" + sn);
            f.lss("width", 50);
            slotFields.put(sn, f);
        }

        UIElement root = new UIElement(); root.lss("width", 190).lss("padding", 2);
        var title = new Label().setText(tl("gui.siege_tools.ammo.title"));
        title.lss("width", "100%");
        title.textStyle(s -> s.textAlignHorizontal(Horizontal.CENTER));
        root.addChild(title);
        root.addChild(sep_ammo());

        TabView tv = new TabView();

        // Tab 1
        UIElement p1 = new UIElement(); p1.lss("padding", 2);
        addRow(p1, tl("gui.siege_tools.ammo.label.scan_range"), fieldScan, tl("gui.siege_tools.ammo.unit.block"));
        addRow(p1, tl("gui.siege_tools.ammo.label.cooldown"), fieldCool, tl("gui.siege_tools.ammo.unit.second"));
        addRow(p1, tl("gui.siege_tools.ammo.label.enter_delay"), fieldEnter, tl("gui.siege_tools.ammo.unit.second"));
        p1.addChild(new Label().setText(tl("gui.siege_tools.ammo.hint.switch_tab")));
        tv.addTab(new Tab().setText(tl("gui.siege_tools.ammo.tab.basic")), p1);

        // 弹药分类页签：按 category 分组
        for (String cat : categories) {
            List<String> keys = catAmmoMap.get(cat);
            if (keys == null || keys.isEmpty()) continue;
            UIElement page = new UIElement(); page.lss("padding", 2);
            page.addChild(new Label().setText(Component.literal("§e" + cat)));
            for (String key : keys) {
                if (ammoDisplayMap.containsKey(key)) {
                    addRow(page, Component.literal(ammoDisplayMap.get(key) + ":"), slotFields.get(key), tl("gui.siege_tools.ammo.unit.count"));
                }
            }
            // 截断 tab 名称
            String tabLabel = cat.replaceAll("§.", "");
            if (tabLabel.length() > 6) tabLabel = tabLabel.substring(0, 6);
            tv.addTab(new Tab().setText(tabLabel), page);
        }

        // Cheat tab
        UIElement cp = new UIElement(); cp.lss("padding", 2);
        cp.addChild(new Label().setText(tl("gui.siege_tools.ammo.cheat.title")));
        if (holder.player.hasPermissions(2)) {
            Button btnT = new Button().setText(tl("gui.siege_tools.ammo.btn.cheat_toggle")); btnT.lss("padding", "3 8");
            btnT.setOnClick(e -> {
                PacketDistributor.sendToServer(C2SVehiclePacket.toggleCheat(holder.pos));
            });
            cp.addChild(btnT);
        } else { cp.addChild(new Label().setText(tl("gui.siege_tools.ammo.cheat.no_perm"))); }
        tv.addTab(new Tab().setText(tl("gui.siege_tools.ammo.tab.cheat")), cp);

        root.addChild(tv);
        root.addChild(sep_ammo());

        UIElement btnRow = new UIElement();
        Button btnSave = new Button().setText(tl("gui.siege_tools.ammo.btn.save")); btnSave.lss("padding", "3 8");
        btnSave.setOnClick(e -> {
            Map<String, Integer> slotsMap = new HashMap<>();
            for (String sn : allAmmoKeys) {
                try { int v = Integer.parseInt(slotFields.get(sn).getText()); if (v > 0) slotsMap.put(sn, v); } catch (Exception ex) { }
            }
            PacketDistributor.sendToServer(C2SVehiclePacket.saveAmmo(
                    holder.pos,
                    sInt(fieldScan.getText(), 12),
                    sInt(fieldCool.getText(), 5),
                    sInt(fieldEnter.getText(), 3),
                    slotsMap
            ));
        });
        btnRow.addChild(btnSave);

        Button btnReset = new Button().setText(tl("gui.siege_tools.ammo.btn.reset")); btnReset.lss("padding", "3 8");
        btnReset.setOnClick(e -> {
            PacketDistributor.sendToServer(C2SVehiclePacket.resetAmmo(holder.pos));
        });
        btnRow.addChild(btnReset);
        root.addChild(btnRow);
        root.addChild(new InventorySlots());

        return ModularUI.of(UI.of(root), holder.player);
    }

    private static Label sep_ammo() { Label s = new Label(); s.setText(Component.translatable("gui.siege_tools.deployer.separator")); s.lss("width", "100%").lss("overflow", "hidden"); return s; }
    private static void addRow(UIElement p, Component label, UIElement f, Component unit) {
        UIElement r = new UIElement(); r.addChild(new Label().setText(label)); r.addChild(f); r.addChild(new Label().setText(unit)); p.addChild(r);
    }
    private static int sInt(String s, int d) { try { return Math.max(0, Integer.parseInt(s)); } catch (Exception e) { return d; } }
}
