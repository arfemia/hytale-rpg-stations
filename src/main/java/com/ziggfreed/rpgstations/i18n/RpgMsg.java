package com.ziggfreed.rpgstations.i18n;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;
import com.ziggfreed.common.i18n.Msg;

/**
 * RpgStations' own prefix-free facade over the mod-agnostic {@code ziggfreed-common}
 * {@link Msg} (design section 4.7/6.2: the common {@code i18n.Msg} carries no fixed
 * namespace of its own so several consumer mods share it; a consumer wanting a
 * prefix-free call site wraps it - mirroring how the MMO's own {@code i18n.Msg} reads).
 * Message ids resolve as {@code rpgstations.<key>} against
 * {@code Server/Languages/<bcp47>/rpgstations.lang}.
 */
public final class RpgMsg {

    private static final String PREFIX = "rpgstations.";

    private RpgMsg() {
    }

    /** A translation {@link Message} for {@code "rpgstations." + key}, with {@code {0},{1},...} bound from {@code args}. */
    @Nonnull
    public static Message tr(@Nonnull String key, @Nonnull Object... args) {
        return Msg.tr(PREFIX, key, args);
    }
}
