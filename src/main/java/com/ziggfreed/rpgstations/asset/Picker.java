package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * The multi-output picker knob group (seam wave, decision 50): a cohesive nested group holding the
 * ONE orthogonal boolean that governs how a station's multi-category output PICKER renders
 * tool-gated categories. A single-category station never shows a picker at all, so this group is a
 * no-op there.
 *
 * <p>A top-level {@link StationAsset#getPicker()} default, whole-GROUP overridable per
 * {@link ActionDef} (the same whole-group-replace semantics every other station group uses -
 * {@code station.ActionResolver} is the resolution choke point). Decision 50 keeps this a
 * standalone nested group rather than a flat prefixed boolean so future picker knobs (ordering,
 * grouping) nest here without a schema smell.
 *
 * <p><b>DORMANT by data model (honest note):</b> {@code ShowLocked} is fully plumbed (schema +
 * {@code station.PickerCategories} locked-tab filtering), but NO producer of a LOCKED category
 * exists yet - {@code buildPickerCategories} emits only unlocked tabs, because the current data
 * model has no PER-CATEGORY tool gate (the {@code Tool} gate is per-action, not per-output-category).
 * So every rendered picker tab is unlocked today and the knob has no observable effect. It goes live
 * the moment per-conversion / per-category tool gates exist (a future leg); until then the default
 * ({@code true}) and {@code false} render identically.
 */
public final class Picker {

    @Nullable protected Boolean showLocked;

    public static final BuilderCodec<Picker> CODEC = BuilderCodec.builder(Picker.class, Picker::new)
            .appendInherited(new KeyedCodec<>("ShowLocked", Codec.BOOLEAN, false),
                    (o, v) -> o.showLocked = v, o -> o.showLocked, (o, p) -> o.showLocked = p.showLocked)
            .documentation("Whether tool-gated (locked) output categories render greyed in the picker (default true); false hides them entirely.").add()
            .build();

    public Picker() {
    }

    @Nonnull
    public static Picker of(@Nullable Boolean showLocked) {
        Picker p = new Picker();
        p.showLocked = showLocked;
        return p;
    }

    @Nullable
    public Boolean getShowLocked() {
        return showLocked;
    }

    /**
     * {@link #showLocked}, reader-defaulted to {@code true} (decision 50's default): a tool-gated
     * category renders greyed via the {@code ui.gate.locked_*} vocabulary naming the required tool.
     * {@code false} hides a locked category from the picker entirely.
     */
    public boolean effectiveShowLocked() {
        return showLocked == null || showLocked;
    }
}
