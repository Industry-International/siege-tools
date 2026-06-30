package com.xkmxz.siege_tools.vehicle.network;

import com.xkmxz.siege_tools.siege_tools;
import com.xkmxz.siege_tools.vehicle.block.AmmoCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S 网络包：切换弹药补给站的作弊模式。
 */
public record C2SToggleCheatMode(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SToggleCheatMode> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(siege_tools.MODID, "toggle_cheat_mode"));

    public static final StreamCodec<FriendlyByteBuf, C2SToggleCheatMode> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public C2SToggleCheatMode decode(FriendlyByteBuf buf) {
                    return new C2SToggleCheatMode(buf.readBlockPos());
                }

                @Override
                public void encode(FriendlyByteBuf buf, C2SToggleCheatMode packet) {
                    buf.writeBlockPos(packet.pos);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SToggleCheatMode payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;
            var level = player.level();
            var be = level.getBlockEntity(payload.pos);
            if (be instanceof AmmoCrateBlockEntity station) {
                station.setCheatMode(!station.isCheatMode());
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§6[弹药补给站] 作弊模式已切换"), false);
            }
        });
    }
}
