package com.ziggfreed.rpgstations.api;

import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * The Stamp step's {@code Stats} leaf delegate (design section 9.5): RpgStations owns the roll +
 * cap MATH (weighted pick, {@code Picks}/{@code Unique}, the M2-fixed cap composition), but never
 * the item-format write - a registered stamper is the ONE thing that knows how "N points of stat
 * X" gets encoded onto a stack. Deliberately a thin, format-agnostic 2-method contract (a
 * simplification of the design doc's literal {@code StampContext}/{@code StampResult} prose - this
 * api is unfrozen pre-1.0.0 and free to reshape) instead of a richer context object: RpgStations
 * never needs anything back from the stamper except these two reads/writes.
 *
 * <p>No stamper registered = the {@code Stats} leaf no-ops entirely (a validator/runtime-audit
 * warning fires once) while {@code Durability} still lands - the standalone anvil (no MMO) stamps
 * native per-stack durability only, exactly as design 9.5 specifies.
 */
public interface EnhanceStamper {

    /**
     * Read-only: {@code stack}'s enhancement state BEFORE this stamp attempt. Called by
     * {@code station.StampCapEngine} during the Stamp step's COMPUTE phase (design 9.5's M5 fix -
     * zero mutation here); must not throw for a bare/never-enhanced stack ({@link StampInspection#empty()}
     * is the correct answer, not an exception).
     */
    @Nonnull
    StampInspection inspect(@Nonnull ItemStack stack);

    /**
     * Apply {@code entries} (already rolled + cap-clamped by RpgStations - the stamper never
     * re-derives caps) to a stack, returning a {@link StampResult}: the NEW stack (matches
     * {@code ItemStack}'s own immutable with-copy convention, e.g. {@code withMaxDurability}) PLUS
     * one {@link EnhanceLine} per stat actually written (empty = nothing reportable - the
     * enhancements-metadata report the standalone session summary renders and
     * {@code StationEnhanceCompletedEvent} carries). Called ONLY inside the Stamp step's COMMIT
     * phase, after every compute-phase validation already passed - a throw here is caught by the
     * caller and the whole step fails with custody restored to its exact pre-step contents (design
     * 9.5's M5 fix); RpgStations never inspects the returned lines' meaning, it renders their
     * provider-composed labels verbatim.
     */
    @Nonnull
    StampResult apply(@Nonnull ItemStack stack, @Nonnull List<StatRoll> entries);
}
