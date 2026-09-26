package com.fullfud.fullfud.client.model;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.entity.ShahedColor;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class ShahedDroneModel extends GeoModel<ShahedDroneEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(FullfudMod.MOD_ID, "geo/shahed_136.geo.json");
    private static final ResourceLocation MODEL_ON_LAUNCHER = new ResourceLocation(FullfudMod.MOD_ID, "geo/shahed_136onlauncher.geo.json");
    private static final ResourceLocation TEXTURE_WHITE = new ResourceLocation(FullfudMod.MOD_ID, "textures/entity/shahed_136.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(FullfudMod.MOD_ID, "animations/shahed_136.animation.json");

    @Override
    public ResourceLocation getModelResource(final ShahedDroneEntity animatable) {
        return animatable.isOnLauncher() ? MODEL_ON_LAUNCHER : MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(final ShahedDroneEntity animatable) {
        return TEXTURE_WHITE;
    }

    @Override
    public ResourceLocation getAnimationResource(final ShahedDroneEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(final ShahedDroneEntity animatable, final long instanceId, final AnimationState<ShahedDroneEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        CoreGeoBone root = this.getAnimationProcessor().getBone("group");
        if (root == null) {
            root = this.getAnimationProcessor().getBone("bone");
        }
        if (root == null) {
            return;
        }
        final CoreGeoBone prop = this.getAnimationProcessor().getBone("group3");
        if (animatable.isOnLauncher()) {
            root.updateRotation(
                root.getInitialSnapshot().getRotX(),
                root.getInitialSnapshot().getRotY(),
                root.getInitialSnapshot().getRotZ()
            );
            if (prop != null) {
                prop.setRotZ(0.0F);
            }
            return;
        }
        final float partialTick = animationState.getPartialTick();
        final float basePitchRad = (float) Math.toRadians(animatable.getVisualPitch(partialTick));
        root.setRotX(-basePitchRad);
        root.setRotZ(0.0F);

        if (prop != null) {
            final float rotSpeed = (animatable.getThrust() > 0.05F || animatable.getDeltaMovement().lengthSqr() > 0.001D) ? 65.0F : 30.0F;
            final float angle = (animatable.tickCount + partialTick) * rotSpeed;
            prop.setRotZ((float) Math.toRadians(-angle));
        }
    }
}
