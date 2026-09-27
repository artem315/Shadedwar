package com.fullfud.fullfud.common.entity;

import com.fullfud.fullfud.core.FullfudRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public class Shahed238DroneEntity extends ShahedDroneEntity {

    public Shahed238DroneEntity(final EntityType<? extends ShahedDroneEntity> entityType, final Level level) {
        super(entityType, level);
    }

    @Override
    public ItemStack createItemStack() {
        if (isSlowVariant()) {
            return new ItemStack(getColor() == ShahedColor.BLACK
                ? FullfudRegistries.SHAHED_238_BLACK_ITEM_SLOW.get()
                : FullfudRegistries.SHAHED_238_ITEM_SLOW.get());
        }
        return new ItemStack(getColor() == ShahedColor.BLACK
            ? FullfudRegistries.SHAHED_238_BLACK_ITEM.get()
            : FullfudRegistries.SHAHED_238_ITEM.get());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (!isOnLauncher()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    com.fullfud.fullfud.client.particle.Shahed238ClientVfx.tick(this);
                });
            }
        }
    }

    @Override
    public void remove(final RemovalReason reason) {
        super.remove(reason);
        if (level().isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (reason.shouldDestroy()) {
                    com.fullfud.fullfud.client.particle.Shahed238ClientVfx.forget(getUUID());
                } else {
                    com.fullfud.fullfud.client.particle.Shahed238ClientVfx.remove(getUUID());
                }
            });
        }
    }

    @Override
    protected byte getAudioDroneType() {
        return AUDIO_TYPE_SHAHED_238;
    }

    @Override
    protected float computeAudioLoopPitch(final float engineMix, final float speedFactor) {
        return 1.18F + engineMix * 0.12F + speedFactor * 0.15F;
    }
}
