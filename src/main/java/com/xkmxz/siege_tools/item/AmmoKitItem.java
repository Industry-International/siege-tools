package com.xkmxz.siege_tools.item;

import com.xkmxz.siege_tools.Config;
import com.xkmxz.siege_tools.api.SiegeToolsAPI;
import com.xkmxz.siege_tools.entity.AmmoKitEntity;
import com.xkmxz.siege_tools.siege_tools;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.joml.Math;

import java.util.List;

/**
 * 弹药补给包（Ammo Kit）
 *
 * 功能：
 * 1. 右键点击同队玩家 → 直接为该玩家补充全部弹药
 * 2. 潜行+右键（空中）→ 投掷放置一个弹药箱实体，持续为范围内同队玩家补充弹药
 *
 * 参考实现：superbwarfare 的 MedicalKitItem
 */
public class AmmoKitItem extends Item {

    private static final int THROW_COOLDOWN = 20;
    private static final int MAX_STACK = 16; // 同 MedicalKitItem，注册时 Config 尚未加载，不可用 Config.ammoKitMaxStackSize

    public AmmoKitItem() {
        super(new Item.Properties().stacksTo(MAX_STACK));
    }

    /**
     * 右键点击实体（玩家）时调用
     * 用于直接为同队玩家补充弹药
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide) {
            return level.isClientSide ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
        }

        if (!(target instanceof ServerPlayer targetPlayer)) {
            return InteractionResult.PASS;
        }

        // 检查队伍：同队或目标是本人（从 player.persistentData 读取，由 KubeJS 写入）
        String myTeam = ((ServerPlayer) player).getPersistentData().getString("team");
        String targetTeam = targetPlayer.getPersistentData().getString("team");
        boolean sameTeam = myTeam.equals(targetTeam) && !myTeam.isEmpty() && !"none".equals(myTeam);
        boolean isSelf = player == target;

        if (!sameTeam && !isSelf) {
            // 不同队 → 提示不可用
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.siege_tools.ammo_kit.wrong_team")
                        .withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }

        // 为目标补充弹药
        boolean refilled = SiegeToolsAPI.refillPlayerAmmo(targetPlayer,
                Config.ammoKitSupplyPrimary,
                Config.ammoKitSupplySecondary,
                Config.ammoKitSupplyTertiary);

        if (refilled) {
            // 播放音效
            level.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.5f);

            // 消耗物品
            if (!player.isCreative()) {
                stack.shrink(1);
            }

            // 添加冷却
            player.getCooldowns().addCooldown(this, Config.ammoKitDirectCooldown);

            // 提示
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.siege_tools.ammo_kit.refilled")
                        .withStyle(ChatFormatting.GREEN));
            }
            if (isSelf && player instanceof ServerPlayer sp) {
                // 给自己补不用额外提示
            } else if (targetPlayer instanceof ServerPlayer tp) {
                tp.sendSystemMessage(Component.translatable("message.siege_tools.ammo_kit.received_from",
                        player.getDisplayName()).withStyle(ChatFormatting.GREEN));
            }

            return InteractionResult.SUCCESS;
        } else {
            // 弹药已满
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.siege_tools.ammo_kit.full")
                        .withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResult.FAIL;
        }
    }

    /**
     * 右键使用物品时调用（空中/方块）
     * 潜行 → 投掷放置弹药箱实体
     * 不潜行 → 提示用法
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            // 潜行 → 投掷放置实体
            if (!level.isClientSide) {
                throwAmmoKit(level, (ServerPlayer) player, stack);
            }
            return InteractionResultHolder.success(stack);
        }

        // 不潜行 → 提示
        if (level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.siege_tools.ammo_kit.hint")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResultHolder.fail(stack);
    }

    /**
     * 投掷弹药箱实体
     */
    private void throwAmmoKit(Level level, ServerPlayer player, ItemStack stack) {
        // 随机水平偏移（类似 MedicalKitItem 的投掷逻辑）
        float yaw = (float) (Math.random() * 2.0 - 1.0) * 180.0f;

        AmmoKitEntity entity = new AmmoKitEntity(siege_tools.AMMO_KIT_ENTITY.get(), level);
        entity.setOwnerTeam(player.getPersistentData().getString("team"));
        entity.moveTo(
                player.getX(),
                player.getEyeY() - 0.25,
                player.getZ(),
                yaw, 0.0f
        );
        entity.setYBodyRot(yaw);
        entity.setYHeadRot(yaw);

        // 沿玩家视线方向投掷
        var lookAngle = player.getLookAngle();
        entity.setDeltaMovement(
                lookAngle.x * 0.8,
                lookAngle.y * 0.8,
                lookAngle.z * 0.8
        );

        level.addFreshEntity(entity);

        // 消耗物品
        if (!player.isCreative()) {
            stack.shrink(1);
        }

        // 冷却
        player.getCooldowns().addCooldown(this, THROW_COOLDOWN);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("des.siege_tools.ammo_kit")
                .withStyle(ChatFormatting.GRAY));
    }
}
