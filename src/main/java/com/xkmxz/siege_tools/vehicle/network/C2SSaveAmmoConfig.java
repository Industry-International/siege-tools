package com.xkmxz.siege_tools.vehicle.network;

import com.xkmxz.siege_tools.siege_tools;
import com.xkmxz.siege_tools.vehicle.block.AmmoCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * C2S 网络包：保存弹药补给站配置。
 */
public record C2SSaveAmmoConfig(
        BlockPos pos,
        int scanRange,
        int cooldown,
        int enterDelay,
        Map<String, Integer> slots
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SSaveAmmoConfig> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(siege_tools.MODID, "save_ammo_config"));

    public static final StreamCodec<FriendlyByteBuf, C2SSaveAmmoConfig> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public C2SSaveAmmoConfig decode(FriendlyByteBuf buf) {
                    BlockPos pos = buf.readBlockPos();
                    int scanRange = buf.readInt();
                    int cooldown = buf.readInt();
                    int enterDelay = buf.readInt();
                    int slotCount = buf.readVarInt();
                    Map<String, Integer> slots = new HashMap<>();
                    for (int i = 0; i < slotCount; i++) {
                        slots.put(buf.readUtf(), buf.readInt());
                    }
                    return new C2SSaveAmmoConfig(pos, scanRange, cooldown, enterDelay, slots);
                }

                @Override
                public void encode(FriendlyByteBuf buf, C2SSaveAmmoConfig packet) {
                    buf.writeBlockPos(packet.pos);
                    buf.writeInt(packet.scanRange);
                    buf.writeInt(packet.cooldown);
                    buf.writeInt(packet.enterDelay);
                    buf.writeVarInt(packet.slots.size());
                    for (Map.Entry<String, Integer> entry : packet.slots.entrySet()) {
                        buf.writeUtf(entry.getKey());
                        buf.writeInt(entry.getValue());
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SSaveAmmoConfig payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;
            var level = player.level();
            var be = level.getBlockEntity(payload.pos);
            if (be instanceof AmmoCrateBlockEntity station) {
                station.applyConfig(payload.scanRange, payload.cooldown, payload.enterDelay, payload.slots);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§a✔ 配置已保存！冷却已重置"), false);
            }
        });
    }
}
