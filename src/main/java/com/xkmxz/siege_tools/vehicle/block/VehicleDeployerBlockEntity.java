package com.xkmxz.siege_tools.vehicle.block;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.mojang.logging.LogUtils;
import com.xkmxz.siege_tools.vehicle.data.VehicleDataManager;
import com.xkmxz.siege_tools.vehicle.registry.ModBlockEntities;
import com.xkmxz.siege_tools.vehicle.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

/**
 * 载具部署台 BlockEntity。
 * 替代 KubeJS block_main.js 的 ensurePD + tick + 部署生命周期。
 */
public class VehicleDeployerBlockEntity extends BlockEntity implements MenuProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    // === 持久化配置 ===
    private boolean inited = false;
    private String vehicleType = "";
    private int respawnDelay = 600;      // tick
    private boolean autoRespawn = false;
    private double offsetX = 0.0, offsetY = 1.0, offsetZ = 0.0;
    private float yaw = 0.0f, pitch = 0.0f;
    private String deployedUUID = "";
    private long cooldownEnd = 0;
    private String deployNBT = "{}";
    private String displayName = "";
    private boolean spawnWithAmmo = true;

    // === 运行时标记 ===
    private boolean pendingDeploy = false;

    public VehicleDeployerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VEHICLE_DEPLOYER.get(), pos, state);
    }

    // ========== Tick ==========

    public static void tick(Level level, BlockPos pos, BlockState state, VehicleDeployerBlockEntity be) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // 确保初始化
        be.ensureInit();

        // 处理 PendingDeploy 标记
        if (be.pendingDeploy) {
            be.pendingDeploy = false;
            be.setChanged();
            if (!be.vehicleType.isEmpty()) {
                // 防重复部署
                if (!be.deployedUUID.isEmpty()) {
                    Entity existing = serverLevel.getEntity(UUID.fromString(be.deployedUUID));
                    if (existing != null && existing.isAlive()) {
                        return; // 已有存活载具
                    }
                }
                be.cooldownEnd = 0;
                be.deployedUUID = "";
                be.setChanged();
                com.xkmxz.siege_tools.vehicle.deploy.VehicleDeployerHelper.spawnVehicle(serverLevel, pos, be);
            }
            return;
        }

        // 未配置车辆类型
        if (be.vehicleType.isEmpty()) return;

        long gameTime = level.getGameTime();

        // 重启后 cooldownEnd 范围校验
        if (be.cooldownEnd > gameTime + 72000) {
            LOGGER.warn("[DeployerBE] cooldownEnd 异常（重启残留）: {} > {}, 重置为 0", be.cooldownEnd, gameTime + 72000);
            be.cooldownEnd = 0;
            be.setChanged();
        }

        // 有 UUID → 检查实体存活
        if (!be.deployedUUID.isEmpty()) {
            try {
                Entity entity = serverLevel.getEntity(UUID.fromString(be.deployedUUID));
                if (entity != null && entity.isAlive()) {
                    return; // 存活，无事可做
                }
            } catch (Exception ignored) {}

            // 实体死亡 → 清 UUID，开始冷却
            LOGGER.info("[DeployerBE] 载具已消失 @[{},{},{}]", pos.getX(), pos.getY(), pos.getZ());
            be.deployedUUID = "";
            be.cooldownEnd = gameTime + be.respawnDelay;
            be.setChanged();
            return;
        }

        // 无 UUID + 冷却已过 → 自动部署
        if (gameTime >= be.cooldownEnd && be.autoRespawn) {
            com.xkmxz.siege_tools.vehicle.deploy.VehicleDeployerHelper.spawnVehicle(serverLevel, pos, be);
        }
    }

    // ========== 初始化 ==========

    public void ensureInit() {
        if (!inited) {
            inited = true;
            setChanged();
            LOGGER.info("[DeployerBE] 方块初始化完成 @[{},{},{}]", worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        }
    }

    // ========== 配置读写 ==========

    public void setPendingDeploy(boolean pending) { this.pendingDeploy = pending; setChanged(); }
    public boolean isPendingDeploy() { return pendingDeploy; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; setChanged(); }

    public int getRespawnDelay() { return respawnDelay; }
    public void setRespawnDelay(int respawnDelay) { this.respawnDelay = respawnDelay; setChanged(); }

    public boolean isAutoRespawn() { return autoRespawn; }
    public void setAutoRespawn(boolean autoRespawn) { this.autoRespawn = autoRespawn; setChanged(); }

    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public double getOffsetZ() { return offsetZ; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public void setOffsets(double ox, double oy, double oz, float yaw, float pitch) {
        this.offsetX = ox; this.offsetY = oy; this.offsetZ = oz;
        this.yaw = yaw; this.pitch = pitch;
        setChanged();
    }

    public String getDeployedUUID() { return deployedUUID; }
    public void setDeployedUUID(String deployedUUID) { this.deployedUUID = deployedUUID; setChanged(); }

    public long getCooldownEnd() { return cooldownEnd; }
    public void setCooldownEnd(long cooldownEnd) { this.cooldownEnd = cooldownEnd; setChanged(); }

    public String getDeployNBT() { return deployNBT; }
    public void setDeployNBT(String deployNBT) { this.deployNBT = deployNBT; setChanged(); }

    public boolean isSpawnWithAmmo() { return spawnWithAmmo; }
    public void setSpawnWithAmmo(boolean spawnWithAmmo) { this.spawnWithAmmo = spawnWithAmmo; setChanged(); }

    // ========== NBT ==========

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("inited", inited);
        tag.putString("vehicleType", vehicleType);
        tag.putInt("respawnDelay", respawnDelay);
        tag.putBoolean("autoRespawn", autoRespawn);
        tag.putDouble("offsetX", offsetX);
        tag.putDouble("offsetY", offsetY);
        tag.putDouble("offsetZ", offsetZ);
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        tag.putString("deployedUUID", deployedUUID);
        tag.putLong("cooldownEnd", cooldownEnd);
        tag.putString("deployNBT", deployNBT);
        tag.putString("displayName", displayName);
        tag.putBoolean("spawnWithAmmo", spawnWithAmmo);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inited = tag.getBoolean("inited");
        vehicleType = tag.getString("vehicleType");
        respawnDelay = tag.contains("respawnDelay") ? tag.getInt("respawnDelay") : 600;
        autoRespawn = tag.contains("autoRespawn") ? tag.getBoolean("autoRespawn") : false;
        offsetX = tag.contains("offsetX") ? tag.getDouble("offsetX") : 0.0;
        offsetY = tag.contains("offsetY") ? tag.getDouble("offsetY") : 1.0;
        offsetZ = tag.contains("offsetZ") ? tag.getDouble("offsetZ") : 0.0;
        yaw = tag.contains("yaw") ? tag.getFloat("yaw") : 0.0f;
        pitch = tag.contains("pitch") ? tag.getFloat("pitch") : 0.0f;
        deployedUUID = tag.getString("deployedUUID");
        cooldownEnd = tag.getLong("cooldownEnd");
        deployNBT = tag.getString("deployNBT");
        if (deployNBT.isEmpty()) deployNBT = "{}";
        displayName = tag.getString("displayName");
        spawnWithAmmo = !tag.contains("spawnWithAmmo") || tag.getBoolean("spawnWithAmmo");
    }

    // ========== MenuProvider ==========

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.siege_tools.vehicle_deployer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return buildLDLib2UI(containerId, playerInv, player);
    }

    private AbstractContainerMenu buildLDLib2UI(int containerId, Inventory playerInv, Player player) {
        // ── 输入字段 ──
        TextField fieldVtype = new TextField().setText(vehicleType); fieldVtype.lss("width", 180);
        TextField fieldDelay = new TextField().setNumbersOnlyInt(20, 72000).setText(String.valueOf(respawnDelay)); fieldDelay.lss("width", 55);
        TextField fieldAuto = new TextField().setNumbersOnlyInt(0, 1).setText(autoRespawn ? "1" : "0"); fieldAuto.lss("width", 40);
        TextField fieldAmmo = new TextField().setNumbersOnlyInt(0, 1).setText(spawnWithAmmo ? "1" : "0"); fieldAmmo.lss("width", 40);
        TextField fieldOx = new TextField().setNumbersOnlyInt(-999, 999).setText(String.valueOf((int)offsetX)); fieldOx.lss("width", 50);
        TextField fieldOy = new TextField().setNumbersOnlyInt(-999, 999).setText(String.valueOf((int)offsetY)); fieldOy.lss("width", 50);
        TextField fieldOz = new TextField().setNumbersOnlyInt(-999, 999).setText(String.valueOf((int)offsetZ)); fieldOz.lss("width", 50);
        TextField fieldYaw = new TextField().setNumbersOnlyInt(-180, 180).setText(String.valueOf((int)yaw)); fieldYaw.lss("width", 50);
        TextField fieldPitch = new TextField().setNumbersOnlyInt(-90, 90).setText(String.valueOf((int)pitch)); fieldPitch.lss("width", 50);
        TextField fieldNBT = new TextField().setText(deployNBT); fieldNBT.lss("width", 250).lss("height", 100);

        // 类别/载具选择器
        Selector catSel = new Selector(); catSel.lss("width", "100%");
        Selector vehSel = new Selector(); vehSel.lss("width", "100%");

        var db = VehicleDataManager.getDatabase();
        var catData = new LinkedHashMap<String, List<String>>();
        var categoryKeys = new ArrayList<String>();
        if (db.isLoaded()) {
            for (String ck : db.getAllCategoryKeys()) {
                var ci = db.getCategories().get(ck);
                categoryKeys.add(ci.getDisplayName());
                catData.put(ci.getDisplayName(), db.getVehiclesByCategory(ck));
            }
        }
        if (categoryKeys.isEmpty()) {
            categoryKeys.add("§c数据库未加载");
            catData.put("§c数据库未加载", List.of("§c请保存配置后重启"));
        }
        catSel.setCandidates(new ArrayList<>(categoryKeys));
        vehSel.setCandidates(new ArrayList<>(catData.getOrDefault(categoryKeys.get(0), List.of())));

        // 联动
        catSel.setOnValueChanged(newCat -> {
            if (newCat != null && catData.containsKey(newCat)) {
                vehSel.setCandidates(new ArrayList<>(catData.get(newCat)));
                var v = catData.get(newCat);
                if (!v.isEmpty()) vehSel.setSelected(v.get(0));
            }
        });
        vehSel.setOnValueChanged(newVid -> { if (newVid != null) fieldVtype.setText(newVid.toString()); });

        // ── UI ──
        UIElement root = new UIElement(); root.lss("width", 280).lss("padding", 6);
        var title = new Label().setText(Component.literal("§6╔══ 载具部署台配置 ══╗"));
        title.lss("width", "100%");
        title.textStyle(s -> s.textAlignHorizontal(com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal.CENTER));
        root.addChild(title);
        root.addChild(sep());

        TabView tv = new TabView();

        // Tab 1: 载具选择
        UIElement p1 = new UIElement(); p1.lss("padding", 4);
        p1.addChild(new Label().setText(Component.literal("§e── 选择载具 ──")));
        addRow(p1, "§7类别:", catSel, "");
        addGap(p1);
        addRow(p1, "§7载具:", vehSel, "");
        addGap(p1);
        addRow(p1, "§7ID:", fieldVtype, "");
        p1.addChild(new Label().setText(Component.literal("§8从下拉选择或直接输入完整 ID")));
        tv.addTab(new Tab().setText("载具"), p1);

        // Tab 2: 基础设置
        UIElement p2 = new UIElement(); p2.lss("padding", 4);
        p2.addChild(new Label().setText(Component.literal("§e── 部署基础参数 ──")));
        addRow(p2, "§7重生延迟:", fieldDelay, " §7tick");
        p2.addChild(new Label().setText(Component.literal("§8(20 tick = 1 秒, 默认 600 = 30s)")));
        addGap(p2);
        addRow(p2, "§7自动重生:", fieldAuto, " §7(1=开启)");
        addGap(p2);
        addRow(p2, "§7生成带弹药:", fieldAmmo, " §7(1=是)");
        tv.addTab(new Tab().setText("基础"), p2);

        // Tab 3: 坐标偏移
        UIElement p3 = new UIElement(); p3.lss("padding", 4);
        p3.addChild(new Label().setText(Component.literal("§e── 部署坐标偏移 ──")));
        addRow(p3, "§7X偏移:", fieldOx, " 格");
        addRow(p3, "§7Y偏移:", fieldOy, " 格");
        addRow(p3, "§7Z偏移:", fieldOz, " 格");
        addGap(p3);
        addRow(p3, "§7朝向(yaw):", fieldYaw, " °");
        addRow(p3, "§7俯仰(pitch):", fieldPitch, " °");
        tv.addTab(new Tab().setText("坐标"), p3);

        // Tab 4: deployNBT
        UIElement p4 = new UIElement(); p4.lss("padding", 4);
        p4.addChild(new Label().setText(Component.literal("§e── deployNBT ──")));
        p4.addChild(new Label().setText(Component.literal("§8留空 {} 使用数据库默认值")));
        addGap(p4);
        p4.addChild(fieldNBT);
        tv.addTab(new Tab().setText("NBT"), p4);

        root.addChild(tv);
        root.addChild(sep());

        // 按钮行
        UIElement btnRow = new UIElement();
        Button btnSave = new Button().setText(Component.literal("§a✔ 保存")); btnSave.lss("padding", "3 10");
        btnSave.setOnServerClick(e -> {
            vehicleType = fieldVtype.getText();
            respawnDelay = Math.max(20, safeInt(fieldDelay.getText(), 600));
            autoRespawn = "1".equals(fieldAuto.getText());
            spawnWithAmmo = "1".equals(fieldAmmo.getText());
            offsetX = safeInt(fieldOx.getText(), 0); offsetY = safeInt(fieldOy.getText(), 1); offsetZ = safeInt(fieldOz.getText(), 0);
            yaw = safeInt(fieldYaw.getText(), 0); pitch = safeInt(fieldPitch.getText(), 0);
            deployNBT = fieldNBT.getText(); if (deployNBT.isEmpty()) deployNBT = "{}";
            setChanged();
            player.displayClientMessage(Component.literal("§a✔ 配置已保存！"), false);
        });
        btnRow.addChild(btnSave);

        Button btnDeploy = new Button().setText(Component.literal("§6⚡ 立即部署")); btnDeploy.lss("padding", "3 10");
        btnDeploy.setOnServerClick(e -> {
            if (vehicleType.isEmpty()) { player.displayClientMessage(Component.literal("§c请先配置载具类型"), false); return; }
            pendingDeploy = true; setChanged();
            player.displayClientMessage(Component.literal("§e⏳ 部署命令已提交"), false);
        });
        btnRow.addChild(btnDeploy);
        root.addChild(btnRow);
        root.addChild(new InventorySlots());

        ModularUI modularUI = ModularUI.of(UI.of(root), player);
        IContainerUIHolder holder = new IContainerUIHolder() {
            @Override public ModularUI createUI(Player p) { return modularUI; }
            @Override public boolean isStillValid(Player p) { return true; }
        };
        modularUI.setMenu(new ModularUIContainerMenu(ModMenuTypes.VEHICLE_DEPLOYER.get(), containerId, playerInv, holder));
        return modularUI.getMenu();
    }

    private static Label sep() {
        Label s = new Label(); s.setText(Component.literal("§8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        s.lss("width", "100%").lss("overflow", "hidden");
        return s;
    }

    private static void addRow(UIElement parent, String label, UIElement field, String unit) {
        UIElement row = new UIElement();
        row.addChild(new Label().setText(Component.literal(label)));
        row.addChild(field);
        row.addChild(new Label().setText(Component.literal(unit)));
        parent.addChild(row);
    }
    private static void addGap(UIElement parent) { parent.addChild(new Label().setText(Component.literal(" "))); }
    private static int safeInt(String s, int def) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; } }
}
