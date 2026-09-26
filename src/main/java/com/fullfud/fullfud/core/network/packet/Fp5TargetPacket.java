package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.common.entity.Fp5FlamingoEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record Fp5TargetPacket(UUID flamingoId, int x, int y, int z) {
    public static Fp5TargetPacket decode(final FriendlyByteBuf buffer) {
        return new Fp5TargetPacket(buffer.readUUID(), buffer.readInt(), buffer.readInt(), buffer.readInt());
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeUUID(flamingoId);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        final ServerPlayer sender = context.getSender();
        if (sender != null && context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                if (sender.containerMenu instanceof com.fullfud.fullfud.common.menu.Fp5MonitorMenu menu
                    && flamingoId.equals(menu.getFlamingoId())
                    && Math.abs((long) x) <= 30000000L && Math.abs((long) z) <= 30000000L
                    && y >= -2048 && y <= 2048) {
                    Fp5FlamingoEntity.find(sender.serverLevel(), flamingoId)
                        .ifPresent(flamingo -> flamingo.setMonitorTarget(new BlockPos(x, y, z)));
                }
            });
        }
        context.setPacketHandled(true);
    }
}
