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
                                   boolean onLauncher, boolean launched, boolean booster, boolean removed) {
    public static Fp5GhostUpdatePacket decode(final FriendlyByteBuf buffer) {
        final UUID id = buffer.readUUID();
        final double x = buffer.readDouble();
        final double y = buffer.readDouble();
        final double z = buffer.readDouble();
        final float yaw = buffer.readFloat();
        final float pitch = buffer.readFloat();
        final float roll = buffer.readFloat();
        final boolean onLauncher = buffer.readBoolean();
        final boolean launched = buffer.readBoolean();
        final boolean booster = buffer.readBoolean();
        final boolean removed = buffer.readBoolean();
        return new Fp5GhostUpdatePacket(id, x, y, z, yaw, pitch, roll, onLauncher, launched, booster, removed);
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
        buffer.writeBoolean(removed);
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
