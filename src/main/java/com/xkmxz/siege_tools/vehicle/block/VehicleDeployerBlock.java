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
import com.xkmxz.siege_tools.vehicle.network.DeployerConfigData;
import com.xkmxz.siege_tools.vehicle.network.S2CVehiclePacket;
import com.xkmxz.siege_tools.vehicle.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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

public class VehicleDeployerBlock extends BaseEntityBlock implements BlockUIMenuType.BlockUI {

    public static final MapCodec<VehicleDeployerBlock> CODEC = simpleCodec(VehicleDeployerBlock::new);

    public VehicleDeployerBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new VehicleDeployerBlockEntity(pos, state); }

    @Nullable @Override @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (BlockEntityTicker<T>) createTickerHelper(type, ModBlockEntities.VEHICLE_DEPLOYER.get(), VehicleDeployerBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            BlockUIMenuType.openUI(sp, pos);
            // 发送 S2C 包，用服务端 BE 的真实数据初始化客户端 GUI 文本框
            BlockEntity raw = level.getBlockEntity(pos);
            if (raw instanceof VehicleDeployerBlockEntity be) {
                PacketDistributor.sendToPlayer(sp, S2CVehiclePacket.initDeployer(
                        pos,
                        new DeployerConfigData(
                                be.getVehicleType(),
                                be.getRespawnDelay(),
                                be.isAutoRespawn(),
                                be.isSpawnWithAmmo(),
                                be.getOffsetX(), be.getOffsetY(), be.getOffsetZ(),
                                be.getYaw(), be.getPitch(),
                                be.getDeployNBT()
                        )
                ));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        Level level = holder.player.level();
        BlockPos pos = holder.pos;
        BlockEntity raw = level.getBlockEntity(pos);
        if (!(raw instanceof VehicleDeployerBlockEntity be)) return null;

        // 注意：TextFields 初始值使用空字符串，真正数据由 S2CDeployerInitData 包推送后填充
        TextField fieldVtype = new TextField(); fieldVtype.setId("deployer_vehicleType"); fieldVtype.lss("width", 180);
        TextField fieldDelay = new TextField().setNumbersOnlyInt(20, 72000).setText("600"); fieldDelay.setId("deployer_respawnDelay"); fieldDelay.lss("width", 55);
        TextField fieldAuto = new TextField().setNumbersOnlyInt(0, 1).setText("0"); fieldAuto.setId("deployer_autoRespawn"); fieldAuto.lss("width", 40);
        TextField fieldAmmo = new TextField().setNumbersOnlyInt(0, 1).setText("1"); fieldAmmo.setId("deployer_spawnWithAmmo"); fieldAmmo.lss("width", 40);
        TextField fieldOx = new TextField().setNumbersOnlyInt(-999, 999).setText("0"); fieldOx.setId("deployer_offsetX"); fieldOx.lss("width", 50);
        TextField fieldOy = new TextField().setNumbersOnlyInt(-999, 999).setText("1"); fieldOy.setId("deployer_offsetY"); fieldOy.lss("width", 50);
        TextField fieldOz = new TextField().setNumbersOnlyInt(-999, 999).setText("0"); fieldOz.setId("deployer_offsetZ"); fieldOz.lss("width", 50);
        TextField fieldYaw = new TextField().setNumbersOnlyInt(-180, 180).setText("0"); fieldYaw.setId("deployer_yaw"); fieldYaw.lss("width", 50);
        TextField fieldPitch = new TextField().setNumbersOnlyInt(-90, 90).setText("0"); fieldPitch.setId("deployer_pitch"); fieldPitch.lss("width", 50);
        TextField fieldNBT = new TextField(); fieldNBT.setId("deployer_deployNBT"); fieldNBT.lss("width", 250).lss("height", 100);

        // 类别/载具选择器
        Selector catSel = new Selector(); catSel.lss("width", "100%");
        Selector vehSel = new Selector(); vehSel.lss("width", "100%");

        var db = VehicleDataManager.getDatabase();
        var catData = new LinkedHashMap<String, List<String>>();
        var categoryKeys = new ArrayList<String>();
        if (db.isLoaded()) {
            for (String ck : db.getAllCategoryKeys()) {
                var ci = db.getCategories().get(ck);
                categoryKeys.add(ci.displayName());
                catData.put(ci.displayName(), db.getVehiclesByCategory(ck));
            }
        }
        if (categoryKeys.isEmpty()) {
            categoryKeys.add("§c数据库未加载");
            catData.put("§c数据库未加载", List.of("§c请保存配置后重启"));
        }
        catSel.setCandidates(new ArrayList<>(categoryKeys));
        var firstVehs = catData.get(categoryKeys.get(0));
        vehSel.setCandidates(new ArrayList<>(firstVehs != null ? firstVehs : List.of()));

        catSel.setOnValueChanged(newCat -> {
            if (newCat != null && catData.containsKey(newCat)) {
                vehSel.setCandidates(new ArrayList<>(catData.get(newCat)));
                var v = catData.get(newCat);
                if (!v.isEmpty()) vehSel.setSelected(v.get(0));
            }
        });
        vehSel.setOnValueChanged(newVid -> { if (newVid != null) fieldVtype.setText(newVid.toString()); });

        UIElement root = new UIElement(); root.lss("width", 280).lss("padding", 6);
        var title = new Label().setText(Component.literal("§6╔══ 载具部署台配置 ══╗"));
        title.lss("width", "100%");
        title.textStyle(s -> s.textAlignHorizontal(Horizontal.CENTER));
        root.addChild(title);
        root.addChild(sep());

        TabView tv = new TabView();

        // Tab 1: 载具选择
        UIElement p1 = new UIElement(); p1.lss("padding", 4);
        p1.addChild(new Label().setText(Component.literal("§e── 选择载具 ──")));
        addRow(p1, "§7类别:", catSel, "");
        addGap(p1); addRow(p1, "§7载具:", vehSel, "");
        addGap(p1); addRow(p1, "§7ID:", fieldVtype, "");
        p1.addChild(new Label().setText(Component.literal("§8从下拉选择或直接输入完整 ID")));
        tv.addTab(new Tab().setText("载具"), p1);

        // Tab 2: 基础设置
        UIElement p2 = new UIElement(); p2.lss("padding", 4);
        p2.addChild(new Label().setText(Component.literal("§e── 部署基础参数 ──")));
        addRow(p2, "§7重生延迟:", fieldDelay, " §7tick");
        p2.addChild(new Label().setText(Component.literal("§8(20 tick = 1 秒, 默认 600 = 30s)")));
        addGap(p2); addRow(p2, "§7自动重生:", fieldAuto, " §7(1=开启)");
        addGap(p2); addRow(p2, "§7生成带弹药:", fieldAmmo, " §7(1=是)");
        tv.addTab(new Tab().setText("基础"), p2);

        // Tab 3: 坐标偏移
        UIElement p3 = new UIElement(); p3.lss("padding", 4);
        p3.addChild(new Label().setText(Component.literal("§e── 部署坐标偏移 ──")));
        addRow(p3, "§7X偏移:", fieldOx, " 格"); addRow(p3, "§7Y偏移:", fieldOy, " 格"); addRow(p3, "§7Z偏移:", fieldOz, " 格");
        addGap(p3); addRow(p3, "§7朝向(yaw):", fieldYaw, " °"); addRow(p3, "§7俯仰(pitch):", fieldPitch, " °");
        tv.addTab(new Tab().setText("坐标"), p3);

        // Tab 4: NBT
        UIElement p4 = new UIElement(); p4.lss("padding", 4);
        p4.addChild(new Label().setText(Component.literal("§e── deployNBT ──")));
        p4.addChild(new Label().setText(Component.literal("§8留空 {} 使用数据库默认值")));
        addGap(p4); p4.addChild(fieldNBT);
        tv.addTab(new Tab().setText("NBT"), p4);

        root.addChild(tv);
        root.addChild(sep());

        UIElement btnRow = new UIElement();
        Button btnSave = new Button().setText(Component.literal("§a✔ 保存")); btnSave.lss("padding", "3 10");
        btnSave.setOnClick(e -> {
            // 客户端读取当前字段值，解析 deployNBT JSON 为 CompoundTag
            String nbtStr = fieldNBT.getText();
            if (nbtStr.isEmpty()) nbtStr = "{}";
            CompoundTag parsed;
            try {
                parsed = com.xkmxz.siege_tools.vehicle.util.JsonToNBTConverter.toCompoundTag(
                        new com.google.gson.Gson().fromJson(nbtStr, com.google.gson.JsonObject.class));
                if (parsed == null) parsed = new CompoundTag();
            } catch (Exception ex) {
                parsed = new CompoundTag();
            }
            PacketDistributor.sendToServer(C2SVehiclePacket.saveDeployer(
                    holder.pos,
                    new com.xkmxz.siege_tools.vehicle.network.DeployerConfigData(
                            fieldVtype.getText(),
                            Math.max(20, sInt(fieldDelay.getText(), 600)),
                            "1".equals(fieldAuto.getText()),
                            "1".equals(fieldAmmo.getText()),
                            sInt(fieldOx.getText(), 0), sInt(fieldOy.getText(), 1), sInt(fieldOz.getText(), 0),
                            (float) sInt(fieldYaw.getText(), 0), (float) sInt(fieldPitch.getText(), 0),
                            parsed
                    )
            ));
        });
        btnRow.addChild(btnSave);

        Button btnDeploy = new Button().setText(Component.literal("§6⚡ 立即部署")); btnDeploy.lss("padding", "3 10");
        btnDeploy.setOnClick(e -> {
            // 客户端发送触发部署网络包到服务端
            PacketDistributor.sendToServer(C2SVehiclePacket.triggerDeploy(holder.pos));
        });
        btnRow.addChild(btnDeploy);
        root.addChild(btnRow);
        root.addChild(new InventorySlots());

        return ModularUI.of(UI.of(root), holder.player);
    }

    private static Label sep() { Label s = new Label(); s.setText(Component.literal("§8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")); s.lss("width", "100%").lss("overflow", "hidden"); return s; }
    private static void addRow(UIElement p, String label, UIElement f, String u) { UIElement r = new UIElement(); r.addChild(new Label().setText(Component.literal(label))); r.addChild(f); r.addChild(new Label().setText(Component.literal(u))); p.addChild(r); }
    private static void addGap(UIElement p) { p.addChild(new Label().setText(Component.literal(" "))); }
    private static int sInt(String s, int d) { try { return Integer.parseInt(s); } catch (Exception e) { return d; } }
}
