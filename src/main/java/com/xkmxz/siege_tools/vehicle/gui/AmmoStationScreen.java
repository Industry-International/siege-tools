package com.xkmxz.siege_tools.vehicle.gui;

import com.xkmxz.siege_tools.siege_tools;
import com.xkmxz.siege_tools.vehicle.network.C2SSaveAmmoConfig;
import net.minecraft.client.Minecraft;
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
 * 弹药补给站配置屏幕（8分页Tab）。
 */
public class AmmoStationScreen extends AbstractContainerScreen<AmmoStationMenu> {

    private static final ResourceLocation BG = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int BG_WIDTH = 176, BG_HEIGHT = 222;

    private int currentTab = 0;
    private final List<EditBox> slotFields = new ArrayList<>();
    private EditBox fieldScanRange, fieldCooldown, fieldEnterDelay;
    private static final String[] TAB_NAMES = {"§7基础", "§7炮弹", "§7小口径", "§7枪/火箭", "§7导弹/航弹", "§aMCSP上", "§aMCSP下", "§c作弊"};

    public AmmoStationScreen(AmmoStationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
        this.inventoryLabelY = 10000; // 隐藏默认标签
    }

    @Override
    protected void init() {
        super.init();
        int l = leftPos, t = topPos;

        // 基础参数字段
        fieldScanRange = new EditBox(font, l + 82, t + 20, 50, 14, Component.literal(""));
        fieldScanRange.setFilter(s -> s.matches("\\d*"));
        fieldScanRange.setValue(String.valueOf(menu.data.get(AmmoStationMenu.DATA_SCAN_RANGE)));
        addWidget(fieldScanRange);

        fieldCooldown = new EditBox(font, l + 82, t + 38, 50, 14, Component.literal(""));
        fieldCooldown.setFilter(s -> s.matches("\\d*"));
        fieldCooldown.setValue(String.valueOf(menu.data.get(AmmoStationMenu.DATA_COOLDOWN)));
        addWidget(fieldCooldown);

        fieldEnterDelay = new EditBox(font, l + 82, t + 56, 50, 14, Component.literal(""));
        fieldEnterDelay.setFilter(s -> s.matches("\\d*"));
        fieldEnterDelay.setValue(String.valueOf(menu.data.get(AmmoStationMenu.DATA_ENTER_DELAY)));
        addWidget(fieldEnterDelay);

        // 弹药字段
        slotFields.clear();
        for (int i = 0; i < AmmoStationMenu.AMMO_SLOT_KEYS.size(); i++) {
            EditBox box = new EditBox(font, 0, 0, 45, 12, Component.literal(""));
            box.setFilter(s -> s.matches("\\d*"));
            box.setValue(String.valueOf(menu.data.get(AmmoStationMenu.DATA_SLOTS_START + i)));
            slotFields.add(box);
            addWidget(box);
        }

        // 保存按钮
        addRenderableWidget(Button.builder(
            Component.literal("§a✔ 保存"),
            btn -> onSave()
        ).bounds(l + 10, t + 115, 70, 16).build());

        // 重置按钮
        addRenderableWidget(Button.builder(
            Component.literal("§e↻ 重置"),
            btn -> onReset()
        ).bounds(l + 90, t + 115, 70, 16).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // renderBackground is called by Screen.render() before renderBg()
        int l = leftPos, t = topPos;

        // 背景
        graphics.blit(BG, l, t, 0, 0, BG_WIDTH, BG_HEIGHT, BG_WIDTH, BG_HEIGHT * 2);

        // 标签页按钮（顶部）
        int[] tabX = {l + 3, l + 35, l + 67, l + 99, l + 131, l + 163, l + 195, l + 227};
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int color = (i == currentTab) ? 0xFFAAAA00 : 0xFF555555;
            int tx = tabX[i], ty = t + 3;
            graphics.fill(tx, ty, tx + 30, ty + 12, color);
            graphics.drawString(font, TAB_NAMES[i], tx + 1, ty + 2, 0xFFFFFFFF);
        }

        // 标题
        graphics.drawString(font, "§6弹药补给站配置", l + 8, t + 5, 0);

        // 根据当前Tab渲染不同内容（基础设置区）
        if (currentTab == 0) {
            graphics.drawString(font, "§7扫描范围:", l + 10, t + 22, 0);
            graphics.drawString(font, "格", l + 135, t + 22, 0);
            graphics.drawString(font, "§7冷却时间:", l + 10, t + 40, 0);
            graphics.drawString(font, "秒", l + 135, t + 40, 0);
            graphics.drawString(font, "§7驶入等待:", l + 10, t + 58, 0);
            graphics.drawString(font, "秒", l + 135, t + 58, 0);
        } else {
            // 弹药配置Tab: 显示对应的弹药字段
            renderAmmoSlots(graphics, l, t);
        }
    }

    private void renderAmmoSlots(GuiGraphics graphics, int l, int t) {
        // 确定当前Tab的弹药索引范围
        int start, end;
        String sectionTitle;
        switch (currentTab) {
            case 1 -> { start = 0; end = 4; sectionTitle = "§e── 大口径炮弹 ──"; }
            case 2 -> { start = 4; end = 8; sectionTitle = "§e── 小口径机炮弹 ──"; }
            case 3 -> { start = 8; end = 12; sectionTitle = "§e── 枪弹/火箭弹 ──"; }
            case 4 -> { start = 12; end = 18; sectionTitle = "§e── 导弹/航弹 ──"; }
            case 5 -> { start = 18; end = 23; sectionTitle = "§e── 坦克炮/导弹(MCSP) ──"; }
            case 6 -> { start = 23; end = 29; sectionTitle = "§e── 机关炮/机枪(MCSP) ──"; }
            default -> { return; }
        }

        int y = t + 18;
        graphics.drawString(font, sectionTitle, l + 5, y, 0);
        y += 12;

        String[] labels = {"§6大口径AP", "§c大口径HE", "§a大口径葡萄", "§6迫击炮弹",
            "§b小口径AP", "§d小口径HE", "§a小口径葡萄", "§b防空弹",
            "§7步枪弹", "§9重弹", "§e小型火箭", "§e火箭弹",
            "§a导弹", "§a中型对地导弹", "§a大型对地导弹", "§a防空导弹",
            "§c中型航弹", "§c小型航弹",
            "§6125mm穿甲", "§c125mm高爆", "§5120mm迫击", "§aTOW-2", "§eMLRS火箭",
            "§b25mm机炮", "§d30mm机炮", "§c40mm高爆", "§740mm烟雾",
            "§77.62mm机枪", "§7小口径弹药"};

        for (int i = start; i < end; i++) {
            EditBox box = slotFields.get(i);
            box.setPosition(l + 55, y + 1);
            box.setWidth(45);
            graphics.drawString(font, labels[i] + ":", l + 5, y + 2, 0);
            graphics.drawString(font, "个", l + 103, y + 2, 0);
            y += 14;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Tab点击检测
        int l = leftPos, t = topPos;
        int[] tabX = {l + 3, l + 35, l + 67, l + 99, l + 131, l + 163, l + 195, l + 227};
        for (int i = 0; i < TAB_NAMES.length; i++) {
            if (mx >= tabX[i] && mx <= tabX[i] + 30 && my >= t + 3 && my <= t + 15) {
                currentTab = i;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void onSave() {
        Map<String, Integer> slots = new HashMap<>();
        for (int i = 0; i < AmmoStationMenu.AMMO_SLOT_KEYS.size(); i++) {
            try {
                int val = Integer.parseInt(slotFields.get(i).getValue());
                if (val > 0) slots.put(AmmoStationMenu.AMMO_SLOT_KEYS.get(i), val);
            } catch (NumberFormatException ignored) {}
        }

        PacketDistributor.sendToServer(new C2SSaveAmmoConfig(
            menu.getBlockPos(),
            parseInt(fieldScanRange.getValue(), 12),
            parseInt(fieldCooldown.getValue(), 5),
            parseInt(fieldEnterDelay.getValue(), 3),
            slots
        ));
    }

    private void onReset() {
        fieldScanRange.setValue("12");
        fieldCooldown.setValue("5");
        fieldEnterDelay.setValue("3");
        for (int i = 0; i < slotFields.size(); i++) {
            slotFields.get(i).setValue("0");
        }
        onSave();
    }

    private int parseInt(String s, int def) {
        try { return Math.max(0, Integer.parseInt(s)); } catch (NumberFormatException e) { return def; }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int x, int y) {}
}
