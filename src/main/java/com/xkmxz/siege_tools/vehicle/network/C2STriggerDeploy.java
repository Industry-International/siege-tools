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
 * C2S 网络包：触发载具部署台立即部署。
 */
public record C2STriggerDeploy(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2STriggerDeploy> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(siege_tools.MODID, "trigger_deploy"));

    public static final StreamCodec<FriendlyByteBuf, C2STriggerDeploy> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public C2STriggerDeploy decode(FriendlyByteBuf buf) {
                    return new C2STriggerDeploy(buf.readBlockPos());
                }

                @Override
                public void encode(FriendlyByteBuf buf, C2STriggerDeploy packet) {
                    buf.writeBlockPos(packet.pos);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2STriggerDeploy payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;
            var level = player.level();
            var be = level.getBlockEntity(payload.pos);
            if (be instanceof VehicleDeployerBlockEntity deployer) {
                if (deployer.getVehicleType() == null || deployer.getVehicleType().isEmpty()) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§c[部署台] 请先配置载具类型"), false);
                    return;
                }
                deployer.setPendingDeploy(true);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§e⏳ 部署命令已提交，将在下次 Tick 执行"), false);
            }
        });
    }
}
