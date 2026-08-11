package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.ziggfreed.common.factor.FactorCondition;

/**
 * The ONE {@code Conditions} leaf codec every gate site in this schema embeds: the shared
 * {@link FactorCondition} shape {@code {Factor, Param?, Min?, Max?}}, built through its codec
 * FACTORY so the {@code Factor} field offers THIS mod's own {@code rpgstations:factors} Asset
 * Editor pick list (the live factor vocabulary, served by {@link AssetEditorDataSets}).
 *
 * <p>Used wherever a factor value must PASS a bound - a station's or an action's {@code Requires}
 * gate, a {@code Roll.Conditions} entry, a {@code StationStep.Conditions} entry. The scale/add
 * sibling {@link FactorRef} ({@code Factor}/{@code Param}/{@code Weight}) is used wherever a factor
 * value is SUMMED instead. One codec each, so a gate authored at any site behaves identically.
 *
 * <p><b>ONE instance, deliberately.</b> The factory mints a fresh codec per call, so every embed
 * site references this constant rather than calling the factory again - a second instance would
 * publish the same shape twice in the generated schema reference.
 *
 * <p>An unregistered factor id fails the gate CLOSED at runtime (a gate never springs open because
 * the mod that owns the factor is not installed); {@code Min}/{@code Max} are inclusive and
 * independently optional, so a condition with NEITHER is a presence check that passes as long as
 * the factor resolves at all.
 */
public final class Conditions {

    /** The shared gate leaf, wired to this mod's live factor dropdown. */
    @Nonnull
    public static final BuilderCodec<FactorCondition> CODEC =
            FactorCondition.codec(AssetEditorDataSets.FACTORS);

    private Conditions() {
    }
}
