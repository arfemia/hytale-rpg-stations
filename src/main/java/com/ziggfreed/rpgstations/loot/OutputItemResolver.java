package com.ziggfreed.rpgstations.loot;

import java.util.function.DoubleSupplier;

import javax.annotation.Nonnull;

/**
 * The PURE resolution of a FRACTIONAL {@code Roll.Grants.OutputItems} tally into whole items: the
 * whole part is granted every time, and the fraction left over is the probability of exactly ONE
 * more. A tally of {@code 1.5} therefore pays one item always plus a second half the time, and
 * averages exactly {@code 1.5} over many cycles.
 *
 * <p>Why fractional at all: a ladder rung worth "half a step more than the rung below" is authorable
 * directly on the floor that earns it. The alternative shape - a separate roll banded to one tier
 * beside the ladder - reads as a special case, and it does not compose with the ladder at all: a
 * modded tool reaching a HIGHER floor while still matching that band's tier condition would collect
 * both payouts. A fractional floor value has no such interaction, at any tier anyone adds later.
 *
 * <p><b>One resolution per cycle, over the SUMMED tally.</b> The caller sums every value the cycle
 * granted (top-level and per-floor {@code Grants}, across every roll) and resolves the total here
 * once, so two rolls paying {@code 0.5} each average one whole item instead of being rounded
 * separately and averaging zero or two. The randomness is INJECTED (a {@code [0,1)} uniform
 * supplier, the same seam {@code RollEvaluator} and {@code StampCapEngine} use) so the behaviour is
 * unit-testable without a live server; production passes {@code ThreadLocalRandom}.
 */
public final class OutputItemResolver {

    private OutputItemResolver() {
    }

    /**
     * {@code floor(tally)} items, plus one more when {@code roll} draws under the leftover fraction.
     * A non-positive or non-finite tally resolves to {@code 0}, and a whole-number tally never draws
     * at all (so a deterministic ladder floor stays deterministic).
     *
     * @param tally the SUMMED fractional item count this cycle granted
     * @param roll  a uniform {@code [0,1)} sample source, consulted at most once
     */
    public static int resolve(double tally, @Nonnull DoubleSupplier roll) {
        if (!Double.isFinite(tally) || tally <= 0.0) {
            return 0;
        }
        double whole = Math.floor(tally);
        double fraction = tally - whole;
        int granted = (int) Math.min(Integer.MAX_VALUE, whole);
        if (fraction > 0.0 && roll.getAsDouble() < fraction && granted < Integer.MAX_VALUE) {
            granted++;
        }
        return granted;
    }

    /**
     * The ONE number a resolved {@code OutputItems} count may be REPORTED as: how many items
     * actually reached the player. {@code resolved} when the grant landed (inventory, or the ground
     * when the inventory was full), {@code 0} when it did not.
     *
     * <p>Split out from the grant call site because "how many were rolled" and "how many were
     * received" are different facts, and every consumer of the number - the session item ledger, the
     * produced row's yield breakdown, and the item-gain toast - must read the SECOND one. A grant
     * result that merely says a drop was ATTEMPTED is how a lost stack came to be announced as a
     * reward, so the caller passes the {@code landed} answer only a
     * {@code util.ItemGrantUtil#grantOrDrop}-shaped grant can give, and every consumer reads what
     * this returns rather than the rolled count beside it.
     *
     * @param resolved the whole-item count {@link #resolve} produced for this cycle
     * @param landed   whether the stack genuinely reached the player
     */
    public static int reportable(int resolved, boolean landed) {
        return landed && resolved > 0 ? resolved : 0;
    }
}
