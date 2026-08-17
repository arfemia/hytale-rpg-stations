package com.ziggfreed.rpgstations.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.util.GuardedLogger;
import com.ziggfreed.rpgstations.RpgStationsPlugin;

/**
 * RPG Stations' own logging facade over {@link RpgStationsPlugin#LOGGER} - this mod's single
 * logging seam, never another mod's.
 *
 * <p>A thin static wrapper over one shared {@link GuardedLogger} instance (no prefix). See
 * {@link GuardedLogger} for the guard itself: the raw flogger {@code LOGGER} throws when no Hytale
 * log manager is installed (a unit-test JVM), and the resulting {@link Error} escapes
 * {@code catch (Exception)} blocks and crashes the test; routing every call through one guarded
 * instance is what keeps a parse / validate / hot per-tick path unit-reachable.
 */
public final class Log {

    private static final GuardedLogger DELEGATE = new GuardedLogger(() -> RpgStationsPlugin.LOGGER, "");

    private Log() {
    }

    public static void info(@Nonnull String message) {
        DELEGATE.info(message);
    }

    public static void info(@Nonnull String message, @Nullable Throwable cause) {
        DELEGATE.info(message, cause);
    }

    public static void warn(@Nonnull String message) {
        DELEGATE.warn(message);
    }

    public static void warn(@Nonnull String message, @Nullable Throwable cause) {
        DELEGATE.warn(message, cause);
    }

    public static void severe(@Nonnull String message) {
        DELEGATE.severe(message);
    }

    public static void severe(@Nonnull String message, @Nullable Throwable cause) {
        DELEGATE.severe(message, cause);
    }

    public static void fine(@Nonnull String message) {
        DELEGATE.fine(message);
    }

    public static void fine(@Nonnull String message, @Nullable Throwable cause) {
        DELEGATE.fine(message, cause);
    }
}
