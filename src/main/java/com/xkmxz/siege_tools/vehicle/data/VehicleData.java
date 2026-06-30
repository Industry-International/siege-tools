package com.xkmxz.siege_tools.vehicle.data;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * 载具数据 — 强类型 record，从 JSON 数据文件解析。
 * fullSpawnNbt 在加载时由 {@link VehicleDataLoader} 自动生成（合并 spawnNbt + defaultAmmo + weapons）。
 */
public record VehicleData(
        int formatVersion,
        String vehicleId,
        @Nullable String mod,
        @Nullable String displayType,
        @Nullable String hudType,
        @Nullable VehicleStats stats,
        @Nullable List<String> parts,
        @Nullable List<WeaponData> weapons,
        @Nullable Map<String, Integer> defaultAmmo,
        @Nullable JsonObject rawSpawnNbt,
        CompoundTag fullSpawnNbt
) {

    public record VehicleStats(
            @Nullable Integer maxHealth,
            @Nullable Integer maxEnergy,
            @Nullable Double mass,
            @Nullable Double upStep,
            @Nullable Integer seatCount,
            @Nullable String containerType,
            @Nullable String engineType,
            @Nullable Boolean hasDecoy
    ) {}

    public record WeaponData(
            String key,
            @Nullable String displayKey,
            List<String> ammoTypes,
            int magazine,
            @Nullable Integer rpm,
            @Nullable Double damage
    ) {}
}
