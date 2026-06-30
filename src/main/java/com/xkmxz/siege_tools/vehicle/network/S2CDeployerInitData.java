package com.xkmxz.siege_tools.vehicle.network;

import com.xkmxz.siege_tools.siege_tools;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C 网络包：服务端向客户端推送载具部署台的初始数据，用于初始化 GUI 文本框。
 */
public record S2CDeployerInitData(
        BlockPos pos,
        String vehicleType,
        int respawnDelay,
        boolean autoRespawn,
        boolean spawnWithAmmo,
        double offsetX, double offsetY, double offsetZ,
        float yaw, float pitch,
        String deployNBT
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CDeployerInitData> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(siege_tools.MODID, "s2c_deployer_init"));

    public static final StreamCodec<FriendlyByteBuf, S2CDeployerInitData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public S2CDeployerInitData decode(FriendlyByteBuf buf) {
                    return new S2CDeployerInitData(
                            buf.readBlockPos(),
                            buf.readUtf(),
                            buf.readInt(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readFloat(), buf.readFloat(),
                            buf.readUtf()
                    );
                }

                @Override
                public void encode(FriendlyByteBuf buf, S2CDeployerInitData packet) {
                    buf.writeBlockPos(packet.pos);
                    buf.writeUtf(packet.vehicleType);
                    buf.writeInt(packet.respawnDelay);
                    buf.writeBoolean(packet.autoRespawn);
                    buf.writeBoolean(packet.spawnWithAmmo);
                    buf.writeDouble(packet.offsetX);
                    buf.writeDouble(packet.offsetY);
                    buf.writeDouble(packet.offsetZ);
                    buf.writeFloat(packet.yaw);
                    buf.writeFloat(packet.pitch);
                    buf.writeUtf(packet.deployNBT);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：通过 ModularUI 的元素 ID 查找 TextField 并更新初始值。
     */
    public static void handle(S2CDeployerInitData payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;
            var menu = player.containerMenu;
            if (menu instanceof com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu mcm) {
                var modularUI = mcm.getModularUI();
                // 更新各字段
                setTextFieldText(modularUI, "deployer_vehicleType", payload.vehicleType);
                setTextFieldText(modularUI, "deployer_respawnDelay", String.valueOf(payload.respawnDelay));
                setTextFieldText(modularUI, "deployer_autoRespawn", payload.autoRespawn ? "1" : "0");
                setTextFieldText(modularUI, "deployer_spawnWithAmmo", payload.spawnWithAmmo ? "1" : "0");
                setTextFieldText(modularUI, "deployer_offsetX", String.valueOf((int) payload.offsetX));
                setTextFieldText(modularUI, "deployer_offsetY", String.valueOf((int) payload.offsetY));
                setTextFieldText(modularUI, "deployer_offsetZ", String.valueOf((int) payload.offsetZ));
                setTextFieldText(modularUI, "deployer_yaw", String.valueOf((int) payload.yaw));
                setTextFieldText(modularUI, "deployer_pitch", String.valueOf((int) payload.pitch));
                setTextFieldText(modularUI, "deployer_deployNBT", payload.deployNBT);
            }
        });
    }

    private static void setTextFieldText(com.lowdragmc.lowdraglib2.gui.ui.ModularUI ui, String id, String text) {
        var elem = ui.getElementById(id);
        if (elem instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.TextField tf) {
            tf.setText(text);
        }
    }
}
