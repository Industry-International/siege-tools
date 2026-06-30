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
            // 发送 S2C 包，用服务端 BE 的真实数据初始化客户端 GUI
            BlockEntity raw = level.getBlockEntity(pos);
            if (raw instanceof VehicleDeployerBlockEntity be) {
                DeployerConfigData cfg = new DeployerConfigData(
                        be.getVehicleType(),
                        be.getRespawnDelay(),
                        be.isAutoRespawn(),
                        be.isSpawnWithAmmo(),
                        be.getOffsetX(), be.getOffsetY(), be.getOffsetZ(),
                        be.getYaw(), be.getPitch(),
                        be.getDeployNBT()
                );
                CompoundTag initData = cfg.toTag();
                if (be.getVehicleType() != null && !be.getVehicleType().isEmpty()) {
                    var db = VehicleDataManager.getDatabase();
                    if (db.isLoaded()) {
                        for (var entry : db.getByCategory().entrySet()) {
                            if (entry.getValue().contains(be.getVehicleType())) {
                                var ci = db.getCategories().get(entry.getKey());
                                if (ci != null) initData.putString("category", ci.displayName());
                                break;
                            }
                        }
                    }
                }
                PacketDistributor.sendToPlayer(sp, S2CVehiclePacket.initDeployer(pos, initData));
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ========== I18n helper ==========

    private static Component tl(String key) { return Component.translatable(key); }

    // ========== GUI ==========

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        Level level = holder.player.level();
        BlockPos pos = holder.pos;
        BlockEntity raw = level.getBlockEntity(pos);
        if (!(raw instanceof VehicleDeployerBlockEntity be)) return null;

        TextField fieldVtype = new TextField(); fieldVtype.setId("deployer_vehicleType"); fieldVtype.lss("width", 140);
        TextField fieldDelay = new TextField().setNumbersOnlyInt(20, 72000).setText("600"); fieldDelay.setId("deployer_respawnDelay"); fieldDelay.lss("width", 50);
        TextField fieldAuto = new TextField().setNumbersOnlyInt(0, 1).setText("0"); fieldAuto.setId("deployer_autoRespawn"); fieldAuto.lss("width", 35);
        TextField fieldAmmo = new TextField().setNumbersOnlyInt(0, 1).setText("1"); fieldAmmo.setId("deployer_spawnWithAmmo"); fieldAmmo.lss("width", 35);
        TextField fieldOx = new TextField().setNumbersOnlyInt(-999, 999).setText("0"); fieldOx.setId("deployer_offsetX"); fieldOx.lss("width", 45);
        TextField fieldOy = new TextField().setNumbersOnlyInt(-999, 999).setText("1"); fieldOy.setId("deployer_offsetY"); fieldOy.lss("width", 45);
        TextField fieldOz = new TextField().setNumbersOnlyInt(-999, 999).setText("0"); fieldOz.setId("deployer_offsetZ"); fieldOz.lss("width", 45);
        TextField fieldYaw = new TextField().setNumbersOnlyInt(-180, 180).setText("0"); fieldYaw.setId("deployer_yaw"); fieldYaw.lss("width", 45);
        TextField fieldPitch = new TextField().setNumbersOnlyInt(-90, 90).setText("0"); fieldPitch.setId("deployer_pitch"); fieldPitch.lss("width", 45);
        TextField fieldNBT = new TextField(); fieldNBT.setId("deployer_deployNBT"); fieldNBT.lss("width", 180).lss("height", 60);

        TextField fieldNbtEnergy = new TextField().setNumbersOnlyInt(0, 999999999);
        fieldNbtEnergy.setId("deployer_nbt_energy"); fieldNbtEnergy.lss("width", 70);
        TextField fieldNbtHealth = new TextField().setNumbersOnlyInt(0, 999999);
        fieldNbtHealth.setId("deployer_nbt_health"); fieldNbtHealth.lss("width", 70);
        TextField fieldNbtInvul = new TextField().setNumbersOnlyInt(0, 1);
        fieldNbtInvul.setId("deployer_nbt_invul"); fieldNbtInvul.lss("width", 35);
        TextField fieldNbtDecoy = new TextField().setNumbersOnlyInt(0, 1);
        fieldNbtDecoy.setId("deployer_nbt_decoy"); fieldNbtDecoy.lss("width", 35);

        Selector catSel = new Selector(); catSel.setId("deployer_category"); catSel.lss("width", "100%");
        Selector vehSel = new Selector(); vehSel.setId("deployer_vehicle"); vehSel.lss("width", "100%");

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
            categoryKeys.add(tl("gui.siege_tools.deployer.msg.no_db").getString());
            catData.put(tl("gui.siege_tools.deployer.msg.no_db").getString(),
                    List.of(tl("gui.siege_tools.deployer.msg.no_db_hint").getString()));
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

        UIElement root = new UIElement(); root.lss("width", 200).lss("padding", 2);
        var title = new Label().setText(tl("gui.siege_tools.deployer.title"));
        title.lss("width", "100%");
        title.textStyle(s -> s.textAlignHorizontal(Horizontal.CENTER));
        root.addChild(title);
        root.addChild(sep());

        TabView tv = new TabView();

        // Tab 1
        UIElement p1 = new UIElement(); p1.lss("padding", 2);
        addRow(p1, tl("gui.siege_tools.deployer.label.category"), catSel, Component.empty());
        addRow(p1, tl("gui.siege_tools.deployer.label.vehicle"), vehSel, Component.empty());
        addRow(p1, tl("gui.siege_tools.deployer.label.id"), fieldVtype, Component.empty());
        p1.addChild(new Label().setText(tl("gui.siege_tools.deployer.hint.select")));
        tv.addTab(new Tab().setText(tl("gui.siege_tools.deployer.tab.vehicle")), p1);

        // Tab 2
        UIElement p2 = new UIElement(); p2.lss("padding", 2);
        addRow(p2, tl("gui.siege_tools.deployer.label.delay"), fieldDelay, tl("gui.siege_tools.deployer.unit.tick"));
        p2.addChild(new Label().setText(tl("gui.siege_tools.deployer.hint.delay")));
        addRow(p2, tl("gui.siege_tools.deployer.label.auto_respawn"), fieldAuto, tl("gui.siege_tools.deployer.value.on"));
        addRow(p2, tl("gui.siege_tools.deployer.label.spawn_ammo"), fieldAmmo, tl("gui.siege_tools.deployer.value.on"));
        tv.addTab(new Tab().setText(tl("gui.siege_tools.deployer.tab.basic")), p2);

        // Tab 3
        UIElement p3 = new UIElement(); p3.lss("padding", 2);
        addRow(p3, tl("gui.siege_tools.deployer.label.offset_x"), fieldOx, tl("gui.siege_tools.deployer.unit.block"));
        addRow(p3, tl("gui.siege_tools.deployer.label.offset_y"), fieldOy, tl("gui.siege_tools.deployer.unit.block"));
        addRow(p3, tl("gui.siege_tools.deployer.label.offset_z"), fieldOz, tl("gui.siege_tools.deployer.unit.block"));
        addRow(p3, tl("gui.siege_tools.deployer.label.yaw"), fieldYaw, tl("gui.siege_tools.deployer.unit.degree"));
        addRow(p3, tl("gui.siege_tools.deployer.label.pitch"), fieldPitch, tl("gui.siege_tools.deployer.unit.degree"));
        tv.addTab(new Tab().setText(tl("gui.siege_tools.deployer.tab.pos")), p3);

        // Tab 4
        UIElement p4 = new UIElement(); p4.lss("padding", 2);
        p4.addChild(new Label().setText(tl("gui.siege_tools.deployer.nbt_simple.hint")));
        addRow(p4, tl("gui.siege_tools.deployer.label.energy"), fieldNbtEnergy, Component.empty());
        addRow(p4, tl("gui.siege_tools.deployer.label.health"), fieldNbtHealth, Component.empty());
        addRow(p4, tl("gui.siege_tools.deployer.label.invulnerable"), fieldNbtInvul, Component.empty());
        addRow(p4, tl("gui.siege_tools.deployer.label.decoy_ready"), fieldNbtDecoy, Component.empty());
        Button btnApplyDefaults = new Button();
        btnApplyDefaults.setText(tl("gui.siege_tools.deployer.btn.apply_defaults"));
        btnApplyDefaults.lss("padding", "2 6");
        btnApplyDefaults.setOnClick(e -> {
            String nbtJson = fieldNBT.getText();
            if (nbtJson.isEmpty() || "{}".equals(nbtJson)) return;
            try {
                var obj = new com.google.gson.Gson().fromJson(nbtJson, com.google.gson.JsonObject.class);
                if (obj != null) {
                    if (obj.has("Energy")) fieldNbtEnergy.setText(String.valueOf(obj.get("Energy").getAsInt()));
                    if (obj.has("Health")) fieldNbtHealth.setText(String.valueOf(obj.get("Health").getAsInt()));
                    if (obj.has("Invulnerable")) fieldNbtInvul.setText(String.valueOf(obj.get("Invulnerable").getAsInt()));
                    if (obj.has("DecoyReady")) fieldNbtDecoy.setText(String.valueOf(obj.get("DecoyReady").getAsInt()));
                }
            } catch (Exception ignored) {}
        });
        p4.addChild(btnApplyDefaults);
        tv.addTab(new Tab().setText(tl("gui.siege_tools.deployer.tab.nbt_simple")), p4);

        // Tab 5
        UIElement p5 = new UIElement(); p5.lss("padding", 2);
        p5.addChild(new Label().setText(tl("gui.siege_tools.deployer.nbt_advanced.hint")));
        p5.addChild(fieldNBT);
        tv.addTab(new Tab().setText(tl("gui.siege_tools.deployer.tab.nbt_advanced")), p5);

        root.addChild(tv);
        root.addChild(sep());

        UIElement btnRow = new UIElement();
        Button btnSave = new Button().setText(tl("gui.siege_tools.deployer.btn.save")); btnSave.lss("padding", "3 8");
        btnSave.setOnClick(e -> {
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
            try { int v = Integer.parseInt(fieldNbtEnergy.getText()); parsed.putInt("Energy", v); } catch (Exception ignored) {}
            try { int v = Integer.parseInt(fieldNbtHealth.getText()); parsed.putInt("Health", v); } catch (Exception ignored) {}
            try { int v = Integer.parseInt(fieldNbtInvul.getText()); parsed.putInt("Invulnerable", v); } catch (Exception ignored) {}
            try { int v = Integer.parseInt(fieldNbtDecoy.getText()); parsed.putInt("DecoyReady", v); } catch (Exception ignored) {}
            PacketDistributor.sendToServer(C2SVehiclePacket.saveDeployer(
                    holder.pos,
                    new DeployerConfigData(
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

        Button btnReset = new Button().setText(tl("gui.siege_tools.deployer.btn.reset")); btnReset.lss("padding", "3 8");
        btnReset.setOnClick(e -> {
            PacketDistributor.sendToServer(C2SVehiclePacket.resetDeployer(holder.pos));
        });
        btnRow.addChild(btnReset);

        Button btnDeploy = new Button().setText(tl("gui.siege_tools.deployer.btn.deploy")); btnDeploy.lss("padding", "3 8");
        btnDeploy.setOnClick(e -> {
            PacketDistributor.sendToServer(C2SVehiclePacket.triggerDeploy(holder.pos));
        });
        btnRow.addChild(btnDeploy);
        root.addChild(btnRow);
        root.addChild(new InventorySlots());

        return ModularUI.of(UI.of(root), holder.player);
    }

    // ========== Helpers ==========

    private static Label sep() { Label s = new Label(); s.setText(tl("gui.siege_tools.deployer.separator")); s.lss("width", "100%").lss("overflow", "hidden"); return s; }
    private static void addRow(UIElement p, Component label, UIElement f, Component unit) {
        UIElement r = new UIElement(); r.addChild(new Label().setText(label)); r.addChild(f); r.addChild(new Label().setText(unit)); p.addChild(r);
    }
    private static int sInt(String s, int d) { try { return Integer.parseInt(s); } catch (Exception e) { return d; } }
}
