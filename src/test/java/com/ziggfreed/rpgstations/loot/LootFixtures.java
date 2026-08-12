package com.ziggfreed.rpgstations.loot;

import java.util.Map;

import javax.annotation.Nonnull;

import com.ziggfreed.common.loot.LootGrants;

/**
 * The three station grant shapes, written the short way, so a test that cares about WHAT a roll
 * hands over does not spell out a reward-spec parameter bag every time.
 *
 * <p>They exist only because these three are registered reward KINDS rather than schema leaves: the
 * shared loot vocabulary has no idea what a cycle's own output or a contribution channel is, which
 * is exactly the point, and the cost is one small builder here.
 */
public final class LootFixtures {

    private LootFixtures() {
    }

    /** Extra units of the cycle's own primary output. */
    @Nonnull
    public static LootGrants outputItems(double count) {
        return reward(StationRewardKinds.KIND_OUTPUT_ITEMS, Map.of("Count", Double.toString(count)));
    }

    /** A one-shot contribution post on {@code channel}. */
    @Nonnull
    public static LootGrants contribution(@Nonnull String channel, double amount) {
        return reward(StationRewardKinds.KIND_CONTRIBUTION,
                Map.of("Channel", channel, "Amount", Double.toString(amount)));
    }

    /** A native effect applied to the worker. */
    @Nonnull
    public static LootGrants effect(@Nonnull String effectId) {
        return reward(StationRewardKinds.KIND_EFFECT, Map.of("Id", effectId));
    }

    @Nonnull
    private static LootGrants reward(@Nonnull String kind, @Nonnull Map<String, String> params) {
        return LootGrants.of(null, null, null,
                new LootGrants.Reward[] {LootGrants.Reward.of(kind, params)});
    }
}
