package com.xkmxz.siege_tools.entity;

import com.xkmxz.siege_tools.api.SiegeToolsAPI;
import com.xkmxz.siege_tools.config.AmmoKitConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;

/**
 * 弹药箱实体（Ammo Kit Entity）
 *
 * 投掷放置后：
 * 1. 物理模拟（重力 + 地面摩擦，同 MedicalKitEntity）
 * 2. 落地后定频扫描范围内同队玩家
 * 3. 为弹药不足的玩家补充弹药
 * 4. 全部补满后自动消失
 *
 * 参考实现：superbwarfare 的 MedicalKitEntity
 */
public class AmmoKitEntity extends Entity {

    /** 落地后的 tick 计数器 */
    private int groundedTicks = 0;
    /** 是否已落地 */
    private boolean grounded = false;
    /** 所有玩家补满后的空闲 tick 计数 */
    private int idleFullTicks = 0;
    /** 总存活 tick */
    private int aliveTicks = 0;
    /** 所属队伍（投放者的队伍） */
    private String ownerTeam = "";

    public AmmoKitEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * 设置所属队伍，在投掷时由 AmmoKitItem 调用
     */
    public void setOwnerTeam(String team) {
        this.ownerTeam = team;
    }

    // ========== 物理 ==========

    @Override
    public boolean isPickable() {
        return true;
    }

    /**
     * 右键交互（拾取）
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            // 潜行+右键 → 拾取弹药箱
            if (!level().isClientSide) {
                this.discard();
                if (!player.isCreative()) {
                    ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(
                            com.xkmxz.siege_tools.siege_tools.AMMO_KIT_ITEM.get()));
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void tick() {
        super.tick();
        aliveTicks++;

        // ===== 物理模拟（同 MedicalKitEntity） =====
        // 添加重力
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.add(0, -0.05, 0));

        // 碰撞检测
        if (!level().noCollision(this.getBoundingBox())) {
            this.moveTowardsClosestSpace(this.getX(),
                    (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0,
                    this.getZ());
        }

        // 移动
        this.move(MoverType.SELF, this.getDeltaMovement());

        float friction = 0.98f;
        if (this.onGround()) {
            this.setXRot(-90.0f);
            var blockPos = this.getBlockPosBelowThatAffectsMyMovement();
            friction = this.level().getBlockState(blockPos)
                    .getFriction(this.level(), blockPos, this) * 0.98f;
        } else {
            updateRotation();
        }

        // 应用摩擦
        this.setDeltaMovement(this.getDeltaMovement().multiply(friction, 0.98, friction));

        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1, -0.9, 1));
        }

        // 液体阻力
        if (this.isInFluidType()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.75));
        }

        // ===== 落地后的逻辑 =====
        if (this.onGround()) {
            if (!grounded) {
                grounded = true;
                groundedTicks = 0;
                // 落地音效
                level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.SHULKER_BOX_OPEN, SoundSource.PLAYERS, 0.6f, 1.2f);
            }

            // 落地后等待 10 tick 才开始扫描
            groundedTicks++;
            if (groundedTicks >= 10 && groundedTicks % AmmoKitConfig.placedScanInterval == 0) {
                scanAndSupply();
            }
        }

        // 检查最大存活时间
        if (AmmoKitConfig.placedMaxLifetime > 0 && aliveTicks > AmmoKitConfig.placedMaxLifetime) {
            this.discard();
        }

        // 客户端粒子效果
        if (level().isClientSide && grounded) {
            for (int i = 0; i < 2; i++) {
                double px = this.getX() + (random.nextDouble() - 0.5) * 0.6;
                double py = this.getY() + 0.1 + random.nextDouble() * 0.3;
                double pz = this.getZ() + (random.nextDouble() - 0.5) * 0.6;
                level().addParticle(ParticleTypes.FALLING_NECTAR, px, py, pz, 0, 0.02, 0);
            }
        }

        this.refreshDimensions();
    }

    /**
     * 扫描范围内玩家并补给弹药
     */
    private void scanAndSupply() {
        if (level().isClientSide) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;

        // 扫描范围内的玩家
        AABB scanBox = this.getBoundingBox().inflate(AmmoKitConfig.placedScanRange);
        List<ServerPlayer> players = serverLevel.getEntitiesOfClass(
                ServerPlayer.class, scanBox,
                player -> player != null && player.isAlive() && !player.isSpectator()
        );

        if (players.isEmpty()) return;

        // 检查每个玩家是否需要弹药
        boolean anyRefilled = false;
        boolean allFull = true;

        for (ServerPlayer targetPlayer : players) {
            // 检查同队
            String playerTeam = targetPlayer.getPersistentData().getString("team");
            if (ownerTeam.isEmpty() || !ownerTeam.equals(playerTeam)) {
                continue;
            }

            // 检查弹药是否已满
            if (SiegeToolsAPI.isPlayerFullySupplied(targetPlayer)) {
                continue;
            }

            allFull = false;

            // 补充弹药
            boolean refilled = SiegeToolsAPI.refillPlayerAmmo(targetPlayer,
                    AmmoKitConfig.supplyPrimary,
                    AmmoKitConfig.supplySecondary,
                    AmmoKitConfig.supplyTertiary);

            if (refilled) {
                anyRefilled = true;
                // 通知玩家
                targetPlayer.sendSystemMessage(
                        Component.translatable("message.siege_tools.ammo_kit.area_refilled")
                                .withStyle(net.minecraft.ChatFormatting.GREEN));
                // 音效
                level().playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1.5f);
            }
        }

        // 如果所有玩家都已补满，累计空闲时间
        if (allFull && anyRefilled) {
            // 有玩家刚被补满，重置空闲计数器
            idleFullTicks = 0;
        } else if (allFull) {
            // 所有玩家本来就满
            idleFullTicks += AmmoKitConfig.placedScanInterval;
            if (AmmoKitConfig.placedIdleDiscardDelay > 0
                    && idleFullTicks >= AmmoKitConfig.placedIdleDiscardDelay) {
                // 空闲超时 → 消失
                level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.SHULKER_BOX_CLOSE, SoundSource.PLAYERS, 0.6f, 1.2f);
                this.discard();
            }
        } else {
            idleFullTicks = 0;
        }
    }

    // ========== 旋转 ==========

    private void updateRotation() {
        Vec3 motion = this.getDeltaMovement();
        if (motion.length() > 0.05) {
            this.setXRot(lerpRotation(this.xRotO, (float) (Math.atan2(-motion.y, motion.horizontalDistance()) * 57.2957763671875)));
            this.setYRot(lerpRotation(this.yRotO, (float) (Math.atan2(motion.x, motion.z) * 57.2957763671875)));
        }
    }

    private static float lerpRotation(float current, float target) {
        float delta = (target - current) % 360.0f;
        if (delta >= 180.0f) delta -= 360.0f;
        if (delta < -180.0f) delta += 360.0f;
        return current + delta * 0.2f;
    }

    // ========== 数据持久化 ==========

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("GroundedTicks")) groundedTicks = tag.getInt("GroundedTicks");
        if (tag.contains("AliveTicks")) aliveTicks = tag.getInt("AliveTicks");
        if (tag.contains("IdleFullTicks")) idleFullTicks = tag.getInt("IdleFullTicks");
        if (tag.contains("Grounded")) grounded = tag.getBoolean("Grounded");
        if (tag.contains("OwnerTeam")) ownerTeam = tag.getString("OwnerTeam");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("GroundedTicks", groundedTicks);
        tag.putInt("AliveTicks", aliveTicks);
        tag.putInt("IdleFullTicks", idleFullTicks);
        tag.putBoolean("Grounded", grounded);
        tag.putString("OwnerTeam", ownerTeam);
    }
}
