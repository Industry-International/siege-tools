package com.xkmxz.siege_tools.vehicle.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 弹药补给站配置的共享数据载体。
 * slots 直接使用 NBT CompoundTag，不再经过 Map 手动序列化。
 */
public record AmmoCrateConfigData(
        int scanRange,
        int cooldown,
        int enterDelay,
        CompoundTag slots
) {
    public static final StreamCodec<FriendlyByteBuf, AmmoCrateConfigData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AmmoCrateConfigData decode(FriendlyByteBuf buf) {
                    int scanRange = buf.readInt();
                    int cooldown = buf.readInt();
                    int enterDelay = buf.readInt();
                    CompoundTag slots = buf.readNbt();
                    if (slots == null) slots = new CompoundTag();
                    return new AmmoCrateConfigData(scanRange, cooldown, enterDelay, slots);
                }

                @Override
                public void encode(FriendlyByteBuf buf, AmmoCrateConfigData data) {
                    buf.writeInt(data.scanRange);
                    buf.writeInt(data.cooldown);
                    buf.writeInt(data.enterDelay);
                    buf.writeNbt(data.slots);
                }
            };
}
