package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The PURE decision core of the doneness ready window (decision 87): a produced batch lands in a
 * custody pile, sits collectable for the resolved {@code Doneness.ReadyMs} of WORLD GAME TIME,
 * then - if nobody gathered it - collapses ONCE to the authored {@code Overdone} items. Zero
 * engine/store touch: every method here takes plain values, so the boundary math, the replacement
 * rule and the resting-state pick are unit-testable without a live server. The window RECORD lives
 * on the block's persisted stash ({@link StationCustodyClaim}'s doneness accessors: the stash-level
 * {@code ProgressGameTime} leaf is the window start, and the windowed pile carries
 * {@link #BATCHES_KEY} in its {@code PendingCycles} map); the impure settle orchestration is
 * {@code StationService#settleDoneness}, the ONE function every touch point (the
 * unattended pass included) calls.
 *
 * <p><b>The clock is game time, never wall clock</b>: game time stands still while the server is
 * down, so an outage advances a window by exactly zero - a batch mid-window at shutdown is exactly
 * as done at the next boot as it was at the stop (the native processing-bench precedent).
 *
 * <p><b>The quantity rule ("one pot, one fate"):</b> when a window expires, the WHOLE windowed
 * pile's counted tally is replaced by the authored {@code Overdone} entries, each entry's
 * {@code Quantity} multiplied by the number of BATCHES produced into the window (one batch = one
 * committed produce phase; each produce while the window is open re-stamps the clock and counts
 * one more batch). Everything in that pile burns together - including anything that was already
 * sitting in it - and nothing outside it is touched (per-socket isolation). The pile's owner and
 * its metadata-bearing {@code Unique} stack are never touched by a settle.
 */
final class StationDoneness {

    /**
     * The reserved {@code PendingCycles} key holding the open window's produced-batch count on the
     * windowed pile. The pile carrying this key IS the windowed pile - the stash-level
     * {@code ProgressGameTime} stamp says WHEN the window (re)started, this key says WHERE and HOW
     * MUCH. Removed when the window settles or the pile is gathered.
     */
    static final String BATCHES_KEY = "doneness:batches";

    /**
     * The reserved {@code PendingCycles} key marking a pile whose window expired and collapsed
     * (value 1): what keeps the block's {@code States.Overdone} look resolvable after the settle,
     * across restarts included. Cleared when a new window opens on the pile or the pile is
     * gathered.
     */
    static final String OVERDONE_KEY = "doneness:overdone";

    /**
     * The whole {@code PendingCycles} key namespace this window record reserves. Any OTHER
     * accrual key (the unattended pass's per-conversion counts,
     * {@link StationUnattended#ACCRUAL_KEY_PREFIX}) lives outside this prefix.
     */
    static final String RESERVED_KEY_PREFIX = "doneness:";

    private StationDoneness() {
    }

    /**
     * Has the window run its course? Boundary-exact: {@code elapsed >= readyMs} is overdone, one
     * millisecond less is still Ready. A non-positive {@code readyMs} never expires (no window).
     */
    static boolean expired(long windowStartGameMs, long nowGameMs, long readyMs) {
        return readyMs > 0 && nowGameMs - windowStartGameMs >= readyMs;
    }

    /**
     * The authored {@code Overdone} entries that actually degrade: the exact-{@code ItemId} route
     * only (the output route rule) - a route-less, {@code ResourceTypeId}, or {@code Tags} entry is
     * skipped, exactly as the decode-time warn announced. Empty when nothing valid is authored,
     * which is the purely-presentational window (nothing ever degrades).
     */
    @Nonnull
    static List<Ingredient> degradableOverdone(@Nullable StationAsset.Doneness resolved) {
        Ingredient[] overdone = resolved != null ? resolved.getOverdone() : null;
        if (overdone == null || overdone.length == 0) {
            return List.of();
        }
        List<Ingredient> out = new ArrayList<>(overdone.length);
        for (Ingredient entry : overdone) {
            if (entry != null && entry.hasItemRoute() && !entry.hasResourceRoute() && !entry.hasTagsRoute()) {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * The replacement tally an expired window's pile collapses to: each valid {@code Overdone}
     * entry's quantity times {@code batches} (floored at one batch), summed per item id in authored
     * order. The caller swaps this in for the pile's whole counted tally.
     */
    @Nonnull
    static Map<String, Integer> overdoneReplacement(@Nonnull List<Ingredient> degradable, int batches) {
        int scale = Math.max(1, batches);
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Ingredient entry : degradable) {
            out.merge(entry.getItemId(), entry.effectiveQuantity() * scale, Integer::sum);
        }
        return out;
    }

    /**
     * The block's RESTING interaction-state name - what a block should look like when no work step
     * is actively running at it - for the given custody contents. Precedence: an EMPTY claim is
     * {@code Empty}; an open ready window prefers {@code Ready}; a collapsed (overdone-marked)
     * pile prefers {@code Overdone}; everything else is {@code Loaded}. An unauthored preferred
     * leaf falls through to {@code Loaded} (the pile IS loaded, the special look is just not
     * authored), and a null {@code states} answers null (no flip at all).
     */
    @Nullable
    static String restingStateName(@Nullable Custody.States states, boolean nonEmpty,
            boolean windowOpen, boolean overdoneMarked) {
        if (states == null) {
            return null;
        }
        if (!nonEmpty) {
            return states.getEmpty();
        }
        if (windowOpen && states.getReady() != null && !states.getReady().isBlank()) {
            return states.getReady();
        }
        if (overdoneMarked && states.getOverdone() != null && !states.getOverdone().isBlank()) {
            return states.getOverdone();
        }
        return states.getLoaded();
    }
}
