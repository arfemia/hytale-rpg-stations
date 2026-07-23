package com.ziggfreed.rpgstations.api;

import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * {@link EnhanceStamper#apply}'s return (design section 9.5, phase 2 round-7 D-6): the mutated
 * stack PLUS the provider's authoritative enhancements-metadata report - one {@link EnhanceLine}
 * per stat the stamp actually wrote (an empty list = applied silently / nothing to report). The
 * lines are what the standalone RpgStations session summary renders for a stamp, so a bare anvil
 * with a registered stamper reports its enhancement with zero MMO stat vocabulary leaking into the
 * engine.
 *
 * <p>Reshaped from the pre-round-7 bare {@code ItemStack} return - a legal pre-1.0.0 api break (the
 * api freezes only once RpgStations 1.0.0 releases; both mods are unreleased, so no shim is needed).
 */
public record StampResult(@Nonnull ItemStack stack, @Nonnull List<EnhanceLine> lines) {

    public StampResult(@Nonnull ItemStack stack, @Nonnull List<EnhanceLine> lines) {
        this.stack = stack;
        this.lines = List.copyOf(lines);
    }

    /** A result that applied nothing reportable (durability-only stamps, or a silent write). */
    @Nonnull
    public static StampResult of(@Nonnull ItemStack stack) {
        return new StampResult(stack, List.of());
    }
}
