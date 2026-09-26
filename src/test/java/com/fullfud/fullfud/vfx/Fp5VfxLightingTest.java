package com.fullfud.fullfud.vfx;

import com.fullfud.fullfud.client.particle.DroneParticleManager;
import com.fullfud.fullfud.client.vfx.VfxLightingRegistry;
import com.fullfud.fullfud.core.network.packet.DroneExplosionPacket;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Regression suite for the custom VFX lighting contract.
 *
 * <p>These tests exist because the reported defects were all silent: the
 * exhaust plume was skipped whenever the shaders loaded, the engine light
 * flicker phase was tied to world coordinates (so a fast missile strobed the
 * scene), and the smoke never received any custom light at all.  Each test
 * pins one of those behaviours down.</p>
 */
@Fp5TestSuite(name = "FP-5 Custom VFX Lighting & Particle Illumination", features = {"F8", "F11"}, milestone = "M3")
public class Fp5VfxLightingTest {

    @Fp5Test(tier = 1, features = {"F8"}, description = "Engine light is permanent, bounded flicker and never evicted by explosion lights")
    public void testEngineLightPriorityAndStability() {
        VfxLightingRegistry.clear();
        final UUID rocket = UUID.randomUUID();
        VfxLightingRegistry.updateRocket(rocket, new Vec3(0.0D, 64.0D, 0.0D), true, 3.0F);

        // A nearby volley of explosions must not push the tracked missile out of
        // the shader slots; that was the "lighting flickers in and out" report.
        for (int i = 0; i < 12; i++) {
            VfxLightingRegistry.addTransientLight(
                new Vec3(1.0D + i, 64.0D, 0.0D), 5.1F, 1.25F, 0.2F, 8.0F, 68.0F, 40, true
            );
        }

        final List<VfxLightingRegistry.Light> lights = VfxLightingRegistry.snapshot(
            new Vec3(0.0D, 64.0D, 0.0D), 4
        );
        boolean rocketPresent = false;
        for (final VfxLightingRegistry.Light light : lights) {
            if (light.key.equals("rocket:" + rocket)) {
                rocketPresent = true;
                // Position may not influence flicker: a missile moving 3
                // blocks/tick used to strobe the entire field.
                Fp5Assertions.assertTrue(light.flicker <= 0.06F,
                    "Engine flicker must stay a restrained tremor (actual: " + light.flicker + ")");
            }
        }
        Fp5Assertions.assertTrue(rocketPresent,
            "Permanent engine light must survive snapshot truncation next to 12 explosion lights");
        Fp5Assertions.assertEquals(4, lights.size(), "Snapshot must still respect the requested slot budget");
        VfxLightingRegistry.clear();
    }

    @Fp5Test(tier = 1, features = {"F8"}, description = "Booster ignition registers a blinding short-radius glare core next to the wide engine light")
    public void testEngineGlareCoreRegistered() {
        VfxLightingRegistry.clear();
        final UUID rocket = UUID.randomUUID();
        VfxLightingRegistry.updateRocket(rocket, new Vec3(0.0D, 64.0D, 0.0D), true, 3.0F);

        VfxLightingRegistry.Light engine = null;
        VfxLightingRegistry.Light glare = null;
        for (final VfxLightingRegistry.Light light : VfxLightingRegistry.snapshot(new Vec3(0.0D, 64.0D, 0.0D), 8)) {
            if (light.key.equals("rocket:" + rocket)) {
                engine = light;
            } else if (light.key.equals("rocketglare:" + rocket)) {
                glare = light;
            }
        }
        Fp5Assertions.assertNotNull(engine, "Wide engine light must be registered");
        Fp5Assertions.assertNotNull(glare, "Short-radius glare core must be registered alongside it");
        Fp5Assertions.assertTrue(glare.radius < engine.radius,
            "Glare core must be tighter than the wide engine light (glare=" + glare.radius + ", engine=" + engine.radius + ")");
        Fp5Assertions.assertTrue(glare.intensity > engine.intensity,
            "Glare core must be brighter than the wide engine light so the launch reads as blinding (glare="
                + glare.intensity + ", engine=" + engine.intensity + ")");
        VfxLightingRegistry.removeRocket(rocket);
        Fp5Assertions.assertEquals(0, VfxLightingRegistry.size(),
            "removeRocket must clean up both engine and glare lights");
        VfxLightingRegistry.clear();
    }

    @Fp5Test(tier = 1, features = {"F11"}, description = "Explosion light follows a dynamic rise/decay envelope instead of a single flash")
    public void testExplosionLightEnvelope() {
        VfxLightingRegistry.clear();
        final long id = VfxLightingRegistry.addTransientLight(
            new Vec3(0.0D, 64.0D, 0.0D), 5.1F, 1.25F, 0.2F, 7.4F, 68.0F, 20, true
        );
        Fp5Assertions.assertTrue(id > 0L, "Dynamic explosion light must register with a valid id");

        final VfxLightingRegistry.Light light = findLight("ephemeral:" + id);
        Fp5Assertions.assertNotNull(light, "Registered dynamic light must be retrievable");
        final float birth = light.intensity;

        for (int i = 0; i < 3; i++) {
            VfxLightingRegistry.tick();
        }
        final float peak = light.intensity;
        Fp5Assertions.assertTrue(peak > birth,
            "Dynamic light must brighten after ignition (birth=" + birth + ", peak=" + peak + ")");

        for (int i = 0; i < 13; i++) {
            VfxLightingRegistry.tick();
        }
        final float decayed = light.intensity;
        Fp5Assertions.assertTrue(decayed < peak,
            "Dynamic light must decay after the fireball collapses (peak=" + peak + ", now=" + decayed + ")");
        Fp5Assertions.assertTrue(decayed > 0.0F,
            "Afterglow must stay above zero while the source is alive");
        VfxLightingRegistry.clear();
    }

    @Fp5Test(tier = 1, features = {"F8"}, description = "Particle radiance sampling reacts to distance, night boost and out-of-range rejection")
    public void testParticleRadianceSampling() {
        VfxLightingRegistry.clear();
        final float[] out = new float[3];
        VfxLightingRegistry.updateRocket(UUID.randomUUID(), Vec3.ZERO, true, 3.0F);

        VfxLightingRegistry.sampleRadiance(1.0D, 0.0D, 0.0D, 1.0F, out);
        final float nearDay = out[0];
        Fp5Assertions.assertTrue(nearDay > 0.25F,
            "Smoke a block from the nozzle must pick up engine light (actual: " + nearDay + ")");

        VfxLightingRegistry.sampleRadiance(1.0D, 0.0D, 0.0D, 2.85F, out);
        final float nearNight = out[0];
        Fp5Assertions.assertTrue(nearNight >= nearDay,
            "Night boost must never reduce sampled radiance (day=" + nearDay + ", night=" + nearNight + ")");

        VfxLightingRegistry.sampleRadiance(400.0D, 0.0D, 0.0D, 2.85F, out);
        Fp5Assertions.assertEquals(0.0F, out[0], 1.0E-4F,
            "Radiance outside every light radius must be exactly zero");
        VfxLightingRegistry.clear();
    }

    @Fp5Test(tier = 2, features = {"F8"}, description = "Particles default to environment-lit so engine and explosion light reaches the smoke")
    public void testParticleDefaultsToEnvironmentLit() {
        final DroneParticleManager.ExplosionParticle particle = new DroneParticleManager.ExplosionParticle(
            0.0D, 0.0D, 0.0D,
            0.0D, 0.0D, 0.0D,
            1.0F, 2.0F,
            0.5F, 0.0F,
            0.3F, 0.3F, 0.3F,
            0.0F, 0.0F,
            0, 20,
            0.0F, 0.98F,
            false, false,
            DroneParticleManager.ParticleOrientation.BILLBOARD,
            DroneParticleManager.BlendMode.ALPHA,
            null, null,
            1, 0.0F, 0.0F, 0.0F
        );
        Fp5Assertions.assertEquals(1.0F, particle.litFactor, 1.0E-4F,
            "Smoke/dust particles must default to fully custom-light reactive");
    }

    @Fp5Test(tier = 1, features = {"F8"}, description = "Nozzle-anchored exhaust emitter exists and is the always-on particle path")
    public void testNozzleAnchoredExhaustEmitterExists() throws Exception {
        final Method emitter = DroneParticleManager.class.getDeclaredMethod(
            "spawnFlamingoExhaustFromNozzle",
            Vec3.class, Vec3.class, Vec3.class, boolean.class, float.class
        );
        Fp5Assertions.assertTrue(java.lang.reflect.Modifier.isPublic(emitter.getModifiers()),
            "Nozzle-anchored exhaust emitter must be public so the client VFX tick can drive it unconditionally");
        Fp5Assertions.assertTrue(java.lang.reflect.Modifier.isStatic(emitter.getModifiers()),
            "Nozzle-anchored exhaust emitter must be static");
    }

    @Fp5Test(tier = 2, features = {"F11"}, description = "Heavy warhead profile keeps a dense smoke, fire and dust budget")
    public void testHeavyProfileDensityBudget() {
        final DroneParticleManager.ExplosionVisualProfile heavy = DroneParticleManager.resolveExplosionVisualProfile(
            DroneExplosionPacket.TYPE_FLAMINGO, 8.0F
        );
        Fp5Assertions.assertNotNull(heavy, "FP-5 profile must resolve");
        Fp5Assertions.assertTrue(heavy.smokeParticles() >= 150,
            "Heavy smoke budget must be dense enough to read as a real cloud (actual: " + heavy.smokeParticles() + ")");
        Fp5Assertions.assertTrue(heavy.fireParticles() >= 120,
            "Heavy fire budget must sustain the fireball-to-smoke transition (actual: " + heavy.fireParticles() + ")");
        Fp5Assertions.assertTrue(heavy.dustParticles() >= 90,
            "Heavy ground dust skirt must scale with the blast (actual: " + heavy.dustParticles() + ")");
        Fp5Assertions.assertEquals(110.0F, heavy.haloRadius(), 0.25F,
            "Heavy halo must keep the 110 m visual contract");
    }

    private static VfxLightingRegistry.Light findLight(final String key) {
        final List<VfxLightingRegistry.Light> lights = VfxLightingRegistry.snapshot(Vec3.ZERO, VfxLightingRegistry.SHADER_LIGHT_SLOTS);
        for (final VfxLightingRegistry.Light light : lights) {
            if (light.key.equals(key)) {
                return light;
            }
        }
        return null;
    }
}
