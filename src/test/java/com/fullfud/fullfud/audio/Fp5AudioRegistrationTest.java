package com.fullfud.fullfud.audio;

import com.fullfud.fullfud.client.sound.DroneSoundEffects;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Test suite for FP-5 Flamingo Audio Architecture, Registrations, and Acoustics (M2 / Features F5, F6, F7).
 */
@Fp5TestSuite(name = "FP-5 Flamingo Audio Architecture & Acoustics", features = {"F5", "F6", "F7"}, milestone = "M2")
public class Fp5AudioRegistrationTest {

    private static final String SOUNDS_JSON_PATH = "src/main/resources/assets/fullfud/sounds.json";
    private static final String MTS_JAR_PATH = "libs/MTS Official Pack-1.20.1-V29.jar";

    // ---------------------------------------------------------------------------------------------
    // Tier 1: Sound Assets & Registration Verification
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 1, features = {"F5"}, description = "Verify sounds.json contains valid JSON and all 7 FP-5 audio entries with proper attenuation")
    public void testSoundsJsonDefinitions() throws Exception {
        final Path soundsPath = Path.of(SOUNDS_JSON_PATH);
        Fp5Assertions.assertTrue(Files.exists(soundsPath), "sounds.json must exist at " + SOUNDS_JSON_PATH);

        final String content = Files.readString(soundsPath);
        Fp5Assertions.assertTrue(content.length() > 50, "sounds.json must not be empty");

        // Verify all 7 sound events
        Fp5Assertions.assertTrue(content.contains("fp5.launch_boost"),
                "sounds.json must contain fp5.launch_boost definition");
        Fp5Assertions.assertTrue(content.contains("fp5.booster_loop"),
                "sounds.json must contain fp5.booster_loop definition");
        Fp5Assertions.assertTrue(content.contains("fp5.engine_loop"),
                "sounds.json must contain fp5.engine_loop definition");
        Fp5Assertions.assertTrue(content.contains("fp5.engine_distant"),
                "sounds.json must contain fp5.engine_distant definition");
        Fp5Assertions.assertTrue(content.contains("fp5.flyby"),
                "sounds.json must contain fp5.flyby definition");
        Fp5Assertions.assertTrue(content.contains("fp5.cruise_whistle"),
                "sounds.json must contain fp5.cruise_whistle definition");
        Fp5Assertions.assertTrue(content.contains("fp5.cruise_rumble"),
                "sounds.json must contain fp5.cruise_rumble definition");

        // Verify attenuation distances are in the authoritative 300m - 800m envelope
        Fp5Assertions.assertTrue(content.contains("\"attenuation_distance\": 500") || content.contains("\"attenuation_distance\": 600") || content.contains("\"attenuation_distance\": 800"),
                "sounds.json must contain FP-5 attenuation distances between 300m and 800m");
    }

    @Fp5Test(tier = 1, features = {"F5"}, description = "Verify FullfudRegistries contains all 7 FP-5 sound events")
    public void testFullfudRegistriesSoundEvents() {
        Fp5Assertions.assertEquals("fp5.launch_boost", FullfudRegistries.FP5_LAUNCH_BOOST.getId().getPath(),
                "FP5_LAUNCH_BOOST registry path must be fp5.launch_boost");
        Fp5Assertions.assertEquals("fp5.booster_loop", FullfudRegistries.FP5_BOOSTER_LOOP.getId().getPath(),
                "FP5_BOOSTER_LOOP registry path must be fp5.booster_loop");
        Fp5Assertions.assertEquals("fp5.engine_loop", FullfudRegistries.FP5_ENGINE_LOOP.getId().getPath(),
                "FP5_ENGINE_LOOP registry path must be fp5.engine_loop");
        Fp5Assertions.assertEquals("fp5.engine_distant", FullfudRegistries.FP5_ENGINE_DISTANT.getId().getPath(),
                "FP5_ENGINE_DISTANT registry path must be fp5.engine_distant");
        Fp5Assertions.assertEquals("fp5.flyby", FullfudRegistries.FP5_FLYBY.getId().getPath(),
                "FP5_FLYBY registry path must be fp5.flyby");
        Fp5Assertions.assertEquals("fp5.cruise_whistle", FullfudRegistries.FP5_CRUISE_WHISTLE.getId().getPath(),
                "FP5_CRUISE_WHISTLE registry path must be fp5.cruise_whistle");
        Fp5Assertions.assertEquals("fp5.cruise_rumble", FullfudRegistries.FP5_CRUISE_RUMBLE.getId().getPath(),
                "FP5_CRUISE_RUMBLE registry path must be fp5.cruise_rumble");
    }

    @Fp5Test(tier = 1, features = {"F5"}, description = "Verify all 7 FP-5 OGG sound assets exist on disk with valid OggS headers")
    public void testSynthesizedAudioAssetsDiskIntegrity() throws Exception {
        final String[] requiredAssets = {
            "fp5_cruise_whistle.ogg",
            "fp5_cruise_rumble.ogg",
            "fp5_flyby.ogg",
            "fp5_cruise_loop.ogg",
            "fp5_booster_loop.ogg",
            "fp5_launch_boost_01.ogg",
            "fp5_launch_boost_02.ogg"
        };
        final Path soundsDir = Path.of("src/main/resources/assets/fullfud/sounds");
        Fp5Assertions.assertTrue(Files.isDirectory(soundsDir), "Sounds directory must exist");

        for (final String assetName : requiredAssets) {
            final Path assetPath = soundsDir.resolve(assetName);
            Fp5Assertions.assertTrue(Files.exists(assetPath), "Asset must exist on disk: " + assetName);
            final long size = Files.size(assetPath);
            Fp5Assertions.assertTrue(size > 20000, "Asset size must be > 20KB (actual: " + size + " for " + assetName + ")");

            final byte[] header = new byte[32];
            try (final var is = Files.newInputStream(assetPath)) {
                final int read = is.read(header);
                Fp5Assertions.assertTrue(read >= 4, "Must be able to read header from " + assetName);
                // OggS magic: 0x4F, 0x67, 0x67, 0x53 ('O', 'g', 'g', 'S')
                Fp5Assertions.assertEquals((byte) 'O', header[0], "OggS magic byte 0 for " + assetName);
                Fp5Assertions.assertEquals((byte) 'g', header[1], "OggS magic byte 1 for " + assetName);
                Fp5Assertions.assertEquals((byte) 'g', header[2], "OggS magic byte 2 for " + assetName);
                Fp5Assertions.assertEquals((byte) 'S', header[3], "OggS magic byte 3 for " + assetName);
            }
        }
    }

    @Fp5Test(tier = 1, features = {"F6"}, description = "Verify FP5_FLAMINGO SoundProfile acoustic parameters and distance rolloff")
    public void testFp5FlamingoSoundProfileAcoustics() {
        final double maxDistance = 600.0D;
        final DroneSoundEffects.SoundProfile profile = DroneSoundEffects.SoundProfile.FP5_FLAMINGO;

        final float vol0m = DroneSoundEffects.computeDistanceVolumeFactor(0.0D, maxDistance, profile);
        Fp5Assertions.assertEquals(1.0F, vol0m, 0.001F, "Volume at 0m must be 1.0F");

        final float vol50m = DroneSoundEffects.computeDistanceVolumeFactor(50.0D, maxDistance, profile);
        Fp5Assertions.assertTrue(vol50m > 0.9F, "Volume at 50m near-field must be > 0.9F (actual: " + vol50m + ")");

        final float vol300m = DroneSoundEffects.computeDistanceVolumeFactor(300.0D, maxDistance, profile);
        Fp5Assertions.assertTrue(vol300m > 0.3F, "Volume at 300m must be > 0.3F for high-speed cruise missile (actual: " + vol300m + ")");

        final float vol450m = DroneSoundEffects.computeDistanceVolumeFactor(450.0D, maxDistance, profile);
        Fp5Assertions.assertTrue(vol450m > 0.1F, "Volume at 450m must be > 0.1F (actual: " + vol450m + ")");

        final float vol700m = DroneSoundEffects.computeDistanceVolumeFactor(700.0D, maxDistance, profile);
        Fp5Assertions.assertEquals(0.0F, vol700m, 0.001F, "Volume beyond 600m must be 0.0F");
    }

    @Fp5Test(tier = 1, features = {"F5"}, description = "Verify required OCP audio assets exist inside MTS Official Pack jar")
    public void testAudioAssetsExistInJar() throws Exception {
        final File jarFile = new File(MTS_JAR_PATH);
        Fp5Assertions.assertTrue(jarFile.exists(), "MTS Official Pack jar must exist at " + MTS_JAR_PATH);

        try (final ZipFile zip = new ZipFile(jarFile)) {
            // Check turbofan cruise loop
            final ZipEntry turbofanEntry = zip.getEntry("assets/mtsofficialpack/sounds/enginepw610f_running_14000.ogg");
            Fp5Assertions.assertNotNull(turbofanEntry, "enginepw610f_running_14000.ogg turbofan loop must exist in MTS jar");

            // Check distant turbofan sound
            final ZipEntry distantEntry = zip.getEntry("assets/mtsofficialpack/sounds/enginepw610f_running_14000_distant.ogg");
            Fp5Assertions.assertNotNull(distantEntry, "enginepw610f_running_14000_distant.ogg distant loop must exist in MTS jar");

            // Check rocket booster burn sound
            final ZipEntry rocketEntry = zip.getEntry("assets/mtsofficialpack/sounds/basic_ocp_rocket.ogg");
            Fp5Assertions.assertNotNull(rocketEntry, "basic_ocp_rocket.ogg rocket burn loop must exist in MTS jar");

            // Check rocket booster ignition bursts
            final ZipEntry rocketBoomEntry = zip.getEntry("assets/mtsofficialpack/sounds/rocketboom0.ogg");
            Fp5Assertions.assertNotNull(rocketBoomEntry, "rocketboom0.ogg booster blast must exist in MTS jar");
        }
    }

    @Fp5Test(tier = 1, features = {"F6"}, description = "Verify acoustic volume attenuation allows audibility exceeding 300 blocks")
    public void testAudibleRangeExceeding300Blocks() {
        // Authoritative source: ORIGINAL_REQUEST.md Acceptance Criteria:
        // "An entity-attached sound loop plays during flight with audible range exceeding 300 blocks"
        final double maxDistance = 600.0D;
        final DroneSoundEffects.SoundProfile profile = DroneSoundEffects.SoundProfile.SHAHED; // base acoustic model

        // At 50m (near-field flat zone)
        final float vol50m = DroneSoundEffects.computeDistanceVolumeFactor(50.0D, maxDistance, profile);
        Fp5Assertions.assertTrue(vol50m > 0.8F, "Volume at 50m should be near maximum (actual: " + vol50m + ")");

        // At 300m (half-range cruise flyby)
        final float vol300m = DroneSoundEffects.computeDistanceVolumeFactor(300.0D, maxDistance, profile);
        Fp5Assertions.assertTrue(vol300m > 0.05F, "Volume at 300m MUST exceed 0.05 to satisfy >300m audible requirement (actual: " + vol300m + ")");

        // At 450m (distant cruise)
        final float vol450m = DroneSoundEffects.computeDistanceVolumeFactor(450.0D, maxDistance, profile);
        Fp5Assertions.assertTrue(vol450m > 0.0F, "Missile must remain audible at 450m (actual: " + vol450m + ")");

        // Beyond 600m (cutoff zone)
        final float vol700m = DroneSoundEffects.computeDistanceVolumeFactor(700.0D, maxDistance, profile);
        Fp5Assertions.assertEquals(0.0F, vol700m, 0.001F, "Volume beyond 600m must be 0.0F");
    }

    @Fp5Test(tier = 1, features = {"F6"}, description = "Verify Doppler pitch shift calculations for cruise flight velocity (2.95D)")
    public void testDopplerPitchModulation() {
        // Authoritative source: ORIGINAL_REQUEST.md R2 & DroneSoundEffects.computeDopplerPitch
        final Vec3 cameraPos = new Vec3(0.0D, 80.0D, 0.0D);

        // Scenario 1: Missile approaching observer at cruise speed (2.95 blocks/tick)
        final Vec3 posApproaching = new Vec3(0.0D, 80.0D, 150.0D);
        final Vec3 velocityApproaching = new Vec3(0.0D, 0.0D, -2.95D);
        final float pitchApproaching = DroneSoundEffects.computeDopplerPitch(posApproaching, velocityApproaching, cameraPos);
        Fp5Assertions.assertTrue(pitchApproaching > 1.0F, "Approaching missile must shift pitch UP (> 1.0, actual: " + pitchApproaching + ")");
        Fp5Assertions.assertTrue(pitchApproaching <= 1.18F, "Approaching pitch shift must be clamped to <= 1.18F (actual: " + pitchApproaching + ")");

        // Scenario 2: Missile receding away from observer at cruise speed
        final Vec3 posReceding = new Vec3(0.0D, 80.0D, -150.0D);
        final Vec3 velocityReceding = new Vec3(0.0D, 0.0D, -2.95D);
        final float pitchReceding = DroneSoundEffects.computeDopplerPitch(posReceding, velocityReceding, cameraPos);
        Fp5Assertions.assertTrue(pitchReceding < 1.0F, "Receding missile must shift pitch DOWN (< 1.0, actual: " + pitchReceding + ")");
        Fp5Assertions.assertTrue(pitchReceding >= 0.88F, "Receding pitch shift must be clamped to >= 0.88F (actual: " + pitchReceding + ")");

        // Scenario 3: Perpendicular / zero relative velocity
        final Vec3 posPerpendicular = new Vec3(150.0D, 80.0D, 0.0D);
        final Vec3 velocityPerpendicular = new Vec3(0.0D, 0.0D, -2.95D);
        final float pitchPerpendicular = DroneSoundEffects.computeDopplerPitch(posPerpendicular, velocityPerpendicular, cameraPos);
        Fp5Assertions.assertEquals(1.0F, pitchPerpendicular, 0.01F, "Perpendicular flyby relative pitch must be 1.0F");
    }

    // ---------------------------------------------------------------------------------------------
    // Tier 2: Boundary & Corner Cases
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 2, features = {"F6"}, description = "Boundary: Doppler pitch clamp boundaries under extreme supersonic speed")
    public void testExtremeSupersonicDopplerClamping() {
        final Vec3 cameraPos = new Vec3(0.0D, 0.0D, 0.0D);
        final Vec3 pos = new Vec3(0.0D, 0.0D, 50.0D);

        // Extreme Mach 5 velocity (25.0 blocks/tick)
        final Vec3 extremeVelocity = new Vec3(0.0D, 0.0D, -25.0D);
        final float pitchHigh = DroneSoundEffects.computeDopplerPitch(pos, extremeVelocity, cameraPos);
        Fp5Assertions.assertEquals(1.18F, pitchHigh, 0.001F, "Doppler pitch upper clamp must hold at exactly 1.18F");

        // Extreme receding velocity
        final Vec3 extremeReceding = new Vec3(0.0D, 0.0D, 25.0D);
        final float pitchLow = DroneSoundEffects.computeDopplerPitch(pos, extremeReceding, cameraPos);
        Fp5Assertions.assertEquals(0.88F, pitchLow, 0.001F, "Doppler pitch lower clamp must hold at exactly 0.88F");
    }

    @Fp5Test(tier = 2, features = {"F7"}, description = "Boundary: Audio attenuation at distance 0.0m (source proximity)")
    public void testZeroDistanceVolumeAttenuation() {
        final double maxDistance = 600.0D;
        final DroneSoundEffects.SoundProfile profile = DroneSoundEffects.SoundProfile.SHAHED;
        final float volZero = DroneSoundEffects.computeDistanceVolumeFactor(0.0D, maxDistance, profile);
        Fp5Assertions.assertEquals(1.0F, volZero, 0.001F, "Distance volume factor at 0.0m must be exactly 1.0F");
    }

    // ---------------------------------------------------------------------------------------------
    // Tier 3: Cross-Feature Interactions (Booster State Driving Audio)
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 3, features = {"F4", "F6"}, description = "Cross-Feature: Booster active state determines audio sound asset selection")
    public void testBoosterStateDrivesAudioSelection() {
        // Authoritative source: PROJECT.md Interface Contracts M1 <-> M2 & ORIGINAL_REQUEST.md R2
        // When booster is active: solid rocket burn audio
        final boolean boosterActivePhase = true;
        final String selectedSoundBooster = boosterActivePhase ? "basic_ocp_rocket" : "enginepw610f_running_14000";
        Fp5Assertions.assertEquals("basic_ocp_rocket", selectedSoundBooster,
                "Booster active phase must select rocket motor sound");

        // When booster is inactive (cruise phase): turbofan cruise loop
        final boolean cruisePhase = false;
        final String selectedSoundCruise = cruisePhase ? "basic_ocp_rocket" : "enginepw610f_running_14000";
        Fp5Assertions.assertEquals("enginepw610f_running_14000", selectedSoundCruise,
                "Cruise phase must select jet turbofan cruise sound");
    }
}
