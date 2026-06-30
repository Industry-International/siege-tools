package com.xkmxz.siege_tools.vehicle.block;

import com.google.gson.*;
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
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

/**
 * 弹药补给站 BlockEntity。
 * 替代 KubeJS ammo_replenish/main.js 的 blockEntityTick + executeStationReplenish()。
 */
public class AmmoCrateBlockEntity extends BlockEntity implements MenuProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String LOG_PREFIX = "[弹药补给站]";

    // === 配置 ===
    private int scanRange = 12;
    private int cooldownSec = 5;
    private int enterDelay = 3;  // 秒
    private Map<String, Integer> slots = getDefaultSlots();

    // === 运行时状态 ===
    private long cooldownEnd = 0;
    private Map<String, Long> vehicleTimers = new HashMap<>();  // UUID → enterGameTime
    private boolean cheatMode = false;
    private boolean pendingReplenish = false;  // GUI 手动触发标记

    public AmmoCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AMMO_STATION.get(), pos, state);
    }

    // ========== Tick ==========

    public static void tick(Level level, BlockPos pos, BlockState state, AmmoCrateBlockEntity be) {
        if (level.isClientSide()) return;
        // 每 20 tick 执行一次
        if (level.getGameTime() % 20 != 0) return;

        // 检查 PendingReplenish 标记（来自 GUI 手动触发）
        if (be.pendingReplenish) {
            be.pendingReplenish = false;
            be.setChanged();
            LOGGER.info("{} 检测到手动补给请求，忽略冷却执行", LOG_PREFIX);
            be.executeReplenish(level, true);
        }

        be.executeReplenish(level, false);
    }

    public void triggerManualReplenish() {
        this.pendingReplenish = true;
        setChanged();
    }

    /**
     * 执行一次完整的扫描+停留计时+补给流程。
     * 对应 KubeJS executeStationReplenish()。
     */
    public void executeReplenish(Level level, boolean ignoreCooldown) {
        try {
            int range = scanRange;
            int cooldownTicks = cooldownSec * 20;
            int enterDelayTicks = enterDelay * 20;

            // 检查冷却
            long gameTime = level.getGameTime();
            if (!ignoreCooldown && gameTime < cooldownEnd) {
                return;  // 冷却中
            }

            BlockPos pos = getBlockPos();
            int bx = pos.getX(), by = pos.getY(), bz = pos.getZ();

            // AABB 扫描
            AABB scanBox = new AABB(bx - range + 0.5, by - range + 0.5, bz - range + 0.5,
                    bx + range + 0.5, by + range + 0.5, bz + range + 0.5);

            List<Entity> entities = level.getEntitiesOfClass(Entity.class, scanBox,
                    e -> !e.isRemoved() && isSBWVehicle(e));

            // 记录本次检测到的载具 UUID
            Set<String> detectedUUIDs = new HashSet<>();
            boolean replenishedAny = false;

            // 计时数据：（保留旧计时器，用于检测离开的车辆）
            Map<String, Long> currentTimers = new HashMap<>(vehicleTimers);

            for (Entity entity : entities) {
                String uuid = entity.getUUID().toString();
                String typeStr = entity.getType().builtInRegistryHolder().key().location().toString();
                detectedUUIDs.add(uuid);

                LOGGER.debug("{} [扫描] 发现载具 type={}, uuid={}...", LOG_PREFIX, typeStr, uuid.substring(0, 8));

                if (!currentTimers.containsKey(uuid)) {
                    // 首次进入范围
                    currentTimers.put(uuid, gameTime);
                    LOGGER.debug("{} [计时] 载具 {}... 首次进入范围", LOG_PREFIX, uuid.substring(0, 8));
                } else {
                    long enterTime = currentTimers.get(uuid);
                    long elapsed = gameTime - enterTime;

                    // 跨 session 负数修复
                    if (elapsed < 0) {
                        currentTimers.put(uuid, gameTime);
                        elapsed = 0;
                    }

                    LOGGER.debug("{} [计时] 载具 {}... 已在范围内 {}/{} ticks",
                            LOG_PREFIX, uuid.substring(0, 8), elapsed, enterDelayTicks);

                    if (elapsed >= enterDelayTicks) {
                        // 停留时间达标 → 补给
                        replenishedAny |= replenishVehicle(level, entity);

                        // 补给后移除计时（下次进入重新计时）
                        currentTimers.remove(uuid);
                    }
                }
            }

            // 清理离开范围的车辆计时
            vehicleTimers.clear();
            for (Map.Entry<String, Long> entry : currentTimers.entrySet()) {
                if (detectedUUIDs.contains(entry.getKey())) {
                    vehicleTimers.put(entry.getKey(), entry.getValue());
                }
            }

            // 有计时数据变化时写回
            if (!detectedUUIDs.isEmpty()) {
                setChanged();
            }

            // 若执行了补给，设置冷却
            if (replenishedAny) {
                cooldownEnd = gameTime + cooldownTicks;
                setChanged();
                LOGGER.info("{} [冷却] 设置冷却至 gameTime={} ({}s后)", LOG_PREFIX, cooldownEnd, cooldownSec);
            }
        } catch (Exception e) {
            LOGGER.error("{} 执行补给出错: {}", LOG_PREFIX, e.getMessage());
        }
    }

    /**
     * 补给单辆载具的弹药。
     * 通过 /data merge entity + /item replace 命令操作载具 Inventory。
     */
    private boolean replenishVehicle(Level level, Entity entity) {
        try {
            if (!(level instanceof ServerLevel serverLevel)) return false;

            // 读取载具 NBT
            CompoundTag nbt = new CompoundTag();
            entity.save(nbt);

            if (!nbt.contains("Inventory", Tag.TAG_COMPOUND)) {
                LOGGER.debug("{} [补给] NBT 中没有 Inventory 字段", LOG_PREFIX);
                return false;
            }

            CompoundTag inventory = nbt.getCompound("Inventory");
            if (!inventory.contains("Items", Tag.TAG_LIST)) {
                LOGGER.debug("{} [补给] Inventory 中没有 Items 字段", LOG_PREFIX);
                return false;
            }

            Tag rawItems = inventory.get("Items");
            if (!(rawItems instanceof net.minecraft.nbt.ListTag items)) {
                return false;
            }

            int itemCount = items.size();
            LOGGER.debug("{} [补给] Inventory.Items 共 {} 个物品", LOG_PREFIX, itemCount);

            // 统计每种弹药当前总量
            Map<String, Integer> currentCounts = new HashMap<>();
            for (int i = 0; i < itemCount; i++) {
                CompoundTag item = items.getCompound(i);
                String itemId = item.getString("id");
                String ammoKey = VehicleDataManager.getAmmoShortName(itemId);
                if (ammoKey == null) continue;
                int count = item.getInt("count");
                currentCounts.put(ammoKey, currentCounts.getOrDefault(ammoKey, 0) + count);
            }

            // 计算需要补充的量
            Map<String, Integer> needToAdd = new HashMap<>();
            for (Map.Entry<String, Integer> slotEntry : slots.entrySet()) {
                String ammoKey = slotEntry.getKey();
                int maxVal = slotEntry.getValue();
                int current = currentCounts.getOrDefault(ammoKey, 0);
                if (current >= maxVal) continue;
                needToAdd.put(ammoKey, maxVal - current);
            }

            if (needToAdd.isEmpty()) {
                LOGGER.debug("{} [补给] 所有弹药均已达到或超过配置最大值", LOG_PREFIX);
                return false;
            }

            // 先在现有物品组上叠加
            for (int i = 0; i < items.size(); i++) {
                CompoundTag item = items.getCompound(i);
                String itemId = item.getString("id");
                String ammoKey = VehicleDataManager.getAmmoShortName(itemId);
                if (ammoKey == null || !needToAdd.containsKey(ammoKey)) continue;

                int currentCount = item.getInt("count");
                int toAdd = needToAdd.get(ammoKey);
                int canAdd = Math.min(toAdd, 64 - currentCount);
                if (canAdd <= 0) continue;

                item.putInt("count", currentCount + canAdd);
                items.set(i, item);
                needToAdd.put(ammoKey, toAdd - canAdd);
                if (needToAdd.get(ammoKey) <= 0) {
                    needToAdd.remove(ammoKey);
                    if (needToAdd.isEmpty()) break;
                }
            }

            // 若还有剩余，添加新物品组
            if (!needToAdd.isEmpty()) {
                int nextSlot = items.size();
                for (Map.Entry<String, Integer> need : needToAdd.entrySet()) {
                    String ammoKey = need.getKey();
                    int remaining = need.getValue();
                    var ammoTypeInfo = VehicleDataManager.getAmmoType(ammoKey);
                    if (ammoTypeInfo == null) continue;
                    String itemId = ammoTypeInfo.id();

                    while (remaining > 0) {
                        int addCount = Math.min(remaining, 64);
                        CompoundTag newItem = new CompoundTag();
                        newItem.putString("id", itemId);
                        newItem.putInt("count", addCount);
                        newItem.putInt("Slot", nextSlot);
                        items.add(newItem);
                        nextSlot++;
                        remaining -= addCount;
                    }
                }

                // 重新整理 Slot 编号
                for (int j = 0; j < items.size(); j++) {
                    CompoundTag slotItem = items.getCompound(j);
                    slotItem.putInt("Slot", j);
                    items.set(j, slotItem);
                }
            }

            // 写回实体 NBT — 使用 /data merge entity 和 /item replace entity
            inventory.put("Items", items);
            String inventorySnbt = inventory.toString();
            String uuid = entity.getUUID().toString();
            CommandSourceStack source = serverLevel.getServer().createCommandSourceStack()
                    .withSuppressedOutput().withPermission(2);

            serverLevel.getServer().getCommands().performPrefixedCommand(source,
                    "data merge entity " + uuid + " {Inventory:" + inventorySnbt + "}");

            // 方案B：/item replace entity 立即可见
            for (int si = 0; si < items.size(); si++) {
                CompoundTag slotItem = items.getCompound(si);
                int slotNum = slotItem.getInt("Slot");
                String itemId = slotItem.getString("id");
                int count = slotItem.getInt("count");
                serverLevel.getServer().getCommands().performPrefixedCommand(source,
                        "item replace entity " + uuid + " container." + slotNum + " " + itemId + " " + count);
            }

            LOGGER.info("{} [补给] 载具 {}... 补给完成", LOG_PREFIX, uuid.substring(0, 8));
            return true;

        } catch (Exception e) {
            LOGGER.error("{} 载具补给出错: {}", LOG_PREFIX, e.getMessage());
            return false;
        }
    }

    /** 判断实体是否为 SBW/MCSP 载具 */
    private static boolean isSBWVehicle(Entity entity) {
        String type = entity.getType().builtInRegistryHolder().key().location().toString();
        return type.startsWith("superbwarfare:") || type.startsWith("mcsp:");
    }

    // ========== 配置管理 ==========

    public void applyConfig(int scanRange, int cooldown, int enterDelay, Map<String, Integer> slots) {
        this.scanRange = scanRange;
        this.cooldownSec = cooldown;
        this.enterDelay = enterDelay;
        this.slots = new HashMap<>(slots);
        this.cooldownEnd = 0;
        setChanged();
    }

    public void resetConfig() {
        this.scanRange = 12;
        this.cooldownSec = 5;
        this.enterDelay = 3;
        this.slots = getDefaultSlots();
        this.cooldownEnd = 0;
        setChanged();
    }

    // ========== Getters ==========

    public int getScanRange() { return scanRange; }
    public int getCooldownSec() { return cooldownSec; }
    public int getEnterDelay() { return enterDelay; }
    public Map<String, Integer> getSlots() { return Collections.unmodifiableMap(slots); }
    public long getCooldownEnd() { return cooldownEnd; }
    public boolean isCheatMode() { return cheatMode; }
    public void setCheatMode(boolean cheatMode) { this.cheatMode = cheatMode; setChanged(); }

    // ========== NBT 持久化 ==========

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("StationConfig", configToJson());
        tag.putLong("CooldownEnd", cooldownEnd);
        tag.putString("VehicleTimers", timersToJson());
        tag.putBoolean("CheatMode", cheatMode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String configStr = tag.getString("StationConfig");
        if (configStr != null && !configStr.isEmpty()) {
            loadConfigFromJson(configStr);
        }
        cooldownEnd = tag.getLong("CooldownEnd");
        String timersStr = tag.getString("VehicleTimers");
        if (timersStr != null && !timersStr.isEmpty()) {
            loadTimersFromJson(timersStr);
        }
        cheatMode = tag.getBoolean("CheatMode");
    }

    // ========== MenuProvider ==========

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.siege_tools.ammo_station");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return buildLDLib2UI(containerId, playerInv, player);
    }

    private AbstractContainerMenu buildLDLib2UI(int containerId, Inventory playerInv, Player player) {
        // ── 输入字段 ──
        TextField fieldScanRange = new TextField().setNumbersOnlyInt(0, 999999).setText(String.valueOf(scanRange)); fieldScanRange.lss("width", 55);
        TextField fieldCooldown = new TextField().setNumbersOnlyInt(0, 999999).setText(String.valueOf(cooldownSec)); fieldCooldown.lss("width", 55);
        TextField fieldEnterDelay = new TextField().setNumbersOnlyInt(1, 999999).setText(String.valueOf(enterDelay)); fieldEnterDelay.lss("width", 55);

        // 弹药字段
        record AmmoEntry(String key, String label) {}
        List<AmmoEntry> allAmmo = List.of(
            new AmmoEntry("large_shell_ap", "§6大口径AP"),
            new AmmoEntry("large_shell_he", "§c大口径HE"),
            new AmmoEntry("large_shell_gs", "§a大口径葡萄"),
            new AmmoEntry("mortar_shell", "§6迫击炮弹"),
            new AmmoEntry("small_shell_ap", "§b小口径AP"),
            new AmmoEntry("small_shell_he", "§d小口径HE"),
            new AmmoEntry("small_shell_gs", "§a小口径葡萄"),
            new AmmoEntry("small_shell_aa", "§b防空弹"),
            new AmmoEntry("rifle_ammo", "§7步枪弹"),
            new AmmoEntry("heavy_ammo", "§9重弹"),
            new AmmoEntry("small_rocket", "§e小型火箭"),
            new AmmoEntry("rocket", "§e火箭弹"),
            new AmmoEntry("missile", "§a导弹"),
            new AmmoEntry("medium_anti_ground_missile", "§a中型对地导弹"),
            new AmmoEntry("large_anti_ground_missile", "§a大型对地导弹"),
            new AmmoEntry("medium_anti_air_missile", "§a防空导弹"),
            new AmmoEntry("medium_aerial_bomb", "§c中型航弹"),
            new AmmoEntry("small_aerial_bomb", "§c小型航弹"),
            new AmmoEntry("mcsp_125mm_ap", "§6125mm穿甲"),
            new AmmoEntry("mcsp_125mm_he", "§c125mm高爆"),
            new AmmoEntry("mcsp_120mm_bulletmortar", "§5120mm迫击"),
            new AmmoEntry("mcsp_tow_2", "§aTOW-2导弹"),
            new AmmoEntry("mcsp_mlrs_shells", "§eMLRS火箭"),
            new AmmoEntry("mcsp_25mm_ap", "§b25mm机炮"),
            new AmmoEntry("mcsp_30mm_ap", "§d30mm机炮"),
            new AmmoEntry("mcsp_40mm_explosive", "§c40mm高爆"),
            new AmmoEntry("mcsp_40mm_smoke", "§740mm烟雾"),
            new AmmoEntry("mcsp_bullet762", "§77.62mm机枪"),
            new AmmoEntry("mcsp_smallarmscartridge", "§7小口径弹药")
        );
        Map<String, TextField> slotFields = new LinkedHashMap<>();
        for (AmmoEntry ae : allAmmo) {
            TextField f = new TextField().setNumbersOnlyInt(0, 999999).setText(String.valueOf(slots.getOrDefault(ae.key, 0)));
            f.lss("width", 55);
            slotFields.put(ae.key, f);
        }

        // ── 构建 UI ──
        UIElement root = new UIElement();
        root.lss("width", 270).lss("padding", 6);

        var title = new Label().setText(Component.literal("§6╔══ 弹药补给站配置 ══╗"));
        title.lss("width", "100%");
        title.textStyle(s -> s.textAlignHorizontal(com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal.CENTER));
        root.addChild(title);
        root.addChild(sep());

        TabView tabView = new TabView();

        // Tab 1: 基础
        UIElement page1 = new UIElement().lss("padding", 4);
        addRow(page1, "§7扫描范围:", fieldScanRange, " §7格");
        addGap(page1);
        addRow(page1, "§7冷却时间:", fieldCooldown, " §7秒");
        addGap(page1);
        addRow(page1, "§7驶入等待:", fieldEnterDelay, " §7秒");
        addGap(page1);
        page1.addChild(new Label().setText(Component.literal("§8← 切换标签页配置弹药")));
        tabView.addTab(new Tab().setText("基础"), page1);

        // Tab 2-7: 弹药配置
        int[][] tabRanges = {{0,4}, {4,8}, {8,12}, {12,18}, {18,23}, {23,29}};
        String[] tabNames = {"炮弹", "小口径", "枪/火箭", "导弹/航弹", "§aMCSP(上)", "§aMCSP(下)"};
        String[] tabTitles = {"§e── 大口径炮弹 ──", "§e── 小口径机炮弹 ──", "§e── 枪弹/火箭弹 ──",
            "§e── 导弹/航弹 ──", "§e── 坦克炮/导弹 ──", "§e── 机关炮/机枪 ──"};
        for (int ti = 0; ti < tabRanges.length; ti++) {
            UIElement page = new UIElement().lss("padding", 4);
            page.addChild(new Label().setText(Component.literal(tabTitles[ti])));
            for (int i = tabRanges[ti][0]; i < tabRanges[ti][1] && i < allAmmo.size(); i++) {
                AmmoEntry ae = allAmmo.get(i);
                addRow(page, ae.label + ":", slotFields.get(ae.key), " 个");
            }
            tabView.addTab(new Tab().setText(tabNames[ti]), page);
        }

        // Tab 8: 作弊
        UIElement cheatPage = new UIElement().lss("padding", 4);
        cheatPage.addChild(new Label().setText(Component.literal("§c── 作弊功能 ──")));
        if (player.hasPermissions(2)) {
            addGap(cheatPage);
            Button btnToggle = new Button().setText(Component.literal("§6⇄ 切换作弊模式")); btnToggle.lss("padding", "3 10");
            btnToggle.setOnServerClick(e -> { cheatMode = !cheatMode; setChanged();
                player.displayClientMessage(Component.literal("§6[弹药补给站] " + (cheatMode ? "§c作弊模式已开启" : "§a作弊模式已关闭")), false); });
            cheatPage.addChild(btnToggle);
            cheatPage.addChild(new Label().setText(Component.literal(cheatMode ? "§a✔ 作弊模式已开启" : "§7作弊模式已关闭")));
            if (cheatMode) {
                addGap(cheatPage);
                Button btnManual = new Button().setText(Component.literal("§4⚡ 立即扫描补给")); btnManual.lss("padding", "4 12");
                btnManual.setOnServerClick(e -> { triggerManualReplenish();
                    player.displayClientMessage(Component.literal("§e⏳ 补给请求已提交"), false); });
                cheatPage.addChild(btnManual);
            }
        } else {
            addGap(cheatPage);
            cheatPage.addChild(new Label().setText(Component.literal("§c你没有权限使用作弊功能")));
        }
        tabView.addTab(new Tab().setText("§c作弊"), cheatPage);

        root.addChild(tabView);
        root.addChild(sep());

        // 按钮行
        UIElement btnRow = new UIElement();
        Button btnSave = new Button().setText(Component.literal("§a✔ 保存配置")); btnSave.lss("padding", "3 10");
        btnSave.setOnServerClick(e -> {
            Map<String, Integer> newSlots = new HashMap<>();
            for (AmmoEntry ae : allAmmo) {
                try { int v = Integer.parseInt(slotFields.get(ae.key).getText()); if (v > 0) newSlots.put(ae.key, v); } catch (NumberFormatException ex) {}
            }
            applyConfig(
                safeInt(fieldScanRange.getText(), 12),
                safeInt(fieldCooldown.getText(), 5),
                safeInt(fieldEnterDelay.getText(), 3),
                newSlots);
            player.displayClientMessage(Component.literal("§a✔ 配置已保存！冷却已重置"), false);
        });
        btnRow.addChild(btnSave);

        Button btnReset = new Button().setText(Component.literal("§e↻ 重置默认")); btnReset.lss("padding", "3 10");
        btnReset.setOnServerClick(e -> { resetConfig();
            player.displayClientMessage(Component.literal("§a✔ 已重置为默认配置"), false); });
        btnRow.addChild(btnReset);
        root.addChild(btnRow);
        root.addChild(new InventorySlots());

        ModularUI modularUI = ModularUI.of(UI.of(root), player);
        IContainerUIHolder holder = new IContainerUIHolder() {
            @Override public ModularUI createUI(Player p) { return modularUI; }
            @Override public boolean isStillValid(Player p) { return true; }
        };
        modularUI.setMenu(new ModularUIContainerMenu(ModMenuTypes.AMMO_STATION.get(), containerId, playerInv, holder));
        return modularUI.getMenu();
    }

    private static Label sep() {
        Label s = new Label(); s.setText(Component.literal("§8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        s.lss("width", "100%").lss("overflow", "hidden");
        return s;
    }

    private static void addRow(UIElement parent, String label, TextField field, String unit) {
        UIElement row = new UIElement();
        row.addChild(new Label().setText(Component.literal(label)));
        row.addChild(field);
        row.addChild(new Label().setText(Component.literal(unit)));
        parent.addChild(row);
    }

    private static void addGap(UIElement parent) {
        parent.addChild(new Label().setText(Component.literal(" ")));
    }

    private static int safeInt(String s, int def) {
        try { return Math.max(0, Integer.parseInt(s)); } catch (NumberFormatException e) { return def; }
    }

    // ========== JSON 序列化 ==========

    private String configToJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("scanRange", scanRange);
        obj.addProperty("cooldown", cooldownSec);
        obj.addProperty("enterDelay", enterDelay);
        JsonObject slotsObj = new JsonObject();
        for (Map.Entry<String, Integer> entry : slots.entrySet()) {
            slotsObj.addProperty(entry.getKey(), entry.getValue());
        }
        obj.add("slots", slotsObj);
        return GSON.toJson(obj);
    }

    private void loadConfigFromJson(String json) {
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) return;
            scanRange = getJsonInt(obj, "scanRange", 12);
            cooldownSec = getJsonInt(obj, "cooldown", 5);
            enterDelay = getJsonInt(obj, "enterDelay", 3);
            if (obj.has("slots")) {
                JsonObject slotsObj = obj.getAsJsonObject("slots");
                Map<String, Integer> loaded = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : slotsObj.entrySet()) {
                    loaded.put(entry.getKey(), entry.getValue().getAsInt());
                }
                slots = loaded;
            }
        } catch (Exception e) {
            LOGGER.warn("[AmmoCrateBE] 解析 StationConfig 失败: {}", e.getMessage());
        }
    }

    private String timersToJson() {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Long> entry : vehicleTimers.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        return GSON.toJson(obj);
    }

    private void loadTimersFromJson(String json) {
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            vehicleTimers.clear();
            if (obj != null) {
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    vehicleTimers.put(entry.getKey(), entry.getValue().getAsLong());
                }
            }
        } catch (Exception e) {
            vehicleTimers.clear();
        }
    }

    private static int getJsonInt(JsonObject obj, String key, int def) {
        return obj.has(key) ? obj.get(key).getAsInt() : def;
    }

    /** 默认弹药配置 */
    public static Map<String, Integer> getDefaultSlots() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("large_shell_ap", 64);
        map.put("large_shell_he", 64);
        map.put("large_shell_gs", 64);
        map.put("small_shell_ap", 64);
        map.put("small_shell_he", 64);
        map.put("small_shell_gs", 64);
        map.put("small_shell_aa", 64);
        map.put("rifle_ammo", 192);
        map.put("heavy_ammo", 128);
        map.put("small_rocket", 32);
        map.put("missile", 8);
        map.put("rocket", 16);
        map.put("medium_anti_ground_missile", 8);
        map.put("large_anti_ground_missile", 8);
        map.put("medium_anti_air_missile", 8);
        map.put("mortar_shell", 32);
        map.put("medium_aerial_bomb", 8);
        map.put("small_aerial_bomb", 8);
        map.put("mcsp_25mm_ap", 128);
        map.put("mcsp_30mm_ap", 128);
        map.put("mcsp_40mm_explosive", 64);
        map.put("mcsp_40mm_smoke", 32);
        map.put("mcsp_120mm_bulletmortar", 32);
        map.put("mcsp_125mm_ap", 32);
        map.put("mcsp_125mm_he", 32);
        map.put("mcsp_bullet762", 256);
        map.put("mcsp_smallarmscartridge", 256);
        map.put("mcsp_tow_2", 16);
        map.put("mcsp_mlrs_shells", 32);
        return map;
    }
}
