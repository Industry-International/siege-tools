package com.xkmxz.siege_tools.vehicle.network;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.xkmxz.siege_tools.siege_tools;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C 网络包：服务端向客户端推送载具部署台的初始数据。
 * 数据载体使用共享的 {@link DeployerConfigData}。
 */
public record S2CDeployerInitData(
        BlockPos pos,
        DeployerConfigData data
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CDeployerInitData> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(siege_tools.MODID, "s2c_deployer_init"));

    public static final StreamCodec<FriendlyByteBuf, S2CDeployerInitData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public S2CDeployerInitData decode(FriendlyByteBuf buf) {
                    return new S2CDeployerInitData(
                            buf.readBlockPos(),
                            DeployerConfigData.STREAM_CODEC.decode(buf)
                    );
                }

                @Override
                public void encode(FriendlyByteBuf buf, S2CDeployerInitData packet) {
                    buf.writeBlockPos(packet.pos);
                    DeployerConfigData.STREAM_CODEC.encode(buf, packet.data);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CDeployerInitData payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;
            var menu = player.containerMenu;
            if (menu instanceof ModularUIContainerMenu mcm) {
                ModularUI ui = mcm.getModularUI();
                DeployerConfigData d = payload.data;
                setText(ui, "deployer_vehicleType", d.vehicleType());
                setText(ui, "deployer_respawnDelay", String.valueOf(d.respawnDelay()));
                setText(ui, "deployer_autoRespawn", d.autoRespawn() ? "1" : "0");
                setText(ui, "deployer_spawnWithAmmo", d.spawnWithAmmo() ? "1" : "0");
                setText(ui, "deployer_offsetX", String.valueOf((int) d.offsetX()));
                setText(ui, "deployer_offsetY", String.valueOf((int) d.offsetY()));
                setText(ui, "deployer_offsetZ", String.valueOf((int) d.offsetZ()));
                setText(ui, "deployer_yaw", String.valueOf((int) d.yaw()));
                setText(ui, "deployer_pitch", String.valueOf((int) d.pitch()));
                setText(ui, "deployer_deployNBT", com.xkmxz.siege_tools.vehicle.block.VehicleDeployerBlockEntity.nbtCompoundToJson(d.deployNBT()));
            }
        });
    }

    private static void setText(ModularUI ui, String id, String text) {
        var elem = ui.getElementById(id);
        if (elem instanceof TextField tf) tf.setText(text);
    }
}
