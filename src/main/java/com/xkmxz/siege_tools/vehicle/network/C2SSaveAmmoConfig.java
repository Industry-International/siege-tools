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
 * C2S 网络包：保存弹药补给站配置。
 * 数据载体使用共享的 {@link AmmoCrateConfigData}。
 */
public record C2SSaveAmmoConfig(
        BlockPos pos,
        AmmoCrateConfigData data
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SSaveAmmoConfig> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(siege_tools.MODID, "save_ammo_config"));

    public static final StreamCodec<FriendlyByteBuf, C2SSaveAmmoConfig> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public C2SSaveAmmoConfig decode(FriendlyByteBuf buf) {
                    return new C2SSaveAmmoConfig(
                            buf.readBlockPos(),
                            AmmoCrateConfigData.STREAM_CODEC.decode(buf)
                    );
                }

                @Override
                public void encode(FriendlyByteBuf buf, C2SSaveAmmoConfig packet) {
                    buf.writeBlockPos(packet.pos);
                    AmmoCrateConfigData.STREAM_CODEC.encode(buf, packet.data);
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
                station.applyConfig(payload.data);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§a✔ 配置已保存！冷却已重置"), false);
            }
        });
    }
}
