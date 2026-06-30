package com.xkmxz.siege_tools.vehicle.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.xkmxz.siege_tools.siege_tools;
import com.xkmxz.siege_tools.vehicle.block.VehicleDeployerBlockEntity;
import com.xkmxz.siege_tools.vehicle.util.JsonToNBTConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S 网络包：保存载具部署台配置。
 */
public record C2SSaveDeployerConfig(
        BlockPos pos,
        String vehicleType,
        int respawnDelay,
        boolean autoRespawn,
        boolean spawnWithAmmo,
        double offsetX, double offsetY, double offsetZ,
        float yaw, float pitch,
        String deployNBT
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SSaveDeployerConfig> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(siege_tools.MODID, "save_deployer_config"));

    public static final StreamCodec<FriendlyByteBuf, C2SSaveDeployerConfig> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public C2SSaveDeployerConfig decode(FriendlyByteBuf buf) {
                    return new C2SSaveDeployerConfig(
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
                public void encode(FriendlyByteBuf buf, C2SSaveDeployerConfig packet) {
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

    public static void handle(C2SSaveDeployerConfig payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;
            var level = player.level();
            var be = level.getBlockEntity(payload.pos);
            if (be instanceof VehicleDeployerBlockEntity deployer) {
                deployer.setVehicleType(payload.vehicleType);
                deployer.setRespawnDelay(Math.max(20, payload.respawnDelay));
                deployer.setAutoRespawn(payload.autoRespawn);
                deployer.setSpawnWithAmmo(payload.spawnWithAmmo);
                deployer.setOffsets(payload.offsetX, payload.offsetY, payload.offsetZ, payload.yaw, payload.pitch);
                // 将网络包中的 JSON 字符串解析为 CompoundTag 再存入 BE
                String nbtStr = payload.deployNBT != null ? payload.deployNBT : "{}";
                CompoundTag parsedNBT;
                try {
                    JsonObject obj = new Gson().fromJson(nbtStr, JsonObject.class);
                    parsedNBT = (obj != null) ? JsonToNBTConverter.toCompoundTag(obj) : new CompoundTag();
                } catch (Exception e) {
                    parsedNBT = new CompoundTag();
                }
                deployer.setDeployNBT(parsedNBT);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§a✔ 配置已保存！"), false);
            }
        });
    }
}
