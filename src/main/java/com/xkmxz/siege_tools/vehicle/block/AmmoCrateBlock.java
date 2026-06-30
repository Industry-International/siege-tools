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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) BlockUIMenuType.openUI(sp, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        Level level = holder.player.level();
        BlockPos pos = holder.pos;

        // ── 从数据库读取弹药类型 ──
        var ammoReg = VehicleDataManager.getAmmoTypes();
        var ammoShortNames = new ArrayList<String>();
        var ammoDisplayMap = new LinkedHashMap<String, String>();
        if (ammoReg != null && ammoReg.isLoaded()) {
            for (String sn : ammoReg.getAllShortNames()) {
                var info = ammoReg.getAmmoType(sn);
                if (info != null) { ammoShortNames.add(sn); ammoDisplayMap.put(sn, "§f" + info.displayName()); }
            }
        }

        // 先读取一次 BE 获取当前值
        BlockEntity raw = level.getBlockEntity(pos);
        if (!(raw instanceof AmmoCrateBlockEntity be)) return null;

        TextField fieldScan = new TextField().setNumbersOnlyInt(0, 999999).setText(String.valueOf(be.getScanRange())); fieldScan.lss("width", 55);
        TextField fieldCool = new TextField().setNumbersOnlyInt(0, 999999).setText(String.valueOf(be.getCooldownSec())); fieldCool.lss("width", 55);
        TextField fieldEnter = new TextField().setNumbersOnlyInt(1, 999999).setText(String.valueOf(be.getEnterDelay())); fieldEnter.lss("width", 55);

        Map<String, TextField> slotFields = new LinkedHashMap<>();
        for (String sn : ammoShortNames) {
            TextField f = new TextField().setNumbersOnlyInt(0, 999999).setText(String.valueOf(be.getSlots().getOrDefault(sn, 0)));
            f.lss("width", 55);
            slotFields.put(sn, f);
        }

        UIElement root = new UIElement(); root.lss("width", 270).lss("padding", 6);
        var title = new Label().setText(Component.literal("§6╔══ 弹药补给站配置 ══╗"));
        title.lss("width", "100%");
        title.textStyle(s -> s.textAlignHorizontal(Horizontal.CENTER));
        root.addChild(title);
        root.addChild(sep());

        TabView tv = new TabView();

        UIElement p1 = new UIElement(); p1.lss("padding", 4);
        addRow(p1, "§7扫描范围:", fieldScan, " §7格");
        addGap(p1); addRow(p1, "§7冷却时间:", fieldCool, " §7秒");
        addGap(p1); addRow(p1, "§7驶入等待:", fieldEnter, " §7秒");
        addGap(p1);
        p1.addChild(new Label().setText(Component.literal("§8← 切换标签页配置弹药")));
        tv.addTab(new Tab().setText("基础"), p1);

        int perPage = 4;
        int total = ammoShortNames.size();
        for (int pi = 0; pi < total; pi += perPage) {
            int end = Math.min(pi + perPage, total);
            UIElement page = new UIElement(); page.lss("padding", 4);
            page.addChild(new Label().setText(Component.literal("§e── 弹药配置 ──")));
            for (int i = pi; i < end; i++) {
                String sn = ammoShortNames.get(i);
                addRow(page, ammoDisplayMap.get(sn) + ":", slotFields.get(sn), " 个");
            }
            tv.addTab(new Tab().setText("弹药" + ((pi/perPage)+1)), page);
        }

        // 作弊
        UIElement cp = new UIElement(); cp.lss("padding", 4);
        cp.addChild(new Label().setText(Component.literal("§c── 作弊功能 ──")));
        if (holder.player.hasPermissions(2)) {
            addGap(cp);
            Button btnT = new Button().setText(Component.literal("§6⇄ 切换作弊模式")); btnT.lss("padding", "3 10");
            btnT.setOnServerClick(e -> {
                BlockEntity r = holder.player.level().getBlockEntity(holder.pos);
                if (r instanceof AmmoCrateBlockEntity b) { b.setCheatMode(!b.isCheatMode()); b.setChanged(); }
                holder.player.displayClientMessage(Component.literal("§6[弹药补给站] 作弊模式已切换"), false); });
            cp.addChild(btnT);
        } else { addGap(cp); cp.addChild(new Label().setText(Component.literal("§c你没有权限使用作弊功能"))); }
        tv.addTab(new Tab().setText("§c作弊"), cp);

        root.addChild(tv);
        root.addChild(sep());

        // 保存/重置按钮
        UIElement btnRow = new UIElement();
        Button btnSave = new Button().setText(Component.literal("§a✔ 保存配置")); btnSave.lss("padding", "3 10");
        btnSave.setOnServerClick(e -> {
            BlockEntity r = holder.player.level().getBlockEntity(holder.pos);
            if (!(r instanceof AmmoCrateBlockEntity b)) return;
            Map<String, Integer> ns = new HashMap<>();
            for (String sn : ammoShortNames) { try { int v = Integer.parseInt(slotFields.get(sn).getText()); if (v > 0) ns.put(sn, v); } catch (Exception ex) {} }
            b.applyConfig(sInt(fieldScan.getText(), 12), sInt(fieldCool.getText(), 5), sInt(fieldEnter.getText(), 3), ns);
            holder.player.displayClientMessage(Component.literal("§a✔ 配置已保存！冷却已重置"), false);
        });
        btnRow.addChild(btnSave);

        Button btnReset = new Button().setText(Component.literal("§e↻ 重置默认")); btnReset.lss("padding", "3 10");
        btnReset.setOnServerClick(e -> {
            BlockEntity r = holder.player.level().getBlockEntity(holder.pos);
            if (r instanceof AmmoCrateBlockEntity b) { b.resetConfig(); holder.player.displayClientMessage(Component.literal("§a✔ 已重置为默认配置"), false); }
        });
        btnRow.addChild(btnReset);
        root.addChild(btnRow);
        root.addChild(new InventorySlots());

        return ModularUI.of(UI.of(root), holder.player);
    }

    private static Label sep() { Label s = new Label(); s.setText(Component.literal("§8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")); s.lss("width", "100%").lss("overflow", "hidden"); return s; }
    private static void addRow(UIElement p, String label, UIElement f, String u) { UIElement r = new UIElement(); r.addChild(new Label().setText(Component.literal(label))); r.addChild(f); r.addChild(new Label().setText(Component.literal(u))); p.addChild(r); }
    private static void addGap(UIElement p) { p.addChild(new Label().setText(Component.literal(" "))); }
    private static int sInt(String s, int d) { try { return Math.max(0, Integer.parseInt(s)); } catch (Exception e) { return d; } }
}
