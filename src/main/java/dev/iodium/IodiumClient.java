package dev.iodium;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.iodium.IodiumConfig.Profile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class IodiumClient implements ClientModInitializer {
    private static final long STUTTER_THRESHOLD_NS = 50_000_000L; // 50 ms ~= below 20 updates/s
    private static long tickStartNs;
    private static long lastTickDurationNs;
    private static long maxTickDurationNs;
    private static long totalTickDurationNs;
    private static long tickSamples;
    private static long stutterCount;

    // Counts how long the player has been standing still (for Smart GC)
    private static int idleTicks = 0;

    @Override
    public void onInitializeClient() {
        IodiumConfig config = IodiumConfig.get();

        // Restore the original render distance / FPS limit when the game closes
        ClientLifecycleEvents.CLIENT_STOPPING.register(IodiumAdaptive::restoreAll);

        ClientTickEvents.START_CLIENT_TICK.register(client -> tickStartNs = System.nanoTime());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (config.profile() == Profile.OFF) {
                IodiumAdaptive.restoreAll(client);
                return;
            }
            if (tickStartNs == 0L) return;
            long duration = Math.max(0L, System.nanoTime() - tickStartNs);
            lastTickDurationNs = duration;
            maxTickDurationNs = Math.max(maxTickDurationNs, duration);
            totalTickDurationNs += duration;
            tickSamples++;

            boolean stutter = duration >= STUTTER_THRESHOLD_NS;
            if (stutter) {
                stutterCount++;
            }

            // Adaptive Chunk Throttle + Limit Background FPS
            IodiumAdaptive.tick(client, config, stutter);

            // Smart GC Trigger: clean up memory while the player stands still
            if (config.enableSmartGC && client.player != null && client.options != null) {
                boolean isIdle = !client.options.keyUp.isDown()
                    && !client.options.keyLeft.isDown()
                    && !client.options.keyDown.isDown()
                    && !client.options.keyRight.isDown()
                    && !client.options.keyJump.isDown();

                if (isIdle) {
                    idleTicks++;
                    // 1200 ticks = about 60 seconds
                    if (idleTicks >= 1200) {
                        System.gc();
                        idleTicks = 0;
                    }
                } else {
                    idleTicks = 0;
                }
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("iodium")
                .then(ClientCommandManager.literal("status")
                    .executes(context -> {
                        context.getSource().sendFeedback(Component.literal(statusLine(config)));
                        return 1;
                    }))
                .then(ClientCommandManager.literal("profile")
                    .then(ClientCommandManager.argument("mode", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("balanced");
                            builder.suggest("smooth");
                            builder.suggest("off");
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            String value = StringArgumentType.getString(context, "mode")
                                .toLowerCase(Locale.ROOT);
                            Profile parsed = Profile.from(value);
                            if (parsed == null) {
                                context.getSource().sendFeedback(Component.literal(
                                    "Iodium: mode must be balanced, smooth, or off."));
                                return 0;
                            }
                            config.setProfile(parsed);
                            config.save();
                            context.getSource().sendFeedback(Component.literal(
                                "Iodium profile set to " + parsed.name().toLowerCase(Locale.ROOT) + "."));
                            return 1;
                        }))
                )
            )
        );

        System.out.println("[Iodium] Initialized: tick diagnostics and optimizations ready.");
    }

    private static String statusLine(IodiumConfig config) {
        Profile profile = config.profile();
        if (profile == Profile.OFF) {
            return "Iodium: profile is OFF. Tick measuring is paused.";
        }
        double lastMs = lastTickDurationNs / 1_000_000.0;
        double maxMs = maxTickDurationNs / 1_000_000.0;
        double avgMs = tickSamples == 0 ? 0.0
            : (totalTickDurationNs / (double) tickSamples) / 1_000_000.0;
        return String.format(Locale.ROOT,
            "Iodium | profile=%s | last tick=%.2f ms | avg=%.2f ms | max=%.2f ms | ticks >=50ms=%d",
            profile.name().toLowerCase(Locale.ROOT), lastMs, avgMs, maxMs, stutterCount);
    }
}
