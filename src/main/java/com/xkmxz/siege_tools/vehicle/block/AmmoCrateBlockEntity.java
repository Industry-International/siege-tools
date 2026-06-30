package com.xkmxz.siege_tools.vehicle.block;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import com.xkmxz.siege_tools.vehicle.data.AmmoTypeRegistry;
import com.xkmxz.siege_tools.vehicle.registry.ModBlockEntities;
import com.xkmxz.siege_tools.vehicle.data.VehicleDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.commands.CommandSourceStack;

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
        return null; // UI handled by KubeJS via KJSBlockUIMenuType
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
