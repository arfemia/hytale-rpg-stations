package com.ziggfreed.rpgstations.loot;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.loot.reward.RewardHandler;
import com.ziggfreed.common.loot.reward.RewardKindRegistry;
import com.ziggfreed.common.loot.reward.RewardKinds;
import com.ziggfreed.common.loot.reward.RewardSpec;
import com.ziggfreed.common.subject.Subject;
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.EffectRef;

/**
 * The three payouts that mean something only AT A STATION, expressed in the shared reward
 * vocabulary so they compose with every other registered kind in the same {@code Grants.Rewards}
 * array.
 *
 * <table>
 *   <caption>What each kind reads</caption>
 *   <tr><th>Kind</th><th>Parameters</th><th>What it does</th></tr>
 *   <tr><td>{@code rpgstations:effect}</td><td>{@code Id}, {@code DurationMs}</td>
 *       <td>applies a native EntityEffect, session-tracked so the session's own teardown removes it</td></tr>
 *   <tr><td>{@code rpgstations:contribution}</td><td>{@code Channel}, {@code Param}, {@code Amount}</td>
 *       <td>posts a ONE-SHOT amount on the cycle-completed event, verbatim and unscaled</td></tr>
 *   <tr><td>{@code rpgstations:output_items}</td><td>{@code Count}</td>
 *       <td>adds fractional extra units of the cycle's OWN primary output</td></tr>
 * </table>
 *
 * <h2>Why these three are a per-pass registry rather than process-wide registrations</h2>
 *
 * <p>All three COLLECT rather than act. An effect has to be tracked on the session that earned it, a
 * contribution has to ride the cycle event that is about to dispatch, and an output-item amount is a
 * fractional tally the whole cycle sums before it resolves to whole items exactly once. None of that
 * is reachable from a handler holding only a reward spec and a player.
 *
 * <p>So one pass builds one registry: {@link #forPass} seeds it from the process-wide vocabulary
 * (so an authored {@code item} or {@code lootable} reward still pays out normally, with the player
 * on the subject handle where those kinds expect it) and then binds these three to THAT pass's
 * {@link Sink}. A pass happens once per work cycle, so a small registry per pass costs nothing worth
 * measuring, and the alternative - a hidden per-thread current-pass handle - would be far harder to
 * reason about at the one place it matters.
 *
 * <p>Outside a station pass these three collect nowhere, which is the honest outcome: an authored
 * {@code rpgstations:contribution} in a table rolled by something that is not a station has no cycle
 * event to ride on.
 */
public final class StationRewardKinds {

    /** Attribution for these registrations in the registry ledger. */
    public static final String OWNER = "rpgstations";

    /** {@code {"Id": "<effectId>", "DurationMs": "3000"}} - a native EntityEffect on the worker. */
    public static final String KIND_EFFECT = "rpgstations:effect";

    /** {@code {"Channel": "<ns>:<id>", "Param": "<opaque>", "Amount": "5"}} - a one-shot post. */
    public static final String KIND_CONTRIBUTION = "rpgstations:contribution";

    /** {@code {"Count": "1.5"}} - extra units of the cycle's own primary output. */
    public static final String KIND_OUTPUT_ITEMS = "rpgstations:output_items";

    private StationRewardKinds() {
    }

    /** Where a pass's collected grants go; one instance per {@code rollAndGrant} pass. */
    public interface Sink {

        /** A native effect to apply and track; {@code durationMs} null defers to the asset's own TTL. */
        void effect(@Nonnull EffectRef effect);

        /** A one-shot contribution to forward on the cycle event, verbatim. */
        void contribution(@Nonnull Contribution post);

        /** A fractional amount of the cycle's own primary output to add to the pass tally. */
        void outputItems(double count);

        /**
         * True while the pass can still carry a cycle-scoped grant. A completion-trigger pass fires
         * from inside session stop, with no cycle event left to ride and no cycle output to add to,
         * so contributions and output items are dropped there rather than queued for a cycle that
         * never comes (the validator reports the authoring ahead of runtime).
         */
        boolean acceptsCycleGrants();
    }

    /** Every kind id this class registers, for a validator or an editor pick list. */
    @Nonnull
    public static List<String> kindIds() {
        return List.of(KIND_EFFECT, KIND_CONTRIBUTION, KIND_OUTPUT_ITEMS);
    }

    /**
     * The vocabulary ONE pass pays out through: every process-wide kind, plus these three bound to
     * {@code sink}. A station kind the shared vocabulary happens to have claimed is overridden here,
     * because at a station the collecting form is the correct one.
     */
    @Nonnull
    public static RewardKindRegistry forPass(@Nonnull Sink sink) {
        RewardKindRegistry kinds = new RewardKindRegistry("rpgstations:rewards");
        RewardKindRegistry shared = RewardKinds.shared();
        for (String id : shared.ids()) {
            kinds.register(id, shared.handler(id));
        }
        kinds.register(KIND_EFFECT, OWNER, effectHandler(sink));
        kinds.register(KIND_CONTRIBUTION, OWNER, contributionHandler(sink));
        kinds.register(KIND_OUTPUT_ITEMS, OWNER, outputItemsHandler(sink));
        return kinds;
    }

    @Nonnull
    private static RewardHandler effectHandler(@Nonnull Sink sink) {
        return (spec, subject) -> {
            String id = trimmedParam(spec, "id");
            if (id == null) {
                return;
            }
            long duration = spec.longParam("durationms", 0L);
            sink.effect(EffectRef.of(id, duration > 0 ? duration : null));
        };
    }

    @Nonnull
    private static RewardHandler contributionHandler(@Nonnull Sink sink) {
        return (spec, subject) -> {
            String channel = trimmedParam(spec, "channel");
            if (channel == null || !sink.acceptsCycleGrants()) {
                return;
            }
            double amount = spec.doubleParam("amount", 0.0);
            if (amount <= 0.0) {
                return;
            }
            sink.contribution(Contribution.of(channel, spec.param("param"), amount));
        };
    }

    @Nonnull
    private static RewardHandler outputItemsHandler(@Nonnull Sink sink) {
        return (spec, subject) -> {
            if (!sink.acceptsCycleGrants()) {
                return;
            }
            double count = spec.doubleParam("count", 0.0);
            if (count > 0.0 && Double.isFinite(count)) {
                sink.outputItems(count);
            }
        };
    }

    @Nullable
    private static String trimmedParam(@Nonnull RewardSpec spec, @Nonnull String key) {
        String value = spec.param(key);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
