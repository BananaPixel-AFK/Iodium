package dev.iodium;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Iodium settings, stored in config/iodium.json. */
public final class IodiumConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Iodium");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("iodium.json");
    private static IodiumConfig instance;

    // false = profile OFF (measuring paused)
    private boolean enabled = true;
    // true = SMOOTH profile, false = BALANCED (only applies while enabled)
    private boolean smooth = false;

    // === Optimization features ===
    public boolean enableSmartGC = true;
    public boolean adaptiveChunkThrottle = true;
    public boolean limitBackgroundFps = true;
    public int backgroundFpsLimit = 10;       // multiples of 10, 10..260
    public int minRenderDistance = 6;         // Adaptive Chunk Throttle never goes below this

    public enum Profile {
        BALANCED, SMOOTH, OFF;

        public static Profile from(String value) {
            return switch (value) {
                case "balanced" -> BALANCED;
                case "smooth" -> SMOOTH;
                case "off" -> OFF;
                default -> null;
            };
        }
    }

    public static synchronized IodiumConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static IodiumConfig load() {
        if (Files.isRegularFile(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                IodiumConfig loaded = GSON.fromJson(reader, IodiumConfig.class);
                if (loaded != null) return loaded;
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Could not read iodium.json, using defaults", e);
            }
        }
        return new IodiumConfig();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not save iodium.json", e);
        }
    }

    /**
     * Called by Sodium after changes on the Iodium page are applied.
     * Uses varargs so it fits a StorageEventHandler of any parameter count.
     */
    public void onSodiumSave(Object... ignored) {
        save();
    }

    public Profile profile() {
        if (!enabled) return Profile.OFF;
        return smooth ? Profile.SMOOTH : Profile.BALANCED;
    }

    public void setProfile(Profile profile) {
        this.enabled = profile != Profile.OFF;
        if (profile != Profile.OFF) {
            this.smooth = profile == Profile.SMOOTH;
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { this.enabled = value; }
    public boolean isSmooth() { return smooth; }
    public void setSmooth(boolean value) { this.smooth = value; }

    public boolean isSmartGC() { return enableSmartGC; }
    public void setSmartGC(boolean value) { this.enableSmartGC = value; }
    public boolean isAdaptiveChunkThrottle() { return adaptiveChunkThrottle; }
    public void setAdaptiveChunkThrottle(boolean value) { this.adaptiveChunkThrottle = value; }
    public boolean isLimitBackgroundFps() { return limitBackgroundFps; }
    public void setLimitBackgroundFps(boolean value) { this.limitBackgroundFps = value; }
}
