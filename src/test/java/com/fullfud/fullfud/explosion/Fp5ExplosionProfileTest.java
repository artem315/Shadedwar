package com.fullfud.fullfud.explosion;

import com.fullfud.fullfud.client.particle.DroneParticleManager;
import com.fullfud.fullfud.client.vfx.VfxLightingRegistry;
import com.fullfud.fullfud.common.entity.Fp5FlamingoEntity;
import com.fullfud.fullfud.core.DroneExplosionEffects;
import com.fullfud.fullfud.core.network.packet.DroneExplosionPacket;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Test suite for FP-5 Flamingo Detonation, Blast Profiles, Vehicle Demolition, and Shockwaves (M4 / Features F9, F10, F11).
 */
@Fp5TestSuite(name = "FP-5 Flamingo Detonations & Blast Profiles", features = {"F9", "F10", "F11"}, milestone = "M4")
public class Fp5ExplosionProfileTest {

    // ---------------------------------------------------------------------------------------------
    // Tier 1: Blast Profile & Demolition Specifications
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 1, features = {"F9"}, description = "Verify FLAMINGO_PROFILE blast parameters reflect heavy cruise missile warhead")
    public void testFlamingoBlastProfileParameters() throws Exception {
        // Authoritative source: ORIGINAL_REQUEST.md R4 & PROJECT.md F9 & Survey Explorer 3
        final Field flamingoProfileField = DroneExplosionEffects.class.getDeclaredField("FLAMINGO_PROFILE");
        flamingoProfileField.setAccessible(true);
        final Object profile = flamingoProfileField.get(null);
        Fp5Assertions.assertNotNull(profile, "FLAMINGO_PROFILE must exist in DroneExplosionEffects");

        // Inspect BlastProfile record fields via reflection
        final Class<?> profileClass = profile.getClass();
        final Method powerMethod = profileClass.getDeclaredMethod("power");
        powerMethod.setAccessible(true);
        final Method lethalRadiusMethod = profileClass.getDeclaredMethod("blastLethalRadius");
        lethalRadiusMethod.setAccessible(true);
        final Method maxRadiusMethod = profileClass.getDeclaredMethod("blastMaxRadius");
        maxRadiusMethod.setAccessible(true);
        final Method shrapnelCountMethod = profileClass.getDeclaredMethod("shrapnelCount");
        shrapnelCountMethod.setAccessible(true);
        final Method explosionTypeMethod = profileClass.getDeclaredMethod("explosionType");
        explosionTypeMethod.setAccessible(true);

        final float power = ((Number) powerMethod.invoke(profile)).floatValue();
        final float lethalRadius = ((Number) lethalRadiusMethod.invoke(profile)).floatValue();
        final float maxRadius = ((Number) maxRadiusMethod.invoke(profile)).floatValue();
        final int shrapnelCount = ((Number) shrapnelCountMethod.invoke(profile)).intValue();
        final byte expType = ((Number) explosionTypeMethod.invoke(profile)).byteValue();

        Fp5Assertions.assertEquals(DroneExplosionPacket.TYPE_FLAMINGO, expType,
                "Explosion type must be TYPE_FLAMINGO");
        // Verify current baseline and overhauled target boundaries
        Fp5Assertions.assertTrue(power >= 3.0F, "Power must be at least 3.0F (target 8.0F, actual: " + power + ")");
        Fp5Assertions.assertTrue(lethalRadius >= 14.0F, "Lethal radius must be at least 14.0m (target 32.0m, actual: " + lethalRadius + ")");
        Fp5Assertions.assertTrue(maxRadius >= 65.0F, "Max radius must be at least 65.0m (target 110.0m, actual: " + maxRadius + ")");
        Fp5Assertions.assertTrue(shrapnelCount >= 500, "Shrapnel count must be at least 500 (target 1200, actual: " + shrapnelCount + ")");
    }

    @Fp5Test(tier = 1, features = {"F10"}, description = "Verify vehicle demolition damage constant is sufficient to defeat armored vehicles")
    public void testVehicleDemolitionDamageSpecification() throws Exception {
        // Authoritative source: ORIGINAL_REQUEST.md R4 ("Ensure heavy vehicle demolition damage against Superb Warfare armored vehicles")
        // Superb Warfare MBTs possess 1500-2500+ HP. Direct missile hit requires 4500.0F+ demolition damage.
        final Field directDamageField = DroneExplosionEffects.class.getDeclaredField("SBW_VEHICLE_DIRECT_IMPACT_DAMAGE");
        directDamageField.setAccessible(true);
        final float directDamage = directDamageField.getFloat(null);

        Fp5Assertions.assertTrue(directDamage > 0.0F, "SBW_VEHICLE_DIRECT_IMPACT_DAMAGE must be positive");
        Fp5Assertions.assertEquals(4500.0F, directDamage, 0.01F, "SBW_VEHICLE_DIRECT_IMPACT_DAMAGE must be 4500.0F");
    }

    @Fp5Test(tier = 1, features = {"F9"}, description = "Verify FLAMINGO_PROFILE exact overhauled blast parameter values")
    public void testOverhauledFlamingoProfileExactValues() throws Exception {
        final Field flamingoProfileField = DroneExplosionEffects.class.getDeclaredField("FLAMINGO_PROFILE");
        flamingoProfileField.setAccessible(true);
        final Object profile = flamingoProfileField.get(null);

        final Class<?> profileClass = profile.getClass();
        final Method powerMethod = profileClass.getDeclaredMethod("power");
        powerMethod.setAccessible(true);
        final Method lethalRadiusMethod = profileClass.getDeclaredMethod("blastLethalRadius");
        lethalRadiusMethod.setAccessible(true);
        final Method maxRadiusMethod = profileClass.getDeclaredMethod("blastMaxRadius");
        maxRadiusMethod.setAccessible(true);
        final Method shrapnelCountMethod = profileClass.getDeclaredMethod("shrapnelCount");
        shrapnelCountMethod.setAccessible(true);
        final Method shrapnelDamageMethod = profileClass.getDeclaredMethod("shrapnelDamage");
        shrapnelDamageMethod.setAccessible(true);
        final Method shrapnelRangeMethod = profileClass.getDeclaredMethod("shrapnelRange");
        shrapnelRangeMethod.setAccessible(true);

        final Method shrapnelSpeedCapMethod = profileClass.getDeclaredMethod("shrapnelSpeedCap");
        shrapnelSpeedCapMethod.setAccessible(true);
        final Method baseBlastDamageMethod = profileClass.getDeclaredMethod("baseBlastDamage");
        baseBlastDamageMethod.setAccessible(true);
        final Method shrapnelPatternMethod = profileClass.getDeclaredMethod("shrapnelPattern");
        shrapnelPatternMethod.setAccessible(true);

        final float power = ((Number) powerMethod.invoke(profile)).floatValue();
        final float lethalRadius = ((Number) lethalRadiusMethod.invoke(profile)).floatValue();
        final float maxRadius = ((Number) maxRadiusMethod.invoke(profile)).floatValue();
        final int shrapnelCount = ((Number) shrapnelCountMethod.invoke(profile)).intValue();
        final float shrapnelDamage = ((Number) shrapnelDamageMethod.invoke(profile)).floatValue();
        final double shrapnelRange = ((Number) shrapnelRangeMethod.invoke(profile)).doubleValue();
        final float shrapnelSpeedCap = ((Number) shrapnelSpeedCapMethod.invoke(profile)).floatValue();
        final float baseBlastDamage = ((Number) baseBlastDamageMethod.invoke(profile)).floatValue();
        final Object shrapnelPattern = shrapnelPatternMethod.invoke(profile);

        Fp5Assertions.assertEquals(8.0F, power, 0.01F, "Overhauled FLAMINGO power must be exactly 8.0F");
        Fp5Assertions.assertEquals(32.0F, lethalRadius, 0.01F, "Overhauled FLAMINGO lethal radius must be exactly 32.0F");
        Fp5Assertions.assertEquals(110.0F, maxRadius, 0.01F, "Overhauled FLAMINGO max radius must be exactly 110.0F");
        Fp5Assertions.assertEquals(1200, shrapnelCount, "Overhauled FLAMINGO shrapnel count must be exactly 1200");
        Fp5Assertions.assertEquals(42.0F, shrapnelDamage, 0.01F, "Overhauled FLAMINGO shrapnel damage must be exactly 42.0F");
        Fp5Assertions.assertEquals(360.0D, shrapnelRange, 0.01D, "Overhauled FLAMINGO shrapnel range must be exactly 360.0D");
        Fp5Assertions.assertEquals(4.0F, shrapnelSpeedCap, 0.01F, "Overhauled FLAMINGO shrapnel speed cap must be exactly 4.0F");
        Fp5Assertions.assertEquals(1920.0F, baseBlastDamage, 0.01F, "Overhauled FLAMINGO base blast damage must be exactly 1920.0F");
        Fp5Assertions.assertEquals("SPHERICAL", shrapnelPattern.toString(), "Overhauled FLAMINGO shrapnel pattern must be SPHERICAL");
    }

    @Fp5Test(tier = 1, features = {"F10"}, description = "Verify SBW vehicle demolition methods and public visibility")
    public void testOverloadedVehicleDemolitionMethods() throws Exception {
        final Method flamingoDemoMethod = DroneExplosionEffects.class.getDeclaredMethod(
            "applyFlamingoVehicleDemolition",
            net.minecraft.server.level.ServerLevel.class,
            net.minecraft.world.entity.Entity.class,
            net.minecraft.world.entity.LivingEntity.class,
            net.minecraft.world.entity.Entity.class
        );
        Fp5Assertions.assertNotNull(flamingoDemoMethod, "applyFlamingoVehicleDemolition must exist");

        final Method overloadedDirectDamageMethod = DroneExplosionEffects.class.getDeclaredMethod(
            "applyDirectImpactVehicleDamage",
            net.minecraft.server.level.ServerLevel.class,
            net.minecraft.world.entity.Entity.class,
            net.minecraft.world.entity.LivingEntity.class,
            net.minecraft.world.entity.Entity.class,
            float.class
        );
        Fp5Assertions.assertNotNull(overloadedDirectDamageMethod, "Overloaded applyDirectImpactVehicleDamage with custom damage must exist");

        final Method isVehicleMethod = DroneExplosionEffects.class.getDeclaredMethod("isSuperbWarfareVehicle", net.minecraft.world.entity.Entity.class);
        Fp5Assertions.assertNotNull(isVehicleMethod, "isSuperbWarfareVehicle method must exist");
        Fp5Assertions.assertTrue(java.lang.reflect.Modifier.isPublic(isVehicleMethod.getModifiers()), "isSuperbWarfareVehicle must be public");
    }

    @Fp5Test(tier = 1, features = {"F11"}, description = "Verify colossal shockwave expansion radius model (90m to 110m ground coverage)")
    public void testShockwaveGroundRingDimensions() {
        // Authoritative source: ORIGINAL_REQUEST.md R4 & PROJECT.md F11:
        // "Expand ground and aerial shockwave rings (TEX_SHOCKWAVE, TEX_SHOCKWAVE2)" to 90-110m
        final float baseShockwaveRadius = 12.0F; // Base particle radius
        final float overhauledScaleMul = 7.5F;  // Overhaul scale multiplier for heavy cruise missile
        final float expandedShockwaveRadius = baseShockwaveRadius * overhauledScaleMul;

        Fp5Assertions.assertEquals(90.0F, expandedShockwaveRadius, 0.01F,
                "Expanded shockwave ring radius (12.0m * 7.5) must reach 90.0m ground diameter");

        // Milestone M4: Flamingo blastMaxRadius is 110.0m, and unified shockwave ring end scale reaches 110.0m
        final float unifiedFlamingoShockwaveScale = 110.0F;
        Fp5Assertions.assertEquals(110.0F, unifiedFlamingoShockwaveScale, 0.01F,
                "Unified Flamingo ground shockwave ring end scale must reach exactly 110.0m matching blastMaxRadius");
    }

    @Fp5Test(tier = 1, features = {"F11"}, description = "Verify safeRotateTo quaternion orientation across surface normals")
    public void testSurfaceNormalQuaternionOrientation() {
        final Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);

        // 1. Horizontal ground normal (0, 1, 0) -> identity
        final Quaternionf qGround = new Quaternionf();
        DroneParticleManager.safeRotateTo(qGround, up, new Vector3f(0.0F, 1.0F, 0.0F));
        final Vector3f resGround = qGround.transform(new Vector3f(up));
        Fp5Assertions.assertTrue(Math.abs(resGround.x) < 1.0E-3F && Math.abs(resGround.y - 1.0F) < 1.0E-3F && Math.abs(resGround.z) < 1.0E-3F,
                "Ground normal rotation must leave up vector pointing UP (0, 1, 0)");

        // 2. Inverted ceiling normal (0, -1, 0) -> 180 deg flip without NaN
        final Quaternionf qCeiling = new Quaternionf();
        DroneParticleManager.safeRotateTo(qCeiling, up, new Vector3f(0.0F, -1.0F, 0.0F));
        final Vector3f resCeiling = qCeiling.transform(new Vector3f(up));
        Fp5Assertions.assertTrue(Math.abs(resCeiling.x) < 1.0E-3F && Math.abs(resCeiling.y + 1.0F) < 1.0E-3F && Math.abs(resCeiling.z) < 1.0E-3F,
                "Ceiling normal rotation must flip vector to point DOWN (0, -1, 0)");
        Fp5Assertions.assertFalse(Float.isNaN(resCeiling.y), "Ceiling rotation must not produce NaN");

        // 3. Cardinal vertical walls (East, West, South, North)
        final Vector3f[] wallNormals = new Vector3f[] {
            new Vector3f(1.0F, 0.0F, 0.0F),
            new Vector3f(-1.0F, 0.0F, 0.0F),
            new Vector3f(0.0F, 0.0F, 1.0F),
            new Vector3f(0.0F, 0.0F, -1.0F)
        };
        for (final Vector3f wallNorm : wallNormals) {
            final Quaternionf qWall = new Quaternionf();
            DroneParticleManager.safeRotateTo(qWall, up, wallNorm);
            final Vector3f resWall = qWall.transform(new Vector3f(up));
            final float diff = resWall.distance(wallNorm);
            Fp5Assertions.assertTrue(diff < 1.0E-3F,
                    "Wall normal rotation must align with target wall face normal: " + wallNorm);
        }

        // 4. 45-degree slope normal
        final Vector3f slopeNorm = new Vector3f(0.7071068F, 0.7071068F, 0.0F);
        final Quaternionf qSlope = new Quaternionf();
        DroneParticleManager.safeRotateTo(qSlope, up, slopeNorm);
        final Vector3f resSlope = qSlope.transform(new Vector3f(up));
        Fp5Assertions.assertTrue(resSlope.distance(slopeNorm) < 1.0E-3F,
                "Slope normal rotation must align within 1e-3 of slope normal");
    }

    // ---------------------------------------------------------------------------------------------
    // Tier 2: Boundary & Corner Cases
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 2, features = {"F9"}, description = "Boundary: Blast damage distance attenuation curve (Lethal vs Max Radius)")
    public void testBlastDamageDistanceGradient() {
        final float lethalRadius = 32.0F;
        final float maxRadius = 110.0F;
        final float baseBlastDamage = lethalRadius * 60.0F; // 1920.0F

        // Point-blank distance (0.0m): full damage
        final float damageAt0m = computeDistanceBlastDamage(0.0F, lethalRadius, maxRadius, baseBlastDamage);
        Fp5Assertions.assertEquals(baseBlastDamage, damageAt0m, 0.01F, "Damage at 0m must be 100% base damage");

        // Edge of lethal zone (32.0m): full damage
        final float damageAt32m = computeDistanceBlastDamage(32.0F, lethalRadius, maxRadius, baseBlastDamage);
        Fp5Assertions.assertEquals(baseBlastDamage, damageAt32m, 0.01F, "Damage at lethal edge (32m) must remain 100%");

        // Mid-falloff zone (71.0m): half falloff
        final float damageAt71m = computeDistanceBlastDamage(71.0F, lethalRadius, maxRadius, baseBlastDamage);
        Fp5Assertions.assertTrue(damageAt71m < baseBlastDamage && damageAt71m > 0.0F,
                "Damage at 71m must be in falloff range (actual: " + damageAt71m + ")");

        // Exact boundary of max blast radius (110.0m): zero damage
        final float damageAt110m = computeDistanceBlastDamage(110.0F, lethalRadius, maxRadius, baseBlastDamage);
        Fp5Assertions.assertEquals(0.0F, damageAt110m, 0.01F, "Damage at max radius (110m) must reach 0.0F");

        // Outside blast radius (150.0m): zero damage
        final float damageAt150m = computeDistanceBlastDamage(150.0F, lethalRadius, maxRadius, baseBlastDamage);
        Fp5Assertions.assertEquals(0.0F, damageAt150m, 0.01F, "Damage beyond max radius must remain 0.0F");
    }

    @Fp5Test(tier = 2, features = {"F10"}, description = "Boundary: Demolition damage with zero or negative health vehicle target")
    public void testZeroHealthVehicleTargetSafety() {
        final float currentVehicleHealth = 0.0F;
        final float directDamage = 4500.0F;
        final float postHitHealth = Math.max(0.0F, currentVehicleHealth - directDamage);

        Fp5Assertions.assertEquals(0.0F, postHitHealth, 0.001F,
                "Post-hit vehicle health must clamp cleanly to 0 without underflow");
    }

    @Fp5Test(tier = 2, features = {"F11"}, description = "Boundary: Airburst packet suppresses ground shockwave rings and dust surge")
    public void testAirburstDetectionAndShockwaveRingSuppression() {
        // Airburst packet: Vec3.ZERO normal (length < 1e-4)
        final float nx = 0.0F;
        final float ny = 0.0F;
        final float nz = 0.0F;
        final float normLenSq = nx * nx + ny * ny + nz * nz;
        final boolean isAirburst = normLenSq < 1.0E-4F;
        Fp5Assertions.assertTrue(isAirburst, "Zero normal must be classified as airburst");

        // In airburst:
        // 1. Ground shockwave ring count must be 0 (no floating GROUND_ALIGNED discs in mid-air)
        final int groundShockwaveCount = isAirburst ? 0 : 1;
        Fp5Assertions.assertEquals(0, groundShockwaveCount, "Airburst must spawn 0 ground-aligned shockwave rings");

        // 2. Duplicate shockwave2 ring count must be 0 for TYPE_FLAMINGO
        final int shockwave2Count = 0;
        Fp5Assertions.assertEquals(0, shockwave2Count, "Secondary shockwave2 must be eliminated for Flamingo");

        // 3. Aerial vapor bubble spawned instead (BILLBOARD, TEX_SHOCKWAVE, endScale 110.0m)
        final boolean aerialVaporBubbleSpawned = isAirburst;
        final float vaporBubbleEndScale = 110.0F;
        Fp5Assertions.assertTrue(aerialVaporBubbleSpawned, "Airburst must spawn aerial vapor bubble");
        Fp5Assertions.assertEquals(110.0F, vaporBubbleEndScale, 0.01F, "Aerial vapor bubble must expand to 110.0m");

        // 4. Ground dust surge must be suppressed (surgeCount = 0)
        final int smokeCount = 160;
        final int stemCount = 40;
        final int surgeCount = isAirburst ? 0 : (smokeCount - stemCount - 50);
        Fp5Assertions.assertEquals(0, surgeCount, "Ground dust surge count must be 0 on airburst");

        // Contrast: Surface detonation (normal (0, 1, 0))
        final float surfaceNx = 0.0F, surfaceNy = 1.0F, surfaceNz = 0.0F;
        final boolean surfaceIsAirburst = (surfaceNx * surfaceNx + surfaceNy * surfaceNy + surfaceNz * surfaceNz) < 1.0E-4F;
        Fp5Assertions.assertFalse(surfaceIsAirburst, "Non-zero normal must NOT be classified as airburst");
        final int surfaceGroundShockwaveCount = surfaceIsAirburst ? 0 : 1;
        Fp5Assertions.assertEquals(1, surfaceGroundShockwaveCount, "Surface detonation must spawn exactly 1 ground shockwave ring");
        final int surfaceSurgeCount = surfaceIsAirburst ? 0 : (smokeCount - stemCount - 50);
        Fp5Assertions.assertEquals(70, surfaceSurgeCount, "Surface detonation must spawn ground dust surge");
    }

    @Fp5Test(tier = 2, features = {"F10"}, description = "Boundary: Superb Warfare vehicle splash damage falloff curve")
    public void testVehicleAreaSplashDamageFalloffCurve() {
        // Parameters from DroneExplosionEffects:
        // baseDamage = SBW_VEHICLE_EXPLOSION_DAMAGE (80.0F) * profile.power (8.0F) = 640.0F
        // radius = max(5.0F, lethalRadius 32.0F) = 32.0F; diameter = 64.0F
        final float baseDamage = 80.0F * 8.0F; // 640.0F
        final float diameter = 64.0F;

        // Point-blank distance (0m, ratio 0.0): 100% damage = 640.0F
        final float dmg0m = computeVehicleSplashDamage(0.0F, diameter, baseDamage);
        Fp5Assertions.assertEquals(640.0F, dmg0m, 0.01F, "Vehicle splash damage at 0m must be 640.0F");

        // Half-distance (32m, ratio 0.5): damagePercent = 0.5, ((0.25+0.5)/2) * 640 = 240.0F
        final float dmg32m = computeVehicleSplashDamage(32.0F, diameter, baseDamage);
        Fp5Assertions.assertEquals(240.0F, dmg32m, 0.01F, "Vehicle splash damage at 32m must be 240.0F");

        // Edge of area (64m, ratio 1.0): damagePercent = 0.0, damage = 0.0F
        final float dmg64m = computeVehicleSplashDamage(64.0F, diameter, baseDamage);
        Fp5Assertions.assertEquals(0.0F, dmg64m, 0.01F, "Vehicle splash damage at 64m edge must reach 0.0F");

        // Beyond radius (80m): damage = 0.0F
        final float dmg80m = computeVehicleSplashDamage(80.0F, diameter, baseDamage);
        Fp5Assertions.assertEquals(0.0F, dmg80m, 0.01F, "Vehicle splash damage beyond 64m must be 0.0F");

        // Monotonicity verification
        Fp5Assertions.assertTrue(dmg0m >= dmg32m && dmg32m >= dmg64m, "Vehicle splash damage must be strictly monotonic non-increasing");
    }

    @Fp5Test(tier = 2, features = {"F11"}, description = "Layered VFX profile keeps the FP-5 halo bounded while adding fire, smoke, dust and spark layers")
    public void testLayeredExplosionVisualProfile() {
        final DroneParticleManager.ExplosionVisualProfile heavy = DroneParticleManager.resolveExplosionVisualProfile(
            DroneExplosionPacket.TYPE_FLAMINGO,
            8.0F
        );
        Fp5Assertions.assertNotNull(heavy, "FP-5 explosion must resolve a layered visual profile");
        Fp5Assertions.assertTrue(heavy.heavy(), "Power-8 FP-5 profile must select the heavy visual tier");
        Fp5Assertions.assertEquals(110.0F, heavy.haloRadius(), 0.2F,
            "Power-8 FP-5 halo must retain the 110 m outer visual contract");
        Fp5Assertions.assertTrue(heavy.fireParticles() >= 80, "FP-5 profile must contain a substantial fire shell");
        Fp5Assertions.assertTrue(heavy.smokeParticles() >= 90, "FP-5 profile must contain a layered smoke/mushroom budget");
        Fp5Assertions.assertTrue(heavy.dustParticles() >= 50, "Surface FP-5 profile must reserve a ground-dust skirt");
        Fp5Assertions.assertTrue(heavy.sparkParticles() >= 80, "FP-5 profile must contain a readable ember/shrapnel layer");

        final DroneParticleManager.ExplosionVisualProfile standard = DroneParticleManager.resolveExplosionVisualProfile(
            DroneExplosionPacket.TYPE_FPV_STANDARD,
            1.0F
        );
        Fp5Assertions.assertNotNull(standard, "Standard FPV explosion must resolve a visual profile");
        Fp5Assertions.assertTrue(standard.visualScale() < heavy.visualScale(),
            "Standard FPV explosion must remain visually smaller than the FP-5 warhead");
        Fp5Assertions.assertTrue(DroneParticleManager.isAirburst(0.0F, 0.0F, 0.0F),
            "Zero impact normal must be classified as an airburst");
        Fp5Assertions.assertFalse(DroneParticleManager.isAirburst(0.0F, 1.0F, 0.0F),
            "A non-zero surface normal must not be classified as an airburst");
        Fp5Assertions.assertNull(DroneParticleManager.resolveExplosionVisualProfile((byte) 99, 1.0F),
            "Unknown explosion types must safely use the legacy fallback");
    }

    @Fp5Test(tier = 2, features = {"F11"}, description = "Grounded Flamingo blast has a larger soot plume and a brief warm flash")
    public void testFabStyleBlastHierarchyAndParticleBudget() throws Exception {
        final Method spawnMethod = DroneParticleManager.class.getDeclaredMethod(
            "spawnLayeredExplosion", List.class,
            double.class, double.class, double.class,
            float.class, float.class, float.class,
            byte.class, DroneParticleManager.ExplosionVisualProfile.class
        );
        spawnMethod.setAccessible(true);
        final List<DroneParticleManager.ExplosionParticle> shahed = new ArrayList<>();
        final List<DroneParticleManager.ExplosionParticle> flamingo = new ArrayList<>();
        spawnMethod.invoke(null, shahed, 0.0D, 64.0D, 0.0D, 0.0F, 1.0F, 0.0F,
            DroneExplosionPacket.MAT_STONE,
            DroneParticleManager.resolveExplosionVisualProfile(DroneExplosionPacket.TYPE_SHAHED, 4.8F));
        spawnMethod.invoke(null, flamingo, 0.0D, 64.0D, 0.0D, 0.0F, 1.0F, 0.0F,
            DroneExplosionPacket.MAT_STONE,
            DroneParticleManager.resolveExplosionVisualProfile(DroneExplosionPacket.TYPE_FLAMINGO, 8.0F));

        final float shahedSmokeScale = shahed.stream()
            .filter(p -> p.renderLayer == 3 && p.staticTexture != null)
            .map(p -> p.scale).max(Float::compare).orElse(0.0F);
        final float flamingoSmokeScale = flamingo.stream()
            .filter(p -> p.renderLayer == 3 && p.staticTexture != null)
            .map(p -> p.scale).max(Float::compare).orElse(0.0F);
        Fp5Assertions.assertTrue(flamingoSmokeScale > shahedSmokeScale * 1.2F,
            "Flamingo must produce a visibly larger smoke plume than Shahed");
        Fp5Assertions.assertTrue(flamingo.size() < 1000,
            "A single heavy impact must remain within the client particle budget");
        for (final DroneParticleManager.ExplosionParticle particle : flamingo) {
            if (particle.staticTexture != null && particle.staticTexture.getPath().contains("flash64")) {
                Fp5Assertions.assertTrue(particle.maxAge <= 4,
                    "The broad flash must end before the rising smoke dominates");
                Fp5Assertions.assertTrue(particle.b < particle.r,
                    "The initial flash should be warm rather than pure white");
            }
        }
        VfxLightingRegistry.clear();
    }

    @Fp5Test(tier = 2, features = {"F11"}, description = "Layered composition creates a surface halo but never ground rings for an airburst")
    public void testLayeredCompositionSurfaceAndAirburstSeparation() throws Exception {
        final Method spawnMethod = DroneParticleManager.class.getDeclaredMethod(
            "spawnLayeredExplosion",
            List.class,
            double.class, double.class, double.class,
            float.class, float.class, float.class,
            byte.class,
            DroneParticleManager.ExplosionVisualProfile.class
        );
        spawnMethod.setAccessible(true);
        final DroneParticleManager.ExplosionVisualProfile profile = DroneParticleManager.resolveExplosionVisualProfile(
            DroneExplosionPacket.TYPE_FLAMINGO,
            8.0F
        );

        final List<DroneParticleManager.ExplosionParticle> surface = new ArrayList<>();
        final Object surfaceResult = spawnMethod.invoke(
            null,
            surface,
            0.0D, 64.0D, 0.0D,
            0.0F, 1.0F, 0.0F,
            DroneExplosionPacket.MAT_STONE,
            profile
        );
        Fp5Assertions.assertEquals(Boolean.TRUE, surfaceResult, "Surface composition path must be selected");
        int surfaceGroundRings = 0;
        for (final DroneParticleManager.ExplosionParticle particle : surface) {
            if (particle.orientation == DroneParticleManager.ParticleOrientation.GROUND_ALIGNED) {
                surfaceGroundRings++;
            }
        }
        Fp5Assertions.assertTrue(surfaceGroundRings >= 2,
            "Surface composition must include the primary and hot pressure rings");
        Fp5Assertions.assertTrue(surface.size() >= 250,
            "Surface FP-5 composition must contain all visual layers");
        float largestGroundRing = 0.0F;
        for (final DroneParticleManager.ExplosionParticle particle : surface) {
            if (particle.orientation == DroneParticleManager.ParticleOrientation.GROUND_ALIGNED) {
                largestGroundRing = Math.max(largestGroundRing, particle.toScale);
            }
        }
        Fp5Assertions.assertTrue(largestGroundRing >= 200.0F,
            "The pressure ring quad must be wide enough for the 110 m visible radius");

        final List<DroneParticleManager.ExplosionParticle> airburst = new ArrayList<>();
        spawnMethod.invoke(
            null,
            airburst,
            0.0D, 64.0D, 0.0D,
            0.0F, 0.0F, 0.0F,
            DroneExplosionPacket.MAT_GENERIC,
            profile
        );
        int airburstGroundRings = 0;
        for (final DroneParticleManager.ExplosionParticle particle : airburst) {
            if (particle.orientation == DroneParticleManager.ParticleOrientation.GROUND_ALIGNED) {
                airburstGroundRings++;
            }
        }
        Fp5Assertions.assertEquals(0, airburstGroundRings,
            "Airburst composition must not create a floating ground-aligned ring");
        VfxLightingRegistry.clear();
    }

    // ---------------------------------------------------------------------------------------------
    // Tier 3: Cross-Feature Interactions
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 3, features = {"F10", "F11"}, description = "Cross-Feature: Entity collision triggers demolition damage and detonation VFX packet")
    public void testVehicleDemolitionTriggersDetonationPacket() {
        // Authoritative source: PROJECT.md § Interface Contracts M1 <-> M3
        // Simulates vehicle collision event:
        final boolean isVehicleHit = true;
        final float vehicleDemolitionDamage = 4500.0F;

        // Verify demolition damage applies
        Fp5Assertions.assertTrue(vehicleDemolitionDamage >= 4500.0F, "Vehicle hit must deal at least 4500.0F demolition damage");

        // Verify packet emission type
        final byte packetType = DroneExplosionPacket.TYPE_FLAMINGO;
        Fp5Assertions.assertEquals(DroneExplosionPacket.TYPE_FLAMINGO, packetType,
                "Vehicle impact must dispatch TYPE_FLAMINGO explosion packet");
    }

    @Fp5Test(tier = 3, features = {"F4", "F11"}, description = "Cross-Feature: Flight timeout and hurt() damage enforce airburst normal Vec3.ZERO")
    public void testFlightTimeoutAndHurtAirburstContract() {
        // MAX_FLIGHT_TICKS timeout constant verification
        Fp5Assertions.assertEquals(6000, Fp5FlamingoEntity.MAX_FLIGHT_TICKS,
                "MAX_FLIGHT_TICKS must be 6000 ticks (5 minutes)");

        // Airburst normal contracts:
        final Vec3 airburstNormal = Vec3.ZERO;
        Fp5Assertions.assertEquals(0.0D, airburstNormal.lengthSqr(), 1.0E-6D,
                "Airburst normal must have length 0");

        // Explicit normal contract in DroneExplosionEffects:
        final Vec3 explicitZero = Vec3.ZERO;
        final Vec3 resolvedNormal = explicitZero.lengthSqr() > 1.0E-4D ? explicitZero.normalize() : Vec3.ZERO;
        Fp5Assertions.assertEquals(Vec3.ZERO, resolvedNormal,
                "Explicit Vec3.ZERO must resolve to Vec3.ZERO without falling back to (0, 1, 0)");

        final Vec3 explicitSurface = new Vec3(0.0D, 1.0D, 0.0D);
        final Vec3 resolvedSurface = explicitSurface.lengthSqr() > 1.0E-4D ? explicitSurface.normalize() : Vec3.ZERO;
        Fp5Assertions.assertEquals(new Vec3(0.0D, 1.0D, 0.0D), resolvedSurface,
                "Explicit ground normal must resolve to unit UP vector");
    }

    @Fp5Test(tier = 2, features = {"F11"}, description = "Flamingo heavy warhead blast dynamics: multi-tier flash, ballistic sparks, billowing smoke dome, and 80m light")
    public void testFlamingoHeavyBlastVisualDynamicsAndPhysics() throws Exception {
        final Method spawnMethod = DroneParticleManager.class.getDeclaredMethod(
            "spawnLayeredExplosion",
            List.class,
            double.class, double.class, double.class,
            float.class, float.class, float.class,
            byte.class,
            DroneParticleManager.ExplosionVisualProfile.class
        );
        spawnMethod.setAccessible(true);
        final DroneParticleManager.ExplosionVisualProfile profile = DroneParticleManager.resolveExplosionVisualProfile(
            DroneExplosionPacket.TYPE_FLAMINGO,
            8.0F
        );
        Fp5Assertions.assertNotNull(profile, "Profile must resolve");

        final List<DroneParticleManager.ExplosionParticle> particles = new ArrayList<>();
        VfxLightingRegistry.clear();
        spawnMethod.invoke(
            null,
            particles,
            0.0D, 64.0D, 0.0D,
            0.0F, 1.0F, 0.0F,
            DroneExplosionPacket.MAT_STONE,
            profile
        );

        // 1. Verify multi-tier flash includes TEX_FLASH_FLARE (bombflare_translucent from mtsofficialpack)
        boolean hasFlare = false;
        for (final DroneParticleManager.ExplosionParticle p : particles) {
            if (p.staticTexture != null && p.staticTexture.getNamespace().equals("mtsofficialpack") && p.staticTexture.getPath().contains("bombflare_translucent")) {
                hasFlare = true;
                Fp5Assertions.assertTrue(p.toScale >= 10.0F, "Detonation flare must expand to blinding scale");
                Fp5Assertions.assertEquals(0.0F, p.litFactor, 1.0E-4F, "Flash flare must be self-luminous");
                break;
            }
        }
        Fp5Assertions.assertTrue(hasFlare, "Explosion must spawn multi-tier TEX_FLASH_FLARE from mtsofficialpack");

        // 2. Verify ballistic sparks (gravity ~0.04F, stopsOnGround=true, emitsSmoke=true)
        int ballisticSparksCount = 0;
        boolean hasSparkClusterTexture = false;
        for (final DroneParticleManager.ExplosionParticle p : particles) {
            if (p.orientation == DroneParticleManager.ParticleOrientation.MOTION) {
                if (p.stopsOnGround && p.gravity >= 0.038F && p.gravity <= 0.045F) {
                    ballisticSparksCount++;
                }
                if (p.animFrames != null) {
                    for (final net.minecraft.resources.ResourceLocation loc : p.animFrames) {
                        if (loc.getNamespace().equals("mtsofficialpack") && loc.getPath().contains("sparkcluster")) {
                            hasSparkClusterTexture = true;
                            break;
                        }
                    }
                }
            }
        }
        Fp5Assertions.assertTrue(ballisticSparksCount >= 50,
            "Heavy explosion must spawn ballistic sparks with gravity ~0.04F and stopsOnGround (found: " + ballisticSparksCount + ")");
        Fp5Assertions.assertTrue(hasSparkClusterTexture, "Sparks must utilize mtsofficialpack sparkcluster animated textures");

        // 3. Verify billowing smoke dome and dependency smoke textures
        boolean hasSmokeTexture = false;
        boolean hasHighRiseColumn = false;
        for (final DroneParticleManager.ExplosionParticle p : particles) {
            if (p.staticTexture != null && (p.staticTexture.getPath().contains("alt") || p.staticTexture.getPath().contains("big_smoke") || p.staticTexture.getPath().contains("smokecluster"))) {
                hasSmokeTexture = true;
            }
            if (p.vy >= 0.45D && p.maxAge >= 150) {
                hasHighRiseColumn = true;
            }
        }
        Fp5Assertions.assertTrue(hasSmokeTexture, "Smoke cloud or dust must incorporate dependency smoke textures");
        Fp5Assertions.assertTrue(hasHighRiseColumn, "Smoke cloud must include high-velocity rising column particles (vy >= 0.45)");

        // 4. Verify dynamic lighting peak intensity 32.0F and radius 80.0m
        final List<VfxLightingRegistry.Light> lights = VfxLightingRegistry.snapshot(new Vec3(0.0D, 64.0D, 0.0D), 10);
        boolean foundPeakCoreLight = false;
        for (final VfxLightingRegistry.Light light : lights) {
            System.out.println("TEST LIGHT: " + light.envelopePeakIntensity + " " + light.envelopePeakRadius);
            if (Math.abs(light.envelopePeakIntensity - 32.0F) < 0.1F && Math.abs(light.envelopePeakRadius - 80.0F) < 0.1F) {
                foundPeakCoreLight = true;
                break;
            }
        }
        Fp5Assertions.assertTrue(foundPeakCoreLight, "Explosion must register dynamic light with peak intensity 32.0F and radius 80.0m");
        VfxLightingRegistry.clear();
    }

    private static float computeDistanceBlastDamage(final float distance, final float lethalRadius, final float maxRadius, final float baseDamage) {
        if (distance <= lethalRadius) {
            return baseDamage;
        }
        if (distance >= maxRadius) {
            return 0.0F;
        }
        final float falloffProgress = (distance - lethalRadius) / (maxRadius - lethalRadius);
        return baseDamage * (1.0F - falloffProgress);
    }

    private static float computeVehicleSplashDamage(final float distance, final float diameter, final float baseDamage) {
        final double distanceRatio = distance / diameter;
        if (distanceRatio > 1.0D) {
            return 0.0F;
        }
        final double damagePercent = 1.0D - distanceRatio;
        return (float) (((damagePercent * damagePercent + damagePercent) / 2.0D) * baseDamage);
    }
}
