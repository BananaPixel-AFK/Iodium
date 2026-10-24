package dev.iodium;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/**
 * Logic for Adaptive Chunk Throttle and Limit Background FPS.
 *
 * Both work by temporarily changing vanilla options (render distance and FPS
 * limit) and then restoring the original values. If the player changes one of
 * those options themselves, the player's choice becomes the new baseline.
 */
final class IodiumAdaptive {
    private static final int WINDOW_TICKS = 200;    // stutter observation window: 10 seconds
    private static final int RECOVER_TICKS = 1200;  // calm for 60 seconds -> raise by 1 chunk
    private static final int COOLDOWN_TICKS = 400;  // 20 seconds between reductions

    // --- Adaptive Chunk Throttle ---
    private static int originalDistance = -1;       // -1 = not changed by us
    private static int lastSetDistance = -1;
    private static int windowTicks;
    private static int windowStutters;
    private static int calmTicks;
    private static int cooldown;

    // --- Limit Background FPS ---
    private static int originalFps = -1;            // -1 = not changed by us
    private static int lastSetFps = -1;

    private IodiumAdaptive() {}

    static void tick(Minecraft client, IodiumConfig config, boolean stutter) {
        if (client.options == null) return;
        tickBackgroundFps(client, config);
        tickChunkThrottle(client, config, stutter);
    }

    /** Restores every option we changed (profile OFF, game closing, etc.). */
    static void restoreAll(Minecraft client) {
        if (client == null || client.options == null) return;
        restoreFps(client);
        restoreDistance(client);
        resetCounters();
    }

    // ---------------------------------------------------------------- FPS

    private static void tickBackgroundFps(Minecraft client, IodiumConfig config) {
        boolean background = config.limitBackgroundFps && !client.isWindowActive();
        if (!background) {
            restoreFps(client);
            return;
        }
        OptionInstance<Integer> option = client.options.framerateLimit();
        int current = option.get();
        int target = clampFps(config.backgroundFpsLimit);

        if (originalFps < 0) {
            originalFps = current;
        }
        if (current != target) {
            option.set(target);
        }
        lastSetFps = target;
    }

    private static void restoreFps(Minecraft client) {
        if (originalFps < 0) return;
        OptionInstance<Integer> option = client.options.framerateLimit();
        // If the player changed it while in the background, don't overwrite it.
        if (option.get() == lastSetFps) {
            option.set(originalFps);
        }
        originalFps = -1;
        lastSetFps = -1;
    }

    private static int clampFps(int value) {
        // The vanilla FPS slider uses multiples of 10, from 10 to 260 (260 = unlimited).
        int rounded = Math.round(value / 10f) * 10;
        return Math.max(10, Math.min(260, rounded));
    }

    // -------------------------------------------------------- Chunk throttle

    private static void tickChunkThrottle(Minecraft client, IodiumConfig config, boolean stutter) {
        if (!config.adaptiveChunkThrottle || client.level == null) {
            restoreDistance(client);
            resetCounters();
            return;
        }
        OptionInstance<Integer> option = client.options.renderDistance();
        int current = option.get();

        // The player changed render distance themselves -> use that value as the new baseline.
        if (originalDistance >= 0 && current != lastSetDistance) {
            originalDistance = -1;
            lastSetDistance = -1;
            resetCounters();
        }

        windowTicks++;
        if (stutter) {
            windowStutters++;
            calmTicks = 0;
        } else {
            calmTicks++;
        }
        if (cooldown > 0) cooldown--;

        // Don't change anything while a screen is open (for example Video Settings).
        boolean screenOpen = client.screen != null;

        if (windowTicks >= WINDOW_TICKS) {
            int threshold = config.profile() == IodiumConfig.Profile.SMOOTH ? 3 : 5;
            int floor = Math.max(2, config.minRenderDistance);
            if (!screenOpen && cooldown <= 0 && windowStutters >= threshold && current > floor) {
                if (originalDistance < 0) {
                    originalDistance = current;
                }
                int next = current - 1;
                option.set(next);
                lastSetDistance = next;
                cooldown = COOLDOWN_TICKS;
                calmTicks = 0;
            }
            windowTicks = 0;
            windowStutters = 0;
        }

        // Calm for long enough -> raise gradually back to the original value.
        if (!screenOpen && originalDistance >= 0 && calmTicks >= RECOVER_TICKS) {
            int next = Math.min(originalDistance, current + 1);
            option.set(next);
            if (next >= originalDistance) {
                originalDistance = -1;
                lastSetDistance = -1;
            } else {
                lastSetDistance = next;
            }
            calmTicks = 0;
        }
    }

    private static void restoreDistance(Minecraft client) {
        if (originalDistance < 0) return;
        OptionInstance<Integer> option = client.options.renderDistance();
        if (option.get() == lastSetDistance) {
            option.set(originalDistance);
        }
        originalDistance = -1;
        lastSetDistance = -1;
    }

    private static void resetCounters() {
        windowTicks = 0;
        windowStutters = 0;
        calmTicks = 0;
        cooldown = 0;
    }
}
