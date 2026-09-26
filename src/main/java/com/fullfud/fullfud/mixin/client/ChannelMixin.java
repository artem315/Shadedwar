package com.fullfud.fullfud.mixin.client;

import com.fullfud.fullfud.client.sound.OpenALFilters;
import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public abstract class ChannelMixin {
    @Shadow
    @Final
    private int source;

    @Inject(method = "destroy", at = @At("HEAD"))
    private void fullfud$onChannelDestroy(final CallbackInfo ci) {
        if (this.source > 0) {
            OpenALFilters.removeFromSource(this.source);
        }
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void fullfud$onChannelStop(final CallbackInfo ci) {
        if (this.source > 0) {
            OpenALFilters.removeFromSource(this.source);
        }
    }
}
