package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.Message;

/**
 * ONE line of what an enhancement stamp actually applied: an opaque stat id, the whole-point value
 * written, and an OPTIONAL fully-styled client-resolved {@code label}.
 *
 * <p>{@link #statId}/{@link #points} are the structured half a consumer reacts to programmatically
 * (an achievement, an item diff), and they are always present because the engine rolled them.
 *
 * <p>{@link #label} is null by default, and that is the honest shape: what a stamped stat MEANS -
 * its display name, its wording, its colour - belongs to whichever mod owns that stat vocabulary,
 * and this engine has none. A label that IS present is rendered VERBATIM, colour and all; with none,
 * the session summary paints the stat id and its point value plainly, which is a correct if
 * unglamorous report and never an empty row.
 *
 * <p>A stamp step produces its lines through {@link #of}, which leaves the label unset - the stamp
 * write boundary reports what it wrote, not how to say it. Consumer-composed summary rows live on
 * {@link SummaryEnricherRegistry} alongside every other one.
 */
public record EnhanceLine(@Nonnull String statId, int points, @Nullable Message label) {

    /** The structured-only line: the stat id and its points, with the display left to a consumer. */
    @Nonnull
    public static EnhanceLine of(@Nonnull String statId, int points) {
        return new EnhanceLine(statId, points, null);
    }
}
