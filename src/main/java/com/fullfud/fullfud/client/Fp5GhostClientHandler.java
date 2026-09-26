package com.fullfud.fullfud.client;

import com.fullfud.fullfud.client.particle.Fp5ClientVfx;
import com.fullfud.fullfud.common.entity.Fp5FlamingoEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.network.packet.Fp5GhostUpdatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Renders server telemetry while vanilla entity tracking is outside the player's chunk view. */
public final class Fp5GhostClientHandler {
    private static final Map<UUID, Ghost> GHOSTS = new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;

    private Fp5GhostClientHandler() { }

    public static void handle(final Fp5GhostUpdatePacket packet) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || packet == null || packet.id() == null) {
            return;
        }
        if (!Double.isFinite(packet.x()) || !Double.isFinite(packet.y()) || !Double.isFinite(packet.z())) {
            return;
        }
        if (lastLevel != minecraft.level) {
            GHOSTS.clear();
            lastLevel = minecraft.level;
        }
        GHOSTS.computeIfAbsent(packet.id(), ignored -> new Ghost(minecraft.level, packet))
            .update(packet, minecraft.level.getGameTime());
    }

    public static void render(final RenderLevelStageEvent event, final Minecraft minecraft) {
        if (lastLevel != minecraft.level) {
            GHOSTS.clear();
            lastLevel = minecraft.level;
        }
        if (minecraft.level == null || minecraft.player == null || GHOSTS.isEmpty()) {
            return;
        }
        final long now = minecraft.level.getGameTime();
        GHOSTS.entrySet().removeIf(entry -> {
            final boolean expired = now - entry.getValue().lastUpdateTick > 40;
            if (expired) {
                Fp5ClientVfx.remove(entry.getKey());
            }
            return expired;
        });
        final Set<UUID> loaded = new HashSet<>();
        for (final var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof Fp5FlamingoEntity flamingo && flamingo.isAlive() && !flamingo.isRemoved() && minecraft.level.hasChunkAt(flamingo.blockPosition())) {
                loaded.add(entity.getUUID());
            }
        }
        final var camera = event.getCamera().getPosition();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean rendered = false;
        for (final Map.Entry<UUID, Ghost> entry : GHOSTS.entrySet()) {
            if (loaded.contains(entry.getKey())) {
                continue;
            }
            final Ghost ghost = entry.getValue();
            final Fp5GhostUpdatePacket data = ghost.current;
            final float blend = Mth.clamp((now - ghost.lastUpdateTick + event.getPartialTick()) / 4.0F, 0.0F, 1.0F);
            final double x = Mth.lerp(blend, ghost.previous.x(), data.x());
            final double y = Mth.lerp(blend, ghost.previous.y(), data.y());
            final double z = Mth.lerp(blend, ghost.previous.z(), data.z());
            final double dx = x - camera.x;
            final double dy = y - camera.y;
            final double dz = z - camera.z;
            if (dx * dx + dy * dy + dz * dz > 4096.0D * 4096.0D) {
                continue;
            }
            final float yaw = Mth.rotLerp(blend, ghost.previous.yaw(), data.yaw());
            final float pitch = Mth.rotLerp(blend, ghost.previous.pitch(), data.pitch());
            final float roll = Mth.rotLerp(blend, ghost.previous.roll(), data.roll());
            ghost.entity.applyClientGhostState(x, y, z, yaw, pitch, roll,
                data.onLauncher(), data.launched(), data.booster());
            minecraft.getEntityRenderDispatcher().render(ghost.entity, dx, dy, dz, yaw,
                event.getPartialTick(), event.getPoseStack(), buffers, LightTexture.FULL_BRIGHT);
            if (data.launched() && ghost.lastParticleTick != now) {
                ghost.lastParticleTick = now;
                Fp5ClientVfx.tick(ghost.entity);
            }
            rendered = true;
        }
        if (rendered) {
            buffers.endBatch();
        }
    }

    private static final class Ghost {
        private final Fp5FlamingoEntity entity;
        private Fp5GhostUpdatePacket previous;
        private Fp5GhostUpdatePacket current;
        private long lastUpdateTick;
        private long lastParticleTick = Long.MIN_VALUE;

        private Ghost(final net.minecraft.client.multiplayer.ClientLevel level, final Fp5GhostUpdatePacket packet) {
            entity = new Fp5FlamingoEntity(FullfudRegistries.FP5_FLAMINGO_ENTITY.get(), level);
            entity.setUUID(packet.id());
            entity.noCulling = true;
            previous = packet;
            current = packet;
        }

        private void update(final Fp5GhostUpdatePacket packet, final long tick) {
            previous = current;
            current = packet;
            lastUpdateTick = tick;
        }
    }
}
