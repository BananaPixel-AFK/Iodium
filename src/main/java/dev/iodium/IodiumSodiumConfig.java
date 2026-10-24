package dev.iodium;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Registers the "Iodium" page in Sodium 0.8+'s Video Settings screen.
 * If Sodium is not installed, this class is never loaded.
 */
public final class IodiumSodiumConfig implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        IodiumConfig config = IodiumConfig.get();

        builder.registerOwnModOptions()
            .setIcon(Identifier.parse("iodium:textures/gui/icon.png"))
            .addPage(builder.createOptionPage()
                .setName(Component.literal("Iodium"))
                .addOptionGroup(builder.createOptionGroup()
                    .setName(Component.literal("Diagnostics"))
                    .addOption(builder.createBooleanOption(Identifier.parse("iodium:diagnostics"))
                        .setName(Component.literal("Tick Diagnostics"))
                        .setTooltip(Component.literal(
                            "Measures how long each client tick takes and counts stutters "
                            + "(ticks of 50 ms or more). Turn off to pause measuring and undo any "
                            + "temporary changes Iodium made. View the results with /iodium status."))
                        .setStorageHandler(config::onSodiumSave)
                        .setBinding(config::setEnabled, config::isEnabled)
                        .setDefaultValue(true))
                    .addOption(builder.createBooleanOption(Identifier.parse("iodium:smooth_profile"))
                        .setName(Component.literal("Smooth Profile"))
                        .setTooltip(Component.literal(
                            "Use the smooth profile instead of balanced. Smooth reacts to stutter "
                            + "sooner when Adaptive Chunk Throttle is enabled."))
                        .setStorageHandler(config::onSodiumSave)
                        .setBinding(config::setSmooth, config::isSmooth)
                        .setDefaultValue(false))
                )
                .addOptionGroup(builder.createOptionGroup()
                    .setName(Component.literal("Optimizations"))
                    .addOption(builder.createBooleanOption(Identifier.parse("iodium:smart_gc"))
                        .setName(Component.literal("Smart GC Trigger"))
                        .setTooltip(Component.literal(
                            "Runs a memory cleanup (garbage collection) after about a minute of "
                            + "standing still, so it is less likely to happen while you are moving."))
                        .setStorageHandler(config::onSodiumSave)
                        .setBinding(config::setSmartGC, config::isSmartGC)
                        .setDefaultValue(true))
                    .addOption(builder.createBooleanOption(Identifier.parse("iodium:adaptive_chunk_throttle"))
                        .setName(Component.literal("Adaptive Chunk Throttle"))
                        .setTooltip(Component.literal(
                            "If client ticks stutter repeatedly, lowers the render distance by 1 chunk "
                            + "(never below the minimum set in config/iodium.json) and raises it again "
                            + "gradually once things calm down. Your original value is restored when "
                            + "the game closes."))
                        .setStorageHandler(config::onSodiumSave)
                        .setBinding(config::setAdaptiveChunkThrottle, config::isAdaptiveChunkThrottle)
                        .setDefaultValue(true))
                    .addOption(builder.createBooleanOption(Identifier.parse("iodium:limit_background_fps"))
                        .setName(Component.literal("Limit Background FPS"))
                        .setTooltip(Component.literal(
                            "Lowers the FPS limit (10 by default, configurable in config/iodium.json) "
                            + "while the game window is not focused, then restores it when you come back."))
                        .setStorageHandler(config::onSodiumSave)
                        .setBinding(config::setLimitBackgroundFps, config::isLimitBackgroundFps)
                        .setDefaultValue(true))
                )
            );
    }
}
