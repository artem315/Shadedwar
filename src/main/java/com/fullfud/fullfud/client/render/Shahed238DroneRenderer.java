package com.fullfud.fullfud.client.render;

import com.fullfud.fullfud.client.model.Shahed238DroneModel;
import com.fullfud.fullfud.common.entity.Shahed238DroneEntity;
import com.fullfud.fullfud.core.config.FullfudClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class Shahed238DroneRenderer extends GeoEntityRenderer<Shahed238DroneEntity> {

    public Shahed238DroneRenderer(final EntityRendererProvider.Context context) {
        super(context, new Shahed238DroneModel());
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(final Shahed238DroneEntity entity, final float entityYaw, final float partialTick, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedLight) {
        poseStack.pushPose();

        if (entity.isOnLauncher()) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getVisualRoll(partialTick)));

        poseStack.translate(0.0D, -0.25D, 0.0D);

        final int skyLight = Math.max(LightTexture.sky(packedLight), 11);
        final int blockLight = Math.max(LightTexture.block(packedLight), 4);
        final int effectiveLight = LightTexture.pack(blockLight, skyLight);

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, effectiveLight);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(final Shahed238DroneEntity entity, final Frustum frustum, final double x, final double y, final double z) {
        if (super.shouldRender(entity, frustum, x, y, z)) {
            return true;
        }
        final double distSq = x * x + y * y + z * z;
        final double max = Math.max(1.0D, FullfudClientConfig.CLIENT.shahedRenderDistanceCap.get());
        return distSq <= max * max && frustum.isVisible(entity.getBoundingBoxForCulling().inflate(8.0D));
    }
}
