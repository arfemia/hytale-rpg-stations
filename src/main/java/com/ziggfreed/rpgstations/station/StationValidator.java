package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.i18n.RpgStationsLangKeys;
import com.ziggfreed.rpgstations.util.Log;
import com.ziggfreed.rpgstations.validation.Finding;
import com.ziggfreed.rpgstations.validation.Report;

/**
 * Read-only content diagnostic for station assets. Ported + reshaped from the MMO's
 * {@code station.StationValidator} (RPG Stations extraction leg 2, design section 4.1):
 * moved onto the RpgStations-local {@code validation/} mini-core, {@code SafeLog} severed to
 * {@code util.Log}. Per the design's DROP list this reshape DROPS the MMO
 * {@code ContentAudit} registration (RpgStations has no cross-domain audit registry of its
 * own yet) and the {@code SkillRegistry} skill-existence checks (skill ids are no longer this
 * engine's business - the MMO bridge validates them, leg 5's {@code StationBridgeValidator}).
 * Per the critique's binding fix (m10), the lang-key presence check is KEPT, rewired against
 * RpgStations' own {@link RpgStationsLangKeys}. The MMO's {@code Requires}/feature-id check
 * is replaced by a factor-known check over RpgStations' own {@link Requires}/{@link Condition}.
 *
 * <p>Pure and side-effect-free (apart from {@link #runAndLog}); never throws.
 */
public final class StationValidator {

    static final String DOMAIN = "station";

    private StationValidator() {
    }

    // ==================== Entry points ====================

    /** Validate the live catalog. Never throws; returns an empty list on failure. */
    @Nonnull
    public static List<Finding> validate() {
        try {
            return validate(StationCatalog.getInstance().all().values(),
                    RpgStationsLangKeys::isKnown,
                    StationValidator::dropListKnownLive,
                    // TODO(leg 4): the real api FactorRegistry replaces this fail-open default
                    // once it exists; nothing is registered yet, so warning on every authored
                    // factor id would be noise, not signal.
                    id -> true);
        } catch (Throwable t) {
            Log.warn("Station validation aborted: " + t.getMessage());
            return new ArrayList<>();
        }
    }

    /** Live {@code ItemDropList} existence check (asset-map lookup - never throws). */
    private static boolean dropListKnownLive(@Nonnull String dropListId) {
        try {
            return ItemDropList.getAssetMap().getAsset(dropListId) != null;
        } catch (Throwable t) {
            return true; // a lookup failure is not evidence the id is wrong - don't flag it
        }
    }

    /**
     * Singleton-free core. {@code langKeyKnown} answers "does this rpgstations lang key
     * exist"; {@code dropListKnown} answers "does this native ItemDropList asset id exist";
     * {@code factorKnown} answers "is this Requires.Condition factor id registered" (the
     * design's "factor-id known-check against the registry" - fail-OPEN by default since no
     * registry exists this leg, warn-not-error either way per the design's stated posture:
     * "providers may register later, so warn not error").
     */
    @Nonnull
    public static List<Finding> validate(@Nonnull Collection<StationAsset> stations,
                                         @Nonnull Predicate<String> langKeyKnown,
                                         @Nonnull Predicate<String> dropListKnown,
                                         @Nonnull Predicate<String> factorKnown) {
        List<Finding> out = new ArrayList<>();
        for (StationAsset a : stations) {
            if (a == null) {
                continue;
            }
            String id = a.getId() == null || a.getId().isBlank() ? "(unnamed)" : a.getId();
            String label = "Station '" + id + "'";

            checkIdentity(a, id, label, langKeyKnown, out);
            checkRecipe(a, id, label, out);
            checkWork(a, id, label, out);
            checkTool(a, id, label, out);
            checkLuck(a, id, label, dropListKnown, out);
            checkRequires(a, id, label, factorKnown, out);
            checkAnimation(a, id, label, out);
            checkPresentationRefs(a, id, label, out);
            checkCamera(a, id, label, out);
            checkCompletion(a, id, label, out);
            checkFlairs(a, id, label, out);
        }
        return out;
    }

    // ==================== Per-section checks ====================

    private static void checkIdentity(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                      @Nonnull Predicate<String> langKeyKnown, @Nonnull List<Finding> out) {
        String nameKey = a.getIdentity() != null ? a.getIdentity().getNameKey() : null;
        if (nameKey == null || nameKey.isBlank()) {
            out.add(Finding.warning(DOMAIN, "MISSING_NAME_KEY",
                    label + " has no Identity.NameKey (falls back to the rpgstations.station." + id
                            + ".name convention key)", id));
            nameKey = "rpgstations.station." + id + ".name";
        }
        if (!langKeyKnown.test(nameKey)) {
            out.add(Finding.warning(DOMAIN, "MISSING_NAME_LANG",
                    label + " name key '" + nameKey + "' has no lang entry", id));
        }
        String descKey = a.getIdentity() != null ? a.getIdentity().getDescKey() : null;
        if (descKey != null && !descKey.isBlank() && !langKeyKnown.test(descKey)) {
            out.add(Finding.warning(DOMAIN, "MISSING_DESC_LANG",
                    label + " desc key '" + descKey + "' has no lang entry", id));
        }
    }

    private static void checkTool(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                  @Nonnull List<Finding> out) {
        StationAsset.Tool tool = a.getTool();
        if (tool == null) {
            return;
        }
        StationAsset.Tool.Gather gather = tool.getGather();
        boolean anyTags = tool.getTags() != null && !tool.getTags().isEmpty();
        boolean gatherTypeSet = gather != null && gather.getGatherType() != null && !gather.getGatherType().isBlank();
        boolean anyIds = hasNonBlank(tool.getIds());
        if (!anyTags && !gatherTypeSet && !anyIds) {
            out.add(Finding.warning(DOMAIN, "EMPTY_TOOL_GATE",
                    label + " authors a Tool group with no non-blank Tags, Gather, or Ids route; the gate is a no-op (remove the group or fill it)", id));
        }
        if (gather != null && !gatherTypeSet) {
            out.add(Finding.warning(DOMAIN, "BLANK_GATHER_TYPE",
                    label + " authors a Tool.Gather route with a blank GatherType; the functional test can never fire", id));
        }
        checkXpScale(tool, gatherTypeSet ? gather.getGatherType() : null, id, label, out);
        checkDurability(tool, id, label, out);
    }

    private static void checkDurability(@Nonnull StationAsset.Tool tool, @Nonnull String id,
                                        @Nonnull String label, @Nonnull List<Finding> out) {
        StationAsset.Tool.Durability durability = tool.getDurability();
        if (durability == null) {
            return;
        }
        boolean perSwingOn = durability.getPerSwing() != null && durability.getPerSwing() > 0;
        boolean perCycleOn = durability.getPerCycle() != null && durability.getPerCycle() > 0;
        if (!perSwingOn && !perCycleOn) {
            out.add(Finding.warning(DOMAIN, "DEAD_DURABILITY_GROUP",
                    label + " authors a Tool.Durability group with no positive PerSwing or PerCycle; the drain is a no-op", id));
            return;
        }
        if (perSwingOn) {
            out.add(Finding.info(DOMAIN, "DURABILITY_PERSWING_ADVISORY",
                    label + " authors Tool.Durability.PerSwing " + durability.getPerSwing()
                            + "; a fast Animation.Swing.IntervalMs multiplies the wear - balance is the author's responsibility", id));
        }
    }

    private static void checkXpScale(@Nonnull StationAsset.Tool tool, @Nullable String gatherFallback,
                                     @Nonnull String id, @Nonnull String label, @Nonnull List<Finding> out) {
        StationAsset.Tool.XpScale scale = tool.getXpScale();
        if (scale == null) {
            return;
        }
        if (scale.getReferencePower() == null || scale.getReferencePower() <= 0) {
            out.add(Finding.warning(DOMAIN, "DEAD_XP_SCALE",
                    label + " authors a Tool.XpScale with a null or nonpositive ReferencePower; the multiplier stays 1.0 forever", id));
        }
        String scaleGather = scale.getGatherType();
        boolean scaleGatherSet = scaleGather != null && !scaleGather.isBlank();
        if (!scaleGatherSet && (gatherFallback == null || gatherFallback.isBlank())) {
            out.add(Finding.warning(DOMAIN, "XP_SCALE_NO_GATHER_TYPE",
                    label + " authors a Tool.XpScale but neither XpScale.GatherType nor Tool.Gather.GatherType resolves; the scale never applies", id));
        }
        if (scale.getMinMult() != null && scale.getMaxMult() != null && scale.getMinMult() > scale.getMaxMult()) {
            out.add(Finding.error(DOMAIN, "XP_SCALE_BAD_CLAMP",
                    label + " Tool.XpScale has MinMult > MaxMult (the clamp is inverted)", id));
        }
        if (scale.getExponent() != null && scale.getExponent() <= 0) {
            out.add(Finding.warning(DOMAIN, "XP_SCALE_BAD_EXPONENT",
                    label + " Tool.XpScale authors a nonpositive Exponent", id));
        }
    }

    /**
     * Luck gate (UNCHANGED schema this leg - {@code StationAsset.Luck}, see that class's
     * javadoc): the skill-existence checks the MMO original ran here are DROPPED (skill ids
     * are not this engine's business); the tier-ladder STRUCTURAL checks stay (they are pure
     * station-shape checks, not skill-registry-dependent).
     */
    private static void checkLuck(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                  @Nonnull Predicate<String> dropListKnown, @Nonnull List<Finding> out) {
        StationAsset.Luck luck = a.getLuck();
        if (luck == null) {
            return;
        }
        StationAsset.Luck.Tier[] tiers = luck.getTiers();
        Map<String, StationAsset.Luck.Tier[]> skillTiers = luck.getSkillTiers();
        boolean hasTiers = tiers != null && tiers.length > 0;
        boolean hasSkillTiers = skillTiers != null && !skillTiers.isEmpty();
        if (!hasTiers && !hasSkillTiers) {
            return;
        }

        checkTierLadder(tiers, label + " Luck.Tiers", id, dropListKnown, out);
        if (hasSkillTiers) {
            List<String> effectiveLuckSkills = luckSkillsFallback(luck, a.getWork());
            for (Map.Entry<String, StationAsset.Luck.Tier[]> entry : skillTiers.entrySet()) {
                String skillId = entry.getKey();
                String ladderLabel = label + " Luck.SkillTiers['" + skillId + "']";
                if (skillId == null || skillId.isBlank()) {
                    out.add(Finding.warning(DOMAIN, "BLANK_SKILLTIER_KEY",
                            label + " Luck.SkillTiers has a blank skill key", id));
                } else if (!effectiveLuckSkills.contains(skillId)) {
                    out.add(Finding.warning(DOMAIN, "SKILLTIER_NOT_A_LUCK_SKILL",
                            ladderLabel + " is not one of the station's effective luck skills "
                                    + effectiveLuckSkills + " - this ladder can never roll", id));
                }
                checkTierLadder(entry.getValue(), ladderLabel, id, dropListKnown, out);
            }
        }

        out.add(Finding.info(DOMAIN, "TIERS_ECONOMY_ADVISORY",
                label + " authors loot Tiers/SkillTiers: the aggregate is UNCAPPED (a floor above 100% is"
                        + " reachable), so frequency control lives entirely in the droplist's own Empty"
                        + " weights - a rich table can net-multiply materials; balance is the author's"
                        + " responsibility", id));
    }

    /**
     * The station's effective luck skills for the {@code SKILLTIER_NOT_A_LUCK_SKILL} check
     * only (mirrors the MMO's {@code StationLuck.luckSkills} pure resolution - not a live
     * call, since {@code StationLuck} did not move this leg; see this class's javadoc).
     */
    @Nonnull
    private static List<String> luckSkillsFallback(@Nullable StationAsset.Luck luck, @Nullable StationAsset.Work work) {
        if (luck != null && luck.getSkills() != null) {
            List<String> out = new ArrayList<>();
            for (String s : luck.getSkills()) {
                if (s != null && !s.isBlank() && !out.contains(s)) {
                    out.add(s);
                }
            }
            return out;
        }
        List<String> out = new ArrayList<>();
        if (work != null && work.getXp() != null) {
            for (StationAsset.WorkXp xp : work.getXp()) {
                if (xp != null && xp.getSkill() != null && !xp.getSkill().isBlank() && !out.contains(xp.getSkill())) {
                    out.add(xp.getSkill());
                }
            }
        }
        return out;
    }

    private static void checkTierLadder(@Nullable StationAsset.Luck.Tier[] tiers, @Nonnull String ladderLabel,
                                        @Nonnull String id, @Nonnull Predicate<String> dropListKnown,
                                        @Nonnull List<Finding> out) {
        if (tiers == null || tiers.length == 0) {
            return;
        }
        Set<Double> seenFloors = new HashSet<>();
        for (int i = 0; i < tiers.length; i++) {
            StationAsset.Luck.Tier t = tiers[i];
            String tLabel = ladderLabel + "[" + i + "]";
            if (t == null) {
                continue;
            }
            Double minLuck = t.getMinLuck();
            if (minLuck == null || minLuck <= 0.0) {
                out.add(Finding.error(DOMAIN, "TIER_MISSING_FLOOR",
                        tLabel + " has a null or nonpositive MinLuck - this tier can never be reached", id));
            }
            String dropList = t.getDropList();
            if (dropList == null || dropList.isBlank()) {
                out.add(Finding.error(DOMAIN, "TIER_MISSING_DROPLIST",
                        tLabel + " has no DropList - this tier rolls nothing even if reached", id));
            } else if (!dropListKnown.test(dropList)) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_DROPLIST",
                        tLabel + " references unknown ItemDropList '" + dropList + "'", id));
            }
            if (minLuck != null && minLuck > 0.0 && minLuck < 5.0) {
                out.add(Finding.warning(DOMAIN, "TIER_SUSPECT_FRACTION",
                        tLabel + " has MinLuck " + minLuck
                                + " (did you author a fraction? MinLuck is a percent, e.g. 50 not 0.5)", id));
            }
            if (minLuck != null && minLuck > 0.0 && !seenFloors.add(minLuck)) {
                out.add(Finding.warning(DOMAIN, "TIER_DUPLICATE_FLOOR",
                        tLabel + " repeats MinLuck " + minLuck + " (the later entry is unreachable)", id));
            }
            warnUnplayedPresentationLeaves(t.getPresentation(), tLabel + ".Presentation", id,
                    "TIER_UNPLAYED_LEAVES", out);
        }
    }

    private static boolean hasNonBlank(@Nullable String[] values) {
        if (values == null) {
            return false;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static void checkRecipe(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                    @Nonnull List<Finding> out) {
        StationAsset.Recipe recipe = a.getRecipe();
        StationAsset.Conversion[] conversions = recipe != null ? recipe.getConversions() : null;
        StationAsset.FromCrafting fromCrafting = recipe != null ? recipe.getFromCrafting() : null;
        boolean hasConversions = conversions != null && conversions.length > 0;
        if (!hasConversions && fromCrafting == null) {
            out.add(Finding.error(DOMAIN, "EMPTY_CONVERSIONS",
                    label + " has neither Recipe.Conversions nor Recipe.FromCrafting - the work loop can never run a cycle", id));
            return;
        }
        if (fromCrafting != null) {
            checkFromCrafting(fromCrafting, id, label, out);
        }
        if (hasConversions) {
            checkConversions(conversions, id, label, out);
        }
    }

    private static void checkFromCrafting(@Nonnull StationAsset.FromCrafting fc, @Nonnull String id,
                                          @Nonnull String label, @Nonnull List<Finding> out) {
        if (!hasNonBlank(fc.getCategories())) {
            out.add(Finding.error(DOMAIN, "FROMCRAFTING_NO_CATEGORIES",
                    label + " Recipe.FromCrafting has no non-blank Categories - it can derive nothing", id));
        }
        if (fc.getOutputPerInput() != null && fc.getOutputPerInput() <= 0) {
            out.add(Finding.warning(DOMAIN, "NONPOSITIVE_OUTPUT_PER_INPUT",
                    label + " Recipe.FromCrafting has a nonpositive OutputPerInput (the deriver defaults it to 1)", id));
        }
    }

    private static void checkConversions(@Nonnull StationAsset.Conversion[] conversions, @Nonnull String id,
                                         @Nonnull String label, @Nonnull List<Finding> out) {
        Set<String> seenInputs = new HashSet<>();
        for (int i = 0; i < conversions.length; i++) {
            StationAsset.Conversion c = conversions[i];
            String cLabel = label + " conversion[" + i + "]";
            if (c == null || c.getInput() == null) {
                out.add(Finding.error(DOMAIN, "MISSING_CONVERSION_INPUT",
                        cLabel + " has no Input", id));
                continue;
            }
            StationAsset.Ingredient in = c.getInput();
            boolean hasItemId = in.getItemId() != null && !in.getItemId().isBlank();
            boolean hasResource = in.getResourceTypeId() != null && !in.getResourceTypeId().isBlank();
            if (!hasItemId && !hasResource) {
                out.add(Finding.error(DOMAIN, "MISSING_CONVERSION_INPUT",
                        cLabel + " Input has neither ItemId nor ResourceTypeId", id));
                continue;
            }
            if (hasItemId && hasResource) {
                out.add(Finding.error(DOMAIN, "AMBIGUOUS_CONVERSION_INPUT",
                        cLabel + " Input sets both ItemId and ResourceTypeId (exactly one is required)", id));
                continue;
            }
            StationAsset.Ingredient outIng = c.getOutput();
            if (outIng == null || outIng.getItemId() == null || outIng.getItemId().isBlank()) {
                out.add(Finding.error(DOMAIN, "MISSING_CONVERSION_OUTPUT",
                        cLabel + " has no Output.ItemId", id));
                continue;
            }
            if (outIng.getResourceTypeId() != null && !outIng.getResourceTypeId().isBlank()) {
                out.add(Finding.warning(DOMAIN, "OUTPUT_RESOURCE_TYPE",
                        cLabel + " Output sets ResourceTypeId; an output must be an exact ItemId (the ResourceTypeId is ignored)", id));
            }
            if ((in.getQuantity() != null && in.getQuantity() <= 0)
                    || (outIng.getQuantity() != null && outIng.getQuantity() <= 0)) {
                out.add(Finding.error(DOMAIN, "NONPOSITIVE_CONVERSION_COUNT",
                        cLabel + " has a nonpositive item Quantity", id));
            }
            String inputRef = hasResource ? in.getResourceTypeId() : in.getItemId();
            if (!seenInputs.add(inputRef.toLowerCase(Locale.ROOT))) {
                out.add(Finding.warning(DOMAIN, "DUPLICATE_CONVERSION_INPUT",
                        cLabel + " repeats input '" + inputRef
                                + "' (first match wins; this entry is dead)", id));
            }
        }
    }

    private static void checkWork(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                  @Nonnull List<Finding> out) {
        StationAsset.Work work = a.getWork();
        if (work == null) {
            return;
        }
        if (work.getCycleMs() != null && work.getCycleMs() <= 0) {
            out.add(Finding.error(DOMAIN, "NONPOSITIVE_CYCLE_MS",
                    label + " has a nonpositive Work.CycleMs", id));
        }
        long effectiveCycleMs = work.getCycleMs() != null && work.getCycleMs() > 0
                ? work.getCycleMs() : StationService.DEFAULT_CYCLE_MS;
        checkIdle(work.getIdle(), effectiveCycleMs, id, label, out);
        if (work.getXp() == null) {
            return;
        }
        for (StationAsset.WorkXp xp : work.getXp()) {
            if (xp == null) {
                continue;
            }
            if (xp.getSkill() == null || xp.getSkill().isBlank()) {
                out.add(Finding.warning(DOMAIN, "MISSING_XP_SKILL",
                        label + " has a Work.Xp entry with no Skill", id));
                continue;
            }
            if (xp.getPerCycle() != null && xp.getPerCycle() <= 0) {
                out.add(Finding.warning(DOMAIN, "NONPOSITIVE_XP_PER_CYCLE",
                        label + " has a nonpositive Work.Xp PerCycle for '" + xp.getSkill()
                                + "' (the entry grants nothing)", id));
            }
        }
    }

    private static void checkIdle(@Nullable StationAsset.Work.Idle idle, long effectiveCycleMs,
                                  @Nonnull String id, @Nonnull String label, @Nonnull List<Finding> out) {
        if (idle == null) {
            return;
        }
        boolean enabled = idle.getEnabled() != null && idle.getEnabled();
        if (enabled && idle.getCycleMs() != null && idle.getCycleMs() <= 0) {
            out.add(Finding.error(DOMAIN, "IDLE_NONPOSITIVE_CYCLE",
                    label + " enables Work.Idle with a nonpositive CycleMs", id));
        }
        if (idle.getCycleMs() != null && idle.getCycleMs() < 2 * effectiveCycleMs) {
            out.add(Finding.warning(DOMAIN, "IDLE_NOT_DELAYED",
                    label + " authors Work.Idle.CycleMs below 2x the effective Work.CycleMs; the reader floors it, but the author should raise it", id));
        }
        if (idle.getXpFraction() != null && (idle.getXpFraction() > 0.25 || idle.getXpFraction() <= 0)) {
            out.add(Finding.warning(DOMAIN, "IDLE_FRACTION_RANGE",
                    label + " authors a Work.Idle.XpFraction outside the tiny-value contract (0, 0.25]", id));
        }
    }

    /**
     * RpgStations' OWN Requires check (design section 4.4.2): a {@code Condition}
     * referencing an unregistered factor id warns ({@code UNKNOWN_FACTOR} - fail-open at
     * validate time since providers may register later, matching {@link #validate()}'s live
     * entry point). No permission-existence check is possible (permission nodes are free text).
     */
    private static void checkRequires(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                      @Nonnull Predicate<String> factorKnown, @Nonnull List<Finding> out) {
        Requires reqs = a.getRequires();
        if (reqs == null || reqs.getConditions() == null) {
            return;
        }
        for (Condition c : reqs.getConditions()) {
            if (c == null || c.getFactor() == null || c.getFactor().isBlank()) {
                continue;
            }
            if (!factorKnown.test(c.getFactor())) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_FACTOR",
                        label + " Requires.Conditions references unknown factor '" + c.getFactor()
                                + "' (the gate fails closed at runtime until a provider registers it)", id));
            }
        }
    }

    private static void checkPresentationRefs(@Nonnull StationAsset a, @Nonnull String id,
                                              @Nonnull String label, @Nonnull List<Finding> out) {
        if (a.getAnimation() != null && a.getAnimation().getEmoteId() != null
                && a.getAnimation().getEmoteId().isBlank()) {
            out.add(Finding.warning(DOMAIN, "BLANK_EMOTE_ID",
                    label + " authors an empty Animation.EmoteId", id));
        }
        if (a.getHold() != null && a.getHold().getEffectId() != null
                && a.getHold().getEffectId().isBlank()) {
            out.add(Finding.warning(DOMAIN, "BLANK_EFFECT_ID",
                    label + " authors an empty Hold.EffectId", id));
        }
    }

    private static void checkAnimation(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                       @Nonnull List<Finding> out) {
        StationAsset.Animation animation = a.getAnimation();
        if (animation == null) {
            return;
        }
        StationAsset.Animation.Swing swing = animation.getSwing();
        if (swing == null) {
            if (animation.getActionClip() != null && !animation.getActionClip().isBlank()) {
                out.add(Finding.warning(DOMAIN, "ACTION_CLIP_WITHOUT_SWING",
                        label + " authors Animation.ActionClip with no Animation.Swing group;"
                                + " the seat-mode swing re-fire only happens per swing tick, so ActionClip never fires", id));
            }
            return;
        }
        String swingLabel = label + " Animation.Swing";
        if (swing.getIntervalMs() == null || swing.getIntervalMs() <= 0) {
            out.add(Finding.error(DOMAIN, "NONPOSITIVE_SWING_INTERVAL",
                    swingLabel + " has a null or nonpositive IntervalMs - the swing timer stays off", id));
        } else if (swing.getIntervalMs() < 250) {
            out.add(Finding.warning(DOMAIN, "SWING_INTERVAL_SPAM",
                    swingLabel + " has an IntervalMs under 250ms (sound spam; faster than any vanilla swing clip)", id));
        }
        if (animation.getEmoteId() == null || animation.getEmoteId().isBlank()) {
            out.add(Finding.warning(DOMAIN, "SWING_WITHOUT_EMOTE",
                    swingLabel + " is authored with no Animation.EmoteId (legal - pure ambience - but usually an authoring mistake)", id));
        }
        warnUnplayedPresentationLeaves(swing.getPresentation(), swingLabel + ".Presentation", id,
                "SWING_UNPLAYED_LEAVES", out);
        checkImpact(swing, swingLabel, id, out);
    }

    private static void checkImpact(@Nonnull StationAsset.Animation.Swing swing, @Nonnull String swingLabel,
                                    @Nonnull String id, @Nonnull List<Finding> out) {
        StationAsset.Animation.Swing.Impact impact = swing.getImpact();
        if (impact == null) {
            return;
        }
        String impactLabel = swingLabel + ".Impact";
        Long delayMs = impact.getDelayMs();
        Long intervalMs = swing.getIntervalMs();
        if (delayMs != null && delayMs > 0 && intervalMs != null && intervalMs > 0 && delayMs >= intervalMs) {
            out.add(Finding.warning(DOMAIN, "IMPACT_OVERLAPS_NEXT_SWING",
                    impactLabel + " DelayMs " + delayMs + " is >= Swing.IntervalMs " + intervalMs
                            + " (the delayed impact lands at or after the next swing re-plays the whole moment)", id));
        }
        if (impact.getPresentation() == null) {
            out.add(Finding.warning(DOMAIN, "IMPACT_WITHOUT_PRESENTATION",
                    impactLabel + " is authored with no Presentation - the delay has nothing to play", id));
        }
        warnUnplayedPresentationLeaves(impact.getPresentation(), impactLabel + ".Presentation", id,
                "IMPACT_UNPLAYED_LEAVES", out);
    }

    private static void checkCamera(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                    @Nonnull List<Finding> out) {
        StationAsset.Camera camera = a.getCamera();
        if (camera == null) {
            return;
        }
        boolean faceBlock = camera.getFaceBlock() != null && camera.getFaceBlock();
        boolean cameraOff = camera.getMode() != null && "None".equalsIgnoreCase(camera.getMode());
        if (faceBlock && cameraOff) {
            out.add(Finding.warning(DOMAIN, "FACE_BLOCK_WITHOUT_CAMERA",
                    label + " authors Camera.FaceBlock true with Camera.Mode \"None\" - the leaf can never take effect", id));
        }
        String faceBlockMode = camera.getFaceBlockMode();
        if (faceBlockMode != null && !faceBlockMode.isBlank()
                && StationCameraPreset.fromId(faceBlockMode) == null) {
            out.add(Finding.warning(DOMAIN, "UNKNOWN_FACE_BLOCK_MODE",
                    label + " authors Camera.FaceBlockMode '" + faceBlockMode
                            + "' which is not a known StationCameraPreset id - falls back to 'look_rot' at runtime", id));
        }
        StationAsset.Hold hold = a.getHold();
        StationAsset.Hold.Seat seat = hold != null ? hold.getSeat() : null;
        boolean seatMode = seat != null && seat.getEnabled() != null && seat.getEnabled();
        if (seatMode && faceBlock) {
            out.add(Finding.warning(DOMAIN, "SEAT_FACE_BLOCK_CONFLICT",
                    label + " authors both Hold.Seat.Enabled true and Camera.FaceBlock true - the native seat"
                            + " mount already locks facing while keeping the camera free; the packet-level"
                            + " FaceBlock lock on top is redundant (or conflicting) with it", id));
        }
    }

    private static void checkCompletion(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                        @Nonnull List<Finding> out) {
        warnUnplayedPresentationLeaves(a.getCompletion(), label + ".Completion", id,
                "COMPLETION_UNPLAYED_LEAVES", out);
    }

    private static void checkFlairs(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                    @Nonnull List<Finding> out) {
        Map<String, StationAsset.Flair> flairs = a.getFlairs();
        if (flairs == null || flairs.isEmpty()) {
            return;
        }
        for (Map.Entry<String, StationAsset.Flair> entry : flairs.entrySet()) {
            String flairId = entry.getKey();
            StationAsset.Flair flair = entry.getValue();
            if (flairId == null || flairId.isBlank()) {
                out.add(Finding.warning(DOMAIN, "BLANK_FLAIR_ID",
                        label + " Flairs has a blank flair id", id));
                flairId = "(blank)";
            }
            if (flair == null) {
                continue;
            }
            String flairLabel = label + " Flairs['" + flairId + "']";
            if (flair.getSwing() == null && flair.getCycle() == null && flair.getRareFind() == null
                    && flair.getCompletion() == null) {
                out.add(Finding.warning(DOMAIN, "EMPTY_FLAIR",
                        flairLabel + " authors neither Swing, Cycle, RareFind, nor Completion - it can never overlay anything", id));
            }
            warnUnplayedPresentationLeaves(flair.getSwing(), flairLabel + ".Swing", id,
                    "FLAIR_UNPLAYED_LEAVES", out);
            warnUnplayedPresentationLeaves(flair.getCycle(), flairLabel + ".Cycle", id,
                    "FLAIR_UNPLAYED_LEAVES", out);
            warnUnplayedPresentationLeaves(flair.getRareFind(), flairLabel + ".RareFind", id,
                    "FLAIR_UNPLAYED_LEAVES", out);
            warnUnplayedPresentationLeaves(flair.getCompletion(), flairLabel + ".Completion", id,
                    "FLAIR_UNPLAYED_LEAVES", out);
        }
    }

    /**
     * Shared leaf check: station-scale playback ({@code StationService.emitMoment}) renders
     * {@code Sound} + {@code Particles} + (new this leg) {@code Shake} - see that method's
     * javadoc - so an authored {@code Animation}/{@code AnimationItem}/{@code AnimationSlot}/
     * {@code Camera} leaf is dead weight.
     */
    private static void warnUnplayedPresentationLeaves(@Nullable Presentation p, @Nonnull String label,
                                                        @Nonnull String id, @Nonnull String code,
                                                        @Nonnull List<Finding> out) {
        if (p == null) {
            return;
        }
        boolean hasUnplayedLeaf = notBlank(p.getAnimation()) || notBlank(p.getAnimationItem())
                || notBlank(p.getAnimationSlot()) || notBlank(p.getCamera());
        if (hasUnplayedLeaf) {
            out.add(Finding.warning(DOMAIN, code,
                    label + " authors an Animation/AnimationItem/AnimationSlot/Camera leaf;"
                            + " station-scale playback renders Sound + Particles + Shake only", id));
        }
    }

    private static boolean notBlank(@Nullable String s) {
        return s != null && !s.isBlank();
    }

    // ==================== Reporting (thin delegators over the shared core) ====================

    @Nonnull
    public static String summarize(@Nonnull List<Finding> findings) {
        return Report.summarize("Station validation", findings);
    }

    public static int problemCount(@Nonnull List<Finding> findings) {
        return Report.problemCount(findings);
    }

    /** Validate the live catalog and log a summary (+ per-finding detail). Never throws. */
    public static void runAndLog() {
        Report.logTo(DOMAIN, "Station validation", validate());
    }
}
