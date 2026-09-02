package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.api.StationContribution;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The PURE decision core of unattended processing (decision 90): a custody-loaded station whose
 * action authors {@code Work.Unattended} keeps settling its conversions while nobody is engaged.
 * The TRANSFORM is immediate (inputs drain from their piles, outputs land in theirs, all
 * analytically in one batch); everything a live worker would have EARNED beyond the items - the
 * per-cycle loot rolls and the per-cycle contribution posts - ACCRUES as a settled-cycle count on
 * the produce pile ({@link #accrualKey}) and pays out to whoever GATHERS that pile, with every
 * factor resolved against the gatherer at the idle contribution rate. Zero engine/store touch:
 * every method takes plain values plus a (possibly detached) {@link StationCustodyClaim}, so the
 * catch-up math, the clamps, the accrual namespace and the transform itself are unit-testable
 * without a live server ({@code UnattendedCatchUpTest} / {@code UnattendedGatherTest}).
 *
 * <p><b>The clock is world game time</b> (the stash-level {@code LastGameTime} leaf, reserved for
 * exactly this since the doneness wave): game time stands still while the server is down, so an
 * outage settles zero cycles - the native processing-bench catch-up precedent, whose 24h ceiling
 * {@code Work.Unattended.CatchUpMaxMs} mirrors.
 *
 * <p><b>Clamped time forfeits; sub-cycle time banks.</b> When a settle commits every cycle the
 * elapsed time paid for, the sub-cycle remainder stays banked (the clock advances by exactly the
 * settled cycles). When anything else clamps the settle - inputs ran short, output room ran out,
 * {@code MaxCycles} hit, or no conversion could run at all - the leftover elapsed time is
 * FORFEITED (the clock jumps to now): banking it would burst-settle the backlog the moment inputs
 * appear, paying for hours the station spent unable to work.
 *
 * <p><b>The refund ledger is never touched.</b> An unattended settle has no session, no in-flight
 * iteration and no worker to refund to; the transform is committed whole or not at all (the D38
 * invariant, pinned append-only in {@code StationRefundLedgerTest}).
 */
final class StationUnattended {

    /**
     * The {@code PendingCycles} key prefix unattended accrual lives under - per-conversion, the
     * conversion addressed by the same {@code conversion:<resolvedIndex>} row key the sneak+F
     * picker uses ({@code StationService#conversionRowKey}). Deliberately OUTSIDE the reserved
     * {@code doneness:} prefix ({@link StationDoneness#RESERVED_KEY_PREFIX}); the two records
     * share a pile without ever sharing a key.
     */
    static final String ACCRUAL_KEY_PREFIX = "accrual:";

    private StationUnattended() {
    }

    // ==================== the accrual key namespace ====================

    /** The {@code PendingCycles} accrual key for the conversion at {@code resolvedIndex}. */
    @Nonnull
    static String accrualKey(int resolvedIndex) {
        return ACCRUAL_KEY_PREFIX + StationService.conversionRowKey(resolvedIndex);
    }

    /** True when {@code key} is an unattended accrual key (never a {@code doneness:} one). */
    static boolean isAccrualKey(@Nullable String key) {
        return key != null && key.startsWith(ACCRUAL_KEY_PREFIX);
    }

    /**
     * The resolved-conversion index an accrual key addresses, or {@code -1} for a key that is not
     * an accrual key or whose index no longer parses (a gather still pays such a key's cycles;
     * only the output-item identity is lost with the index).
     */
    static int parseAccrualIndex(@Nullable String key) {
        if (!isAccrualKey(key)) {
            return -1;
        }
        return StationService.parseConversionRowIndex(key.substring(ACCRUAL_KEY_PREFIX.length()));
    }

    // ==================== the catch-up math ====================

    /** The elapsed game time one settle may consume: {@code min(elapsed, catchUpMax)}, never negative. */
    static long usableElapsed(long elapsedGameMs, long catchUpMaxMs) {
        return Math.max(0L, Math.min(elapsedGameMs, catchUpMaxMs));
    }

    /**
     * How many whole cycles {@code usableElapsedMs} pays for at {@code cycleMs} a cycle - the raw
     * time-derived count, before the input/room/{@code MaxCycles} clamps. Zero elapsed (the
     * outage case: game time stood still) settles zero; a non-positive {@code cycleMs} settles
     * zero rather than dividing by it.
     */
    static long rawCycles(long usableElapsedMs, long cycleMs) {
        if (cycleMs <= 0 || usableElapsedMs <= 0) {
            return 0L;
        }
        return usableElapsedMs / cycleMs;
    }

    /**
     * Where the catch-up clock lands after a settle. {@code settled == raw} (nothing but time
     * bounded the settle) banks the sub-cycle remainder: the clock advances to
     * {@code now - (usable - settled * cycleMs)}, so progress toward the next cycle is kept.
     * Anything clamped ({@code settled < raw}) forfeits the leftover outright: the clock jumps to
     * {@code now}, because the station could not have worked that time.
     */
    static long advancedLastGameTime(long nowGameMs, long usableElapsedMs, long rawCycles,
            int settledCycles, long cycleMs) {
        if (settledCycles < rawCycles) {
            return nowGameMs;
        }
        return nowGameMs - Math.max(0L, usableElapsedMs - settledCycles * cycleMs);
    }

    /** True when the unattended pass should touch this block at all: a stash stands and no live session owns it (attended is the authority). */
    static boolean shouldVisit(boolean stashPresent, boolean liveSessionAtBlock) {
        return stashPresent && !liveSessionAtBlock;
    }

    /**
     * The candidate order of the analytic scan: resolved-array indices ordered by effective
     * {@code Conversion.Tier} ascending, STABLE inside a tier - the same rule the attended scan's
     * {@code tierOrdered} applies, kept as INDICES here because the accrual key must record the
     * RESOLVED index, not the sorted position.
     */
    @Nonnull
    static int[] tierOrderedIndices(@Nullable StationAsset.Conversion[] conversions) {
        if (conversions == null || conversions.length == 0) {
            return new int[0];
        }
        List<Integer> order = new ArrayList<>(conversions.length);
        for (int i = 0; i < conversions.length; i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt(i -> conversions[i] != null ? conversions[i].effectiveTier() : 0));
        int[] out = new int[order.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = order.get(i);
        }
        return out;
    }

    // ==================== the analytic settle ====================

    /** What one settle committed (or why it did nothing). */
    record Settle(int settledCycles, int conversionIndex, @Nullable String produceSocketId,
            boolean clockStamped) {

        /** Nothing ran and the clock was left alone (sub-cycle time keeps banking). */
        static final Settle NOTHING = new Settle(0, -1, null, false);

        /** Nothing ran but the clock was written (first stamp, or a clamped/forfeited backlog). */
        static final Settle CLOCK_ONLY = new Settle(0, -1, null, true);

        boolean transformed() {
            return settledCycles > 0;
        }
    }

    /**
     * The whole analytic settle over one claim: pick the first runnable conversion (tier order,
     * per-pile availability, {@code IsExactSet}, output room - the attended custody scan's own
     * rules, with output room answered by the CUSTODY piles since there is no worker inventory to
     * land in), settle {@code min(raw, inputs, room, MaxCycles)} whole cycles as ONE batch
     * (Consume drains oldest-first through the shared pile cores, Produce lands per output
     * socket, the produce pile inheriting the FIRST-consumed socket's owner per decision 82), and
     * accrue the settled count on the produce pile under {@link #accrualKey}. Mutates only the
     * claim; the caller marks dirty (for a TRANSFORM - a clock-only stamp is deliberately
     * best-effort volatile, see the caller), stamps doneness batches, and refreshes
     * displays/states.
     *
     * @param workCycleMs the resolved {@code Work.CycleMs} fallback (a conversion's own
     *                    {@code DurationMs} outranks it, the attended pace rule)
     */
    @Nonnull
    static Settle settle(@Nonnull StationCustodyClaim claim,
            @Nonnull List<Custody.ResolvedSocket> sockets,
            @Nullable StationAsset.Conversion[] conversions,
            @Nullable StationAsset.Yield yield,
            int custodyMaxQuantity,
            @Nonnull StationAsset.Work.Unattended unattended,
            long workCycleMs,
            long nowGameMs,
            @Nonnull Function<String, String[]> resourceTypesOf,
            @Nonnull Function<String, Map<String, String[]>> tagsOf) {
        Long last = claim.unattendedLastGameTime();
        if (last == null || nowGameMs < last) {
            // First visit (or a clock that moved backwards): anchor the catch-up clock and let
            // time start accruing from here - nothing is owed for time nobody measured.
            claim.setUnattendedLastGameTime(nowGameMs);
            return Settle.CLOCK_ONLY;
        }
        long elapsed = nowGameMs - last;
        if (elapsed <= 0) {
            return Settle.NOTHING;
        }
        long usable = usableElapsed(elapsed, unattended.effectiveCatchUpMaxMs());

        // Candidate scan: the first row whose inputs the piles satisfy AND whose outputs have
        // custody room wins, exactly like the attended custody scan; time then decides how many
        // cycles of it actually run.
        for (int index : tierOrderedIndices(conversions)) {
            StationAsset.Conversion c = conversions[index];
            if (!runnableShape(c)) {
                continue;
            }
            long cycleMs = c.getDurationMs() != null && c.getDurationMs() > 0 ? c.getDurationMs() : workCycleMs;
            long raw = rawCycles(usable, cycleMs);

            int inputCycles = inputCycles(claim, sockets, c, resourceTypesOf, tagsOf);
            if (inputCycles <= 0) {
                continue;
            }
            if (c.effectiveIsExactSet() && !StationCustody.exactSetSatisfied(c, claim::items, sockets,
                    resourceTypesOf, tagsOf)) {
                continue;
            }
            Map<String, Integer> producedPerCycle = producedPerCycleBySocket(sockets, c, yield);
            int roomCycles = roomCycles(claim, sockets, c, producedPerCycle, custodyMaxQuantity);
            if (roomCycles <= 0) {
                continue;
            }

            int settled = (int) Math.min(Math.min(raw, inputCycles),
                    Math.min(roomCycles, unattended.effectiveMaxCycles()));
            if (settled <= 0) {
                // The row could run but the elapsed time has not paid for a whole cycle yet:
                // leave the clock alone so the partial cycle keeps banking.
                return Settle.NOTHING;
            }

            // Decision 82: the produce pile inherits the FIRST-consumed socket's owner (its
            // pile's recorded owner, else the stash's).
            String firstInputSocket = StationCustody.socketIdFor(
                    c.getInput()[0].getSocket(), null, sockets);
            UUID producedOwner = claim.pileOwner(firstInputSocket);
            if (producedOwner == null) {
                producedOwner = claim.ownerId;
            }

            for (Ingredient in : c.getInput()) {
                String socketId = StationCustody.socketIdFor(in.getSocket(), null, sockets);
                StationCustody.drainFromPile(claim.items(socketId),
                        StationCustody.ingredientEntryMatcher(in, resourceTypesOf, tagsOf),
                        in.effectiveQuantity() * settled, null);
            }
            String produceSocketId = null;
            for (Ingredient out : c.getOutput()) {
                String socketId = StationCustody.socketIdFor(out.getSocket(), null, sockets);
                if (produceSocketId == null) {
                    produceSocketId = socketId;
                }
                int perCycle = StationYield.resolveQuantity(yield, out.effectiveQuantity());
                claim.addTo(socketId, producedOwner, out.getItemId(), perCycle * settled);
            }
            if (produceSocketId != null) {
                claim.accruePendingCycles(produceSocketId, accrualKey(index), settled);
            }
            claim.setUnattendedLastGameTime(
                    advancedLastGameTime(nowGameMs, usable, raw, settled, cycleMs));
            return new Settle(settled, index, produceSocketId, true);
        }

        // No conversion can run at all (no inputs, contaminated exact set, or no room): the
        // backlog is forfeited rather than banked, or topping the station up would burst-settle
        // hours the station spent unable to work.
        claim.setUnattendedLastGameTime(nowGameMs);
        return Settle.CLOCK_ONLY;
    }

    /** The most whole cycles the piles' INPUTS pay for (0 when any input is short of one cycle). */
    private static int inputCycles(@Nonnull StationCustodyClaim claim,
            @Nonnull List<Custody.ResolvedSocket> sockets, @Nonnull StationAsset.Conversion c,
            @Nonnull Function<String, String[]> resourceTypesOf,
            @Nonnull Function<String, Map<String, String[]>> tagsOf) {
        int cycles = Integer.MAX_VALUE;
        for (Ingredient in : c.getInput()) {
            int need = in.effectiveQuantity();
            if (need <= 0) {
                continue;
            }
            String socketId = StationCustody.socketIdFor(in.getSocket(), null, sockets);
            int have = StationCustody.availableInPile(claim.items(socketId),
                    StationCustody.ingredientEntryMatcher(in, resourceTypesOf, tagsOf));
            cycles = Math.min(cycles, have / need);
            if (cycles <= 0) {
                return 0;
            }
        }
        return cycles == Integer.MAX_VALUE ? 0 : cycles;
    }

    /** Per produced SOCKET, how many items one cycle lands there (Yield already applied). */
    @Nonnull
    private static Map<String, Integer> producedPerCycleBySocket(
            @Nonnull List<Custody.ResolvedSocket> sockets, @Nonnull StationAsset.Conversion c,
            @Nullable StationAsset.Yield yield) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Ingredient produced : c.getOutput()) {
            String socketId = StationCustody.socketIdFor(produced.getSocket(), null, sockets);
            out.merge(socketId, StationYield.resolveQuantity(yield, produced.effectiveQuantity()),
                    Integer::sum);
        }
        return out;
    }

    /**
     * The most whole cycles CUSTODY ROOM pays for, on the NET per-cycle flow: a cycle's consume
     * frees pile room before its produce fills it, so a socket (and the block total) bounds only
     * a POSITIVE net gain - a transform that shrinks or keeps its pile's tally is roomless-proof.
     * {@code Integer.MAX_VALUE}-capped, never negative.
     */
    private static int roomCycles(@Nonnull StationCustodyClaim claim,
            @Nonnull List<Custody.ResolvedSocket> sockets, @Nonnull StationAsset.Conversion c,
            @Nonnull Map<String, Integer> producedPerCycle, int custodyMaxQuantity) {
        // Per-cycle drains per socket (what the consume side frees).
        Map<String, Integer> drainedPerCycle = new LinkedHashMap<>();
        int drainedTotal = 0;
        for (Ingredient in : c.getInput()) {
            int need = in.effectiveQuantity();
            if (need <= 0) {
                continue;
            }
            String socketId = StationCustody.socketIdFor(in.getSocket(), null, sockets);
            drainedPerCycle.merge(socketId, need, Integer::sum);
            drainedTotal += need;
        }
        long cycles = Integer.MAX_VALUE;
        int producedTotal = 0;
        for (Map.Entry<String, Integer> e : producedPerCycle.entrySet()) {
            producedTotal += e.getValue();
            int net = e.getValue() - drainedPerCycle.getOrDefault(e.getKey(), 0);
            if (net <= 0) {
                continue;
            }
            int room = socketMaxQuantityOf(sockets, e.getKey(), custodyMaxQuantity)
                    - claim.totalQuantity(e.getKey());
            cycles = Math.min(cycles, Math.max(0, room) / net);
        }
        int netTotal = producedTotal - drainedTotal;
        if (netTotal > 0) {
            int blockRoom = custodyMaxQuantity - claim.totalQuantity();
            cycles = Math.min(cycles, Math.max(0, blockRoom) / netTotal);
        }
        return (int) Math.min(Integer.MAX_VALUE, cycles);
    }

    /** The resolved socket's min-of-caps quantity ceiling; an unlisted socket id falls to the block-level cap. */
    private static int socketMaxQuantityOf(@Nonnull List<Custody.ResolvedSocket> sockets,
            @Nonnull String socketId, int custodyMaxQuantity) {
        for (Custody.ResolvedSocket socket : sockets) {
            if (socket.id().equalsIgnoreCase(socketId)) {
                return socket.maxQuantity();
            }
        }
        return custodyMaxQuantity;
    }

    /** The same runnable-shape gate the attended scans apply (both sides authored, outputs exact ids). */
    private static boolean runnableShape(@Nullable StationAsset.Conversion c) {
        if (c == null || !c.isComplete()) {
            return false;
        }
        for (Ingredient in : c.getInput()) {
            if (in == null || in.routeCount() > 1) {
                return false;
            }
        }
        for (Ingredient out : c.getOutput()) {
            if (out == null || out.getItemId() == null || out.getItemId().isBlank()) {
                return false;
            }
        }
        return true;
    }

    // ==================== the gather plan (decision 90's payout half) ====================

    /**
     * What one gather pays for: the accrued cycles allocated per conversion index, capped by the
     * SAME {@code MaxCycles} ceiling a settle burst wears (one knob, both ends). Accrued cycles
     * beyond the ceiling are forfeited with the gather - the keys clear either way, so a pile
     * never carries stale accrual forward.
     */
    record GatherPlan(int grantCycles, @Nonnull Map<Integer, Integer> cyclesByConversionIndex) {

        boolean anythingOwed() {
            return grantCycles > 0;
        }
    }

    /**
     * Allocates the gather ceiling across {@code accrued} (accrual key to settled-cycle count, in
     * pile insertion order): each key gets what remains of the budget in order, and its parsed
     * conversion index keys the allocation ({@code -1} for a key whose index no longer resolves -
     * those cycles still pay rolls and contributions, only the output-item identity is gone).
     * Non-accrual keys and non-positive counts are ignored.
     */
    @Nonnull
    static GatherPlan gatherPlan(@Nonnull Map<String, Integer> accrued, int maxCycles) {
        Map<Integer, Integer> byIndex = new LinkedHashMap<>();
        int budget = Math.max(0, maxCycles);
        int granted = 0;
        for (Map.Entry<String, Integer> e : accrued.entrySet()) {
            if (!isAccrualKey(e.getKey()) || e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            int take = Math.min(e.getValue(), budget - granted);
            if (take <= 0) {
                continue;
            }
            byIndex.merge(parseAccrualIndex(e.getKey()), take, Integer::sum);
            granted += take;
        }
        return new GatherPlan(granted, byIndex);
    }

    /** {@code perCycle} contribution amounts multiplied by {@code cycles} (the gather's whole batch in one list). */
    @Nonnull
    static List<StationContribution> scaledByCycles(@Nonnull List<StationContribution> perCycle, int cycles) {
        if (cycles <= 0 || perCycle.isEmpty()) {
            return List.of();
        }
        List<StationContribution> out = new ArrayList<>(perCycle.size());
        for (StationContribution c : perCycle) {
            out.add(new StationContribution(c.channel(), c.param(), c.amount() * cycles));
        }
        return out;
    }
}
