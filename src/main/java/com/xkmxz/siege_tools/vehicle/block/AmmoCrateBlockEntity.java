package com.xkmxz.siege_tools.vehicle.block;

import com.mojang.logging.LogUtils;
import com.xkmxz.siege_tools.vehicle.data.VehicleDataManager;
import com.xkmxz.siege_tools.vehicle.registry.ModBlockEntities;
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

    /** 一次性应用来自网络包 AmmoCrateConfigData 的配置 */
    public void applyConfig(com.xkmxz.siege_tools.vehicle.network.AmmoCrateConfigData cfg) {
        this.scanRange = cfg.scanRange();
        this.cooldownSec = cfg.cooldown();
        this.enterDelay = cfg.enterDelay();
        // 从 CompoundTag 解析 slots
        Map<String, Integer> loaded = new HashMap<>();
        CompoundTag slotsTag = cfg.slots();
        if (slotsTag != null) {
            for (String key : slotsTag.getAllKeys()) {
                loaded.put(key, slotsTag.getInt(key));
            }
        }
        this.slots = loaded;
        this.cooldownEnd = 0;
        setChanged();
    }

    public void applyConfig(int scanRange, int cooldown, int enterDelay, Map<String, Integer> slots) {
        this.scanRange = scanRange;
        this.cooldownSec = cooldown;
        this.enterDelay = enterDelay;
        this.slots = new HashMap<>(slots);
        this.cooldownEnd = 0;
        setChanged();
    }

    public void resetConfig() {
        applyDataPackDefaults();
    }

    public void applyDataPackDefaults() {
        CompoundTag def = VehicleDataManager.getDefaultAmmoConfig();
        this.scanRange = def.contains("scanRange") ? def.getInt("scanRange") : 12;
        this.cooldownSec = def.contains("cooldown") ? def.getInt("cooldown") : 5;
        this.enterDelay = def.contains("enterDelay") ? def.getInt("enterDelay") : 3;
        if (def.contains("slots")) {
            CompoundTag slotsTag = def.getCompound("slots");
            Map<String, Integer> loaded = new HashMap<>();
            for (String key : slotsTag.getAllKeys()) {
                loaded.put(key, slotsTag.getInt(key));
            }
            this.slots = loaded;
        } else {
            this.slots = new HashMap<>();
        }
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

    // ========== NBT 持久化（纯 NBT 格式，无 JSON 字符串） ==========

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("scanRange", scanRange);
        tag.putInt("cooldown", cooldownSec);
        tag.putInt("enterDelay", enterDelay);
        // slots: { "ammo_short_name": count, ... }
        CompoundTag slotsTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : slots.entrySet()) {
            slotsTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("slots", slotsTag);
        tag.putLong("cooldownEnd", cooldownEnd);
        // vehicleTimers: { "UUID": gameTime, ... }
        CompoundTag timersTag = new CompoundTag();
        for (Map.Entry<String, Long> entry : vehicleTimers.entrySet()) {
            timersTag.putLong(entry.getKey(), entry.getValue());
        }
        tag.put("vehicleTimers", timersTag);
        tag.putBoolean("cheatMode", cheatMode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        scanRange = tag.contains("scanRange") ? tag.getInt("scanRange") : 12;
        cooldownSec = tag.contains("cooldown") ? tag.getInt("cooldown") : 5;
        enterDelay = tag.contains("enterDelay") ? tag.getInt("enterDelay") : 3;
        if (tag.contains("slots", Tag.TAG_COMPOUND)) {
            CompoundTag slotsTag = tag.getCompound("slots");
            if (slotsTag.getAllKeys().isEmpty()) {
                // 空 slots → 使用默认值（新方块或未配置的旧存档）
                slots = getDefaultSlots();
            } else {
                Map<String, Integer> loaded = new HashMap<>();
                for (String key : slotsTag.getAllKeys()) {
                    loaded.put(key, slotsTag.getInt(key));
                }
                slots = loaded;
            }
        } else {
            slots = getDefaultSlots();
        }
        cooldownEnd = tag.getLong("cooldownEnd");
        if (tag.contains("vehicleTimers", Tag.TAG_COMPOUND)) {
            CompoundTag timersTag = tag.getCompound("vehicleTimers");
            vehicleTimers.clear();
            for (String key : timersTag.getAllKeys()) {
                vehicleTimers.put(key, timersTag.getLong(key));
            }
        }
        cheatMode = tag.contains("cheatMode") && tag.getBoolean("cheatMode");
    }

    // ========== MenuProvider ==========

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.siege_tools.ammo_station");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return null; // UI handled by BlockUI interface on the block
    }

    /** 默认弹药配置（从弹药注册表获取所有类型，默认 64 个） */
    public static Map<String, Integer> getDefaultSlots() {
        Map<String, Integer> result = new HashMap<>();
        var reg = VehicleDataManager.getAmmoTypes();
        if (reg != null && reg.isLoaded()) {
            for (String name : reg.getAllShortNames()) {
                result.put(name, 64);
            }
        }
        return result;
    }
}
