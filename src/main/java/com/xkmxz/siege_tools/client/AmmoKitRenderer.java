package com.xkmxz.siege_tools.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xkmxz.siege_tools.entity.AmmoKitEntity;
import com.xkmxz.siege_tools.siege_tools;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 弹药箱实体渲染器 — 将弹药箱渲染为一个旋转的弹药补给包物品
 */
public class AmmoKitRenderer extends EntityRenderer<AmmoKitEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.parse("textures/atlas/blocks.png");
    private final ItemRenderer itemRenderer;

    public AmmoKitRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(AmmoKitEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        // 渲染物品
        poseStack.pushPose();

        // 如果在地上，平躺；否则旋转展示
        if (entity.onGround()) {
            poseStack.translate(0.0, 0.15, 0.0);
            poseStack.scale(0.8f, 0.8f, 0.8f);
        } else {
            poseStack.translate(0.0, 0.25, 0.0);
            poseStack.scale(1.0f, 1.0f, 1.0f);
        }

        ItemStack stack = new ItemStack(siege_tools.AMMO_KIT_ITEM.get());
        itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, entity.level(), entity.getId());

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(AmmoKitEntity entity) {
        return TEXTURE;
    }
}
