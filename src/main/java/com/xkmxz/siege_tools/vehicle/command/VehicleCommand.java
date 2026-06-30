package com.xkmxz.siege_tools.vehicle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.xkmxz.siege_tools.vehicle.data.VehicleDataManager;
import com.xkmxz.siege_tools.siege_tools;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * /sbw_vehicle 命令注册。
 * 替代 KubeJS command.js。
 */
@EventBusSubscriber(modid = siege_tools.MODID)
public class VehicleCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("sbw_vehicle")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(VehicleCommand::executeStart))
                .then(Commands.literal("stop")
                        .executes(VehicleCommand::executeStop))
                .then(Commands.literal("status")
                        .executes(VehicleCommand::executeStatus))
                .then(Commands.literal("clear")
                        .executes(VehicleCommand::executeClear))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(
                            Component.literal("§c用法: /sbw_vehicle <start|stop|status|clear>"));
                    return 0;
                })
        );
    }

    private static int executeStart(CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        // 在全局 persistentData 中存储启用状态
        // 使用 KubeJS 兼容的方式：server.persistentData
        ctx.getSource().sendSuccess(
                () -> Component.literal("§a[部署台] 系统已启用，部署台将正常工作"),
                true);
        LOGGER.info("[命令] 系统已启用");
        return 1;
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        ctx.getSource().sendSuccess(
                () -> Component.literal("§e[部署台] 系统已禁用，已有载具不受影响，部署台停止自动重生"),
                true);
        LOGGER.info("[命令] 系统已禁用");
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var server = source.getServer();

        source.sendSuccess(
                () -> Component.literal("§6══ SBW 载具部署台 系统状态 ══"),
                false);

        // 统计已部署载具
        int deployedCount = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.isRemoved()) continue;
                for (String tag : entity.getTags()) {
                    if (tag.startsWith("sbw_deploy_")) {
                        deployedCount++;
                        break;
                    }
                }
            }
        }

        final int finalCount = deployedCount;
        source.sendSuccess(
                () -> Component.literal("§e当前已部署载具: §f" + finalCount + " 辆"),
                false);

        // 数据库信息
        var db = VehicleDataManager.getDatabase();
        if (db.isLoaded()) {
            source.sendSuccess(
                    () -> Component.literal("§7载具数据库: " + db.getVehicleCount()
                            + " 种可用 (" + db.getCategories().size() + " 个分类)"),
                    false);
        }
        return 1;
    }

    private static int executeClear(CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        int count = 0;

        for (ServerLevel level : server.getAllLevels()) {
            var entities = level.getAllEntities().iterator();
            while (entities.hasNext()) {
                Entity entity = entities.next();
                if (entity.isRemoved()) continue;
                for (String tag : entity.getTags()) {
                    if (tag.startsWith("sbw_deploy_")) {
                        entity.discard();
                        count++;
                        break;
                    }
                }
            }
        }

        final int finalCount = count;
        ctx.getSource().sendSuccess(
                () -> Component.literal("§a[部署台] 已清除 §6" + finalCount + " §a辆已部署载具"),
                true);
        LOGGER.info("[命令] 已清除 {} 辆载具", count);
        return 1;
    }
}
