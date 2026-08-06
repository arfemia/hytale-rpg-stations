package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditor;

/**
 * The ONE outbound numeric-post leaf: {@code {Channel, Param?, Amount}}. This is the WRITE-side
 * twin of {@link FactorRef}/{@link Condition}'s read side, and it works the same way in reverse -
 * a namespaced channel id plus an opaque {@code Param}, resolved by nobody here. Where a
 * {@code Factor} asks a registered provider for a number, a {@code Contribution} hands a number
 * OUT: this engine forwards {@code {Channel, Param, Amount}} verbatim on the cycle-completed event
 * and never interprets a channel itself. Declare a channel id through
 * {@code api.ContributionChannelRegistry} to get it into the {@code rpgstations:channels} editor
 * dropdown and out of the {@code UNKNOWN_CHANNEL} validator warn; an undeclared channel still
 * forwards (the warn never blocks).
 *
 * <p><b>Two authoring sites, two documented meanings, one record.</b> Scaling is decided by WHERE
 * an entry is authored, never by a flag on the entry itself:
 * <ul>
 *   <li>{@code Work.PerCycleContributions[]} - posted on EVERY completed cycle and SCALED: each
 *   {@code Amount} is multiplied by the resolved tool multiplier ({@code Tool.PowerScale}) and, on
 *   an idle cycle, pre-scaled by {@code Work.Idle.Fraction}.
 *   <li>{@code Roll.Grants.Contributions[]} - posted ONCE when a roll grants, VERBATIM: it inherits
 *   neither the tool multiplier nor the idle fraction, so a rare find is worth the same whatever
 *   tool the player happens to hold.
 * </ul>
 * The group key at the {@code Work} site says {@code PerCycle} out loud precisely so the two sites
 * never read as the same blob in an author's eye. A {@code Scaled} knob on this record is
 * deliberately REJECTED: it would be meaningless at the grants site and would let an author defeat
 * the one-shot rule. First-party precedent for site-fixed meaning: {@code EntityStatType}'s
 * {@code MinValueEffects}/{@code MaxValueEffects}, one {@code EntityStatEffects} type whose meaning
 * is fixed purely by the key it hangs under.
 */
public final class Contribution {

    @Nullable protected String channel;
    @Nullable protected String param;
    @Nullable protected Double amount;

    public static final BuilderCodec<Contribution> CODEC = BuilderCodec.builder(Contribution.class, Contribution::new)
            .appendInherited(new KeyedCodec<>("Channel", Codec.STRING, false),
                    (o, v) -> o.channel = v, o -> o.channel, (o, p) -> o.channel = p.channel)
            .documentation("The namespaced channel id this contribution posts to (e.g. 'yourmod:crop_quality'), "
                    + "opaque to this engine - it is never resolved here, only forwarded. Pick it from the "
                    + "rpgstations:channels dropdown, or declare your own via ContributionChannelRegistry.")
            .addValidator(CodecWarnValidators.nonBlank("Contributions[].Channel should not be blank."))
            .metadata(new UIEditor(new UIEditor.Dropdown(AssetEditorDataSets.CHANNELS))).add()
            .appendInherited(new KeyedCodec<>("Param", Codec.STRING, false),
                    (o, v) -> o.param = v, o -> o.param, (o, p) -> o.param = p.param)
            .documentation("Optional channel-interpreted argument, opaque to this engine - whatever the channel's "
                    + "owner documents (commonly the specific thing being credited).").add()
            .appendInherited(new KeyedCodec<>("Amount", Codec.DOUBLE, false),
                    (o, v) -> o.amount = v, o -> o.amount, (o, p) -> o.amount = p.amount)
            .documentation("The amount posted. Scaling is decided by WHERE this entry is authored - see the owning "
                    + "group's documentation.")
            .addValidator(CodecWarnValidators.positive("Contributions[].Amount should be positive.")).add()
            .build();

    public Contribution() {
    }

    @Nonnull
    public static Contribution of(@Nullable String channel, @Nullable String param, @Nullable Double amount) {
        Contribution c = new Contribution();
        c.channel = channel;
        c.param = param;
        c.amount = amount;
        return c;
    }

    /** The namespaced channel id; opaque to this engine, forwarded verbatim. */
    @Nullable
    public String getChannel() {
        return channel;
    }

    /** The optional channel-interpreted argument; opaque to this engine, forwarded verbatim. */
    @Nullable
    public String getParam() {
        return param;
    }

    /** The authored amount; null means unauthored, which each site reads its own way (see {@link #isPostable()}). */
    @Nullable
    public Double getAmount() {
        return amount;
    }

    /**
     * The ONE-SHOT (grants-site) gate: a non-blank {@link #channel} AND a positive {@link #amount}.
     *
     * <p>Deliberately NOT the per-cycle site's filter. A {@code Work.PerCycleContributions} entry
     * is forwarded whenever its {@code Channel} is non-blank, with a null {@code Amount} read as
     * {@code 0.0}, so a zero-amount entry still reaches a listener (it renders as a visible
     * zero row in a session breakdown rather than vanishing). Only a roll grant, which either
     * fires or does not, gates on a positive amount here.
     */
    public boolean isPostable() {
        return channel != null && !channel.isBlank() && amount != null && amount > 0;
    }
}
