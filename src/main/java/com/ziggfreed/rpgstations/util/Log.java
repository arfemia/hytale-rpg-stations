package com.ziggfreed.rpgstations.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.RpgStationsPlugin;

/**
 * RPG Stations' own logging facade over {@link RpgStationsPlugin#LOGGER} (a port of
 * hyMMO's {@code util.SafeLog} and the kweebec-nightmare {@code util.SafeLog}; RPG
 * Stations never reaches for the MMO's facade).
 *
 * <p>The raw flogger {@code LOGGER} throws when no Hytale log manager is installed (a
 * unit-test JVM): the resulting {@link Error} escapes {@code catch (Exception)} blocks
 * and crashes the test. Every method here swallows any logging failure so a parse /
 * validate / hot per-tick path stays unit-reachable; the guard is zero-cost when
 * nothing throws.
 */
public final class Log {

    private Log() {
    }

    public static void info(@Nonnull String message) {
        try {
            RpgStationsPlugin.LOGGER.atInfo().log(message);
        } catch (Throwable ignored) {
            // no log manager (unit JVM) - swallow so the caller stays test-reachable
        }
    }

    public static void info(@Nonnull String message, @Nullable Throwable cause) {
        try {
            RpgStationsPlugin.LOGGER.atInfo().withCause(cause).log(message);
        } catch (Throwable ignored) {
        }
    }

    public static void warn(@Nonnull String message) {
        try {
            RpgStationsPlugin.LOGGER.atWarning().log(message);
        } catch (Throwable ignored) {
        }
    }

    public static void warn(@Nonnull String message, @Nullable Throwable cause) {
        try {
            RpgStationsPlugin.LOGGER.atWarning().withCause(cause).log(message);
        } catch (Throwable ignored) {
        }
    }

    public static void severe(@Nonnull String message) {
        try {
            RpgStationsPlugin.LOGGER.atSevere().log(message);
        } catch (Throwable ignored) {
        }
    }

    public static void severe(@Nonnull String message, @Nullable Throwable cause) {
        try {
            RpgStationsPlugin.LOGGER.atSevere().withCause(cause).log(message);
        } catch (Throwable ignored) {
        }
    }

    public static void fine(@Nonnull String message) {
        try {
            RpgStationsPlugin.LOGGER.atFine().log(message);
        } catch (Throwable ignored) {
        }
    }

    public static void fine(@Nonnull String message, @Nullable Throwable cause) {
        try {
            RpgStationsPlugin.LOGGER.atFine().withCause(cause).log(message);
        } catch (Throwable ignored) {
        }
    }
}
