package com.ziggfreed.rpgstations.loot;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.loot.reward.CollectingRewardKind;
import com.ziggfreed.common.loot.reward.RewardKindRegistry;
import com.ziggfreed.common.loot.reward.RewardSpec;
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
 * <h2>Registered once, collected by the pass</h2>
 *
 * <p>All three COLLECT rather than act. An effect has to be tracked on the session that earned it, a
 * contribution has to ride the cycle event that is about to dispatch, and an output-item amount is a
 * fractional tally the whole cycle sums before it resolves to whole items exactly once. None of that
 * is reachable from a handler holding only a reward spec and a player.
 *
 * <p>So each is a Ziggfreed Common {@link CollectingRewardKind} whose collector is this class's
 * {@link Sink}. {@link #registerInto} puts all three into the ONE shared vocabulary at plugin setup,
 * and a station pass layers its own {@code Sink} onto the subject it pays
 * ({@code Subject.withFacets}); each grant then finds that pass's sink by its type. A table rolled
 * by a {@code Lootable} reward is paid through the very same subject, so a station kind authored in
 * a nested table reaches the same pass, and the content audit and the Asset Editor's reward-kind
 * list both know the three like any other kind.
 *
 * <p>Inside a pass, a grant the pass cannot carry (a cycle-scoped kind in a completion pass, a blank
 * {@code Id} or {@code Channel}, a non-positive amount) returns quietly: it counts as paid, so a
 * {@code Cue} authored beside it still earns. Outside a station pass there is no sink to find, so
 * the grant fails, counts as lost and is reported, which is the honest outcome: an authored
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

    /**
     * Where a pass's collected grants go; one instance per {@code rollAndGrant} pass, layered onto
     * the subject that pass pays so the three kinds find it by this type.
     */
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

    /** {@code rpgstations:effect}. One instance, so registering it again changes nothing. */
    private static final CollectingRewardKind<Sink> EFFECT =
            CollectingRewardKind.of(KIND_EFFECT, Sink.class, (sink, spec) -> {
                String id = trimmedParam(spec, "id");
                if (id == null) {
                    return;
                }
                long duration = spec.longParam("durationms", 0L);
                sink.effect(EffectRef.of(id, duration > 0 ? duration : null));
            });

    /** {@code rpgstations:contribution}. */
    private static final CollectingRewardKind<Sink> CONTRIBUTION =
            CollectingRewardKind.of(KIND_CONTRIBUTION, Sink.class, (sink, spec) -> {
                String channel = trimmedParam(spec, "channel");
                if (channel == null || !sink.acceptsCycleGrants()) {
                    return;
                }
                double amount = spec.doubleParam("amount", 0.0);
                if (amount <= 0.0) {
                    return;
                }
                sink.contribution(Contribution.of(channel, spec.param("param"), amount));
            });

    /** {@code rpgstations:output_items}. */
    private static final CollectingRewardKind<Sink> OUTPUT_ITEMS =
            CollectingRewardKind.of(KIND_OUTPUT_ITEMS, Sink.class, (sink, spec) -> {
                if (!sink.acceptsCycleGrants()) {
                    return;
                }
                double count = spec.doubleParam("count", 0.0);
                if (count > 0.0 && Double.isFinite(count)) {
                    sink.outputItems(count);
                }
            });

    /** Every kind id this class registers, for a validator or an editor pick list. */
    @Nonnull
    public static List<String> kindIds() {
        return List.of(KIND_EFFECT, KIND_CONTRIBUTION, KIND_OUTPUT_ITEMS);
    }

    /**
     * Register the three station kinds into {@code kinds} under {@link #OWNER}. The plugin hands it
     * the shared vocabulary once at setup; a test hands it the same before it drives a pass, since
     * that vocabulary is process-wide and a reset empties it. Registering again is a no-op, because
     * each kind is one instance.
     */
    public static void registerInto(@Nonnull RewardKindRegistry kinds) {
        kinds.register(KIND_EFFECT, OWNER, EFFECT);
        kinds.register(KIND_CONTRIBUTION, OWNER, CONTRIBUTION);
        kinds.register(KIND_OUTPUT_ITEMS, OWNER, OUTPUT_ITEMS);
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
