package com.fullfud.fullfud.client.sound;

import com.fullfud.fullfud.mixin.client.ChannelAccessor;
import com.fullfud.fullfud.mixin.client.SoundEngineAccessor;
import com.fullfud.fullfud.mixin.client.SoundManagerAccessor;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

@OnlyIn(Dist.CLIENT)
public final class OpenALFilters {
    public interface FilterDriver {
        int genFilter();
        void setFilterType(int filterId, int type);
        void setFilterParam(int filterId, int param, float value);
        void bindDirectFilter(int sourceId, int filterId);
        void unbindDirectFilter(int sourceId);
        void deleteFilter(int filterId);
    }

    private static final FilterDriver DEFAULT_DRIVER = new FilterDriver() {
        @Override
        public int genFilter() {
            return EXTEfx.alGenFilters();
        }

        @Override
        public void setFilterType(final int filterId, final int type) {
            EXTEfx.alFilteri(filterId, EXTEfx.AL_FILTER_TYPE, type);
        }

        @Override
        public void setFilterParam(final int filterId, final int param, final float value) {
            EXTEfx.alFilterf(filterId, param, value);
        }

        @Override
        public void bindDirectFilter(final int sourceId, final int filterId) {
            AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, filterId);
        }

        @Override
        public void unbindDirectFilter(final int sourceId) {
            AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, 0);
        }

        @Override
        public void deleteFilter(final int filterId) {
            EXTEfx.alDeleteFilters(filterId);
        }
    };

    private static volatile FilterDriver driver = DEFAULT_DRIVER;
    private static boolean initialized;
    private static boolean efxAvailable;
    private static final Map<Integer, Integer> SOURCE_FILTERS = new java.util.concurrent.ConcurrentHashMap<>();

    private OpenALFilters() {
    }

    public static boolean isAvailable() {
        ensureInitialized();
        return efxAvailable;
    }

    public static synchronized void ensureInitialized() {
        if (initialized && efxAvailable) {
            return;
        }
        try {
            final long currentContext = ALC10.alcGetCurrentContext();
            if (currentContext == 0L) {
                return;
            }
            final long currentDevice = ALC10.alcGetContextsDevice(currentContext);
            if (currentDevice == 0L) {
                return;
            }
            if (!ALC10.alcIsExtensionPresent(currentDevice, "ALC_EXT_EFX")) {
                initialized = true;
                efxAvailable = false;
                return;
            }
            efxAvailable = true;
            initialized = true;
        } catch (Throwable ignored) {
            initialized = true;
            efxAvailable = false;
        }
    }

    public static void applyToSource(final int sourceId, final float gain, final float gainHF) {
        if (!efxAvailable || sourceId <= 0) {
            return;
        }
        Integer filterId = SOURCE_FILTERS.get(sourceId);
        if (filterId == null || filterId == 0) {
            final int f = driver.genFilter();
            if (f == 0) {
                return;
            }
            driver.setFilterType(f, EXTEfx.AL_FILTER_LOWPASS);
            driver.setFilterParam(f, EXTEfx.AL_LOWPASS_GAIN, clamp01(gain));
            driver.setFilterParam(f, EXTEfx.AL_LOWPASS_GAINHF, clamp01(gainHF));
            driver.bindDirectFilter(sourceId, f);
            SOURCE_FILTERS.put(sourceId, f);
            return;
        }
        driver.setFilterParam(filterId, EXTEfx.AL_LOWPASS_GAIN, clamp01(gain));
        driver.setFilterParam(filterId, EXTEfx.AL_LOWPASS_GAINHF, clamp01(gainHF));
    }

    public static void removeFromSource(final int sourceId) {
        if (!efxAvailable || sourceId <= 0) {
            return;
        }
        final Integer filterId = SOURCE_FILTERS.remove(sourceId);
        if (filterId == null || filterId == 0) {
            return;
        }
        driver.unbindDirectFilter(sourceId);
        try {
            driver.deleteFilter(filterId);
        } catch (Throwable ignored) {
        }
    }

    public static void clearAll() {
        if (!efxAvailable) {
            SOURCE_FILTERS.clear();
            return;
        }
        for (final Map.Entry<Integer, Integer> entry : SOURCE_FILTERS.entrySet()) {
            final int sourceId = entry.getKey();
            final int filterId = entry.getValue();
            try {
                if (sourceId > 0) {
                    driver.unbindDirectFilter(sourceId);
                }
                if (filterId != 0) {
                    driver.deleteFilter(filterId);
                }
            } catch (Throwable ignored) {
            }
        }
        SOURCE_FILTERS.clear();
    }

    public static void cleanup() {
        clearAll();
    }

    public static int getFilterForSource(final int sourceId) {
        final Integer filterId = SOURCE_FILTERS.get(sourceId);
        return filterId != null ? filterId : 0;
    }

    public static int getActiveFilterCount() {
        return SOURCE_FILTERS.size();
    }

    public static boolean hasFilterForSource(final int sourceId) {
        return SOURCE_FILTERS.containsKey(sourceId);
    }

    public static void setDriverForTesting(final FilterDriver testDriver) {
        driver = testDriver != null ? testDriver : DEFAULT_DRIVER;
    }

    public static void resetDriver() {
        driver = DEFAULT_DRIVER;
    }

    public static void setEfxAvailableForTesting(final boolean available) {
        efxAvailable = available;
    }

    public static synchronized void resetInitializationForTesting() {
        initialized = false;
        efxAvailable = false;
        driver = DEFAULT_DRIVER;
        SOURCE_FILTERS.clear();
    }

    public static void applyFilterForInstance(final SoundInstance instance, final float gain, final float gainHF) {
        final ChannelAccess.ChannelHandle handle = getChannelHandle(instance);
        if (handle == null) {
            return;
        }
        final float clampedGain = clamp01(gain);
        final float clampedGainHF = clamp01(gainHF);
        handle.execute(channel -> {
            ensureInitialized();
            if (!efxAvailable) {
                return;
            }
            final int sourceId = getSourceId(channel);
            if (sourceId > 0) {
                applyToSource(sourceId, clampedGain, clampedGainHF);
            }
        });
    }

    public static void removeFilterForInstance(final SoundInstance instance) {
        final ChannelAccess.ChannelHandle handle = getChannelHandle(instance);
        if (handle == null) {
            return;
        }
        handle.execute(channel -> {
            ensureInitialized();
            if (!efxAvailable) {
                return;
            }
            final int sourceId = getSourceId(channel);
            if (sourceId > 0) {
                removeFromSource(sourceId);
            }
        });
    }

    private static ChannelAccess.ChannelHandle getChannelHandle(final SoundInstance instance) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        final SoundManager soundManager = minecraft.getSoundManager();
        if (!(soundManager instanceof SoundManagerAccessor soundManagerAccessor)) {
            return null;
        }
        final SoundEngine soundEngine = soundManagerAccessor.fullfud$getSoundEngine();
        if (!(soundEngine instanceof SoundEngineAccessor soundEngineAccessor)) {
            return null;
        }
        final Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel =
            soundEngineAccessor.fullfud$getInstanceToChannel();
        return instanceToChannel.get(instance);
    }

    private static int getSourceId(final Object channel) {
        if (!(channel instanceof ChannelAccessor channelAccessor)) {
            return 0;
        }
        return channelAccessor.fullfud$getSource();
    }

    private static float clamp01(final float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
