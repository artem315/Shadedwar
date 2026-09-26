package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.client.particle.DroneParticleManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DroneExplosionPacket(
    double x,
    double y,
    double z,
    float normalX,
    float normalY,
    float normalZ,
    byte explosionType,
    byte materialType,
    float power
) {
    public static final byte TYPE_FPV_STANDARD = 0;
    public static final byte TYPE_FPV_STRIKE = 1;
    public static final byte TYPE_SHAHED = 2;
    public static final byte TYPE_FLAMINGO = 3;

    public static final byte MAT_GENERIC = 0;
    public static final byte MAT_DIRT = 1;
    public static final byte MAT_SAND = 2;
    public static final byte MAT_STONE = 3;
    public static final byte MAT_WATER = 4;
    public static final byte MAT_WOOD = 5;

    public static DroneExplosionPacket decode(final FriendlyByteBuf buffer) {
        final double x = buffer.readDouble();
        final double y = buffer.readDouble();
        final double z = buffer.readDouble();
        final float nx = buffer.readFloat();
        final float ny = buffer.readFloat();
        final float nz = buffer.readFloat();
        final byte expType = buffer.readByte();
        final byte matType = buffer.readByte();
        final float p = buffer.readFloat();
        return new DroneExplosionPacket(x, y, z, nx, ny, nz, expType, matType, p);
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeFloat(normalX);
        buffer.writeFloat(normalY);
        buffer.writeFloat(normalZ);
        buffer.writeByte(explosionType);
        buffer.writeByte(materialType);
        buffer.writeFloat(power);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        if (!context.getDirection().getReceptionSide().isClient()) {
            context.setPacketHandled(true);
            return;
        }

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DroneParticleManager.handleExplosionPacket(this)));
        context.setPacketHandled(true);
    }
}
