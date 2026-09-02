package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * The inventory-side counterpart of {@code StationCustody}'s pile matchers, for the ingredient
 * routes the native container API has no call for: a {@code Tags}-route input has no batch
 * check/remove in our tag-MAP shape, so availability and consumption both walk the container's
 * slots through the SAME injected item-id predicate ({@code StationService#liveIngredientMatcher}
 * at the live seam) - one matcher answers the count and the drain, so the two can never disagree
 * on which stacks qualify.
 *
 * <p>Both walks read slot ORDER as the deterministic tie-break (the first matching slots are
 * consumed first), and the drain plans against a snapshot taken by the walk before any removal
 * runs, so mutation never happens under iteration. World-thread only, like every inventory touch.
 */
final class InventoryIngredients {

    private InventoryIngredients() {
    }

    /**
     * Total quantity across the container's stacks whose item id {@code matches} accepts. Null
     * container answers 0 (an unresolvable player reads as "has nothing").
     */
    static int countMatching(@Nullable ItemContainer container, @Nonnull Predicate<String> matches) {
        if (container == null) {
            return 0;
        }
        int[] total = {0};
        container.forEach((slot, stack) -> {
            if (stack != null && stack.getItemId() != null && stack.getQuantity() > 0
                    && matches.test(stack.getItemId())) {
                total[0] += stack.getQuantity();
            }
        });
        return total[0];
    }

    /**
     * Remove up to {@code quantity} items whose id {@code matches} accepts, first matching slots
     * first, tallying the REAL removed ids into {@code drainedOut} (the session-ledger convention
     * every consume path follows). Returns the amount actually removed (0..quantity); a caller
     * treats a short answer as the all-or-nothing failure its own pre-count should have prevented.
     */
    static int drainMatching(@Nullable ItemContainer container, @Nonnull Predicate<String> matches,
            int quantity, @Nullable Map<String, Integer> drainedOut) {
        if (container == null || quantity <= 0) {
            return 0;
        }
        List<int[]> plan = new ArrayList<>();
        List<String> planIds = new ArrayList<>();
        int[] remaining = {quantity};
        container.forEach((slot, stack) -> {
            if (remaining[0] <= 0 || stack == null || stack.getItemId() == null
                    || stack.getQuantity() <= 0 || !matches.test(stack.getItemId())) {
                return;
            }
            int take = Math.min(stack.getQuantity(), remaining[0]);
            remaining[0] -= take;
            plan.add(new int[] {slot, take});
            planIds.add(stack.getItemId());
        });
        int removed = 0;
        for (int i = 0; i < plan.size(); i++) {
            short slot = (short) plan.get(i)[0];
            int take = plan.get(i)[1];
            var tx = container.removeItemStackFromSlot(slot, take);
            if (tx == null || !tx.succeeded()) {
                continue;
            }
            removed += take;
            if (drainedOut != null) {
                drainedOut.merge(planIds.get(i), take, Integer::sum);
            }
        }
        return removed;
    }
}
