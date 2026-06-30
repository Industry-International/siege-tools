package com.xkmxz.siege_tools.vehicle.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 载具部署台配置的共享数据载体。
 * 仅做传输用，deployNBT 直接使用 NBT CompoundTag，不再经过 JSON 字符串。
 */
public record DeployerConfigData(
        String vehicleType,
        int respawnDelay,
        boolean autoRespawn,
        boolean spawnWithAmmo,
        double offsetX, double offsetY, double offsetZ,
        float yaw, float pitch,
        CompoundTag deployNBT
) {
    public static final StreamCodec<FriendlyByteBuf, DeployerConfigData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public DeployerConfigData decode(FriendlyByteBuf buf) {
                    String vehicleType = buf.readUtf();
                    int respawnDelay = buf.readInt();
                    boolean autoRespawn = buf.readBoolean();
                    boolean spawnWithAmmo = buf.readBoolean();
                    double ox = buf.readDouble(), oy = buf.readDouble(), oz = buf.readDouble();
                    float yaw = buf.readFloat(), pitch = buf.readFloat();
                    CompoundTag deployNBT = buf.readNbt();
                    if (deployNBT == null) deployNBT = new CompoundTag();
                    return new DeployerConfigData(vehicleType, respawnDelay, autoRespawn, spawnWithAmmo,
                            ox, oy, oz, yaw, pitch, deployNBT);
                }

                @Override
                public void encode(FriendlyByteBuf buf, DeployerConfigData data) {
                    buf.writeUtf(data.vehicleType);
                    buf.writeInt(data.respawnDelay);
                    buf.writeBoolean(data.autoRespawn);
                    buf.writeBoolean(data.spawnWithAmmo);
                    buf.writeDouble(data.offsetX);
                    buf.writeDouble(data.offsetY);
                    buf.writeDouble(data.offsetZ);
                    buf.writeFloat(data.yaw);
                    buf.writeFloat(data.pitch);
                    buf.writeNbt(data.deployNBT);
                }
            };

    /** 字段数量校验：encode 写多少次，这里就应是多少 */
    static int fieldCount() { return 10; }
}
