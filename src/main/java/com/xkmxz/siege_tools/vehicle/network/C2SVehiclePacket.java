package com.xkmxz.siege_tools.vehicle.network;

import com.xkmxz.siege_tools.siege_tools;
import com.xkmxz.siege_tools.vehicle.block.AmmoCrateBlockEntity;
import com.xkmxz.siege_tools.vehicle.block.VehicleDeployerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一 C2S 网络包 — 处理所有载具系统的客户端→服务端操作。
 * <p>
 * action 类型：
 * <ul>
 *   <li>{@code save_deployer} — 保存载具部署台配置</li>
 *   <li>{@code save_ammo} — 保存弹药补给站配置</li>
 *   <li>{@code trigger_deploy} — 触发立即部署</li>
 *   <li>{@code toggle_cheat} — 切换作弊模式</li>
 *   <li>{@code reset_ammo} — 重置弹药补给站配置</li>
 * </ul>
 */
public record C2SVehiclePacket(
        BlockPos pos,
        String action,
        CompoundTag data
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SVehiclePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(siege_tools.MODID, "c2s_vehicle"));

    public static final StreamCodec<FriendlyByteBuf, C2SVehiclePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public C2SVehiclePacket decode(FriendlyByteBuf buf) {
                    BlockPos pos = buf.readBlockPos();
                    String action = buf.readUtf();
                    CompoundTag data = buf.readNbt();
                    if (data == null) data = new CompoundTag();
                    return new C2SVehiclePacket(pos, action, data);
                }

                @Override
                public void encode(FriendlyByteBuf buf, C2SVehiclePacket pkt) {
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

    /** 保存载具部署台配置 */
    public static C2SVehiclePacket saveDeployer(BlockPos pos, DeployerConfigData cfg) {
        return new C2SVehiclePacket(pos, "save_deployer", cfg.toTag());
    }

    /** 保存弹药补给站配置 */
    public static C2SVehiclePacket saveAmmo(BlockPos pos, int scanRange, int cooldown, int enterDelay, Map<String, Integer> slots) {
        return new C2SVehiclePacket(pos, "save_ammo", ammoConfigToTag(scanRange, cooldown, enterDelay, slots));
    }

    /** 触发部署 */
    public static C2SVehiclePacket triggerDeploy(BlockPos pos) {
        return new C2SVehiclePacket(pos, "trigger_deploy", new CompoundTag());
    }

    /** 切换作弊模式 */
    public static C2SVehiclePacket toggleCheat(BlockPos pos) {
        return new C2SVehiclePacket(pos, "toggle_cheat", new CompoundTag());
    }

    /** 重置弹药配置 */
    public static C2SVehiclePacket resetAmmo(BlockPos pos) {
        return new C2SVehiclePacket(pos, "reset_ammo", new CompoundTag());
    }

    /** 重置部署台为默认配置 */
    public static C2SVehiclePacket resetDeployer(BlockPos pos) {
        return new C2SVehiclePacket(pos, "reset_deployer", new CompoundTag());
    }

    // ========== 处理 ==========

    public static void handle(C2SVehiclePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (player == null) return;
            var level = player.level();

            switch (pkt.action) {
                case "save_deployer" -> handleSaveDeployer(pkt, level, player);
                case "save_ammo" -> handleSaveAmmo(pkt, level, player);
                case "trigger_deploy" -> handleTriggerDeploy(pkt, level, player);
                case "toggle_cheat" -> handleToggleCheat(pkt, level, player);
                case "reset_ammo" -> handleResetAmmo(pkt, level, player);
                case "reset_deployer" -> handleResetDeployer(pkt, level, player);
                default -> {}
            }
        });
    }

    private static void handleSaveDeployer(C2SVehiclePacket pkt, net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player) {
        if (!(level.getBlockEntity(pkt.pos) instanceof VehicleDeployerBlockEntity be)) return;
        be.applyConfig(DeployerConfigData.fromTag(pkt.data));
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.siege_tools.save_success"), false);
    }

    private static void handleSaveAmmo(C2SVehiclePacket pkt, net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player) {
        if (!(level.getBlockEntity(pkt.pos) instanceof AmmoCrateBlockEntity be)) return;
        int scanRange = pkt.data.getInt("scanRange");
        int cooldown = pkt.data.getInt("cooldown");
        int enterDelay = pkt.data.getInt("enterDelay");
        be.applyConfig(scanRange, cooldown, enterDelay, readSlotsMap(pkt.data));
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.siege_tools.save_ammo_success"), false);
    }

    private static void handleTriggerDeploy(C2SVehiclePacket pkt, net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player) {
        if (!(level.getBlockEntity(pkt.pos) instanceof VehicleDeployerBlockEntity be)) return;
        if (be.getVehicleType() == null || be.getVehicleType().isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.siege_tools.no_vehicle"), false);
            return;
        }
        be.setPendingDeploy(true);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.siege_tools.deploy_pending"), false);
    }

    private static void handleToggleCheat(C2SVehiclePacket pkt, net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player) {
        if (!(level.getBlockEntity(pkt.pos) instanceof AmmoCrateBlockEntity be)) return;
        be.setCheatMode(!be.isCheatMode());
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.siege_tools.cheat_toggled"), false);
    }

    private static void handleResetAmmo(C2SVehiclePacket pkt, net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player) {
        if (!(level.getBlockEntity(pkt.pos) instanceof AmmoCrateBlockEntity be)) return;
        be.resetConfig();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.siege_tools.reset_success"), false);
    }

    private static void handleResetDeployer(C2SVehiclePacket pkt, net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player) {
        if (!(level.getBlockEntity(pkt.pos) instanceof VehicleDeployerBlockEntity be)) return;
        be.resetConfig();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.siege_tools.reset_success"), false);
    }

    private static CompoundTag ammoConfigToTag(int scanRange, int cooldown, int enterDelay, Map<String, Integer> slots) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("scanRange", scanRange);
        tag.putInt("cooldown", cooldown);
        tag.putInt("enterDelay", enterDelay);
        CompoundTag slotsTag = new CompoundTag();
        for (Map.Entry<String, Integer> e : slots.entrySet()) {
            slotsTag.putInt(e.getKey(), e.getValue());
        }
        tag.put("slots", slotsTag);
        return tag;
    }

    private static Map<String, Integer> readSlotsMap(CompoundTag tag) {
        Map<String, Integer> map = new HashMap<>();
        if (tag.contains("slots")) {
            CompoundTag slotsTag = tag.getCompound("slots");
            for (String key : slotsTag.getAllKeys()) {
                map.put(key, slotsTag.getInt(key));
            }
        }
        return map;
    }
}
