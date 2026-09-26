package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.client.Fp5GhostClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record Fp5GhostUpdatePacket(UUID id, double x, double y, double z,
                                   float yaw, float pitch, float roll,
                                   boolean onLauncher, boolean launched, boolean booster) {
    public static Fp5GhostUpdatePacket decode(final FriendlyByteBuf buffer) {
        return new Fp5GhostUpdatePacket(buffer.readUUID(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
            buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);
        buffer.writeFloat(roll);
        buffer.writeBoolean(onLauncher);
        buffer.writeBoolean(launched);
        buffer.writeBoolean(booster);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> Fp5GhostClientHandler.handle(this)));
        }
        context.setPacketHandled(true);
    }
}
