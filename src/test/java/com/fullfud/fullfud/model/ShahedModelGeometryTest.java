package com.fullfud.fullfud.model;

import com.fullfud.fullfud.common.entity.Shahed238DroneEntity;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Fp5TestSuite(name = "Shahed-136, 238 & Launcher Remodeling Suite", features = {"REMODEL_136", "JET_238", "LAUNCHER"}, milestone = "M5")
public class ShahedModelGeometryTest {

    private static final String GEO_DIR = "src/main/resources/assets/fullfud/geo/";
    private static final String TEX_DIR = "src/main/resources/assets/fullfud/textures/";

    @Fp5Test(tier = 1, features = {"LAUNCHER"}, description = "Verify launcher.geo.json structure and absence of phantom drones")
    public void testLauncherGeometry() throws Exception {
        final Path path = Path.of(GEO_DIR, "launcher.geo.json");
        Fp5Assertions.assertTrue(Files.exists(path), "launcher.geo.json must exist");
        final String content = Files.readString(path, StandardCharsets.UTF_8);

        Fp5Assertions.assertTrue(content.contains("\"name\": \"launcher\""), "launcher.geo.json must have bone 'launcher'");
        Fp5Assertions.assertFalse(content.contains("shahed-136"), "launcher.geo.json must NOT contain shahed-136 example bone");
        Fp5Assertions.assertFalse(content.contains("group2"), "launcher.geo.json must NOT contain group2");
        Fp5Assertions.assertFalse(content.contains("group3"), "launcher.geo.json must NOT contain group3");
    }

    @Fp5Test(tier = 1, features = {"REMODEL_136"}, description = "Verify shahed_136.geo.json bones, hierarchy, and propeller")
    public void testShahed136Geometry() throws Exception {
        final Path path = Path.of(GEO_DIR, "shahed_136.geo.json");
        Fp5Assertions.assertTrue(Files.exists(path), "shahed_136.geo.json must exist");
        final String content = Files.readString(path, StandardCharsets.UTF_8);

        Fp5Assertions.assertTrue(content.contains("\"name\": \"group\""), "shahed_136.geo.json must have root bone 'group'");
        Fp5Assertions.assertTrue(content.contains("\"name\": \"group2\""), "shahed_136.geo.json must have bone 'group2'");
        Fp5Assertions.assertTrue(content.contains("\"name\": \"group3\""), "shahed_136.geo.json must have propeller bone 'group3'");
    }

    @Fp5Test(tier = 1, features = {"REMODEL_136", "LAUNCHER"}, description = "Verify shahed_136onlauncher.geo.json rail alignment")
    public void testShahed136OnLauncherGeometry() throws Exception {
        final Path path = Path.of(GEO_DIR, "shahed_136onlauncher.geo.json");
        Fp5Assertions.assertTrue(Files.exists(path), "shahed_136onlauncher.geo.json must exist");
        final String content = Files.readString(path, StandardCharsets.UTF_8);

        Fp5Assertions.assertTrue(content.contains("\"name\": \"group\""), "shahed_136onlauncher must have root bone 'group'");
        Fp5Assertions.assertTrue(content.contains("\"pivot\": [0, 17.60722, -8.61398]") || content.contains("17.60722"), "Pivot must match launcher rails");
        Fp5Assertions.assertTrue(content.contains("\"rotation\": [167.5, 0, 180]") || content.contains("167.5"), "Rotation must match launcher rail angle");
        Fp5Assertions.assertFalse(content.contains("\"name\": \"launcher\""), "shahed_136onlauncher must NOT contain launcher bone");
    }

    @Fp5Test(tier = 1, features = {"JET_238"}, description = "Verify shahed_238.geo.json flight model")
    public void testShahed238Geometry() throws Exception {
        final Path path = Path.of(GEO_DIR, "shahed_238.geo.json");
        Fp5Assertions.assertTrue(Files.exists(path), "shahed_238.geo.json must exist");
        final String content = Files.readString(path, StandardCharsets.UTF_8);

        Fp5Assertions.assertTrue(content.contains("\"name\": \"group\""), "shahed_238.geo.json must have root bone 'group'");
        Fp5Assertions.assertFalse(content.contains("\"name\": \"group3\""), "Jet 238 must not have propeller bone group3");
    }

    @Fp5Test(tier = 1, features = {"JET_238", "LAUNCHER"}, description = "Verify shahed_238onlauncher.geo.json matches 136 rail alignment")
    public void testShahed238OnLauncherGeometry() throws Exception {
        final Path path = Path.of(GEO_DIR, "shahed_238onlauncher.geo.json");
        Fp5Assertions.assertTrue(Files.exists(path), "shahed_238onlauncher.geo.json must exist");
        final String content = Files.readString(path, StandardCharsets.UTF_8);

        Fp5Assertions.assertTrue(content.contains("\"name\": \"group\""), "shahed_238onlauncher must have root bone 'group'");
        Fp5Assertions.assertTrue(content.contains("17.60722"), "shahed_238onlauncher pivot Y must be 17.60722");
        Fp5Assertions.assertTrue(content.contains("167.5"), "shahed_238onlauncher rotation must be 167.5 deg pitch");
    }

    @Fp5Test(tier = 1, features = {"TEXTURES"}, description = "Verify all entity and item textures exist")
    public void testTexturesPresence() {
        Fp5Assertions.assertTrue(Files.exists(Path.of(TEX_DIR, "entity/shahed_launcher.png")), "shahed_launcher.png must exist");
        Fp5Assertions.assertTrue(Files.exists(Path.of(TEX_DIR, "entity/shahed_136.png")), "shahed_136.png must exist");
        Fp5Assertions.assertFalse(Files.exists(Path.of(TEX_DIR, "entity/shahed_136_black.png")), "shahed_136_black.png must not exist");
        Fp5Assertions.assertFalse(Files.exists(Path.of(TEX_DIR, "item/shahed_136_black.png")), "item shahed_136_black.png must not exist");
        Fp5Assertions.assertTrue(Files.exists(Path.of(TEX_DIR, "entity/shahed_238_white.png")), "shahed_238_white.png must exist");
        Fp5Assertions.assertTrue(Files.exists(Path.of(TEX_DIR, "entity/shahed_238_black.png")), "shahed_238_black.png must exist");

        Fp5Assertions.assertTrue(Files.exists(Path.of(TEX_DIR, "item/shahed_238.png")), "shahed_238 item icon must exist");
        Fp5Assertions.assertTrue(Files.exists(Path.of(TEX_DIR, "item/shahed_238_black.png")), "shahed_238_black item icon must exist");
    }

    @Fp5Test(tier = 1, features = {"ANIMATION"}, description = "Verify group3 propeller animation exists")
    public void testPropellerAnimation() throws Exception {
        final Path animPath = Path.of("src/main/resources/assets/fullfud/animations/shahed_136.animation.json");
        Fp5Assertions.assertTrue(Files.exists(animPath), "shahed_136.animation.json must exist");
        final String content = Files.readString(animPath, StandardCharsets.UTF_8);

        Fp5Assertions.assertTrue(content.contains("\"group3\""), "Animation must contain propeller bone 'group3'");
    }

    @Fp5Test(tier = 1, features = {"REGISTRY"}, description = "Verify Shahed-238 classes and registry fields")
    public void testShahed238Registries() throws Exception {
        Fp5Assertions.assertNotNull(FullfudRegistries.SHAHED_238_ENTITY, "SHAHED_238_ENTITY must be registered");
        Fp5Assertions.assertNotNull(FullfudRegistries.SHAHED_238_ITEM, "SHAHED_238_ITEM must be registered");
        Fp5Assertions.assertNotNull(FullfudRegistries.SHAHED_238_BLACK_ITEM, "SHAHED_238_BLACK_ITEM must be registered");
        Fp5Assertions.assertNotNull(FullfudRegistries.SHAHED_238_ITEM_SLOW, "SHAHED_238_ITEM_SLOW must be registered");
        Fp5Assertions.assertNotNull(FullfudRegistries.SHAHED_238_BLACK_ITEM_SLOW, "SHAHED_238_BLACK_ITEM_SLOW must be registered");

        Fp5Assertions.assertTrue(ShahedDroneEntity.class.isAssignableFrom(Shahed238DroneEntity.class),
                "Shahed238DroneEntity must inherit from ShahedDroneEntity");
    }

    @Fp5Test(tier = 1, features = {"JET_238", "AUDIO"}, description = "Verify Shahed-238 audio profile and sound instance subclass")
    public void testShahed238Audio() throws Exception {
        final Class<?> profileEnum = Class.forName("com.fullfud.fullfud.client.sound.DroneSoundEffects$SoundProfile");
        final Object shahed238Profile = Enum.valueOf((Class<Enum>) profileEnum, "SHAHED_238");
        Fp5Assertions.assertNotNull(shahed238Profile, "SHAHED_238 sound profile must exist");

        final Class<?> soundClass = Class.forName("com.fullfud.fullfud.client.sound.Shahed238EngineLoopSoundInstance");
        final Class<?> parentClass = Class.forName("com.fullfud.fullfud.client.sound.ShahedEngineLoopSoundInstance");
        Fp5Assertions.assertTrue(parentClass.isAssignableFrom(soundClass),
                "Shahed238EngineLoopSoundInstance must inherit from ShahedEngineLoopSoundInstance");
    }

    @Fp5Test(tier = 1, features = {"JET_238", "VFX"}, description = "Verify Shahed-238 VFX client methods exist")
    public void testShahed238Vfx() throws Exception {
        final Class<?> vfxClass = Class.forName("com.fullfud.fullfud.client.particle.Shahed238ClientVfx");
        Fp5Assertions.assertNotNull(vfxClass.getMethod("tick", Shahed238DroneEntity.class),
                "Shahed238ClientVfx.tick must exist");
        Fp5Assertions.assertNotNull(vfxClass.getMethod("computeNozzleAnchor", Shahed238DroneEntity.class),
                "Shahed238ClientVfx.computeNozzleAnchor must exist");
        Fp5Assertions.assertNotNull(vfxClass.getMethod("computeNozzleAnchor", Shahed238DroneEntity.class, float.class),
                "Shahed238ClientVfx.computeNozzleAnchor with partialTick must exist");
        Fp5Assertions.assertNotNull(vfxClass.getMethod("renderFrame", float.class),
                "Shahed238ClientVfx.renderFrame must exist");

        final Class<?> particleManager = Class.forName("com.fullfud.fullfud.client.particle.DroneParticleManager");
        Fp5Assertions.assertNotNull(
                particleManager.getMethod("spawnShahed238JetExhaust",
                        net.minecraft.world.phys.Vec3.class,
                        net.minecraft.world.phys.Vec3.class,
                        net.minecraft.world.phys.Vec3.class,
                        float.class),
                "DroneParticleManager.spawnShahed238JetExhaust must exist"
        );
    }

    @Fp5Test(tier = 1, features = {"ANIMATION", "REMODEL_136"}, description = "Verify idle and running aliases in shahed_136.animation.json")
    public void testShahed136AnimationAliases() throws Exception {
        final Path animPath = Path.of("src/main/resources/assets/fullfud/animations/shahed_136.animation.json");
        final String content = Files.readString(animPath, StandardCharsets.UTF_8);
        Fp5Assertions.assertTrue(content.contains("\"idle\""), "Must contain 'idle' animation alias");
        Fp5Assertions.assertTrue(content.contains("\"running\""), "Must contain 'running' animation alias");
        Fp5Assertions.assertTrue(content.contains("group3"), "Must animate group3 propeller");
    }

    @Fp5Test(tier = 1, features = {"LAUNCHER", "REMODEL_136", "JET_238"}, description = "Verify drone launcher mounting alignment, slope angle, and pivot preservation")
    public void testLauncherMountOrientation() throws Exception {
        final Path p136 = Path.of(GEO_DIR, "shahed_136onlauncher.geo.json");
        final Path p238 = Path.of(GEO_DIR, "shahed_238onlauncher.geo.json");
        final Path pLaunch = Path.of(GEO_DIR, "launcher.geo.json");

        final String c136 = Files.readString(p136, StandardCharsets.UTF_8);
        final String c238 = Files.readString(p238, StandardCharsets.UTF_8);
        final String cLaunch = Files.readString(pLaunch, StandardCharsets.UTF_8);

        // Verify rail angle 12.5 degrees in launcher
        Fp5Assertions.assertTrue(cLaunch.contains("12.5"), "Launcher rails must be tilted at 12.5 deg");

        // Verify drone pitch 167.5 (180 - 12.5) and roll 180 in onlauncher models
        Fp5Assertions.assertTrue(c136.contains("167.5"), "136 on launcher must preserve 167.5 deg pitch");
        Fp5Assertions.assertTrue(c136.contains("180"), "136 on launcher must preserve 180 deg roll");
        Fp5Assertions.assertTrue(c238.contains("167.5"), "238 on launcher must preserve 167.5 deg pitch");
        Fp5Assertions.assertTrue(c238.contains("180"), "238 on launcher must preserve 180 deg roll");

        // Verify both drones share the exact same pivot on launcher
        Fp5Assertions.assertTrue(c136.contains("17.60722"), "136 pivot Y must be 17.60722");
        Fp5Assertions.assertTrue(c238.contains("17.60722"), "238 pivot Y must be 17.60722");
        Fp5Assertions.assertTrue(c136.contains("-8.61398"), "136 pivot Z must be -8.61398");
        Fp5Assertions.assertTrue(c238.contains("-8.61398"), "238 pivot Z must be -8.61398");
    }
}
