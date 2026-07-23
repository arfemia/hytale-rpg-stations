package com.ziggfreed.rpgstations.station;

import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.ziggfreed.rpgstations.api.EnhanceLine;

/**
 * ONE committed enhancement stamp's engine-side record (design section 9.5, phase 2 round-7 D-6):
 * what the Stamp step wrote onto a custody item, captured by
 * {@code StationStepHandlers.StampHandler} AFTER the mutated stack is written back to the claim
 * (so it only ever records a committed enhancement, never a cancelled/denied ritual). Session-
 * scoped, never persisted - lives on {@link StationSession#enhanceOutcomes} for the duration of
 * the session, exactly like every other session field, and drives BOTH the standalone summary's
 * enhance ledger rows ({@code StationService#enhanceLedgerRows}) AND the api's
 * {@code StationEnhanceCompletedEvent}.
 *
 * <p>{@link #before}/{@link #after} are immutable {@code ItemStack} copies (the "copy-of-item"
 * report); {@link #lines} is the provider's own opaque-stat report (the "enhancements-metadata"
 * report) rendered VERBATIM - RpgStations never interprets either, keeping the engine free of MMO
 * stat vocabulary. {@link #durabilityAdded} is the station's own authored {@code Durability.AddMax}
 * delta, which RpgStations DOES own (durability is native, real without any stamper).
 */
record StationEnhanceOutcome(@Nonnull String itemId, @Nonnull ItemStack before, @Nonnull ItemStack after,
        @Nonnull List<EnhanceLine> lines, double durabilityAdded) {

    StationEnhanceOutcome(@Nonnull String itemId, @Nonnull ItemStack before, @Nonnull ItemStack after,
            @Nonnull List<EnhanceLine> lines, double durabilityAdded) {
        this.itemId = itemId;
        this.before = before;
        this.after = after;
        this.lines = List.copyOf(lines);
        this.durabilityAdded = durabilityAdded;
    }
}
