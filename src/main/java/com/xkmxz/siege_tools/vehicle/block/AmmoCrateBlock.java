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

    // 弹药分类定义（参照 KubeJS 布局分组）
    private static final class AmmoCat {
        final String tabName, sectionTitle;
        final List<String> keys;
        AmmoCat(String tabName, String sectionTitle, List<String> keys) {
            this.tabName = tabName; this.sectionTitle = sectionTitle; this.keys = keys;
        }
    }
    private static final List<AmmoCat> AMMO_CATEGORIES = List.of(
        new AmmoCat("炮弹",    "大口径炮弹",
            List.of("large_shell_ap", "large_shell_he", "large_shell_gs", "mortar_shell")),
        new AmmoCat("小口径",  "小口径机炮弹",
            List.of("small_shell_ap", "small_shell_he", "small_shell_gs", "small_shell_aa")),
        new AmmoCat("枪/火箭", "枪弹/火箭弹",
            List.of("rifle_ammo", "heavy_ammo", "small_rocket", "rocket")),
        new AmmoCat("导弹/航弹", "导弹/航弹",
            List.of("missile", "medium_anti_ground_missile", "large_anti_ground_missile",
                    "medium_anti_air_missile", "medium_aerial_bomb", "small_aerial_bomb")),
        new AmmoCat("§aMCSP(上)", "坦克炮/导弹",
            List.of("mcsp_125mm_ap", "mcsp_125mm_he", "mcsp_120mm_bulletmortar", "mcsp_tow_2", "mcsp_mlrs_shells")),
        new AmmoCat("§aMCSP(下)", "机关炮/机枪",
            List.of("mcsp_25mm_ap", "mcsp_30mm_ap", "mcsp_40mm_explosive", "mcsp_40mm_smoke",
                    "mcsp_bullet762", "mcsp_smallarmscartridge"))
    );

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            BlockUIMenuType.openUI(sp, pos);
            // 发送 S2C 包，用服务端 BE 的真实数据初始化客户端 GUI 文本框
            BlockEntity raw = level.getBlockEntity(pos);
            if (raw instanceof AmmoCrateBlockEntity be) {
                // slots Map → CompoundTag
                net.minecraft.nbt.CompoundTag slotsTag = new net.minecraft.nbt.CompoundTag();
                for (java.util.Map.Entry<String, Integer> entry : be.getSlots().entrySet()) {
                    slotsTag.putInt(entry.getKey(), entry.getValue());
                }
                // 构建初始化数据包
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

        // ── 从数据库读取弹药类型 ──
        var ammoReg = VehicleDataManager.getAmmoTypes();
        // ── 按弹药分类收集弹药短名 ──
        var ammoShortNames = new ArrayList<String>();
        var ammoDisplayMap = new LinkedHashMap<String, String>();
        var ammoCatMap = new LinkedHashMap<String, String>(); // key → category tab name
        for (var ac : AMMO_CATEGORIES) {
            for (var key : ac.keys) {
                ammoShortNames.add(key);
                String display = "§f" + key;
                if (ammoReg != null) {
                    var info = ammoReg.getAmmoType(key);
                    if (info != null) display = "§f" + info.displayName();
                }
                ammoDisplayMap.put(key, display);
                ammoCatMap.put(key, ac.tabName);
            }
        }

        // 注意：TextFields 初始值使用空字符串，真正数据由 S2CAmmoCrateInitData 包推送后填充
        TextField fieldScan = new TextField().setNumbersOnlyInt(0, 999999).setText("12"); fieldScan.setId("ammo_scanRange"); fieldScan.lss("width", 55);
        TextField fieldCool = new TextField().setNumbersOnlyInt(0, 999999).setText("5"); fieldCool.setId("ammo_cooldown"); fieldCool.lss("width", 55);
        TextField fieldEnter = new TextField().setNumbersOnlyInt(1, 999999).setText("3"); fieldEnter.setId("ammo_enterDelay"); fieldEnter.lss("width", 55);

        Map<String, TextField> slotFields = new LinkedHashMap<>();
        for (String sn : ammoShortNames) {
            TextField f = new TextField().setNumbersOnlyInt(0, 999999).setText("0");
            f.setId("ammo_slot_" + sn);
            f.lss("width", 55);
            slotFields.put(sn, f);
        }

        // 紧凑布局
        UIElement root = new UIElement(); root.lss("width", 250).lss("padding", 5);
        var title = new Label().setText(Component.literal("§6╔══ 弹药补给站配置 ══╗"));
        title.lss("width", "100%");
        title.textStyle(s -> s.textAlignHorizontal(Horizontal.CENTER));
        root.addChild(title);
        root.addChild(sep());

        TabView tv = new TabView();

        // Tab 1: 基础
        UIElement p1 = new UIElement(); p1.lss("padding", 3);
        addRow(p1, "§7扫描范围:", fieldScan, " §7格");
        addGap(p1); addRow(p1, "§7冷却时间:", fieldCool, " §7秒");
        addGap(p1); addRow(p1, "§7驶入等待:", fieldEnter, " §7秒");
        addGap(p1);
        p1.addChild(new Label().setText(Component.literal("§8← 切换标签页配置弹药")));
        tv.addTab(new Tab().setText("基础"), p1);

        // 弹药分类页签（按 AMMO_CATEGORIES 分组）
        for (var ac : AMMO_CATEGORIES) {
            UIElement page = new UIElement(); page.lss("padding", 3);
            page.addChild(new Label().setText(Component.literal("§e── " + ac.sectionTitle + " ──")));
            for (var key : ac.keys) {
                if (ammoDisplayMap.containsKey(key)) {
                    addRow(page, ammoDisplayMap.get(key) + ":", slotFields.get(key), " 个");
                }
            }
            tv.addTab(new Tab().setText(ac.tabName), page);
        }

        // 作弊页
        UIElement cp = new UIElement(); cp.lss("padding", 3);
        cp.addChild(new Label().setText(Component.literal("§c── 作弊功能 ──")));
        if (holder.player.hasPermissions(2)) {
            addGap(cp);
            Button btnT = new Button().setText(Component.literal("§6⇄ 切换作弊模式")); btnT.lss("padding", "3 10");
            btnT.setOnClick(e -> {
                PacketDistributor.sendToServer(C2SVehiclePacket.toggleCheat(holder.pos));
            });
            cp.addChild(btnT);
        } else { addGap(cp); cp.addChild(new Label().setText(Component.literal("§c你没有权限使用作弊功能"))); }
        tv.addTab(new Tab().setText("§c作弊"), cp);

        root.addChild(tv);
        root.addChild(sep());

        // 保存/重置按钮
        UIElement btnRow = new UIElement();
        Button btnSave = new Button().setText(Component.literal("§a✔ 保存配置")); btnSave.lss("padding", "3 10");
        btnSave.setOnClick(e -> {
            java.util.Map<String, Integer> slotsMap = new java.util.HashMap<>();
            for (String sn : ammoShortNames) {
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

        Button btnReset = new Button().setText(Component.literal("§e↻ 重置默认")); btnReset.lss("padding", "3 10");
        btnReset.setOnClick(e -> {
            PacketDistributor.sendToServer(C2SVehiclePacket.resetAmmo(holder.pos));
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
