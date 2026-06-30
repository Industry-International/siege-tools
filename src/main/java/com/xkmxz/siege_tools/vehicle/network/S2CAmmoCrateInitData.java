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
 * S2C 网络包：服务端向客户端推送弹药补给站的初始数据。
 * 数据载体使用共享的 {@link AmmoCrateConfigData}。
 */
public record S2CAmmoCrateInitData(
        BlockPos pos,
        AmmoCrateConfigData data
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CAmmoCrateInitData> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(siege_tools.MODID, "s2c_ammo_crate_init"));

    public static final StreamCodec<FriendlyByteBuf, S2CAmmoCrateInitData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public S2CAmmoCrateInitData decode(FriendlyByteBuf buf) {
                    return new S2CAmmoCrateInitData(
                            buf.readBlockPos(),
                            AmmoCrateConfigData.STREAM_CODEC.decode(buf)
                    );
                }

                @Override
                public void encode(FriendlyByteBuf buf, S2CAmmoCrateInitData packet) {
                    buf.writeBlockPos(packet.pos);
                    AmmoCrateConfigData.STREAM_CODEC.encode(buf, packet.data);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CAmmoCrateInitData payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;
            var menu = player.containerMenu;
            if (menu instanceof ModularUIContainerMenu mcm) {
                ModularUI ui = mcm.getModularUI();
                AmmoCrateConfigData d = payload.data;
                setText(ui, "ammo_scanRange", String.valueOf(d.scanRange()));
                setText(ui, "ammo_cooldown", String.valueOf(d.cooldown()));
                setText(ui, "ammo_enterDelay", String.valueOf(d.enterDelay()));
                // 弹药槽位
                if (d.slots() != null) {
                    for (String key : d.slots().getAllKeys()) {
                        setText(ui, "ammo_slot_" + key, String.valueOf(d.slots().getInt(key)));
                    }
                }
            }
        });
    }

    private static void setText(ModularUI ui, String id, String text) {
        var elem = ui.getElementById(id);
        if (elem instanceof TextField tf) tf.setText(text);
    }
}
