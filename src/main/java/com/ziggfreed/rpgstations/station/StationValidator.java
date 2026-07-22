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
import com.ziggfreed.rpgstations.api.impl.FactorRegistryImpl;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.FlairAsset;
import com.ziggfreed.rpgstations.asset.LootableAsset;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.i18n.RpgStationsLangKeys;
import com.ziggfreed.rpgstations.loot.LootableCatalog;
import com.ziggfreed.rpgstations.loot.RollPoolCatalog;
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

    /**
     * Validate the live catalog (stations, named lootable tables, AND (leg F, design section 9.6)
     * standalone {@link FlairAsset}s - design section 4.8's "validator coverage"). Never throws;
     * returns an empty list on failure. {@code factorKnown} is backed by the LIVE api-facing
     * {@link FactorRegistryImpl} (the built-in {@code rpgstations:} factors are always registered
     * by plugin {@code setup()}, so this is a real check now, unlike the leg-2 fail-open
     * placeholder).
     */
    @Nonnull
    public static List<Finding> validate() {
        try {
            List<Finding> out = new ArrayList<>(validate(StationCatalog.getInstance().all().values(),
                    StationValidator::langKeyKnownLive,
                    StationValidator::dropListKnownLive,
                    FactorRegistryImpl.getInstance()::isKnown,
                    id -> LootableCatalog.getInstance().get(id) != null,
                    id -> RollPoolCatalog.getInstance().get(id) != null,
                    StationValidator::modelKnownLive));
            out.addAll(validateLootables(LootableCatalog.getInstance().all().values(),
                    StationValidator::dropListKnownLive, FactorRegistryImpl.getInstance()::isKnown));
            out.addAll(validateFlairAssets(FlairCatalog.getInstance().all().values(),
                    id -> StationCatalog.getInstance().getStation(id) != null));
            return out;
        } catch (Throwable t) {
            Log.warn("Station validation aborted: " + t.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * The STRUCTURAL-only pass (D4 fix - timing, not checks): every per-station/per-action check
     * EXCEPT the cross-layer reference-existence ones (lang key / native {@code ItemDropList} id /
     * this mod's own {@code Lootable}/{@code RollPool}/station-id references). Those depend on
     * OTHER asset stores or the merged i18n lang map that may not have finished folding for a
     * LATER pack layer yet at the moment THIS layer's Station/Flair fold callback fires (the
     * boot-log evidence: {@code STAMP_UNKNOWN_POOL} for a RollPool folded one line later,
     * {@code LOOT_UNKNOWN_DROPLIST}/{@code MISSING_*_LANG} for a pack layer's own Drops/lang that
     * had not loaded yet relative to that SAME layer's Station fold). Safe to run at EVERY
     * per-fold event (never a false positive from an incomplete later layer); {@link #validate()}
     * (still the full set) now only runs from {@code /rpgstations validate} (already post-load)
     * and the ONE deferred post-load audit ({@code RpgStationsPlugin}'s first-{@code
     * PlayerReadyEvent} hook, mirroring the MMO's own {@code ContentAudit} startup-audit timing).
     */
    @Nonnull
    public static List<Finding> validateStructural() {
        try {
            List<Finding> out = new ArrayList<>(validate(StationCatalog.getInstance().all().values(),
                    ALWAYS_KNOWN, ALWAYS_KNOWN, FactorRegistryImpl.getInstance()::isKnown, ALWAYS_KNOWN, ALWAYS_KNOWN,
                    ALWAYS_KNOWN));
            out.addAll(validateLootables(LootableCatalog.getInstance().all().values(),
                    ALWAYS_KNOWN, FactorRegistryImpl.getInstance()::isKnown));
            out.addAll(validateFlairAssets(FlairCatalog.getInstance().all().values(), ALWAYS_KNOWN));
            return out;
        } catch (Throwable t) {
            Log.warn("Station validation (structural) aborted: " + t.getMessage());
            return new ArrayList<>();
        }
    }

    /** A cross-layer reference check deferred out of the per-fold structural pass - always passes. */
    private static final Predicate<String> ALWAYS_KNOWN = id -> true;

    /** Live {@code ItemDropList} existence check (asset-map lookup - never throws). */
    private static boolean dropListKnownLive(@Nonnull String dropListId) {
        try {
            return ItemDropList.getAssetMap().getAsset(dropListId) != null;
        } catch (Throwable t) {
            return true; // a lookup failure is not evidence the id is wrong - don't flag it
        }
    }

    /**
     * Live {@code ModelAsset} existence check (round-4 puppet design's {@code Puppet.Look.ModelId}
     * reference), over {@code ziggfreed-common}'s {@code entity.PlayerModelService#modelExists}
     * (the same live asset-map lookup {@code Hide.Route: "ModelSwap"}'s predecessor design used -
     * never throws, fails OPEN here to match {@link #dropListKnownLive}'s stance).
     */
    private static boolean modelKnownLive(@Nonnull String modelId) {
        try {
            return com.ziggfreed.common.entity.PlayerModelService.modelExists(modelId);
        } catch (Throwable t) {
            return true; // a lookup failure is not evidence the id is wrong - don't flag it
        }
    }

    /**
     * Lang-key-known check (design 4.8/critique m10), MERGED view (D5 fix): a
     * {@link RpgStationsLangKeys} hit answers fast for the jar's own shipped keys, but a pack
     * (e.g. the anvil's {@code station.anvil.name}/{@code .desc}) can additively author its OWN
     * {@code rpgstations.lang} overlay this hand-maintained jar-only set never knows about - so a
     * miss falls through to a LIVE query against the engine's actual merged i18n store
     * ({@code I18nModule.getMessage}, the same lookup a client's own message resolution uses),
     * which sees every loaded layer (jar defaults AND every pack overlay), not just the jar's.
     * Fails OPEN on a lookup error (module not up yet, etc.) - matching
     * {@link #dropListKnownLive}'s own "a lookup failure is not evidence the key is wrong" stance.
     */
    private static boolean langKeyKnownLive(@Nonnull String fullKey) {
        if (RpgStationsLangKeys.isKnown(fullKey)) {
            return true;
        }
        try {
            var i18n = com.hypixel.hytale.server.core.modules.i18n.I18nModule.get();
            return i18n != null && i18n.getMessage(
                    com.hypixel.hytale.server.core.modules.i18n.I18nModule.DEFAULT_LANGUAGE, fullKey) != null;
        } catch (Throwable t) {
            return true; // a lookup failure is not evidence the key is missing - don't flag it
        }
    }

    /**
     * Singleton-free core (4-arg convenience: {@code lootableKnown} defaults to always-known,
     * for a caller that does not care about {@code Loot.Tables} reference checks - e.g. every
     * pre-leg-3 test fixture). {@code langKeyKnown} answers "does this rpgstations lang key
     * exist"; {@code dropListKnown} answers "does this native ItemDropList asset id exist";
     * {@code factorKnown} answers "is this factor id registered" (warn-not-error either way -
     * "providers may register later").
     */
    @Nonnull
    public static List<Finding> validate(@Nonnull Collection<StationAsset> stations,
                                         @Nonnull Predicate<String> langKeyKnown,
                                         @Nonnull Predicate<String> dropListKnown,
                                         @Nonnull Predicate<String> factorKnown) {
        return validate(stations, langKeyKnown, dropListKnown, factorKnown, id -> true, id -> true, id -> true);
    }

    /**
     * Singleton-free core, 6-arg convenience - {@code lootableKnown} answers "does this
     * LootableAsset id exist"; {@code rollPoolKnown} (phase 2 leg E) answers "does this RollPool
     * id exist"; {@code modelKnown} (round-4 puppet design) defaults to always-known here, for a
     * caller that does not care about {@code Puppet.Look.ModelId} reference checks.
     */
    @Nonnull
    public static List<Finding> validate(@Nonnull Collection<StationAsset> stations,
                                         @Nonnull Predicate<String> langKeyKnown,
                                         @Nonnull Predicate<String> dropListKnown,
                                         @Nonnull Predicate<String> factorKnown,
                                         @Nonnull Predicate<String> lootableKnown,
                                         @Nonnull Predicate<String> rollPoolKnown) {
        return validate(stations, langKeyKnown, dropListKnown, factorKnown, lootableKnown, rollPoolKnown, id -> true);
    }

    /**
     * Singleton-free core, full form - {@code modelKnown} (round-4 puppet-presentation design)
     * answers "does this ModelAsset id exist" for a {@code Puppet.Look.Source: "Model"}'s
     * {@code ModelId} reference (warn-not-error, same stance as every other reference check here).
     */
    @Nonnull
    public static List<Finding> validate(@Nonnull Collection<StationAsset> stations,
                                         @Nonnull Predicate<String> langKeyKnown,
                                         @Nonnull Predicate<String> dropListKnown,
                                         @Nonnull Predicate<String> factorKnown,
                                         @Nonnull Predicate<String> lootableKnown,
                                         @Nonnull Predicate<String> rollPoolKnown,
                                         @Nonnull Predicate<String> modelKnown) {
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
            checkLoot(a, id, label, dropListKnown, factorKnown, lootableKnown, out);
            checkRequires(a, id, label, factorKnown, out);
            checkAnimation(a, id, label, out);
            checkPresentationRefs(a, id, label, out);
            checkCamera(a, id, label, out);
            checkMount(a, id, label, out);
            checkCompletion(a, id, label, out);
            checkFlairs(a, id, label, out);
            checkCustody(a.getCustody(), a.getRecipe(), label, id, out);
            checkPuppet(a.getPuppet(), a.getHold(), label, id, modelKnown, out);
            checkActions(a, id, label, dropListKnown, factorKnown, lootableKnown, rollPoolKnown, modelKnown, out);
        }
        return out;
    }

    /**
     * Placed-input custody (design section 9.4, phase-2 leg C): a {@link Custody} group with
     * neither an explicit {@link Custody#getInput()} NOR an {@code effectiveRecipe} to derive
     * placement acceptance from has no way to ever accept a held stack - the state-dependent F
     * interaction can never place anything (a silent dead-content trap, not merely cosmetic).
     */
    private static void checkCustody(@Nullable Custody custody, @Nullable StationAsset.Recipe effectiveRecipe,
            @Nonnull String label, @Nonnull String id, @Nonnull List<Finding> out) {
        if (custody == null) {
            return;
        }
        if (custody.getInput() == null && effectiveRecipe == null) {
            out.add(Finding.warning(DOMAIN, "CUSTODY_NO_INPUT_MATCHER",
                    label + " authors a Custody group with no Input matcher AND no Recipe to derive"
                            + " placement acceptance from - nothing can ever be placed", id));
        }
        Integer maxQuantity = custody.getMaxQuantity();
        if (maxQuantity != null && maxQuantity <= 0) {
            out.add(Finding.warning(DOMAIN, "CUSTODY_NON_POSITIVE_MAX",
                    label + " Custody.MaxQuantity is non-positive (" + maxQuantity + ") - falls back to the "
                            + Custody.DEFAULT_MAX_QUANTITY + " default", id));
        }
        Custody.Display display = custody.getDisplay();
        if (display != null && display.getScale() != null && display.getScale() <= 0) {
            out.add(Finding.warning(DOMAIN, "CUSTODY_DISPLAY_NON_POSITIVE_SCALE",
                    label + " Custody.Display.Scale is non-positive (" + display.getScale() + ") - falls back to "
                            + "the 1.0 default", id));
        }
    }

    /**
     * The round-4 puppet-presentation route (design {@code rpg-stations-puppet-presentation
     * -design-2026-07-22.md} section 3.7): every finding here is a WARNING or INFO (advisory),
     * never an ERROR, per the maintainer's "validator warns on odd combinations, never blocks."
     * {@code hold} is the RESOLVED {@code Hold} group for the same scope {@code puppet} resolves
     * at (the station's own {@code Hold} at station scope, or the action's own/inherited
     * {@code Hold} when called from {@code checkActions}).
     */
    private static void checkPuppet(@Nullable Puppet puppet, @Nullable StationAsset.Hold hold,
            @Nonnull String label, @Nonnull String id, @Nonnull Predicate<String> modelKnown,
            @Nonnull List<Finding> out) {
        if (puppet == null) {
            return;
        }
        boolean enabled = puppet.effectiveEnabled();

        Puppet.Hide hide = puppet.getHide();
        String effectiveRoute = hide != null ? hide.effectiveRoute() : Puppet.HIDE_ROUTE_SCALE;
        if (hide != null) {
            String rawRoute = hide.getRoute();
            if (rawRoute != null && !rawRoute.isBlank() && !Puppet.HIDE_ROUTE_SCALE.equalsIgnoreCase(rawRoute)
                    && !Puppet.HIDE_ROUTE_EFFECT.equalsIgnoreCase(rawRoute)
                    && !Puppet.HIDE_ROUTE_NONE.equalsIgnoreCase(rawRoute)) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_PUPPET_HIDE_ROUTE",
                        label + " Puppet.Hide.Route '" + rawRoute
                                + "' is not one of Scale/Effect/None - falls back to Scale at runtime", id));
            }
            if (Puppet.HIDE_ROUTE_EFFECT.equalsIgnoreCase(effectiveRoute)
                    && (hide.getEffectId() == null || hide.getEffectId().isBlank())) {
                out.add(Finding.warning(DOMAIN, "PUPPET_HIDE_EFFECT_MISSING_ID",
                        label + " Puppet.Hide.Route is \"Effect\" but EffectId is blank - the route is inert"
                                + " (Effect is schema-reserved, unimplemented this leg)", id));
            }
        }
        if (enabled && Puppet.HIDE_ROUTE_NONE.equalsIgnoreCase(effectiveRoute)) {
            out.add(Finding.warning(DOMAIN, "PUPPET_WITHOUT_HIDE",
                    label + " authors an active Puppet with Hide.Route \"None\" - the real player AND the"
                            + " puppet both render (a deliberate two-worker look, or an authoring oversight)", id));
        }
        if (!enabled && hide != null && !Puppet.HIDE_ROUTE_NONE.equalsIgnoreCase(effectiveRoute)) {
            out.add(Finding.warning(DOMAIN, "HIDE_WITHOUT_PUPPET",
                    label + " authors Puppet.Hide.Route \"" + effectiveRoute + "\" but Puppet.Enabled is false -"
                            + " the hide route never applies (Enabled gates the whole group)", id));
        }

        Puppet.Look look = puppet.getLook();
        if (look != null) {
            String rawSource = look.getSource();
            if (rawSource != null && !rawSource.isBlank() && !Puppet.LOOK_SOURCE_PLAYER_CLONE.equalsIgnoreCase(rawSource)
                    && !Puppet.LOOK_SOURCE_MODEL.equalsIgnoreCase(rawSource)) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_PUPPET_LOOK_SOURCE",
                        label + " Puppet.Look.Source '" + rawSource
                                + "' is not one of PlayerClone/Model - falls back to PlayerClone at runtime", id));
            }
            if (Puppet.LOOK_SOURCE_MODEL.equalsIgnoreCase(look.effectiveSource())) {
                String modelId = look.getModelId();
                boolean modelIdBlank = modelId == null || modelId.isBlank();
                String fallback = look.getFallbackModelId();
                boolean fallbackAuthored = fallback != null && !fallback.isBlank();
                if (!fallbackAuthored && (modelIdBlank || !modelKnown.test(modelId))) {
                    out.add(Finding.warning(DOMAIN, "PUPPET_LOOK_MODEL_UNKNOWN",
                            label + " Puppet.Look.Source is \"Model\" but ModelId "
                                    + (modelIdBlank ? "is blank" : "'" + modelId + "' is not a known ModelAsset")
                                    + " and no FallbackModelId is authored - falls back to the default rig at runtime",
                            id));
                }
            }
        }

        checkPuppetProp(puppet.getProp(), label + " Puppet", id, out);

        if (enabled) {
            StationAsset.Hold.Mount mount = hold != null ? hold.getMount() : null;
            boolean effectiveMovementLock = hold == null || hold.getMovementLock() == null || hold.getMovementLock();
            if (mount == null && !effectiveMovementLock) {
                out.add(Finding.warning(DOMAIN, "PUPPET_WITHOUT_HOLD",
                        label + " authors an active Puppet but the resolved Hold has neither a Mount nor an"
                                + " effective MovementLock - the player could walk away from their own puppet", id));
            }
            if (mount != null && !mount.isEntitySurface()) {
                out.add(Finding.info(DOMAIN, "PUPPET_SEAT_MOUNT_ADVISORY",
                        label + " layers an active Puppet on a Block (seat) mount - the puppet supersedes the"
                                + " seat's Action-slot swing routing entirely (design section 2), a genuine"
                                + " simplification, not a conflict", id));
            }
        }
    }

    /**
     * The shared {@link Puppet.Prop}/{@code StationStep.PuppetOverride.Prop} core (DRY - one prop
     * shape, one check, whether authored at the group level or per step).
     */
    private static void checkPuppetProp(@Nullable Puppet.Prop prop, @Nonnull String label, @Nonnull String id,
            @Nonnull List<Finding> out) {
        if (prop == null) {
            return;
        }
        String rawSource = prop.getSource();
        if (rawSource != null && !rawSource.isBlank() && !Puppet.PROP_SOURCE_MIRROR_HELD.equalsIgnoreCase(rawSource)
                && !Puppet.PROP_SOURCE_ITEM_ID.equalsIgnoreCase(rawSource)
                && !Puppet.PROP_SOURCE_NONE.equalsIgnoreCase(rawSource)) {
            out.add(Finding.warning(DOMAIN, "UNKNOWN_PUPPET_PROP_SOURCE",
                    label + ".Prop.Source '" + rawSource
                            + "' is not one of MirrorHeld/ItemId/None - falls back to MirrorHeld at runtime", id));
        }
        if (Puppet.PROP_SOURCE_ITEM_ID.equalsIgnoreCase(prop.effectiveSource())
                && (prop.getItemId() == null || prop.getItemId().isBlank())) {
            out.add(Finding.warning(DOMAIN, "PUPPET_PROP_ITEM_ID_MISSING",
                    label + ".Prop.Source is \"ItemId\" but Prop.ItemId is blank - the puppet holds nothing", id));
        }
        String rawSlot = prop.getSlot();
        if (rawSlot != null && !rawSlot.isBlank() && !Puppet.PROP_SLOT_HOTBAR.equalsIgnoreCase(rawSlot)
                && !Puppet.PROP_SLOT_UTILITY.equalsIgnoreCase(rawSlot)) {
            out.add(Finding.warning(DOMAIN, "UNKNOWN_PUPPET_PROP_SLOT",
                    label + ".Prop.Slot '" + rawSlot
                            + "' is not one of Hotbar/Utility - falls back to Hotbar at runtime", id));
        }
    }

    /** Validates every standalone {@link LootableAsset}'s {@code Rolls} (the same {@link #checkRoll} core). */
    @Nonnull
    public static List<Finding> validateLootables(@Nonnull Collection<LootableAsset> lootables,
                                                   @Nonnull Predicate<String> dropListKnown,
                                                   @Nonnull Predicate<String> factorKnown) {
        List<Finding> out = new ArrayList<>();
        for (LootableAsset l : lootables) {
            if (l == null) {
                continue;
            }
            String id = l.getId() == null || l.getId().isBlank() ? "(unnamed)" : l.getId();
            String label = "Lootable '" + id + "'";
            Roll[] rolls = l.getRolls();
            if (rolls == null || rolls.length == 0) {
                out.add(Finding.warning(DOMAIN, "LOOT_EMPTY_TABLE", label + " has no Rolls", id));
                continue;
            }
            for (int i = 0; i < rolls.length; i++) {
                checkRoll(rolls[i], label + ".Rolls[" + i + "]", id, dropListKnown, factorKnown, out);
            }
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
     * The conditional-lootable declaration (design section 4.4.3/4.5, REPLACES the leg-2
     * {@code checkLuck}): validates {@code Loot.Tables} references, then every inline {@code
     * Loot.Rolls} entry via the shared {@link #checkRoll} core (also used by {@link
     * #validateLootables} for a standalone {@link LootableAsset}'s own Rolls).
     */
    private static void checkLoot(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                  @Nonnull Predicate<String> dropListKnown, @Nonnull Predicate<String> factorKnown,
                                  @Nonnull Predicate<String> lootableKnown, @Nonnull List<Finding> out) {
        StationAsset.Loot loot = a.getLoot();
        if (loot == null) {
            return;
        }
        String[] tables = loot.getTables();
        if (tables != null) {
            for (String t : tables) {
                if (t == null || t.isBlank()) {
                    out.add(Finding.warning(DOMAIN, "LOOT_BLANK_TABLE", label + " Loot.Tables has a blank entry", id));
                } else if (!lootableKnown.test(t.toLowerCase(Locale.ROOT))) {
                    out.add(Finding.warning(DOMAIN, "LOOT_UNKNOWN_TABLE",
                            label + " Loot.Tables references unknown lootable '" + t + "'", id));
                }
            }
        }
        Roll[] rolls = loot.getRolls();
        if (rolls != null) {
            for (int i = 0; i < rolls.length; i++) {
                checkRoll(rolls[i], label + " Loot.Rolls[" + i + "]", id, dropListKnown, factorKnown, out);
            }
        }
    }

    /**
     * The shared {@link Roll} structural core (design 4.8's "validator coverage" + the M3
     * critique fix 5): {@code Conditions}/{@code Chance.AddFactors}/{@code Ladder.Value} factor
     * ids run through {@code factorKnown} (the {@code UNKNOWN_FACTOR} code {@link #checkRequires}
     * already uses - one code, one meaning, across every factor-reference site); every {@code
     * Grants.DropList} (top-level or per-floor) runs through {@code dropListKnown}; a {@code
     * Grants.BonusOutputCopies} authored under a non-{@code Cycle} {@link Roll#effectiveTrigger()}
     * is flagged {@code LOOT_BONUS_COPIES_WRONG_TRIGGER} (M3 fix 5 - there is no live cycle
     * output for a Completion-trigger roll to copy).
     */
    static void checkRoll(@Nullable Roll roll, @Nonnull String label, @Nonnull String id,
                          @Nonnull Predicate<String> dropListKnown, @Nonnull Predicate<String> factorKnown,
                          @Nonnull List<Finding> out) {
        if (roll == null) {
            return;
        }
        String trigger = roll.effectiveTrigger();
        checkConditionFactors(roll.getConditions(), label + ".Conditions", id, factorKnown, out);

        Roll.Chance chance = roll.getChance();
        if (chance != null) {
            checkConditionFactors(chance.getAddFactors(), label + ".Chance.AddFactors", id, factorKnown, out);
            if (chance.getBasePercent() != null && chance.getBasePercent() < 0) {
                out.add(Finding.warning(DOMAIN, "LOOT_NEGATIVE_BASE_PERCENT",
                        label + ".Chance has a negative BasePercent", id));
            }
            if (chance.getCapPercent() != null && chance.getCapPercent() <= 0) {
                out.add(Finding.warning(DOMAIN, "LOOT_NONPOSITIVE_CAP_PERCENT",
                        label + ".Chance has a nonpositive CapPercent - the roll can never hit", id));
            }
        }

        Roll.Grants topGrants = roll.getGrants();
        checkGrants(topGrants, label + ".Grants", id, trigger, dropListKnown, out);
        boolean hasAnything = topGrants != null && !topGrants.isEmpty();

        Roll.Ladder ladder = roll.getLadder();
        if (ladder != null) {
            hasAnything = true;
            Condition value = ladder.getValue();
            if (value == null || value.getFactor() == null || value.getFactor().isBlank()) {
                out.add(Finding.error(DOMAIN, "LOOT_LADDER_MISSING_VALUE",
                        label + ".Ladder has no Value.Factor - it can never resolve a floor", id));
            } else if (!factorKnown.test(value.getFactor())) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_FACTOR",
                        label + ".Ladder.Value references unknown factor '" + value.getFactor() + "'", id));
            }
            Roll.Ladder.Floor[] floors = ladder.getFloors();
            if (floors == null || floors.length == 0) {
                out.add(Finding.warning(DOMAIN, "LOOT_LADDER_EMPTY", label + ".Ladder has no Floors", id));
            } else {
                checkFloors(floors, label, id, trigger, dropListKnown, out);
            }
        }

        if (!hasAnything) {
            out.add(Finding.warning(DOMAIN, "LOOT_ROLL_EMPTY",
                    label + " authors neither Grants nor a Ladder - it can never grant anything", id));
        }
    }

    private static void checkFloors(@Nonnull Roll.Ladder.Floor[] floors, @Nonnull String rollLabel,
                                    @Nonnull String id, @Nonnull String trigger,
                                    @Nonnull Predicate<String> dropListKnown, @Nonnull List<Finding> out) {
        Set<Double> seenFloors = new HashSet<>();
        for (int i = 0; i < floors.length; i++) {
            Roll.Ladder.Floor f = floors[i];
            String fLabel = rollLabel + ".Ladder.Floors[" + i + "]";
            if (f == null) {
                continue;
            }
            Double min = f.getMin();
            if (min == null || min <= 0.0) {
                out.add(Finding.error(DOMAIN, "LOOT_LADDER_FLOOR_MISSING_MIN",
                        fLabel + " has a null or nonpositive Min - this floor can never be reached", id));
            } else if (!seenFloors.add(min)) {
                out.add(Finding.warning(DOMAIN, "LOOT_LADDER_DUPLICATE_FLOOR",
                        fLabel + " repeats Min " + min + " (the later entry is unreachable)", id));
            }
            if (f.getGrants() == null) {
                // M3 fix 2: a floor's ONLY reward path is its own Grants - no direct DropList leaf.
                out.add(Finding.error(DOMAIN, "LOOT_LADDER_FLOOR_EMPTY_GRANTS",
                        fLabel + " has no Grants - this floor rolls nothing even if reached", id));
            } else {
                checkGrants(f.getGrants(), fLabel + ".Grants", id, trigger, dropListKnown, out);
            }
            warnUnplayedPresentationLeaves(f.getPresentation(), fLabel + ".Presentation", id,
                    "LOOT_FLOOR_UNPLAYED_LEAVES", out);
        }
    }

    private static void checkConditionFactors(@Nullable Condition[] conditions, @Nonnull String label,
                                              @Nonnull String id, @Nonnull Predicate<String> factorKnown,
                                              @Nonnull List<Finding> out) {
        if (conditions == null) {
            return;
        }
        for (Condition c : conditions) {
            if (c == null || c.getFactor() == null || c.getFactor().isBlank()) {
                continue;
            }
            if (!factorKnown.test(c.getFactor())) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_FACTOR",
                        label + " references unknown factor '" + c.getFactor() + "'", id));
            }
        }
    }

    private static void checkGrants(@Nullable Roll.Grants grants, @Nonnull String label, @Nonnull String id,
                                    @Nonnull String trigger, @Nonnull Predicate<String> dropListKnown,
                                    @Nonnull List<Finding> out) {
        if (grants == null) {
            return;
        }
        if (grants.getBonusOutputCopies() != null && grants.getBonusOutputCopies() > 0
                && !Roll.TRIGGER_CYCLE.equalsIgnoreCase(trigger)) {
            out.add(Finding.warning(DOMAIN, "LOOT_BONUS_COPIES_WRONG_TRIGGER",
                    label + " authors BonusOutputCopies under a non-Cycle Trigger ('" + trigger
                            + "') - there is no live cycle output to copy there", id));
        }
        String dropListId = grants.getDropList();
        if (dropListId != null && !dropListId.isBlank() && !dropListKnown.test(dropListId)) {
            out.add(Finding.warning(DOMAIN, "LOOT_UNKNOWN_DROPLIST",
                    label + " references unknown ItemDropList '" + dropListId + "'", id));
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
        if (!hasConversions && fromCrafting == null && !anyActionProvidesRunSource(a.getActions())) {
            // Multi-action stations (design 9.1) author per-action Recipe/Steps instead of a
            // station-level one - this is only a real dead-station bug when NEITHER the station
            // level NOR any authored action can ever run a cycle.
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

    /**
     * True when at least one authored {@code Actions} entry supplies its OWN runnable recipe/
     * program source - either a per-action {@code Recipe} (Conversions or FromCrafting) or a
     * {@code Steps} program (the anvil's {@code enhance} action runs entirely off a Stamp-step
     * ritual, no Recipe at all). Mirrors {@link #checkActions}'s {@code ACTION_NO_BODY} per-action
     * check, but answers the station-wide question {@link #checkRecipe} needs: "can THIS station
     * ever run a cycle through ANY route".
     */
    private static boolean anyActionProvidesRunSource(@Nullable Map<String, ActionDef> actions) {
        if (actions == null || actions.isEmpty()) {
            return false;
        }
        for (ActionDef def : actions.values()) {
            if (def == null) {
                continue;
            }
            if (def.getSteps() != null && def.getSteps().length > 0) {
                return true;
            }
            StationAsset.Recipe actionRecipe = def.getRecipe();
            if (actionRecipe != null) {
                StationAsset.Conversion[] actionConversions = actionRecipe.getConversions();
                if ((actionConversions != null && actionConversions.length > 0) || actionRecipe.getFromCrafting() != null) {
                    return true;
                }
            }
        }
        return false;
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
        String recipe = camera.getRecipe();
        if (recipe != null && !recipe.isBlank()
                && StationCameraPreset.fromId(recipe) == null) {
            out.add(Finding.warning(DOMAIN, "UNKNOWN_CAMERA_RECIPE",
                    label + " authors Camera.Recipe '" + recipe
                            + "' which is not a known StationCameraPreset id - falls back to 'look_rot' at runtime", id));
        }
        StationAsset.Hold hold = a.getHold();
        StationAsset.Hold.Mount mount = hold != null ? hold.getMount() : null;
        if (mount != null && faceBlock) {
            out.add(Finding.warning(DOMAIN, "MOUNT_FACE_BLOCK_CONFLICT",
                    label + " authors both Hold.Mount (a native Block or Entity mount) and Camera.FaceBlock"
                            + " true - the mount already locks facing while keeping the camera free; the"
                            + " packet-level FaceBlock lock on top is redundant (or conflicting) with it", id));
        }
    }

    /**
     * The Mount knob family (design section 9.2, phase 2 leg D): an unrecognized
     * {@code Surface} value, an {@code Entity} group authored under a Block surface (ignored at
     * runtime), and the untested {@code Steerable true} combo - all warn-only, per the maintainer
     * ruling ("validator warns on odd combos, never blocks").
     */
    private static void checkMount(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                   @Nonnull List<Finding> out) {
        StationAsset.Hold hold = a.getHold();
        StationAsset.Hold.Mount mount = hold != null ? hold.getMount() : null;
        if (mount == null) {
            return;
        }
        String surface = mount.getSurface();
        boolean entitySurface = mount.isEntitySurface();
        if (surface != null && !surface.isBlank()
                && !"Block".equalsIgnoreCase(surface.trim()) && !entitySurface) {
            out.add(Finding.warning(DOMAIN, "UNKNOWN_MOUNT_SURFACE",
                    label + " authors Hold.Mount.Surface '" + surface
                            + "' which is neither \"Block\" nor \"Entity\" - falls back to Block at runtime", id));
        }
        StationAsset.Hold.Mount.Entity entity = mount.getEntity();
        if (entity == null) {
            return;
        }
        if (!entitySurface) {
            out.add(Finding.warning(DOMAIN, "MOUNT_ENTITY_GROUP_IGNORED",
                    label + " authors Hold.Mount.Entity with Surface \"Block\" (or omitted) - the Entity"
                            + " group is only read when Surface is \"Entity\"", id));
        } else if (entity.effectiveSteerable()) {
            out.add(Finding.warning(DOMAIN, "MOUNT_STEERABLE_UNTESTED",
                    label + " authors Hold.Mount.Entity.Steerable true - reserved for a future"
                            + " vehicle-like station, not yet verified in-game", id));
        }
    }

    private static void checkCompletion(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                        @Nonnull List<Finding> out) {
        warnUnplayedPresentationLeaves(a.getCompletion(), label + ".Completion", id,
                "COMPLETION_UNPLAYED_LEAVES", out);
    }

    /**
     * Station-inline {@code Flairs} coverage (design section 9.6, leg F reshape - the old fixed
     * {@code Swing}/{@code Cycle}/{@code RareFind}/{@code Completion} leaf check is replaced by
     * an open {@code Moments} map walk). {@link #checkFlairMoments} is the shared core also used
     * by {@link #validateFlairAssets} for a standalone {@code FlairAsset}'s own {@code Moments}.
     */
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
            checkFlairMoments(flair.getMoments(), label + " Flairs['" + flairId + "']", id, out);
        }
    }

    /**
     * Shared {@code Moments} map coverage (design section 9.6, leg F): an empty/absent map can
     * never overlay anything ({@code EMPTY_FLAIR}), each authored Presentation still gets the
     * existing unplayed-leaves check, and an unrecognized moment id (typo'd against the 5
     * well-known ids / the {@code step:} prefix - {@link StationFlairs#isKnownMomentId}) warns
     * ONLY - per the design's own binding note, a future engine moment must never fail an older
     * pack's validation.
     */
    private static void checkFlairMoments(@Nullable Map<String, Presentation> moments, @Nonnull String label,
                                          @Nonnull String id, @Nonnull List<Finding> out) {
        if (moments == null || moments.isEmpty()) {
            out.add(Finding.warning(DOMAIN, "EMPTY_FLAIR",
                    label + " authors no Moments - it can never overlay anything", id));
            return;
        }
        for (Map.Entry<String, Presentation> entry : moments.entrySet()) {
            String momentId = entry.getKey();
            if (momentId == null || momentId.isBlank()) {
                out.add(Finding.warning(DOMAIN, "BLANK_FLAIR_MOMENT_ID",
                        label + " Moments has a blank moment id", id));
                continue;
            }
            if (!StationFlairs.isKnownMomentId(momentId)) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_FLAIR_MOMENT_ID",
                        label + " Moments['" + momentId + "'] is not a recognized moment id (cycle/swing/impact/"
                                + "rare_find/completion, or a step:<actionId>:<stepId> id) - check for a typo", id));
            }
            warnUnplayedPresentationLeaves(entry.getValue(), label + ".Moments['" + momentId + "']", id,
                    "FLAIR_UNPLAYED_LEAVES", out);
        }
    }

    /**
     * Standalone {@link FlairAsset} coverage (design section 9.6, leg F): the SAME
     * {@link #checkFlairMoments} core the station-inline path uses, plus a
     * {@code Stations}-references-an-unknown-station check ({@code stationKnown} is the caller's
     * predicate - the singleton {@link #validate()} entry backs it with the live
     * {@link StationCatalog}). Never blocks; every finding here is a warning.
     */
    @Nonnull
    public static List<Finding> validateFlairAssets(@Nonnull Collection<FlairAsset> flairAssets,
                                                     @Nonnull Predicate<String> stationKnown) {
        List<Finding> out = new ArrayList<>();
        for (FlairAsset fa : flairAssets) {
            if (fa == null) {
                continue;
            }
            String id = fa.getId() == null || fa.getId().isBlank() ? "(unnamed)" : fa.getId();
            String label = "FlairAsset '" + id + "'";
            String[] stations = fa.getStations();
            if (stations != null) {
                for (String stationId : stations) {
                    if (stationId != null && !stationId.isBlank() && !stationKnown.test(stationId.toLowerCase(Locale.ROOT))) {
                        out.add(Finding.warning(DOMAIN, "FLAIR_ASSET_UNKNOWN_STATION",
                                label + " Stations references unknown station '" + stationId + "'", id));
                    }
                }
            }
            checkFlairMoments(fa.getMoments(), label, id, out);
        }
        return out;
    }

    /**
     * Multi-action station coverage (design section 9.1, this leg): per-action override
     * structure - "warn on odd combos, never block" (every finding here is WARNING/INFO, never
     * ERROR, matching the design's binding note). A station with no {@code Actions} map is a
     * no-op call (nothing to iterate) - the implicit single-{@code "work"}-action path is
     * validated entirely by the existing station-level checks above it.
     */
    private static void checkActions(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
            @Nonnull Predicate<String> dropListKnown, @Nonnull Predicate<String> factorKnown,
            @Nonnull Predicate<String> lootableKnown, @Nonnull Predicate<String> rollPoolKnown,
            @Nonnull Predicate<String> modelKnown, @Nonnull List<Finding> out) {
        Map<String, ActionDef> actions = a.getActions();
        if (actions == null || actions.isEmpty()) {
            return;
        }
        boolean sawCatchAll = false;
        Set<String> seenItemIds = new HashSet<>();
        Set<String> seenResourceTypeIds = new HashSet<>();
        for (Map.Entry<String, ActionDef> entry : actions.entrySet()) {
            String actionId = entry.getKey() == null || entry.getKey().isBlank() ? "(unnamed)" : entry.getKey();
            ActionDef def = entry.getValue();
            String actionLabel = label + " Actions['" + actionId + "']";
            if (def == null) {
                out.add(Finding.warning(DOMAIN, "EMPTY_ACTION_ENTRY", actionLabel + " has no body", id));
                continue;
            }
            ActionInput input = def.getInput();
            boolean catchAll = input == null || input.isCatchAll();
            if (catchAll) {
                if (sawCatchAll) {
                    out.add(Finding.warning(DOMAIN, "UNREACHABLE_ACTION",
                            actionLabel + " authors no Input matcher (or an all-blank one) AFTER an earlier"
                                    + " catch-all action - selection resolves 'first match wins', so this"
                                    + " action can never be reached", id));
                }
                sawCatchAll = true;
            } else {
                // AMBIGUOUS_ACTION_INPUT (design 9.1): an exact ItemId/ResourceTypeId collision
                // with an EARLIER action - "first match wins" means this action's matching route
                // is unreachable via that exact id (a Tags/Function overlap is not flagged - too
                // fuzzy to call an authoring mistake outright, so this stays a targeted check).
                String itemId = input.getItemId();
                if (itemId != null && !itemId.isBlank() && !seenItemIds.add(itemId.toLowerCase(Locale.ROOT))) {
                    out.add(Finding.warning(DOMAIN, "AMBIGUOUS_ACTION_INPUT",
                            actionLabel + " Input.ItemId '" + itemId + "' repeats an earlier action's exact"
                                    + " ItemId - 'first match wins' makes this route unreachable via that id", id));
                }
                String resourceTypeId = input.getResourceTypeId();
                if (resourceTypeId != null && !resourceTypeId.isBlank()
                        && !seenResourceTypeIds.add(resourceTypeId.toLowerCase(Locale.ROOT))) {
                    out.add(Finding.warning(DOMAIN, "AMBIGUOUS_ACTION_INPUT",
                            actionLabel + " Input.ResourceTypeId '" + resourceTypeId + "' repeats an earlier"
                                    + " action's exact ResourceTypeId - 'first match wins' makes this route"
                                    + " unreachable via that id", id));
                }
            }
            String function = input != null ? input.getFunction() : null;
            if (function != null && !function.isBlank() && !isKnownFunction(function)) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_ACTION_FUNCTION",
                        actionLabel + " Input.Function '" + function
                                + "' is not one of Weapon/Armor/Tool", id));
            }
            boolean hasBody = def.getRecipe() != null || (def.getSteps() != null && def.getSteps().length > 0);
            if (!hasBody) {
                out.add(Finding.warning(DOMAIN, "ACTION_NO_BODY",
                        actionLabel + " authors neither Recipe (for the implicit convert-loop program) nor"
                                + " Steps - this action can never run a cycle", id));
            }
            if (def.getCustody() != null) {
                StationAsset.Recipe effectiveRecipe = def.getRecipe() != null ? def.getRecipe() : a.getRecipe();
                checkCustody(def.getCustody(), effectiveRecipe, actionLabel, id, out);
            }
            Puppet resolvedPuppet = def.getPuppet() != null ? def.getPuppet() : a.getPuppet();
            if (def.getPuppet() != null) {
                StationAsset.Hold effectiveHold = def.getHold() != null ? def.getHold() : a.getHold();
                checkPuppet(def.getPuppet(), effectiveHold, actionLabel, id, modelKnown, out);
            }
            StationStep[] steps = def.getSteps();
            if (steps != null && steps.length > 0) {
                boolean puppetActive = resolvedPuppet != null && resolvedPuppet.effectiveEnabled();
                checkSteps(steps, actionLabel, id, dropListKnown, factorKnown, lootableKnown, rollPoolKnown,
                        puppetActive, out);
            }
        }
    }

    private static boolean isKnownFunction(@Nonnull String function) {
        return "Weapon".equalsIgnoreCase(function) || "Armor".equalsIgnoreCase(function)
                || "Tool".equalsIgnoreCase(function);
    }

    /**
     * The authored step-program coverage (design 9.3/9.5): duplicate {@code Id}s, the one
     * schema-reserved-unimplemented type ({@code Mount}), an unimplemented
     * {@code Consume.From}/{@code Produce.To} route, a {@code Wait} step missing BOTH routes (or
     * authoring only the unimplemented {@code Beats} one), an {@code OnConditionFail.Goto}
     * referencing an unknown sibling step id, a {@code Roll} step's inline {@link Roll}s through
     * the SAME shared {@link #checkRoll} core every other Roll site uses, and (phase 2 leg E) a
     * {@code Stamp} step's own coverage - see the {@code Stamp}-specific checks below. Also (round-4
     * puppet design) a per-step {@code Puppet} override (unplayed-leaf coverage) - {@code
     * puppetActive} is the resolved action's OWN {@link Puppet#effectiveEnabled()}, computed once
     * by the caller ({@link #checkActions}) rather than re-derived per step.
     */
    private static void checkSteps(@Nonnull StationStep[] steps,
            @Nonnull String actionLabel, @Nonnull String id, @Nonnull Predicate<String> dropListKnown,
            @Nonnull Predicate<String> factorKnown, @Nonnull Predicate<String> lootableKnown,
            @Nonnull Predicate<String> rollPoolKnown, boolean puppetActive, @Nonnull List<Finding> out) {
        Set<String> seenIds = new HashSet<>();
        Set<String> knownIds = new HashSet<>();
        for (StationStep s : steps) {
            if (s != null && s.getId() != null && !s.getId().isBlank()) {
                knownIds.add(s.getId().toLowerCase(Locale.ROOT));
            }
        }
        for (int i = 0; i < steps.length; i++) {
            StationStep step = steps[i];
            String stepLabel = actionLabel + ".Steps[" + i + "]";
            if (step == null) {
                out.add(Finding.warning(DOMAIN, "EMPTY_STEP", stepLabel + " is empty", id));
                continue;
            }
            if (step.getId() == null || step.getId().isBlank()) {
                out.add(Finding.warning(DOMAIN, "MISSING_STEP_ID", stepLabel + " has no Id", id));
            } else if (!seenIds.add(step.getId().toLowerCase(Locale.ROOT))) {
                out.add(Finding.warning(DOMAIN, "DUPLICATE_STEP_ID",
                        stepLabel + " repeats Id '" + step.getId() + "'", id));
            }
            if (step.getType() == null || step.getType().isBlank()) {
                out.add(Finding.warning(DOMAIN, "MISSING_STEP_TYPE", stepLabel + " has no Type", id));
            } else if (step.isReservedUnimplemented()) {
                out.add(Finding.warning(DOMAIN, "UNIMPLEMENTED_STEP_TYPE",
                        stepLabel + " authors Type '" + step.getType()
                                + "' which is schema-reserved but has no handler yet", id));
            }
            checkConditionFactors(step.getConditions(), stepLabel + ".Conditions", id, factorKnown, out);
            StationStep.OnConditionFail onFail = step.getOnConditionFail();
            String gotoId = onFail != null ? onFail.getGoto() : null;
            if (gotoId != null && !gotoId.isBlank() && !knownIds.contains(gotoId.toLowerCase(Locale.ROOT))) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_GOTO_TARGET",
                        stepLabel + ".OnConditionFail.Goto references unknown step id '" + gotoId + "'", id));
            }
            StationStep.PuppetOverride puppetOverride = step.getPuppet();
            if (puppetOverride != null) {
                if (!puppetActive) {
                    out.add(Finding.warning(DOMAIN, "PUPPET_STEP_OVERRIDE_WITHOUT_PUPPET",
                            stepLabel + " authors a Puppet override (Clip/Prop) but the resolved action's"
                                    + " Puppet group is not active - this override never plays", id));
                }
                checkPuppetProp(puppetOverride.getProp(), stepLabel + ".Puppet", id, out);
            }
            if (StationStep.TYPE_CONSUME.equalsIgnoreCase(step.getType())) {
                StationStep.Consume consume = step.getConsume();
                if (consume == null) {
                    out.add(Finding.warning(DOMAIN, "CONSUME_STEP_EMPTY", stepLabel + " has no Consume group", id));
                } else if (!StationStep.Consume.FROM_INVENTORY.equalsIgnoreCase(consume.effectiveFrom())
                        && !StationStep.Consume.FROM_CUSTODY.equalsIgnoreCase(consume.effectiveFrom())) {
                    out.add(Finding.warning(DOMAIN, "UNIMPLEMENTED_CONSUME_SOURCE",
                            stepLabel + " authors From '" + consume.effectiveFrom()
                                    + "' which has no handler yet (only 'Inventory'/'Custody' are implemented)", id));
                }
            } else if (StationStep.TYPE_PRODUCE.equalsIgnoreCase(step.getType())) {
                StationStep.Produce produce = step.getProduce();
                if (produce == null) {
                    out.add(Finding.warning(DOMAIN, "PRODUCE_STEP_EMPTY", stepLabel + " has no Produce group", id));
                } else if (!StationStep.Produce.TO_INVENTORY.equalsIgnoreCase(produce.effectiveTo())) {
                    out.add(Finding.warning(DOMAIN, "UNIMPLEMENTED_PRODUCE_DEST",
                            stepLabel + " authors To '" + produce.effectiveTo()
                                    + "' which has no handler yet (only 'Inventory' is implemented)", id));
                }
            } else if (StationStep.TYPE_WAIT.equalsIgnoreCase(step.getType())) {
                StationStep.Wait wait = step.getWait();
                boolean hasDuration = wait != null && wait.getDurationMs() != null && wait.getDurationMs() > 0;
                boolean hasBeats = wait != null && wait.getBeats() != null && wait.getBeats() > 0;
                if (!hasDuration && hasBeats) {
                    out.add(Finding.warning(DOMAIN, "UNIMPLEMENTED_WAIT_BEATS",
                            stepLabel + " authors only Wait.Beats, which has no handler yet"
                                    + " (author DurationMs, or both, until Beats lands)", id));
                } else if (!hasDuration) {
                    out.add(Finding.warning(DOMAIN, "WAIT_MISSING_DURATION",
                            stepLabel + " has no positive Wait.DurationMs - the step can never proceed", id));
                }
            } else if (StationStep.TYPE_ROLL.equalsIgnoreCase(step.getType())) {
                StationStep.RollGroup group = step.getRoll();
                String lootableId = group != null ? group.getLootable() : null;
                if (lootableId != null && !lootableId.isBlank()
                        && !lootableKnown.test(lootableId.toLowerCase(Locale.ROOT))) {
                    out.add(Finding.warning(DOMAIN, "LOOT_UNKNOWN_TABLE",
                            stepLabel + ".Lootable references unknown lootable '" + lootableId + "'", id));
                }
                Roll[] inlineRolls = group != null ? group.getRolls() : null;
                if (inlineRolls != null) {
                    for (int r = 0; r < inlineRolls.length; r++) {
                        checkRoll(inlineRolls[r], stepLabel + ".Rolls[" + r + "]", id, dropListKnown, factorKnown, out);
                    }
                }
            } else if (StationStep.TYPE_STAMP.equalsIgnoreCase(step.getType())) {
                checkStamp(step.getStamp(), stepLabel, id, factorKnown, rollPoolKnown, out);
            }
        }
    }

    /**
     * A Stamp step's own coverage (design 9.5, phase 2 leg E): no {@code Stamp} group at all, no
     * {@code Reagents} (a free ritual - warn, not an error, some future station may genuinely want
     * that), a {@code Stats.Pool} reference to an unknown {@code RollPool}, a non-positive
     * {@code Caps.PerItemBudget} when authored, and a {@code SkillScaledBudget.Factor} referencing
     * an unregistered factor (the SAME {@code factorKnown} check every other factor reference in
     * this file uses - warn-not-error, "providers may register later").
     */
    private static void checkStamp(@Nullable StationStep.Stamp stamp, @Nonnull String stepLabel,
            @Nonnull String id, @Nonnull Predicate<String> factorKnown, @Nonnull Predicate<String> rollPoolKnown,
            @Nonnull List<Finding> out) {
        if (stamp == null) {
            out.add(Finding.warning(DOMAIN, "STAMP_STEP_EMPTY", stepLabel + " has no Stamp group", id));
            return;
        }
        if (stamp.getReagents() == null || stamp.getReagents().length == 0) {
            out.add(Finding.warning(DOMAIN, "STAMP_NO_REAGENTS", stepLabel + " authors no Reagents (a free ritual)", id));
        }
        StationStep.Stamp.Stats stats = stamp.getStats();
        if (stats == null && stamp.getDurability() == null) {
            out.add(Finding.warning(DOMAIN, "STAMP_NO_PAYLOAD",
                    stepLabel + " authors neither Stats nor Durability - this step grants nothing", id));
            return;
        }
        if (stats == null) {
            return;
        }
        String pool = stats.getPool();
        if (pool != null && !pool.isBlank() && !rollPoolKnown.test(pool.toLowerCase(Locale.ROOT))) {
            out.add(Finding.warning(DOMAIN, "STAMP_UNKNOWN_POOL",
                    stepLabel + " Stats.Pool references unknown RollPool '" + pool + "'", id));
        }
        if ((stats.getEntries() == null || stats.getEntries().length == 0) && (pool == null || pool.isBlank())) {
            out.add(Finding.warning(DOMAIN, "STAMP_STATS_NO_ENTRIES",
                    stepLabel + " authors Stats with neither Pool nor inline Entries", id));
        }
        StationStep.Stamp.Stats.Caps caps = stats.getCaps();
        if (caps != null) {
            Double perItemBudget = caps.getPerItemBudget();
            if (perItemBudget != null && perItemBudget <= 0.0) {
                out.add(Finding.warning(DOMAIN, "STAMP_NONPOSITIVE_BUDGET",
                        stepLabel + " Caps.PerItemBudget is not positive (" + perItemBudget + ")", id));
            }
            StationStep.Stamp.Stats.SkillScaledBudget scaled = caps.getSkillScaledBudget();
            String factor = scaled != null ? scaled.getFactor() : null;
            if (factor != null && !factor.isBlank() && !factorKnown.test(factor.toLowerCase(Locale.ROOT))) {
                out.add(Finding.warning(DOMAIN, "STAMP_UNKNOWN_FACTOR",
                        stepLabel + " Caps.SkillScaledBudget.Factor references unregistered factor '"
                                + factor + "'", id));
            }
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

    /**
     * Validate the live catalog (full set, incl. cross-layer reference checks) and log a summary
     * (+ per-finding detail). Never throws. Callers: {@code /rpgstations validate} (on-demand,
     * already post-load) and {@code RpgStationsPlugin}'s ONE deferred post-load audit (first
     * {@code PlayerReadyEvent}, D4 fix). Per-fold auto-logging uses {@link #runStructuralAndLog}
     * instead - see {@link #validateStructural}'s javadoc for why.
     */
    public static void runAndLog() {
        Report.logTo(DOMAIN, "Station validation", validate());
    }

    /**
     * Validate the live catalog (STRUCTURAL-only, D4 fix) and log a summary (+ per-finding
     * detail). Never throws. Safe to call from every per-fold {@code LoadedAssetsEvent} handler -
     * see {@link #validateStructural}'s javadoc.
     */
    public static void runStructuralAndLog() {
        Report.logTo(DOMAIN, "Station validation", validateStructural());
    }
}
