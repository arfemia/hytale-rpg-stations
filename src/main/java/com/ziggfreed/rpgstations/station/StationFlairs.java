package com.ziggfreed.rpgstations.station;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 * flair's non-null leaves onto the station's base moment presentation, per LEAF. The overlay
 * folds this mod's own {@link Presentation} shape, {@link Presentation.Shake} included.
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
 * #stepMomentId} builds the per-step {@code Step:<ActionId>:<StepId>} id a multi-action
 * station's {@code Present} step resolves against. {@code Swing} and {@code Impact} are the two
 * cues one swing tick fires: the swing itself, and the strike landing behind it (whose lateness is
 * its own {@code Presentation.DelayMs}, nothing dedicated). They are separate ids precisely so a
 * flair can re-skin or re-time either one alone. The overlay-MERGE semantics themselves (per-leaf,
 * sorted-flair-id-order stacking) are the same for every id.
 *
 * <p>{@code DelayMs} overlays as an ordinary leaf, so a flair can re-time a moment as well as
 * re-skin it (and inherits the base moment's own timing when it authors none). A flair that wants
 * its cue to play AT ONCE over a delayed base moment must therefore author {@code DelayMs: 0}
 * explicitly - omitting the leaf inherits the base timing, it does not cancel it. The delay itself
 * is applied downstream of this fold, in {@code StationService#emitMoment}, so whichever
 * {@code Presentation} wins here is the one whose timing is honored.
 *
 * <p><b>Overlay semantics:</b> per LEAF, an unlocked flair's non-null value replaces the
 * current value; a leaf the flair omits falls through untouched. A flair on a moment with NO
 * base presentation ADDS one. Multiple unlocked flairs STACK, applied in SORTED flair-id
 * order (a later id's non-null leaf wins over an earlier id's on the same leaf). An unlocked
 * id with no matching entry in the station's effective flair map is silently ignored.
 *
 * <p><b>Moment ids are written {@code Is_Like_This} and matched case-insensitively.</b> Every id
 * this engine mints is underscore-separated PascalCase ({@code Cycle}, {@code Rare_Find},
 * {@code Refused:No_Materials}, {@code Step:Mill:Chop}), the same shape Hytale's own ids take, and
 * that is the spelling every constant here, every shipped file and every document shows. Matching
 * never depends on it: every moment map the engine holds is built through
 * {@link #caseInsensitiveMomentKeys}, which keeps each key's authored spelling and answers a lookup
 * under any casing, so a pack authoring {@code cycle} resolves exactly as one authoring
 * {@code Cycle} does, with no validator refusal and no rewrite. The prefix checks here fold case
 * the same way. Flair ids meet in one lowercase namespace ({@link FlairCatalog} lowercases the
 * authored keys and this class lowercases whatever a provider hands back), which is what lets a
 * single {@code FlairUnlockProvider} satisfy both authoring routes.
 */
public final class StationFlairs {

    /** The cycle-complete moment, played per finished (real or idle) cycle at the block. */
    public static final String MOMENT_CYCLE = "Cycle";
    /** The per-swing cue, fired together with the work animation re-fire. */
    public static final String MOMENT_SWING = "Swing";
    /** The delayed swing-impact cue (design 9.6 - split off {@link #MOMENT_SWING} this leg). */
    public static final String MOMENT_IMPACT = "Impact";
    /** A reached loot-ladder floor's flourish. */
    public static final String MOMENT_RARE_FIND = "Rare_Find";
    /** The session-completion moment, played at the player's own position. */
    public static final String MOMENT_COMPLETION = "Completion";
    /** A produced batch's doneness window opening: output now waits Ready in its custody pile. */
    public static final String MOMENT_READY = "Ready";
    /** A doneness window expiring: the waiting pile collapsed to its authored Overdone items. */
    public static final String MOMENT_OVERDONE = "Overdone";
    /**
     * A press the station turned away, whatever the reason - the catch-all a per-reason
     * {@link #refusedMomentId} falls through to, per leaf. Played by {@link StationRefusals}.
     */
    public static final String MOMENT_REFUSED = "Refused";

    private static final Set<String> WELL_KNOWN_MOMENT_IDS = caseInsensitiveSet(
            MOMENT_CYCLE, MOMENT_SWING, MOMENT_IMPACT, MOMENT_RARE_FIND, MOMENT_COMPLETION,
            MOMENT_READY, MOMENT_OVERDONE, MOMENT_REFUSED);

    /**
     * The prefix of a PER-REASON refusal moment id, {@code Refused:<Reason>}, where {@code Reason}
     * is the refusal's own wording key tail (the part of {@code ui.station.<reason>} after the
     * dot) in id casing: {@code Refused:No_Materials}, {@code Refused:Wrong_Tool},
     * {@code Refused:Occupied}. A per-reason entry overlays the bare {@link #MOMENT_REFUSED} entry
     * per leaf, so an author dresses one reason differently without restating the rest.
     */
    public static final String REFUSED_MOMENT_PREFIX = MOMENT_REFUSED + ":";

    /**
     * The prefix an author uses to mint a moment id of their own: a loot roll's {@code Cue} names a
     * moment, and a station that wants a jackpot to sound different from an ordinary find needs
     * more moment ids than this engine can usefully enumerate.
     *
     * <p>It works exactly like {@link #stepMomentId}'s prefix - an id under it always passes the
     * typo check, so a well-known id stays spell-checked while an author-defined one is free. Name
     * it in the roll's {@code Cue} and again as a key in the action's {@code Moments} map (or in a
     * flair), and the two meet at the emission.
     */
    public static final String CUE_MOMENT_PREFIX = "Cue:";

    private static final String STEP_MOMENT_PREFIX = "Step:";

    private StationFlairs() {
    }

    /**
     * Builds the per-step moment id {@code Step:<ActionId>:<StepId>} (design section 9.6). Both
     * parts keep their authored spelling ({@code Step:Mill:Chop}); matching is case-insensitive
     * wherever the id is looked up, so the spelling is presentation, never identity.
     */
    @Nonnull
    public static String stepMomentId(@Nonnull String actionId, @Nonnull String stepId) {
        return STEP_MOMENT_PREFIX + actionId + ":" + stepId;
    }

    /**
     * Builds the per-reason refusal moment id {@code Refused:<Reason>} (see
     * {@link #REFUSED_MOMENT_PREFIX}); {@code reason} arrives in id casing from
     * {@link StationRefusals#reasonOf} and is kept as given.
     */
    @Nonnull
    public static String refusedMomentId(@Nonnull String reason) {
        return REFUSED_MOMENT_PREFIX + reason;
    }

    /** Whether {@code momentId} is the bare {@link #MOMENT_REFUSED} or a {@code Refused:<Reason>} id, under any casing. */
    public static boolean isRefusalMomentId(@Nullable String momentId) {
        return momentId != null
                && (MOMENT_REFUSED.equalsIgnoreCase(momentId) || hasPrefix(momentId, REFUSED_MOMENT_PREFIX));
    }

    /**
     * {@code moments} as a CASE-INSENSITIVE map that keeps every key's authored spelling, dropping
     * blank keys and null values: the ONE shape every moment map in this engine is held in - a
     * station's inline {@code Flairs} entry, a standalone {@code FlairAsset}, an action's own
     * {@code Moments}, the settings' engine-wide defaults, a pattern's cues at refusal time.
     *
     * <p>Moment ids are matched by plain map lookup at play time, so building the map here (rather
     * than folding case at every lookup) is what makes a key authored {@code cycle} resolve
     * against {@link #MOMENT_CYCLE} instead of validating as a known id and then silently never
     * matching, while the spelling an author chose is what the map still shows. A later duplicate
     * under a different casing keeps the FIRST spelling and takes the later value, the same
     * later-wins rule the rest of this schema follows.
     */
    @Nonnull
    public static Map<String, Presentation> caseInsensitiveMomentKeys(@Nonnull Map<String, Presentation> moments) {
        Map<String, Presentation> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, Presentation> e : moments.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
                continue;
            }
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * Whether {@code momentId} is a PER-STEP id ({@code Step:<ActionId>:<StepId>}) rather than one of
     * the well-known engine moments - the one class of moment id an action's own {@code Moments} map
     * may drive a step's iteration-entry cue with.
     */
    public static boolean isStepMomentId(@Nullable String momentId) {
        return momentId != null && hasPrefix(momentId, STEP_MOMENT_PREFIX);
    }

    /**
     * Whether {@code momentId} is a RECOGNIZED id: one of the well-known engine constants
     * (case-insensitive) or an id under one of the three open prefixes - {@code Step:} (per step),
     * {@code Cue:} (author-minted loot cues), {@code Refused:} (per refusal reason). An unrecognized
     * id is never an error (design 9.6 - "future engine moments must not break old packs") -
     * callers use this ONLY for a validator warning surfacing a likely typo, never to reject
     * content.
     */
    public static boolean isKnownMomentId(@Nullable String momentId) {
        if (momentId == null || momentId.isBlank()) {
            return false;
        }
        return WELL_KNOWN_MOMENT_IDS.contains(momentId)
                || hasPrefix(momentId, STEP_MOMENT_PREFIX)
                || hasPrefix(momentId, CUE_MOMENT_PREFIX)
                || hasPrefix(momentId, REFUSED_MOMENT_PREFIX);
    }

    /**
     * Leaf-overlay resolution: {@code base} leaves, overridden by each of the player's
     * unlocked flairs' non-null leaves for {@code momentId}, applied in sorted flair-id order.
     * Returns {@code base} (possibly {@code null}) UNCHANGED whenever there is nothing to
     * overlay - the zero-cost common path under the default provider. {@code flairs} is the
     * ALREADY-MERGED {@code flairId -> momentId -> Presentation} map ({@link
     * FlairCatalog#effectiveFlairsFor}, every inner map case-insensitive by construction) - this
     * method itself is decoupled from either source asset type.
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

        // The per-leaf rule itself is Presentation.overlaid - the ONE overlay every layered
        // presentation in this engine resolves through; this loop only decides WHICH flair
        // moments stack, and in what order. A flair authoring no leaf at all still overlays
        // (rebuilding the base leaf for leaf), which is what keeps a leaf added to Presentation
        // covered by the parity guard rather than silently dropped.
        Presentation out = base;
        for (String flairId : new TreeSet<>(lowercased(unlockedIds))) {
            Map<String, Presentation> moments = flairs.get(flairId);
            if (moments == null) {
                continue; // unlocked id with no matching effective flair - ignored, never an error
            }
            Presentation momentPresentation = moments.get(momentId);
            if (momentPresentation == null) {
                continue;
            }
            out = out == null ? Presentation.overlaid(new Presentation(), momentPresentation)
                    : Presentation.overlaid(out, momentPresentation);
        }
        return out;
    }

    /** Whether {@code id} starts with {@code prefix} under any casing. */
    private static boolean hasPrefix(@Nonnull String id, @Nonnull String prefix) {
        return id.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    @Nonnull
    private static Set<String> caseInsensitiveSet(@Nonnull String... ids) {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Collections.addAll(out, ids);
        return Collections.unmodifiableSet(out);
    }

    /**
     * {@code ids} lowercased, dropping nulls/blanks - applied to whatever a registered
     * {@code FlairUnlockProvider} hands back, so a provider's own spelling never has to match the
     * canonicalized flair ids {@link FlairCatalog} folds.
     */
    @Nonnull
    private static Set<String> lowercased(@Nonnull Set<String> ids) {
        Set<String> out = new TreeSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                out.add(id.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }
}
