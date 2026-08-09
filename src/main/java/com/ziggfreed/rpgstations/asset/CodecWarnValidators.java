package com.ziggfreed.rpgstations.asset;

import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.validation.ValidationResults;
import com.hypixel.hytale.codec.validation.Validator;

/**
 * Hand-written WARN-only codec validators (schema/DX review round, ruling 75). The engine's
 * built-in {@code Validators} factory (min/range/nonEmptyArray/nonNull/...) all call
 * {@link ValidationResults#fail}, and {@code ContainedAssetCodec} gates asset registration on
 * {@code !hasFailed()} - attaching one of those to a field-local invariant here would silently
 * DROP the whole station/action/lootable/etc. asset on a bad value, the exact OPPOSITE of this
 * mod's standing never-block-at-decode posture (every finding is advisory; an asset always
 * loads). These factories report a {@link ValidationResults#warn} line at the asset's own decode
 * path/line instead - visible to a pack author at fold time (in the boot log), not only from the
 * deferred full {@code station.StationValidator} pass - and never fail an asset load.
 *
 * <p>Attach with {@code .addValidator(...)} on a {@code FieldBuilder} (same chain position as
 * {@code .documentation(...)}/{@code .metadata(...)}); a {@code null} value never warns here (the
 * leaf being unauthored/optional is a separate, already-nullable concern - use
 * {@code Validators.nonNull()} from the engine's own factory, which DOES fail(), only for a leaf
 * that is genuinely required for the asset to mean anything).
 */
final class CodecWarnValidators {

    private CodecWarnValidators() {
    }

    /** Warns when an authored number is zero or negative (quantities, budgets, weights, ...). */
    @Nonnull
    static <T extends Number> Validator<T> positive(@Nonnull String message) {
        return warnIf(v -> v != null && v.doubleValue() <= 0.0, message);
    }

    /** Warns when an authored number is negative (durations/offsets that may legitimately be zero). */
    @Nonnull
    static <T extends Number> Validator<T> nonNegative(@Nonnull String message) {
        return warnIf(v -> v != null && v.doubleValue() < 0.0, message);
    }

    /** Warns when an authored string is present but blank/whitespace-only. */
    @Nonnull
    static Validator<String> nonBlank(@Nonnull String message) {
        return warnIf(v -> v != null && v.isBlank(), message);
    }

    /** Warns when an authored array is present but empty (distinct from unauthored/null, which is fine). */
    @Nonnull
    static <T> Validator<T[]> nonEmptyIfAuthored(@Nonnull String message) {
        return warnIf(v -> v != null && v.length == 0, message);
    }

    /**
     * The generic warn-if-predicate core every factory above wraps. A {@code null} value never
     * triggers {@code shouldWarn} unless the predicate itself tests for it - every factory here
     * treats "unauthored" as out of scope and null-guards internally.
     */
    @Nonnull
    static <T> Validator<T> warnIf(@Nonnull Predicate<T> shouldWarn, @Nonnull String message) {
        return new Validator<>() {
            @Override
            public void accept(T value, ValidationResults results) {
                if (shouldWarn.test(value)) {
                    results.warn(message);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema target) {
                // Warn-only: publishes no hard schema constraint. A leaf that genuinely wants a
                // published min/max keeps the engine's own fail()-backed Validators.range()/min()
                // instead (which DOES feed the generated SCHEMA.md per P8's undersold-upside note)
                // - this class exists for the invariants that must stay advisory.
            }
        };
    }
}
