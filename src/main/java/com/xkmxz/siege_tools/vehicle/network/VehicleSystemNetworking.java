package com.xkmxz.siege_tools.vehicle.network;

import com.xkmxz.siege_tools.siege_tools;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 网络通道注册 — 仅注册 C2SVehiclePacket 和 S2CVehiclePacket 两个统一包。
 */
public class VehicleSystemNetworking {

    public static void register(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(siege_tools.MODID)
                .versioned("2.0")
                .optional();

        // 统一的 C2S 包（处理所有客户端→服务端操作）
        registrar.playToServer(
                C2SVehiclePacket.TYPE,
                C2SVehiclePacket.STREAM_CODEC,
                C2SVehiclePacket::handle
        );

        // 统一的 S2C 包（处理所有服务端→客户端操作）
        registrar.playToClient(
                S2CVehiclePacket.TYPE,
                S2CVehiclePacket.STREAM_CODEC,
                S2CVehiclePacket::handle
        );
    }
}
