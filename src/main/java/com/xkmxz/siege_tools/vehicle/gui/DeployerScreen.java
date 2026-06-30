package com.xkmxz.siege_tools.vehicle.gui;

import com.xkmxz.siege_tools.vehicle.data.VehicleDatabase;
import com.xkmxz.siege_tools.vehicle.data.VehicleDataManager;
import com.xkmxz.siege_tools.vehicle.network.C2SSaveDeployerConfig;
import com.xkmxz.siege_tools.vehicle.network.C2STriggerDeploy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * 载具部署台配置屏幕（5分页Tab）。
 */
public class DeployerScreen extends AbstractContainerScreen<DeployerMenu> {

    private static final ResourceLocation BG = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int BG_WIDTH = 176, BG_HEIGHT = 222;

    private int currentTab = 0;
    private static final String[] TAB_NAMES = {"§7载具", "§7基础", "§7坐标", "§aNBT简单", "§bNBT高级"};

    // 所有输入字段
    private EditBox fieldVehicleType, fieldRespawnDelay, fieldAutoRespawn, fieldSpawnAmmo;
    private EditBox fieldOffsetX, fieldOffsetY, fieldOffsetZ, fieldYaw, fieldPitch;
    private EditBox fieldNbtEnergy, fieldNbtHealth, fieldNbtInvul, fieldNbtDecoy;
    private EditBox fieldDeployNBT;

    public DeployerScreen(DeployerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        int l = leftPos, t = topPos;

        // Tab 1: 载具选择
        fieldVehicleType = new EditBox(font, l + 30, t + 20, 130, 14, Component.literal(""));
        fieldVehicleType.setMaxLength(128);
        addWidget(fieldVehicleType);

        // Tab 2: 基础设置
        fieldRespawnDelay = new EditBox(font, l + 75, t + 20, 45, 14, Component.literal(""));
        fieldRespawnDelay.setFilter(s -> s.matches("\\d*"));
        fieldRespawnDelay.setValue(String.valueOf(menu.data.get(DeployerMenu.RESPAWN_DELAY)));
        addWidget(fieldRespawnDelay);

        fieldAutoRespawn = new EditBox(font, l + 75, t + 38, 30, 14, Component.literal(""));
        fieldAutoRespawn.setFilter(s -> s.matches("[01]"));
        fieldAutoRespawn.setValue(String.valueOf(menu.data.get(DeployerMenu.AUTO_RESPAWN)));
        addWidget(fieldAutoRespawn);

        fieldSpawnAmmo = new EditBox(font, l + 75, t + 56, 30, 14, Component.literal(""));
        fieldSpawnAmmo.setFilter(s -> s.matches("[01]"));
        fieldSpawnAmmo.setValue(String.valueOf(menu.data.get(DeployerMenu.SPAWN_WITH_AMMO)));
        addWidget(fieldSpawnAmmo);

        // Tab 3: 坐标偏移
        int oy = t + 18;
        fieldOffsetX = makeIntBox(l + 55, oy, 50); addWidget(fieldOffsetX);
        fieldOffsetY = makeIntBox(l + 55, oy + 16, 50); addWidget(fieldOffsetY);
        fieldOffsetZ = makeIntBox(l + 55, oy + 32, 50); addWidget(fieldOffsetZ);
        fieldYaw = makeIntBox(l + 55, oy + 52, 50); addWidget(fieldYaw);
        fieldPitch = makeIntBox(l + 55, oy + 68, 50); addWidget(fieldPitch);

        setOffsetValues();

        // Tab 4: NBT简单模式
        fieldNbtEnergy = new EditBox(font, 0, 0, 60, 14, Component.literal(""));
        fieldNbtEnergy.setFilter(s -> s.matches("\\d*"));
        addWidget(fieldNbtEnergy);
        fieldNbtHealth = new EditBox(font, 0, 0, 60, 14, Component.literal(""));
        fieldNbtHealth.setFilter(s -> s.matches("\\d*"));
        addWidget(fieldNbtHealth);
        fieldNbtInvul = new EditBox(font, 0, 0, 30, 14, Component.literal(""));
        fieldNbtInvul.setFilter(s -> s.matches("[01]"));
        fieldNbtInvul.setValue("0");
        addWidget(fieldNbtInvul);
        fieldNbtDecoy = new EditBox(font, 0, 0, 30, 14, Component.literal(""));
        fieldNbtDecoy.setFilter(s -> s.matches("[01]"));
        fieldNbtDecoy.setValue("0");
        addWidget(fieldNbtDecoy);

        // Tab 5: NBT高级
        fieldDeployNBT = new EditBox(font, l + 10, t + 50, 154, 60, Component.literal(""));
        fieldDeployNBT.setMaxLength(4096);
        fieldDeployNBT.setValue("{}");
        addWidget(fieldDeployNBT);

        // 按钮
        addRenderableWidget(Button.builder(Component.literal("§a✔ 保存"), b -> onSave())
                .bounds(l + 5, t + 115, 50, 16).build());
        addRenderableWidget(Button.builder(Component.literal("§e↻ 重置"), b -> onReset())
                .bounds(l + 60, t + 115, 50, 16).build());
        addRenderableWidget(Button.builder(Component.literal("§6⚡部署"), b -> onDeploy())
                .bounds(l + 115, t + 115, 50, 16).build());
    }

    private EditBox makeIntBox(int x, int y, int w) {
        EditBox box = new EditBox(font, x, y, w, 14, Component.literal(""));
        box.setFilter(s -> s.matches("-?\\d*"));
        box.setValue("0");
        return box;
    }

    private void setOffsetValues() {
        fieldOffsetX.setValue(String.valueOf(menu.data.get(DeployerMenu.OFFSET_X) / 100.0));
        fieldOffsetY.setValue(String.valueOf(menu.data.get(DeployerMenu.OFFSET_Y) / 100.0));
        fieldOffsetZ.setValue(String.valueOf(menu.data.get(DeployerMenu.OFFSET_Z) / 100.0));
        fieldYaw.setValue(String.valueOf(menu.data.get(DeployerMenu.YAW) / 10.0));
        fieldPitch.setValue(String.valueOf(menu.data.get(DeployerMenu.PITCH) / 10.0));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mx, int my) {
        renderBackground(graphics, mx, my, partialTick);
        int l = leftPos, t = topPos;
        graphics.blit(BG, l, t, 0, 0, BG_WIDTH, BG_HEIGHT, BG_WIDTH, BG_HEIGHT * 2);

        // Tab按钮
        int[] tx = {l+3, l+35, l+67, l+99, l+131};
        for (int i = 0; i < TAB_NAMES.length; i++) {
            graphics.fill(tx[i], t+3, tx[i]+30, t+15, i == currentTab ? 0xFFAAAA00 : 0xFF555555);
            graphics.drawString(font, TAB_NAMES[i], tx[i]+1, t+5, 0xFFFFFFFF);
        }

        switch (currentTab) {
            case 0 -> renderTabVehicle(graphics, l, t);
            case 1 -> renderTabBasic(graphics, l, t);
            case 2 -> renderTabOffset(graphics, l, t);
            case 3 -> renderTabNbtSimple(graphics, l, t);
            case 4 -> renderTabNbtAdvanced(graphics, l, t);
        }
    }

    private void renderTabVehicle(GuiGraphics g, int l, int t) {
        g.drawString(font, "§e── 选择载具 ──", l + 5, t + 12, 0);
        g.drawString(font, "§7ID:", l + 10, t + 22, 0);
        g.drawString(font, "§8输入载具 ID（如 superbwarfare:t_90a）", l + 10, t + 38, 0);
        g.drawString(font, "§8数据库状态: " + (VehicleDataManager.getDatabase().isLoaded()
                ? "§a已加载(" + VehicleDataManager.getDatabase().getVehicleCount() + "种)"
                : "§c未加载"), l + 10, t + 80, 0);
    }

    private void renderTabBasic(GuiGraphics g, int l, int t) {
        g.drawString(font, "§e── 部署基础参数 ──", l + 5, t + 12, 0);
        g.drawString(font, "§7重生延迟:", l + 10, t + 22, 0);
        g.drawString(font, "§7tick (20=1秒)", l + 123, t + 22, 0);
        g.drawString(font, "§7自动重生(1/0):", l + 10, t + 40, 0);
        g.drawString(font, "§7生成带弹药(1/0):", l + 10, t + 58, 0);
    }

    private void renderTabOffset(GuiGraphics g, int l, int t) {
        g.drawString(font, "§e── 部署坐标偏移 ──", l + 5, t + 12, 0);
        g.drawString(font, "§7X:", l + 10, t + 20, 0); g.drawString(font, "格", l + 108, t + 20, 0);
        g.drawString(font, "§7Y:", l + 10, t + 36, 0); g.drawString(font, "格", l + 108, t + 36, 0);
        g.drawString(font, "§7Z:", l + 10, t + 52, 0); g.drawString(font, "格", l + 108, t + 52, 0);
        g.drawString(font, "§7Yaw:", l + 10, t + 70, 0); g.drawString(font, "°", l + 108, t + 70, 0);
        g.drawString(font, "§7Pitch:", l + 10, t + 86, 0); g.drawString(font, "°", l + 108, t + 86, 0);
    }

    private void renderTabNbtSimple(GuiGraphics g, int l, int t) {
        g.drawString(font, "§e── NBT 参数配置 ──", l + 5, t + 12, 0);
        g.drawString(font, "§7Energy 能量:", l + 5, t + 20, 0);
        fieldNbtEnergy.setPosition(l + 72, t + 18);
        g.drawString(font, "§7Health 生命:", l + 5, t + 36, 0);
        fieldNbtHealth.setPosition(l + 72, t + 34);
        g.drawString(font, "§7无敌(1/0):", l + 5, t + 52, 0);
        fieldNbtInvul.setPosition(l + 72, t + 50);
        g.drawString(font, "§7诱饵(1/0):", l + 5, t + 68, 0);
        fieldNbtDecoy.setPosition(l + 72, t + 66);
    }

    private void renderTabNbtAdvanced(GuiGraphics g, int l, int t) {
        g.drawString(font, "§e── deployNBT 原始 JSON ──", l + 5, t + 12, 0);
        g.drawString(font, "§7留空{}则使用数据库默认值", l + 5, t + 28, 0);
        g.drawString(font, "§8也会与NBT简单页的值合并", l + 5, t + 114, 0);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int l = leftPos, t = topPos;
        int[] tx = {l+3, l+35, l+67, l+99, l+131};
        for (int i = 0; i < TAB_NAMES.length; i++) {
            if (mx >= tx[i] && mx <= tx[i] + 30 && my >= t + 3 && my <= t + 15) {
                currentTab = i;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void onSave() {
        PacketDistributor.sendToServer(new C2SSaveDeployerConfig(
            menu.getBlockPos(),
            fieldVehicleType.getValue(),
            parseInt(fieldRespawnDelay.getValue(), 600),
            "1".equals(fieldAutoRespawn.getValue()),
            "1".equals(fieldSpawnAmmo.getValue()),
            parseDouble(fieldOffsetX.getValue(), 0),
            parseDouble(fieldOffsetY.getValue(), 1),
            parseDouble(fieldOffsetZ.getValue(), 0),
            (float)parseDouble(fieldYaw.getValue(), 0),
            (float)parseDouble(fieldPitch.getValue(), 0),
            fieldDeployNBT.getValue()
        ));
    }

    private void onReset() {
        fieldRespawnDelay.setValue("600");
        fieldAutoRespawn.setValue("1");
        fieldSpawnAmmo.setValue("1");
        fieldOffsetX.setValue("0"); fieldOffsetY.setValue("1"); fieldOffsetZ.setValue("0");
        fieldYaw.setValue("0"); fieldPitch.setValue("0");
        fieldDeployNBT.setValue("{}");
        fieldNbtEnergy.setValue(""); fieldNbtHealth.setValue("");
        fieldNbtInvul.setValue("0"); fieldNbtDecoy.setValue("0");
        onSave();
    }

    private void onDeploy() {
        PacketDistributor.sendToServer(new C2STriggerDeploy(menu.getBlockPos()));
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int x, int y) {}
}
