package com.xkmxz.siege_tools.vehicle.network;

import com.xkmxz.siege_tools.siege_tools;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 网络通道注册。
 * 使用 NeoForge 1.21.1 的 Payload 网络系统。
 */
public class VehicleSystemNetworking {

    public static void register(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(siege_tools.MODID)
                .versioned("1.0")
                .optional();

        // 弹药补给站配置保存
        registrar.playToServer(
                C2SSaveAmmoConfig.TYPE,
                C2SSaveAmmoConfig.STREAM_CODEC,
                C2SSaveAmmoConfig::handle
        );

        // 载具部署台配置保存
        registrar.playToServer(
                C2SSaveDeployerConfig.TYPE,
                C2SSaveDeployerConfig.STREAM_CODEC,
                C2SSaveDeployerConfig::handle
        );

        // 立即部署触发
        registrar.playToServer(
                C2STriggerDeploy.TYPE,
                C2STriggerDeploy.STREAM_CODEC,
                C2STriggerDeploy::handle
        );

        // 切换作弊模式
        registrar.playToServer(
                C2SToggleCheatMode.TYPE,
                C2SToggleCheatMode.STREAM_CODEC,
                C2SToggleCheatMode::handle
        );

        // 载具部署台 GUI 初始化数据（S2C）
        registrar.playToClient(
                S2CDeployerInitData.TYPE,
                S2CDeployerInitData.STREAM_CODEC,
                S2CDeployerInitData::handle
        );

        // 弹药补给站 GUI 初始化数据（S2C）
        registrar.playToClient(
                S2CAmmoCrateInitData.TYPE,
                S2CAmmoCrateInitData.STREAM_CODEC,
                S2CAmmoCrateInitData::handle
        );
    }
}
