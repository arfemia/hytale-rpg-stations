package com.ziggfreed.rpgstations.station;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.api.impl.FlairUnlockRegistryImpl;
import com.ziggfreed.rpgstations.asset.Presentation;

/**
 * The achievement/reward flair override seam. A station may author NAMED cosmetic flair
 * layers ({@code StationAsset#getFlairs}, plus any standalone {@code asset.FlairAsset} folded
 * onto it - see {@link FlairCatalog#effectiveFlairsFor}); a grantor (any run-a-command reward
 * system) unlocks a flair id for a player, and {@link #effective} overlays every unlocked
 * flair's non-null leaves onto the station's base moment presentation, per LEAF. Ported verbatim
 * from the MMO's {@code station.StationFlairs} (RPG Stations extraction leg 2); {@code
 * asset.type.Presentation} severed to RpgStations' own {@link Presentation} (which drops
 * {@code Feedback} and gains {@link Presentation.Shake} - the overlay now folds {@code Shake}
 * instead of {@code Feedback}).
 *
 * <p><b>Leg 4:</b> the single-provider {@code UnlockProvider}/{@code setProvider} seam is
 * RETIRED (design section 3.2/9.6) in favor of the api's {@link FlairUnlockRegistryImpl} UNION -
 * every registered {@code FlairUnlockProvider} answers "which flair ids has this player
 * unlocked, across every station"; THIS class does the per-station filtering by checking each
 * unlocked id against the station's own effective flair map (unchanged). No provider
 * registered = empty union = base presentations only, the same zero-content starting state the
 * old default provider gave.
 *
 * <p><b>Leg F (design section 9.6):</b> the fixed {@code Slot} enum ({@code CYCLE}/{@code SWING}/
 * {@code RARE_FIND}/{@code COMPLETION}) is RETIRED in favor of an open STRING moment id - the
 * open flair/moment vocabulary. {@link #MOMENT_CYCLE}/{@link #MOMENT_SWING}/{@link #MOMENT_IMPACT}/
 * {@link #MOMENT_RARE_FIND}/{@link #MOMENT_COMPLETION} are the engine's own well-known ids
 * (constants, not an enum - nothing hardcodes the vocabulary as a closed set); {@link
 * #stepMomentId} builds the per-step {@code step:<actionId>:<stepId>} id a multi-action
 * station's {@code Present} step resolves against. {@code impact} is a NEW id this leg, split
 * off from {@code swing} (previously the delayed swing-impact cue reused the {@code SWING} slot
 * verbatim) - a deliberate widening of the vocabulary, not a behavior regression: no shipped
 * content authors a {@code swing}-keyed flair expecting it to also cover the impact cue
 * (unreleased, unshipped feature), and a flair author can now target either cue independently.
 * The overlay-MERGE semantics themselves (per-leaf, sorted-flair-id-order stacking) are
 * UNCHANGED - only the slot identity moved from a closed enum to an open string.
 *
 * <p><b>Overlay semantics:</b> per LEAF, an unlocked flair's non-null value replaces the
 * current value; a leaf the flair omits falls through untouched. A flair on a moment with NO
 * base presentation ADDS one. Multiple unlocked flairs STACK, applied in SORTED flair-id
 * order (a later id's non-null leaf wins over an earlier id's on the same leaf). An unlocked
 * id with no matching entry in the station's effective flair map is silently ignored.
 */
public final class StationFlairs {

    /** The cycle-complete moment, played per finished (real or idle) cycle at the block. */
    public static final String MOMENT_CYCLE = "cycle";
    /** The per-swing cue, fired together with the work animation re-fire. */
    public static final String MOMENT_SWING = "swing";
    /** The delayed swing-impact cue (design 9.6 - split off {@link #MOMENT_SWING} this leg). */
    public static final String MOMENT_IMPACT = "impact";
    /** A reached loot-ladder floor's flourish. */
    public static final String MOMENT_RARE_FIND = "rare_find";
    /** The session-completion moment, played at the player's own position. */
    public static final String MOMENT_COMPLETION = "completion";

    private static final Set<String> WELL_KNOWN_MOMENT_IDS = Set.of(
            MOMENT_CYCLE, MOMENT_SWING, MOMENT_IMPACT, MOMENT_RARE_FIND, MOMENT_COMPLETION);

    private static final String STEP_MOMENT_PREFIX = "step:";

    private StationFlairs() {
    }

    /** Builds the per-step moment id {@code step:<actionId>:<stepId>} (design section 9.6). */
    @Nonnull
    public static String stepMomentId(@Nonnull String actionId, @Nonnull String stepId) {
        return STEP_MOMENT_PREFIX + actionId + ":" + stepId;
    }

    /**
     * Whether {@code momentId} is a RECOGNIZED id: one of the 5 well-known engine constants
     * (case-insensitive) or a {@code step:}-prefixed per-step id. An unrecognized id is never an
     * error (design 9.6 - "future engine moments must not break old packs") - callers use this
     * ONLY for a validator warning surfacing a likely typo, never to reject content.
     */
    public static boolean isKnownMomentId(@Nullable String momentId) {
        if (momentId == null || momentId.isBlank()) {
            return false;
        }
        String lower = momentId.toLowerCase(Locale.ROOT);
        return WELL_KNOWN_MOMENT_IDS.contains(lower) || lower.startsWith(STEP_MOMENT_PREFIX);
    }

    /**
     * Leaf-overlay resolution: {@code base} leaves, overridden by each of the player's
     * unlocked flairs' non-null leaves for {@code momentId}, applied in sorted flair-id order.
     * Returns {@code base} (possibly {@code null}) UNCHANGED whenever there is nothing to
     * overlay - the zero-cost common path under the default provider. {@code flairs} is the
     * ALREADY-MERGED {@code flairId -> momentId -> Presentation} map ({@link
     * FlairCatalog#effectiveFlairsFor}) - this method itself is decoupled from either source
     * asset type.
     */
    @Nullable
    public static Presentation effective(@Nullable Presentation base,
                                         @Nullable Map<String, Map<String, Presentation>> flairs,
                                         @Nonnull String momentId, @Nonnull UUID playerUuid,
                                         @Nonnull String stationId) {
        if (flairs == null || flairs.isEmpty()) {
            return base;
        }
        Set<String> unlockedIds = FlairUnlockRegistryImpl.getInstance().unlockedFlairIds(playerUuid);
        if (unlockedIds == null || unlockedIds.isEmpty()) {
            return base;
        }

        String sound = base != null ? base.getSound() : null;
        String particles = base != null ? base.getParticles() : null;
        String animation = base != null ? base.getAnimation() : null;
        String animationItem = base != null ? base.getAnimationItem() : null;
        String animationSlot = base != null ? base.getAnimationSlot() : null;
        String camera = base != null ? base.getCamera() : null;
        Presentation.Shake shake = base != null ? base.getShake() : null;
        boolean overlaidAny = false;

        for (String flairId : new TreeSet<>(unlockedIds)) {
            if (flairId == null) {
                continue;
            }
            Map<String, Presentation> moments = flairs.get(flairId);
            if (moments == null) {
                continue; // unlocked id with no matching effective flair - ignored, never an error
            }
            Presentation momentPresentation = moments.get(momentId);
            if (momentPresentation == null) {
                continue;
            }
            overlaidAny = true;
            if (momentPresentation.getSound() != null) {
                sound = momentPresentation.getSound();
            }
            if (momentPresentation.getParticles() != null) {
                particles = momentPresentation.getParticles();
            }
            if (momentPresentation.getAnimation() != null) {
                animation = momentPresentation.getAnimation();
            }
            if (momentPresentation.getAnimationItem() != null) {
                animationItem = momentPresentation.getAnimationItem();
            }
            if (momentPresentation.getAnimationSlot() != null) {
                animationSlot = momentPresentation.getAnimationSlot();
            }
            if (momentPresentation.getCamera() != null) {
                camera = momentPresentation.getCamera();
            }
            if (momentPresentation.getShake() != null) {
                shake = momentPresentation.getShake();
            }
        }

        if (!overlaidAny) {
            return base;
        }
        return Presentation.of(sound, particles, animation, animationItem, animationSlot, camera, shake);
    }
}
