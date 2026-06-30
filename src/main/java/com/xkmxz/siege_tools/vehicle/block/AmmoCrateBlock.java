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

    private record CatDef(String name, List<String> keys) {}

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        var ammoReg = VehicleDataManager.getAmmoTypes();

        // 收集分类数据
        var categories = new ArrayList<CatDef>();
        var ammoDisplayMap = new LinkedHashMap<String, String>();
        var allAmmoKeys = new ArrayList<String>();
        if (ammoReg != null && ammoReg.isLoaded()) {
            for (String cat : ammoReg.getAllCategories()) {
                List<String> keys = ammoReg.getShortNamesByCategory(cat);
                if (keys.isEmpty()) continue;
                categories.add(new CatDef(cat, keys));
                for (String key : keys) {
                    var info = ammoReg.getAmmoType(key);
                    ammoDisplayMap.put(key, info != null ? "§f" + info.displayName() : "§f" + key);
                    allAmmoKeys.add(key);
                }
            }
        }
        if (categories.isEmpty()) {
            var all = new ArrayList<>(ammoReg != null ? ammoReg.getAllShortNames() : List.of());
            Collections.sort(all);
            categories.add(new CatDef("弹药", all));
            for (String key : all) ammoDisplayMap.put(key, "§f" + key);
        }

        // 输入字段
        TextField fieldScan = new TextField().setNumbersOnlyInt(0, 999999).setText("12"); fieldScan.setId("ammo_scanRange"); fieldScan.lss("width", 50);
        TextField fieldCool = new TextField().setNumbersOnlyInt(0, 999999).setText("5"); fieldCool.setId("ammo_cooldown"); fieldCool.lss("width", 50);
        TextField fieldEnter = new TextField().setNumbersOnlyInt(1, 999999).setText("3"); fieldEnter.setId("ammo_enterDelay"); fieldEnter.lss("width", 50);

        Map<String, TextField> slotFields = new LinkedHashMap<>();
        for (String sn : allAmmoKeys) {
            TextField f = new TextField().setNumbersOnlyInt(0, 999999).setText("0");
            f.setId("ammo_slot_" + sn); f.lss("width", 50);
            slotFields.put(sn, f);
        }

        // ========== 构建所有页面 ==========

        // 基础页面
        UIElement basicPage = new UIElement(); basicPage.lss("padding", 2);
        basicPage.addChild(label(tl("gui.siege_tools.ammo.label.scan_range")));
        basicPage.addChild(fieldScan);
        basicPage.addChild(gap());
        basicPage.addChild(label(tl("gui.siege_tools.ammo.label.cooldown")));
        basicPage.addChild(fieldCool);
        basicPage.addChild(gap());
        basicPage.addChild(label(tl("gui.siege_tools.ammo.label.enter_delay")));
        basicPage.addChild(fieldEnter);

        // 弹药分类页面
        var catPages = new LinkedHashMap<String, UIElement>();
        for (var cd : categories) {
            UIElement page = new UIElement(); page.lss("padding", 2);
            page.addChild(label(Component.literal("§e" + cd.name())));
            for (String key : cd.keys()) {
                UIElement row = new UIElement();
                row.addChild(label(Component.literal(ammoDisplayMap.getOrDefault(key, "§f" + key) + ":")));
                row.addChild(slotFields.get(key));
                row.addChild(label(tl("gui.siege_tools.ammo.unit.count")));
                page.addChild(row);
            }
            catPages.put(cd.name(), page);
        }

        // 作弊页面
        UIElement cheatPage = new UIElement(); cheatPage.lss("padding", 2);
        cheatPage.addChild(label(tl("gui.siege_tools.ammo.cheat.title")));
        if (holder.player.hasPermissions(2)) {
            Button btnT = new Button(); btnT.setText(tl("gui.siege_tools.ammo.btn.cheat_toggle")); btnT.lss("padding", "3 8");
            btnT.setOnClick(e -> PacketDistributor.sendToServer(C2SVehiclePacket.toggleCheat(holder.pos)));
            cheatPage.addChild(btnT);
        } else {
            cheatPage.addChild(label(tl("gui.siege_tools.ammo.cheat.no_perm")));
        }

        // ========== TabView：MC 创造菜单风格 ==========
        TabView tv = new TabView();
        var allTabs = new ArrayList<Tab>();

        // 基础 tab
        var tabBasic = new Tab().setText(tl("gui.siege_tools.ammo.tab.basic"));
        tv.addTab(tabBasic, basicPage);
        allTabs.add(tabBasic);

        // 每个分类一个 tab
        for (var cd : categories) {
            String label = cd.name().replaceAll("§.", "");
            if (label.length() > 4) label = label.substring(0, 4);
            var tab = new Tab().setText(label);
            tv.addTab(tab, catPages.get(cd.name()));
            allTabs.add(tab);
        }

        // 作弊 tab
        var tabCheat = new Tab().setText(tl("gui.siege_tools.ammo.tab.cheat"));
        tv.addTab(tabCheat, cheatPage);
        allTabs.add(tabCheat);

        // 跟踪当前选中的 tab 索引
        final int[] currentIdx = {0};
        tv.setOnTabSelected(selected -> {
            for (int i = 0; i < allTabs.size(); i++) {
                if (allTabs.get(i) == selected) { currentIdx[0] = i; break; }
            }
        });

        // ========== 根容器 ==========
        UIElement root = new UIElement(); root.lss("width", 270).lss("padding", 2).lss("overflow", "hidden");
        root.addChild(title(tl("gui.siege_tools.ammo.title")));
        root.addChild(sep());

        // tab 切换按钮行（MC 风格：← tab头 →）
        UIElement tabNav = new UIElement(); tabNav.lss("width", "100%").lss("overflow", "hidden");
        Button btnPrevTab = new Button(); btnPrevTab.setText(Component.literal("§7◀")); btnPrevTab.lss("padding", "1 4");
        btnPrevTab.setOnClick(e -> {
            if (allTabs.isEmpty()) return;
            int idx = (currentIdx[0] - 1 + allTabs.size()) % allTabs.size();
            tv.selectTab(allTabs.get(idx));
        });
        tabNav.addChild(btnPrevTab);
        tabNav.addChild(tv);
        Button btnNextTab = new Button(); btnNextTab.setText(Component.literal("§7▶")); btnNextTab.lss("padding", "1 4");
        btnNextTab.setOnClick(e -> {
            if (allTabs.isEmpty()) return;
            int idx = (currentIdx[0] + 1) % allTabs.size();
            tv.selectTab(allTabs.get(idx));
        });
        tabNav.addChild(btnNextTab);

        root.addChild(tabNav);
        root.addChild(sep());
        root.addChild(btnRow(fieldScan, fieldCool, fieldEnter, slotFields, allAmmoKeys, holder.pos));
        root.addChild(new InventorySlots());

        return ModularUI.of(UI.of(root), holder.player);
    }

    // ========== 辅助方法 ==========

    private static UIElement btnRow(TextField fieldScan, TextField fieldCool, TextField fieldEnter,
                                     Map<String, TextField> slotFields, List<String> allAmmoKeys, BlockPos pos) {
        UIElement row = new UIElement();
        Button btnSave = new Button(); btnSave.setText(tl("gui.siege_tools.ammo.btn.save")); btnSave.lss("padding", "3 8");
        btnSave.setOnClick(e -> {
            Map<String, Integer> slotsMap = new HashMap<>();
            for (String sn : allAmmoKeys) {
                try { int v = Integer.parseInt(slotFields.get(sn).getText()); if (v > 0) slotsMap.put(sn, v); } catch (Exception ex) {}
            }
            PacketDistributor.sendToServer(C2SVehiclePacket.saveAmmo(pos,
                    sInt(fieldScan.getText(), 12), sInt(fieldCool.getText(), 5), sInt(fieldEnter.getText(), 3), slotsMap));
        });
        row.addChild(btnSave);

        Button btnReset = new Button(); btnReset.setText(tl("gui.siege_tools.ammo.btn.reset")); btnReset.lss("padding", "3 8");
        btnReset.setOnClick(e -> PacketDistributor.sendToServer(C2SVehiclePacket.resetAmmo(pos)));
        row.addChild(btnReset);
        return row;
    }

    private static Label title(Component text) {
        Label t = new Label(); t.setText(text);
        t.lss("width", "100%");
        t.textStyle(s -> s.textAlignHorizontal(Horizontal.CENTER));
        return t;
    }
    private static Label label(Component text) { Label r = new Label(); r.setText(text); return r; }
    private static Label gap() { Label r = new Label(); r.setText(Component.literal(" ")); return r; }
    private static Label sep() { Label s = new Label(); s.setText(tl("gui.siege_tools.deployer.separator")); s.lss("width", "100%").lss("overflow", "hidden"); return s; }
    private static int sInt(String s, int d) { try { return Math.max(0, Integer.parseInt(s)); } catch (Exception e) { return d; } }
}
