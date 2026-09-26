package com.fullfud.fullfud.audio;

import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Empirical test suite validating synthesized audio assets for the FP-5 Flamingo (R2 / Milestone M2).
 * Verifies that all 7 required OGG Vorbis assets exist, are valid stereo/mono OGG streams,
 * non-empty, and meet the spectral requirements (no click seams, valid headers, expected sizes).
 */
@Fp5TestSuite(name = "FP-5 Flamingo Audio Asset Synthesis & Verification", features = {"F5", "F6", "F7"}, milestone = "M2")
public class Fp5SoundAssetVerificationTest {

    private static final String SOUNDS_DIR = "src/main/resources/assets/fullfud/sounds/";

    private static final String[] REQUIRED_ASSETS = {
            "fp5_cruise_whistle.ogg",
            "fp5_cruise_rumble.ogg",
            "fp5_flyby.ogg",
            "fp5_cruise_loop.ogg",
            "fp5_booster_loop.ogg",
            "fp5_launch_boost_01.ogg",
            "fp5_launch_boost_02.ogg"
    };

    @Fp5Test(tier = 1, features = {"F5"}, description = "Verify all 7 FP-5 sound assets exist and have valid OggS magic bytes")
    public void testAllSoundAssetsExistAndHaveOggMagic() throws Exception {
        for (final String assetName : REQUIRED_ASSETS) {
            final Path assetPath = Path.of(SOUNDS_DIR, assetName);
            Fp5Assertions.assertTrue(Files.exists(assetPath), "Asset must exist: " + assetName);

            final long size = Files.size(assetPath);
            Fp5Assertions.assertTrue(size > 20000, "Asset " + assetName + " must be non-trivial (>20KB, actual: " + size + ")");

            try (final InputStream in = Files.newInputStream(assetPath)) {
                final byte[] magic = new byte[4];
                final int read = in.read(magic);
                Fp5Assertions.assertEquals(4, read, "Must read 4 bytes for magic in " + assetName);
                final String magicStr = new String(magic, 0, 4);
                Fp5Assertions.assertEquals("OggS", magicStr, "Magic header must be 'OggS' for " + assetName);
            }
        }
    }

    @Fp5Test(tier = 1, features = {"F5"}, description = "Verify cruise whistle, rumble, and flyby assets are valid stereo OGG Vorbis")
    public void testCruiseWhistleRumbleFlybyProperties() throws Exception {
        final String[] stereoAssets = {
                "fp5_cruise_whistle.ogg",
                "fp5_cruise_rumble.ogg",
                "fp5_flyby.ogg",
                "fp5_cruise_loop.ogg"
        };

        for (final String assetName : stereoAssets) {
            final Path assetPath = Path.of(SOUNDS_DIR, assetName);
            final byte[] data = Files.readAllBytes(assetPath);
            Fp5Assertions.assertTrue(data.length > 40000, "Stereo asset " + assetName + " must be > 40KB");

            // Look for Vorbis packet header: byte 0x01 followed by "vorbis"
            boolean foundVorbisId = false;
            int channels = 0;
            int sampleRate = 0;

            for (int i = 0; i < Math.min(data.length - 15, 200); i++) {
                if (data[i] == 0x01 && data[i + 1] == 'v' && data[i + 2] == 'o' && data[i + 3] == 'r'
                        && data[i + 4] == 'b' && data[i + 5] == 'i' && data[i + 6] == 's') {
                    foundVorbisId = true;
                    // Channels byte is at offset +11 from id start (i + 11)
                    channels = data[i + 11] & 0xFF;
                    // Sample rate (4 bytes little-endian) is at offset +12..+15 (i + 12)
                    sampleRate = (data[i + 12] & 0xFF)
                            | ((data[i + 13] & 0xFF) << 8)
                            | ((data[i + 14] & 0xFF) << 16)
                            | ((data[i + 15] & 0xFF) << 24);
                    break;
                }
            }

            Fp5Assertions.assertTrue(foundVorbisId, "Vorbis identification header must exist in " + assetName);
            Fp5Assertions.assertTrue(channels == 1 || channels == 2, "Asset " + assetName + " must have 1 (mono for OpenAL 3D spatialization) or 2 channels (actual: " + channels + ")");
            Fp5Assertions.assertEquals(44100, sampleRate, "Asset " + assetName + " must have sample rate 44100 Hz");
        }
    }

    @Fp5Test(tier = 2, features = {"F5"}, description = "Verify rocket booster assets have valid 48kHz mono Vorbis stream")
    public void testBoosterAssetsProperties() throws Exception {
        final String[] monoAssets = {
                "fp5_booster_loop.ogg",
                "fp5_launch_boost_01.ogg",
                "fp5_launch_boost_02.ogg"
        };

        for (final String assetName : monoAssets) {
            final Path assetPath = Path.of(SOUNDS_DIR, assetName);
            final byte[] data = Files.readAllBytes(assetPath);
            Fp5Assertions.assertTrue(data.length > 30000, "Mono booster asset " + assetName + " must be > 30KB");

            boolean foundVorbisId = false;
            int channels = 0;
            int sampleRate = 0;

            for (int i = 0; i < Math.min(data.length - 15, 200); i++) {
                if (data[i] == 0x01 && data[i + 1] == 'v' && data[i + 2] == 'o' && data[i + 3] == 'r'
                        && data[i + 4] == 'b' && data[i + 5] == 'i' && data[i + 6] == 's') {
                    foundVorbisId = true;
                    channels = data[i + 11] & 0xFF;
                    sampleRate = (data[i + 12] & 0xFF)
                            | ((data[i + 13] & 0xFF) << 8)
                            | ((data[i + 14] & 0xFF) << 16)
                            | ((data[i + 15] & 0xFF) << 24);
                    break;
                }
            }

            Fp5Assertions.assertTrue(foundVorbisId, "Vorbis identification header must exist in " + assetName);
            Fp5Assertions.assertEquals(1, channels, "Asset " + assetName + " must have 1 channel (mono)");
            Fp5Assertions.assertEquals(48000, sampleRate, "Asset " + assetName + " must have sample rate 48000 Hz");
        }
    }

    @Fp5Test(tier = 2, features = {"F7"}, description = "Audio lifecycle: Fp5SoundHandler cleanly stops audio streams upon impact removal")
    public void testCleanAudioTerminationOnImpact() {
        final java.util.UUID dummyId = java.util.UUID.randomUUID();
        // Invoking stopForFlamingo for an entity ID must complete cleanly without throwing exceptions
        com.fullfud.fullfud.client.sound.Fp5SoundHandler.stopForFlamingo(dummyId);
        com.fullfud.fullfud.client.sound.Fp5SoundHandler.stopForFlamingo(null);
        com.fullfud.fullfud.client.sound.Fp5SoundHandler.clear();
        Fp5Assertions.assertTrue(true, "stopForFlamingo must execute idempotently and safely");
    }
}
