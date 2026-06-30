package com.xkmxz.siege_tools.vehicle.network;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.xkmxz.siege_tools.siege_tools;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 统一 S2C 网络包 — 处理所有载具系统的服务端→客户端操作。
 * <p>
 * action 类型：
 * <ul>
 *   <li>{@code init_deployer} — 初始化载具部署台 GUI 文本框</li>
 *   <li>{@code init_ammo} — 初始化弹药补给站 GUI 文本框</li>
 * </ul>
 */
public record S2CVehiclePacket(
        BlockPos pos,
        String action,
        CompoundTag data
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CVehiclePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(siege_tools.MODID, "s2c_vehicle"));

    public static final StreamCodec<FriendlyByteBuf, S2CVehiclePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public S2CVehiclePacket decode(FriendlyByteBuf buf) {
                    BlockPos pos = buf.readBlockPos();
                    String action = buf.readUtf();
                    CompoundTag data = buf.readNbt();
                    if (data == null) data = new CompoundTag();
                    return new S2CVehiclePacket(pos, action, data);
                }

                @Override
                public void encode(FriendlyByteBuf buf, S2CVehiclePacket pkt) {
                    buf.writeBlockPos(pkt.pos);
                    buf.writeUtf(pkt.action);
                    buf.writeNbt(pkt.data);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ========== 工厂方法 ==========

    /** 初始化载具部署台 GUI */
    public static S2CVehiclePacket initDeployer(BlockPos pos, DeployerConfigData cfg) {
        return new S2CVehiclePacket(pos, "init_deployer", cfg.toTag());
    }

    /** 初始化弹药补给站 GUI */
    public static S2CVehiclePacket initAmmo(BlockPos pos, CompoundTag slotsNbt) {
        return new S2CVehiclePacket(pos, "init_ammo", slotsNbt);
    }

    // ========== 客户端处理 ==========

    public static void handle(S2CVehiclePacket pkt, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;
            var menu = player.containerMenu;
            if (!(menu instanceof ModularUIContainerMenu mcm)) return;

            switch (pkt.action) {
                case "init_deployer" -> handleInitDeployer(pkt, mcm.getModularUI());
                case "init_ammo" -> handleInitAmmo(pkt, mcm.getModularUI());
            }
        });
    }

    private static void handleInitDeployer(S2CVehiclePacket pkt, ModularUI ui) {
        DeployerConfigData d = DeployerConfigData.fromTag(pkt.data);
        setText(ui, "deployer_vehicleType", d.vehicleType());
        setText(ui, "deployer_respawnDelay", String.valueOf(d.respawnDelay()));
        setText(ui, "deployer_autoRespawn", d.autoRespawn() ? "1" : "0");
        setText(ui, "deployer_spawnWithAmmo", d.spawnWithAmmo() ? "1" : "0");
        setText(ui, "deployer_offsetX", String.valueOf((int) d.offsetX()));
        setText(ui, "deployer_offsetY", String.valueOf((int) d.offsetY()));
        setText(ui, "deployer_offsetZ", String.valueOf((int) d.offsetZ()));
        setText(ui, "deployer_yaw", String.valueOf((int) d.yaw()));
        setText(ui, "deployer_pitch", String.valueOf((int) d.pitch()));
        String nbtJson = com.xkmxz.siege_tools.vehicle.block.VehicleDeployerBlockEntity.nbtCompoundToJson(d.deployNBT());
        setText(ui, "deployer_deployNBT", nbtJson);
    }

    private static void handleInitAmmo(S2CVehiclePacket pkt, ModularUI ui) {
        setText(ui, "ammo_scanRange", String.valueOf(pkt.data.getInt("scanRange")));
        setText(ui, "ammo_cooldown", String.valueOf(pkt.data.getInt("cooldown")));
        setText(ui, "ammo_enterDelay", String.valueOf(pkt.data.getInt("enterDelay")));
        if (pkt.data.contains("slots")) {
            CompoundTag slots = pkt.data.getCompound("slots");
            for (String key : slots.getAllKeys()) {
                setText(ui, "ammo_slot_" + key, String.valueOf(slots.getInt(key)));
            }
        }
    }

    private static void setText(ModularUI ui, String id, String text) {
        var elem = ui.getElementById(id);
        if (elem instanceof TextField tf) tf.setText(text);
    }
}
