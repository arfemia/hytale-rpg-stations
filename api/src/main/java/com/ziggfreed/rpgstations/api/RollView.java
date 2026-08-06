package com.ziggfreed.rpgstations.api;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A read-only projection of ONE authored loot roll, handed to a {@link ValidationHook} through
 * {@link ValidationScope#allRolls()}. It exposes both halves a lint needs:
 * the REFERENCE STRUCTURE (which factor ids and which contribution channels the roll names) and
 * the FORMULA NUMBERS (chance percents, ladder floor thresholds, factor weights, contribution
 * amounts), so a mod that owns a channel or a factor family can check its own composition rules
 * without this engine knowing any of them.
 *
 * <p>Every accessor returns a plain immutable snapshot taken at validate time; a hook may retain
 * whatever it likes. An interface rather than a record on purpose: it is the view most likely to
 * grow, and a new default-bodied accessor is the one post-freeze addition shape the growth policy
 * allows (see {@code api/CLAUDE.md}).
 */
public interface RollView {

    /** The id of the asset that owns this roll (station, action, lootable, or roll pool). */
    @Nonnull
    String ownerId();

    /**
     * The authoring path this roll sits at, e.g. {@code "Station[sawmill].Loot.Rolls[0]"} - the
     * same label the engine's own findings use, so a hook's message points an author at the exact
     * block to edit.
     */
    @Nonnull
    String site();

    /** The effective trigger, {@code "Cycle"} or {@code "Completion"} (reader-default applied). */
    @Nonnull
    String trigger();

    /**
     * EVERY factor reference in this roll, flattened across {@code Conditions},
     * {@code Chance.AddFactors} and {@code Ladder.Values}, in that order. This is the list a
     * double-count lint walks: a factor family owner decides for itself which combinations of
     * {@code (factor, param)} are redundant.
     */
    @Nonnull
    List<FactorRefView> factors();

    /** The authored {@code Chance.BasePercent}; null when unauthored (or no Chance group at all). */
    @Nullable
    Double chanceBasePercent();

    /** The authored {@code Chance.CapPercent}; null when unauthored (or no Chance group at all). */
    @Nullable
    Double chanceCapPercent();

    /**
     * The {@code Ladder.Values} references, summed engine-side before the floor lookup; empty when
     * the roll authors no ladder.
     */
    @Nonnull
    List<FactorRefView> ladderValues();

    /** The ladder's floors in authored order; empty when the roll authors no ladder. */
    @Nonnull
    List<LadderFloorView> ladderFloors();

    /**
     * The roll's TOP-LEVEL {@code Grants.Contributions} entries (a floor's own contributions are
     * on that floor's {@link LadderFloorView}). One-shot and unscaled by contract.
     */
    @Nonnull
    List<StationContribution> contributions();

    /**
     * Every contribution channel this roll names, lowercased - top-level grants plus every ladder
     * floor's grants. The cheap "is this roll any of my business" test for a channel owner.
     */
    @Nonnull
    Set<String> contributionChannels();

    /** One ladder floor: its threshold plus the one-shot contributions it grants at that floor. */
    record LadderFloorView(@Nullable Double min, @Nonnull List<StationContribution> contributions) {
    }
}
