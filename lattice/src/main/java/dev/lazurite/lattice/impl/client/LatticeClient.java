package dev.lazurite.lattice.impl.client;

import dev.lazurite.lattice.api.point.ViewPoint;
import dev.lazurite.lattice.impl.ViewPointHelper;
import dev.lazurite.lattice.impl.api.player.InternalLatticeLocalPlayer;
import dev.lazurite.lattice.impl.network.SetViewPointPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

public final class LatticeClient {

    public static void init() {

        MinecraftForge.EVENT_BUS.addListener(LatticeClient::onEntityJoinLevel);
        MinecraftForge.EVENT_BUS.addListener(LatticeClient::onClientTick);
    }

    private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof LocalPlayer localPlayer && localPlayer instanceof InternalLatticeLocalPlayer internalPlayer) {
            internalPlayer.setViewPointEntityId(localPlayer.getId());
            internalPlayer.setViewPoint((ViewPoint) (Object) localPlayer);
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        final var mc = Minecraft.getInstance();
        final var localPlayer = mc.player;
        final var clientLevel = mc.level;
        if (localPlayer == null || clientLevel == null) {
            return;
        }

        if (!(localPlayer instanceof InternalLatticeLocalPlayer internalLatticeLocalPlayer)) {
            return;
        }
        final var localPlayerId = localPlayer.getId();
        final var viewPointEntityId = internalLatticeLocalPlayer.getViewPointEntityId();

        if (viewPointEntityId != localPlayerId) {
            final var viewPoint = internalLatticeLocalPlayer.getViewPoint();

            if (viewPoint instanceof Entity entity) {
                if (entity.isRemoved() || !entity.isAlive()) {
                    internalLatticeLocalPlayer.setViewPointEntityId(localPlayerId);
                    internalLatticeLocalPlayer.setViewPoint((ViewPoint) (Object) localPlayer);
                    if (mc.getCameraEntity() != localPlayer) {
                        mc.setCameraEntity(localPlayer);
                    }
                    if (mc.levelRenderer != null) {
                        mc.levelRenderer.allChanged();
                    }
                } else if (mc.getCameraEntity() != entity) {
                    final var viewPointEntity = clientLevel.getEntity(viewPointEntityId);
                    if (viewPointEntity != null) {
                        mc.setCameraEntity(viewPointEntity);
                    }
                }
            } else {
                internalLatticeLocalPlayer.setViewPointEntityId(localPlayerId);
                internalLatticeLocalPlayer.setViewPoint((ViewPoint) (Object) localPlayer);
                if (mc.getCameraEntity() != localPlayer) {
                    mc.setCameraEntity(localPlayer);
                }
                if (mc.levelRenderer != null) {
                    mc.levelRenderer.allChanged();
                }
            }
        } else {
            if (mc.getCameraEntity() != null && mc.getCameraEntity() != localPlayer && (mc.getCameraEntity().isRemoved() || !mc.getCameraEntity().isAlive() || internalLatticeLocalPlayer.getViewPoint() == (Object) localPlayer)) {
                mc.setCameraEntity(localPlayer);
            }
        }
    }

    public static void handleSetViewPointPacket(SetViewPointPacket msg) {
        final var mc = Minecraft.getInstance();
        final var localPlayer = mc.player;
        if (localPlayer == null) {
            return;
        }
        if (!(localPlayer instanceof InternalLatticeLocalPlayer internalLatticeLocalPlayer)) {
            return;
        }

        if (msg.isEntity()) {
            final var clientLevel = localPlayer.level();
            final int entityId = msg.getEntityId();
            internalLatticeLocalPlayer.setViewPointEntityId(entityId);

            if (entityId == localPlayer.getId()) {
                internalLatticeLocalPlayer.setViewPoint((ViewPoint) (Object) localPlayer);
                if (mc.getCameraEntity() != localPlayer) {
                    mc.setCameraEntity(localPlayer);
                }
                if (mc.levelRenderer != null) {
                    mc.levelRenderer.allChanged();
                }
            } else {
                final var entity = clientLevel.getEntity(entityId);
                if (entity != null) {
                    final ViewPoint entityViewPoint = (entity instanceof ViewPoint vp) ? vp : ViewPointHelper.resolveViewPoint(entity);
                    if (entityViewPoint != null) {
                        internalLatticeLocalPlayer.setViewPoint(entityViewPoint);
                    }
                    if (mc.getCameraEntity() != entity) {
                        mc.setCameraEntity(entity);
                    }
                }
            }
        } else {
            internalLatticeLocalPlayer.setViewPointEntityId(localPlayer.getId());
            internalLatticeLocalPlayer.setViewPoint((ViewPoint) (Object) localPlayer);
            if (mc.getCameraEntity() != localPlayer) {
                mc.setCameraEntity(localPlayer);
            }
            if (mc.levelRenderer != null) {
                mc.levelRenderer.allChanged();
            }
        }
    }
}
