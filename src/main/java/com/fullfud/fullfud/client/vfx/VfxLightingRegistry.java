package com.fullfud.fullfud.client.vfx;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the mod's floating point light sources.  These values are intentionally
 * not Minecraft light levels: colors and intensity remain above the 0..1
 * display range until the HDR/tone-map pass clamps them.
 *
 * <p>Two classes of source exist:</p>
 * <ul>
 *   <li><b>Permanent</b> engine lights, keyed by entity UUID.  They are always
 *   uploaded to the shader regardless of how many explosions are active, and
 *   their flicker phase depends only on time so a fast missile cannot strobe
 *   the whole scene.</li>
 *   <li><b>Dynamic</b> explosion lights.  Instead of a single flash followed by
 *   a weak stub, the fireball light follows a rise/decay envelope for its whole
 *   life, growing with the blast and then cooling into an orange afterglow.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class VfxLightingRegistry {
    public static final int MAX_LIGHTS = 48;
    public static final int SHADER_LIGHT_SLOTS = 8;

    private static final long START_NANOS = System.nanoTime();
    /** Intensity treated as "full brightness" when tinting mod particles. */
    private static final float REFERENCE_INTENSITY = 6.0F;

    private static final Map<String, Light> LIGHTS = new LinkedHashMap<>();
    private static final List<Light> SNAPSHOT = new ArrayList<>(SHADER_LIGHT_SLOTS);
    private static final Comparator<Light> LIGHT_PRIORITY = Comparator
        // Permanent engine lights always win a slot; they are the ones the
        // player tracks with their eyes and complained about disappearing.
        .comparingInt((Light light) -> light.permanent ? 1 : 0)
        .reversed()
        .thenComparingDouble((Light light) -> light.distanceScore)
        .thenComparing(Comparator.comparingDouble((Light light) -> light.intensity).reversed());
    private static long nextEphemeralId;

    private VfxLightingRegistry() {
    }

    public static void tick() {
        final Iterator<Map.Entry<String, Light>> iterator = LIGHTS.entrySet().iterator();
        while (iterator.hasNext()) {
            final Light light = iterator.next().getValue();
            light.ageTicks++;
            if (light.dynamic) {
                updateEnvelope(light);
            }
            if (light.ephemeral && light.ageTicks >= light.lifetimeTicks) {
                iterator.remove();
            }
        }
    }

    /**
     * The fireball envelope: a quick rise while the detonation light expands,
     * then an inverse-square-ish decay into a long warm afterglow.  This is what
     * makes a night explosion genuinely light up the terrain instead of one
     * single-frame flash.
     */
    private static void updateEnvelope(final Light light) {
        final float life = Math.max(1.0F, (float) light.lifetimeTicks);
        final float t = Mth.clamp((float) light.ageTicks / life, 0.0F, 1.0F);
        final float rise = Mth.clamp(t / 0.14F, 0.0F, 1.0F);
        final float decay = (float) Math.pow(1.0F - t, 1.65D);
        final float envelope = rise * decay;
        light.intensity = light.envelopePeakIntensity * Math.max(0.045F, envelope);
        light.radius = light.envelopePeakRadius * (0.32F + 0.68F * envelope);
    }

    public static void updateRocket(final UUID id, final Vec3 position, final boolean booster, final float speed) {
        if (id == null || position == null || !isFinite(position)) {
            return;
        }
        final String key = "rocket:" + id;
        Light light = LIGHTS.get(key);
        if (light == null) {
            light = new Light(key, false);
            LIGHTS.put(key, light);
        }
        light.permanent = true;
        light.ephemeral = false;
        light.dynamic = false;
        light.x = position.x;
        light.y = position.y;
        light.z = position.z;
        final float speedBoost = Mth.clamp(speed * 0.02F, 0.0F, 0.35F);
        light.radius = booster ? 46.0F + speedBoost * 16.0F : 22.0F;
        light.intensity = booster ? 6.4F : 2.35F;
        light.red = booster ? 4.1F : 1.95F;
        light.green = booster ? 1.05F : 0.52F;
        light.blue = booster ? 0.16F : 0.20F;
        // A restrained, time-only tremor.  The old implementation mixed the
        // world position into the phase, so a 3 blocks/tick missile strobed the
        // entire light field every tick.
        light.flicker = booster ? 0.045F : 0.018F;
        // No contact-shadow disc: the light sits AT the nozzle, so a dark decal
        // under it contradicts the flooded launch pad.  Real cast shadows come
        // from the screen-space shadow rays in the scene lighting pass.
        light.shadowStrength = 0.0F;
        light.shadowRadius = booster ? 6.0F : 2.6F;
        // The source itself is drawn by the emissive glare billboard; the old
        // 40-80 block volume sprite read as a flat orange wash.
        light.volumetric = false;
        light.ageTicks = 0;
        light.lifetimeTicks = Integer.MAX_VALUE;

        // Second source at the same nozzle: a short-radius, near white-hot core.
        // This is what produces the blinding launch glare and the bright spike
        // at the tip of the exhaust streak instead of a soft orange wash.
        final String glareKey = "rocketglare:" + id;
        Light glare = LIGHTS.get(glareKey);
        if (glare == null) {
            glare = new Light(glareKey, false);
            LIGHTS.put(glareKey, glare);
        }
        glare.permanent = true;
        glare.ephemeral = false;
        glare.dynamic = false;
        glare.x = position.x;
        glare.y = position.y;
        glare.z = position.z;
        glare.radius = booster ? 13.0F : 6.5F;
        glare.intensity = booster ? 17.5F : 6.5F;
        glare.red = booster ? 6.6F : 3.1F;
        glare.green = booster ? 4.35F : 1.55F;
        glare.blue = booster ? 2.45F : 0.95F;
        glare.flicker = 0.055F;
        glare.shadowStrength = 0.0F;
        glare.shadowRadius = 1.0F;
        glare.volumetric = false;
        glare.ageTicks = 0;
        glare.lifetimeTicks = Integer.MAX_VALUE;

        while (LIGHTS.size() > MAX_LIGHTS) {
            final String oldestKey = LIGHTS.keySet().iterator().next();
            if (oldestKey.equals(key) || oldestKey.equals(glareKey)) {
                break;
            }
            LIGHTS.remove(oldestKey);
        }
    }

    public static void removeRocket(final UUID id) {
        if (id != null) {
            LIGHTS.remove("rocket:" + id);
            LIGHTS.remove("rocketglare:" + id);
        }
    }

    public static long addTransientLight(
        final Vec3 position,
        final float red,
        final float green,
        final float blue,
        final float intensity,
        final float radius,
        final int lifetimeTicks
    ) {
        return addTransientLight(position, red, green, blue, intensity, radius, lifetimeTicks, false);
    }

    /**
     * @param dynamic when true the source animates through the rise/decay
     *                envelope instead of holding a constant intensity, which is
     *                what turns a one-frame explosion flash into a sustained
     *                fireball that actually illuminates the night.
     */
    public static long addTransientLight(
        final Vec3 position,
        final float red,
        final float green,
        final float blue,
        final float intensity,
        final float radius,
        final int lifetimeTicks,
        final boolean dynamic
    ) {
        if (position == null || !isFinite(position)) {
            return -1L;
        }
        final long id = ++nextEphemeralId;
        final Light light = new Light("ephemeral:" + id, true);
        light.x = position.x;
        light.y = position.y;
        light.z = position.z;
        light.red = red;
        light.green = green;
        light.blue = blue;
        light.intensity = intensity;
        light.radius = radius;
        light.shadowRadius = Math.max(1.0F, radius * 0.35F);
        light.shadowStrength = 0.92F;
        light.flicker = dynamic ? 0.06F : 0.02F;
        light.lifetimeTicks = Math.max(1, lifetimeTicks);
        light.dynamic = dynamic;
        light.envelopePeakIntensity = intensity;
        light.envelopePeakRadius = radius;
        if (dynamic) {
            updateEnvelope(light);
        }
        LIGHTS.put(light.key, light);
        while (LIGHTS.size() > MAX_LIGHTS) {
            final String oldestKey = LIGHTS.keySet().iterator().next();
            LIGHTS.remove(oldestKey);
        }
        return id;
    }

    /**
     * Ignition flood light for the launch pad.  The engine light climbs away
     * with the missile within a second or two; this source stays at the rail
     * and keeps the pad and surrounding ground lit through the booster burn,
     * then cools off with the dynamic envelope.
     */
    public static long addLaunchPadLight(final Vec3 position) {
        final long id = addTransientLight(position, 4.8F, 1.55F, 0.36F, 9.5F, 40.0F, 110, true);
        final Light light = LIGHTS.get("ephemeral:" + id);
        if (light != null) {
            light.shadowStrength = 0.0F;
            light.volumetric = false;
            light.flicker = 0.07F;
        }
        return id;
    }

    public static void remove(final long id) {
        LIGHTS.remove("ephemeral:" + id);
    }

    public static void clear() {
        LIGHTS.clear();
        SNAPSHOT.clear();
    }

    /**
     * Copies the most useful nearby sources into a reusable list.  Permanent
     * engine lights are always admitted first so a nearby volley of explosions
     * can never evict the missile the player is watching.  The caller must
     * consume the list before the next render pass.
     */
    public static List<Light> snapshot(final Vec3 cameraPosition, final int requestedSlots) {
        SNAPSHOT.clear();
        if (cameraPosition == null || requestedSlots <= 0) {
            return SNAPSHOT;
        }
        for (final Light light : LIGHTS.values()) {
            final double dx = light.x - cameraPosition.x;
            final double dy = light.y - cameraPosition.y;
            final double dz = light.z - cameraPosition.z;
            final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!light.permanent && distance > 220.0D) {
                continue;
            }
            if (light.permanent && distance > 320.0D) {
                continue;
            }
            light.distanceScore = distance - Math.min(90.0D, light.intensity * 6.0D);
            SNAPSHOT.add(light);
        }
        SNAPSHOT.sort(LIGHT_PRIORITY);
        final int limit = Math.min(SHADER_LIGHT_SLOTS, Math.min(requestedSlots, SNAPSHOT.size()));
        if (SNAPSHOT.size() > limit) {
            SNAPSHOT.subList(limit, SNAPSHOT.size()).clear();
        }
        return SNAPSHOT;
    }

    /**
     * CPU-side light sampling for the mod's own billboard particles.  Vanilla
     * particles are lit by the block lightmap and therefore never reacted to the
     * custom light field, which is why smoke stayed pitch black right behind the
     * engine at night.  This returns an additive radiance value the particle
     * renderer folds into its vertex colour.
     */
    public static void sampleRadiance(
        final double x,
        final double y,
        final double z,
        final float nightBoost,
        final float[] out
    ) {
        float red = 0.0F;
        float green = 0.0F;
        float blue = 0.0F;
        for (final Light light : LIGHTS.values()) {
            final double dx = light.x - x;
            final double dy = light.y - y;
            final double dz = light.z - z;
            final double distanceSq = dx * dx + dy * dy + dz * dz;
            final double radius = light.radius;
            if (radius <= 0.0D || distanceSq >= radius * radius) {
                continue;
            }
            final double distance = Math.sqrt(distanceSq);
            final double falloff = 1.0D - distance / radius;
            final double attenuation = falloff * falloff;
            // Chromaticity is separated from energy: the raw HDR colour above 1
            // is a scene-pass multiplier and would instantly clip a billboard to
            // flat white if used verbatim as particle tint.
            final float chromaPeak = Math.max(light.red, Math.max(light.green, light.blue));
            if (chromaPeak <= 0.0F) {
                continue;
            }
            final float energy = (float) (attenuation * nightBoost)
                * Mth.clamp(effectiveLightIntensity(light) / REFERENCE_INTENSITY, 0.0F, 1.75F);
            red += (light.red / chromaPeak) * energy;
            green += (light.green / chromaPeak) * energy;
            blue += (light.blue / chromaPeak) * energy;
        }
        // Gentle soft clip so a point-blank engine does not blow the particle to
        // a flat white disc; the tone mapping downstream stays in charge of HDR.
        out[0] = red / (1.0F + red * 0.55F);
        out[1] = green / (1.0F + green * 0.55F);
        out[2] = blue / (1.0F + blue * 0.55F);
    }

    /**
     * Stable, time-only flicker.  The phase is derived from the light key, not
     * from its world position, so a fast moving missile keeps a steady flame
     * instead of strobing every tick as its coordinates change.
     */
    public static float effectiveLightIntensity(final Light light) {
        if (light == null) {
            return 0.0F;
        }
        if (light.flicker <= 0.0F) {
            return light.intensity;
        }
        final float time = (System.nanoTime() - START_NANOS) * 0.000000001F;
        final float phase = (light.key.hashCode() & 0x3FF) * 0.0061F;
        final float primary = (float) Math.sin(time * 11.0F + phase);
        final float secondary = (float) Math.sin(time * 23.0F + phase * 2.7F);
        final float amount = Mth.clamp(light.flicker, 0.0F, 0.35F);
        return Math.max(0.0F, light.intensity * (1.0F + amount * (primary * 0.6F + secondary * 0.4F)));
    }

    public static int size() {
        return LIGHTS.size();
    }

    public static int activeExplosionLightCount() {
        int count = 0;
        for (final Light light : LIGHTS.values()) {
            if (light.ephemeral) {
                count++;
            }
        }
        return count;
    }

    private static boolean isFinite(final Vec3 value) {
        return value != null
            && Double.isFinite(value.x)
            && Double.isFinite(value.y)
            && Double.isFinite(value.z);
    }

    public static final class Light {
        public final String key;
        public double x;
        public double y;
        public double z;
        public float red;
        public float green;
        public float blue;
        public float intensity;
        public float radius;
        public float flicker;
        public float shadowStrength;
        public float shadowRadius;
        public int ageTicks;
        public int lifetimeTicks;
        public boolean ephemeral;
        public boolean permanent;
        public boolean dynamic;
        /** Whether the soft volume billboard is drawn for this source. */
        public boolean volumetric = true;
        public float envelopePeakIntensity;
        public float envelopePeakRadius;
        public double distanceScore;

        private Light(final String key, final boolean ephemeral) {
            this.key = key;
            this.ephemeral = ephemeral;
            this.lifetimeTicks = ephemeral ? 20 : Integer.MAX_VALUE;
        }
    }
}
