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

import java.util.HashMap;
import java.util.Map;

/**
 * S2C 网络包：服务端向客户端推送弹药补给站的初始数据，用于初始化 GUI 文本框。
 */
public record S2CAmmoCrateInitData(
        BlockPos pos,
        int scanRange,
        int cooldown,
        int enterDelay,
        Map<String, Integer> slots
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CAmmoCrateInitData> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(siege_tools.MODID, "s2c_ammo_crate_init"));

    public static final StreamCodec<FriendlyByteBuf, S2CAmmoCrateInitData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public S2CAmmoCrateInitData decode(FriendlyByteBuf buf) {
                    BlockPos pos = buf.readBlockPos();
                    int scanRange = buf.readInt();
                    int cooldown = buf.readInt();
                    int enterDelay = buf.readInt();
                    int slotCount = buf.readVarInt();
                    Map<String, Integer> slots = new HashMap<>();
                    for (int i = 0; i < slotCount; i++) {
                        slots.put(buf.readUtf(), buf.readInt());
                    }
                    return new S2CAmmoCrateInitData(pos, scanRange, cooldown, enterDelay, slots);
                }

                @Override
                public void encode(FriendlyByteBuf buf, S2CAmmoCrateInitData packet) {
                    buf.writeBlockPos(packet.pos);
                    buf.writeInt(packet.scanRange);
                    buf.writeInt(packet.cooldown);
                    buf.writeInt(packet.enterDelay);
                    buf.writeVarInt(packet.slots.size());
                    for (Map.Entry<String, Integer> entry : packet.slots.entrySet()) {
                        buf.writeUtf(entry.getKey());
                        buf.writeInt(entry.getValue());
                    }
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
                ModularUI modularUI = mcm.getModularUI();
                setTextFieldText(modularUI, "ammo_scanRange", String.valueOf(payload.scanRange));
                setTextFieldText(modularUI, "ammo_cooldown", String.valueOf(payload.cooldown));
                setTextFieldText(modularUI, "ammo_enterDelay", String.valueOf(payload.enterDelay));
                // 弹药槽位
                for (Map.Entry<String, Integer> entry : payload.slots.entrySet()) {
                    setTextFieldText(modularUI, "ammo_slot_" + entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        });
    }

    private static void setTextFieldText(ModularUI ui, String id, String text) {
        var elem = ui.getElementById(id);
        if (elem instanceof TextField tf) {
            tf.setText(text);
        }
    }
}
