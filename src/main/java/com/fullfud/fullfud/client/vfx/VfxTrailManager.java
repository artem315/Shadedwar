package com.fullfud.fullfud.client.vfx;

import com.fullfud.fullfud.common.entity.Fp5FlamingoEntity;
import com.fullfud.fullfud.core.config.FullfudClientConfig;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side history for the physical FP-5 exhaust path.
 *
 * <p>The old particle path was reconstructed from only two positions.  This
 * buffer keeps a bounded, resampled history instead, so a fast or interpolated
 * missile cannot produce gaps or a straight line between distant packets.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class VfxTrailManager {
    // ~30 s of cruise at 0.8 block spacing would be ~2200; the booster climb
    // (the part the player actually watches as a glowing streak) fits easily.
    public static final int MAX_SAMPLES = 1024;
    public static final float DEFAULT_MAX_LIFETIME_SECONDS = 30.0F;
    private static final float MIN_SAMPLE_DISTANCE = 0.10F;
    private static final float MAX_SAMPLE_DISTANCE = 0.80F;
    private static final float TELEPORT_DISTANCE = 32.0F;

    private static final Map<UUID, TrailState> TRAILS = new LinkedHashMap<>();
    private static long clientTick;

    private VfxTrailManager() {
    }

    public static void tick() {
        clientTick++;
        final Iterator<Map.Entry<UUID, TrailState>> iterator = TRAILS.entrySet().iterator();
        while (iterator.hasNext()) {
            final TrailState state = iterator.next().getValue();
            state.ageAndPrune(clientTick);
            if (state.detached && state.samples.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public static void sampleEntity(final Fp5FlamingoEntity entity) {
        if (entity == null || !entity.isAlive() || !entity.isLaunched()) {
            return;
        }
        sampleEntity(entity, entity.position());
    }

    /**
     * Samples the physical booster path from the nozzle-facing point rather
     * than from the entity centre.  The renderer owns the model transform;
     * keeping the small offset here gives the custom tube a stable physical
     * origin without coupling the common trail manager to GeckoLib.
     */
    public static void sampleEntity(final Fp5FlamingoEntity entity, final Vec3 anchor) {
        if (entity == null || anchor == null || !TrailState.isFinite(anchor) || !entity.isAlive() || !entity.isLaunched()) {
            return;
        }
        final TrailState state = TRAILS.computeIfAbsent(entity.getUUID(), ignored -> new TrailState());
        state.detached = false;
        final float speed = (float) entity.getDeltaMovement().length();
        state.add(anchor, entity.isBoosterActive() ? 1.0F : 0.16F, speed, clientTick);
        state.markHead(anchor, entity.isBoosterActive(), clientTick);
        // Single authoritative light update: anchored samples carry the
        // booster/jet nozzle position so the custom light field follows the
        // physically burning stage, not the entity centre.
        VfxLightingRegistry.updateRocket(entity.getUUID(), anchor, entity.isBoosterActive(), speed);
    }

    public static void detach(final UUID id) {
        if (id == null) {
            return;
        }
        final TrailState state = TRAILS.get(id);
        if (state != null) {
            state.detached = true;
        }
        VfxLightingRegistry.removeRocket(id);
    }

    public static void clear() {
        TRAILS.clear();
        clientTick = 0L;
    }

    public static Collection<TrailState> states() {
        return TRAILS.values();
    }

    public static int activeTrailCount() {
        int count = 0;
        for (final TrailState state : TRAILS.values()) {
            if (state.hasGeometry()) {
                count++;
            }
        }
        return count;
    }

    public static float ageSeconds(final Sample sample, final float partialTick) {
        if (sample == null) {
            return 0.0F;
        }
        return Math.max(0.0F, (clientTick - sample.createdTick + partialTick) / 20.0F);
    }

    public static float maxLifetimeSeconds() {
        return configuredLifetimeTicks() / 20.0F;
    }

    private static long configuredLifetimeTicks() {
        return FullfudClientConfig.CLIENT.vfxTrailLifetimeTicks.get();
    }

    public static long currentTick() {
        return clientTick;
    }

    public static final class TrailState {
        private final List<Sample> samples = new ArrayList<>(MAX_SAMPLES);
        private Sample lastSample;
        private boolean detached;
        private Vec3 prevHead;
        private Vec3 head;
        private long headTick = Long.MIN_VALUE;
        private boolean boosterActive;

        public List<Sample> samples() {
            return samples;
        }

        /**
         * Records the nozzle anchors of the last two ticks.  The entity is
         * rendered interpolated between them, so the renderer uses these to
         * end the streak (and place the glare) exactly at the drawn nozzle
         * instead of up to one tick of travel ahead of it.
         */
        private void markHead(final Vec3 anchor, final boolean booster, final long tick) {
            if (head == null || head.distanceToSqr(anchor) > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
                prevHead = anchor;
            } else if (tick != headTick) {
                prevHead = head;
            }
            head = anchor;
            headTick = tick;
            boosterActive = booster;
        }

        public boolean hasHead() {
            return !detached && head != null && prevHead != null;
        }

        public Vec3 prevHead() {
            return prevHead;
        }

        public Vec3 head() {
            return head;
        }

        public long headTick() {
            return headTick;
        }

        public boolean isBoosterActive() {
            return boosterActive;
        }

        public Vec3 interpolatedHead(final float partialTick) {
            if (head == null || prevHead == null) {
                return head;
            }
            final double t = Math.max(0.0D, Math.min(1.0D, partialTick));
            return new Vec3(
                prevHead.x + (head.x - prevHead.x) * t,
                prevHead.y + (head.y - prevHead.y) * t,
                prevHead.z + (head.z - prevHead.z) * t
            );
        }

        public boolean isDetached() {
            return detached;
        }

        public boolean hasGeometry() {
            return samples.size() >= 2;
        }

        private void add(final Vec3 position, final float heat, final float speed, final long tick) {
            if (!isFinite(position)) {
                return;
            }
            if (lastSample != null) {
                final double dx = position.x - lastSample.x;
                final double dy = position.y - lastSample.y;
                final double dz = position.z - lastSample.z;
                final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (!Double.isFinite(distance)) {
                    return;
                }
                if (distance > TELEPORT_DISTANCE) {
                    samples.clear();
                    lastSample = null;
                } else if (distance < MIN_SAMPLE_DISTANCE && tick == lastSample.createdTick) {
                    return;
                } else if (distance > MAX_SAMPLE_DISTANCE) {
                    final int steps = Math.min(8, Math.max(1, (int) Math.ceil(distance / MAX_SAMPLE_DISTANCE)));
                    for (int i = 1; i <= steps; i++) {
                        final double t = (double) i / steps;
                        addSample(
                            lastSample.x + dx * t,
                            lastSample.y + dy * t,
                            lastSample.z + dz * t,
                            heat,
                            speed,
                            tick
                        );
                    }
                    lastSample = samples.get(samples.size() - 1);
                    return;
                }
            }
            addSample(position.x, position.y, position.z, heat, speed, tick);
            lastSample = samples.get(samples.size() - 1);
        }

        private void addSample(final double x, final double y, final double z, final float heat, final float speed, final long tick) {
            final float width = clamp(0.42F + speed * 0.085F, 0.42F, 1.35F);
            samples.add(new Sample(x, y, z, heat, width, tick));
            while (samples.size() > MAX_SAMPLES) {
                samples.remove(0);
            }
        }

        private void ageAndPrune(final long tick) {
            final Iterator<Sample> iterator = samples.iterator();
            while (iterator.hasNext()) {
                final Sample sample = iterator.next();
                if (tick - sample.createdTick > configuredLifetimeTicks()) {
                    iterator.remove();
                }
            }
            if (samples.isEmpty()) {
                lastSample = null;
            }
        }

        private static boolean isFinite(final Vec3 value) {
            return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
        }

        private static float clamp(final float value, final float min, final float max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    public static final class Sample {
        public final double x;
        public final double y;
        public final double z;
        public final float heat;
        public final float width;
        public final long createdTick;

        private Sample(final double x, final double y, final double z, final float heat, final float width, final long createdTick) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.heat = TrailState.clamp(heat, 0.0F, 1.0F);
            this.width = width;
            this.createdTick = createdTick;
        }
    }
}
