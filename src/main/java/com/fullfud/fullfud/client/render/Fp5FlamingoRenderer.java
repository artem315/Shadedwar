package com.fullfud.fullfud.client.render;

import com.fullfud.fullfud.client.model.Fp5FlamingoModel;
import com.fullfud.fullfud.common.entity.Fp5FlamingoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class Fp5FlamingoRenderer extends GeoEntityRenderer<Fp5FlamingoEntity> {
    public Fp5FlamingoRenderer(final EntityRendererProvider.Context context) {
        super(context, new Fp5FlamingoModel());
        // Keep a subtle vanilla contact shadow as a fallback; the local VFX
        // pipeline adds depth-aware shadows and custom light volumes.
        this.shadowRadius = 0.65F;
    }

    @Override
    public void render(final Fp5FlamingoEntity entity, final float entityYaw, final float partialTick, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedLight) {
        final float yaw;
        final float pitch;
        final float roll;
        if (entity.isOnLauncher()) {
            yaw = entity.getYRot();
            pitch = entity.getXRot();
            roll = 0.0F;
        } else {
            yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            pitch = Mth.rotLerp(partialTick, entity.xRotO, entity.getXRot());
            roll = entity.getVisualRoll(partialTick);
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        poseStack.scale(Fp5FlamingoEntity.SCALE, Fp5FlamingoEntity.SCALE, Fp5FlamingoEntity.SCALE);

        final int skyLight = Math.max(LightTexture.sky(packedLight), 11);
        final int blockLight = Math.max(LightTexture.block(packedLight), 4);
        final int effectiveLight = LightTexture.pack(blockLight, skyLight);

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, effectiveLight);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(final Fp5FlamingoEntity entity, final Frustum frustum, final double x, final double y, final double z) {
        if (super.shouldRender(entity, frustum, x, y, z)) {
            return true;
        }
        final double distSq = x * x + y * y + z * z;
        return distSq <= 4096.0D * 4096.0D && frustum.isVisible(entity.getBoundingBoxForCulling().inflate(8.0D));
    }
}
