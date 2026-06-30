package com.xkmxz.siege_tools.vehicle.network;

import com.xkmxz.siege_tools.siege_tools;
import com.xkmxz.siege_tools.vehicle.block.VehicleDeployerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S 网络包：保存载具部署台配置。
 * 数据载体使用共享的 {@link DeployerConfigData}。
 */
public record C2SSaveDeployerConfig(
        BlockPos pos,
        DeployerConfigData data
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SSaveDeployerConfig> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(siege_tools.MODID, "save_deployer_config"));

    public static final StreamCodec<FriendlyByteBuf, C2SSaveDeployerConfig> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public C2SSaveDeployerConfig decode(FriendlyByteBuf buf) {
                    return new C2SSaveDeployerConfig(
                            buf.readBlockPos(),
                            DeployerConfigData.STREAM_CODEC.decode(buf)
                    );
                }

                @Override
                public void encode(FriendlyByteBuf buf, C2SSaveDeployerConfig packet) {
                    buf.writeBlockPos(packet.pos);
                    DeployerConfigData.STREAM_CODEC.encode(buf, packet.data);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SSaveDeployerConfig payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;
            var level = player.level();
            var be = level.getBlockEntity(payload.pos);
            if (be instanceof VehicleDeployerBlockEntity deployer) {
                deployer.applyConfig(payload.data);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§a✔ 配置已保存！"), false);
            }
        });
    }
}
