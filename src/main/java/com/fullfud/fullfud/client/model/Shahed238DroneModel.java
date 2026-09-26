package com.fullfud.fullfud.client.model;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.entity.Shahed238DroneEntity;
import com.fullfud.fullfud.common.entity.ShahedColor;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class Shahed238DroneModel extends GeoModel<Shahed238DroneEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(FullfudMod.MOD_ID, "geo/shahed_238.geo.json");
    private static final ResourceLocation MODEL_ON_LAUNCHER = new ResourceLocation(FullfudMod.MOD_ID, "geo/shahed_238onlauncher.geo.json");
    private static final ResourceLocation TEXTURE_WHITE = new ResourceLocation(FullfudMod.MOD_ID, "textures/entity/shahed_238_white.png");
    private static final ResourceLocation TEXTURE_BLACK = new ResourceLocation(FullfudMod.MOD_ID, "textures/entity/shahed_238_black.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(FullfudMod.MOD_ID, "animations/shahed_136.animation.json");

    @Override
    public ResourceLocation getModelResource(final Shahed238DroneEntity animatable) {
        return animatable.isOnLauncher() ? MODEL_ON_LAUNCHER : MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(final Shahed238DroneEntity animatable) {
        return animatable.getColor() == ShahedColor.BLACK ? TEXTURE_BLACK : TEXTURE_WHITE;
    }

    @Override
    public ResourceLocation getAnimationResource(final Shahed238DroneEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(final Shahed238DroneEntity animatable, final long instanceId, final AnimationState<Shahed238DroneEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        CoreGeoBone root = this.getAnimationProcessor().getBone("group");
        if (root == null) {
            root = this.getAnimationProcessor().getBone("bone");
        }
        if (root == null) {
            return;
        }
        if (animatable.isOnLauncher()) {
            root.updateRotation(
                root.getInitialSnapshot().getRotX(),
                root.getInitialSnapshot().getRotY(),
                root.getInitialSnapshot().getRotZ()
            );
            return;
        }
        final float partialTick = animationState.getPartialTick();
        final float basePitchRad = (float) Math.toRadians(animatable.getVisualPitch(partialTick));
        root.setRotX(-basePitchRad);
        root.setRotZ(0.0F);
    }
}
