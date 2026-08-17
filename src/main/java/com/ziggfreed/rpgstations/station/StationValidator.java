package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.cosmetics.EmoteAsset;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.ziggfreed.common.codec.Rotation;
import com.ziggfreed.common.entity.PlayerModelService;
import com.ziggfreed.common.factor.FactorCondition;
import com.ziggfreed.common.factor.FactorFormula;
import com.ziggfreed.common.loot.LootGrants;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.LootableAsset;
import com.ziggfreed.common.loot.LootableConfig;
import com.ziggfreed.common.loot.LootableValidator;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.common.loot.reward.RewardSpec;
import com.ziggfreed.common.loot.stamp.RollPoolConfig;
import com.ziggfreed.common.loot.stamp.StampSpec;
import com.ziggfreed.common.loot.stamp.StatRollEntry;
import com.ziggfreed.common.validation.Finding;
import com.ziggfreed.common.validation.Severity;
import com.ziggfreed.common.validation.ValidationReport;
import com.ziggfreed.rpgstations.api.FindingSink;
import com.ziggfreed.rpgstations.api.ValidationHook;
import com.ziggfreed.rpgstations.api.ValidationScope;
import com.ziggfreed.rpgstations.api.impl.ContributionChannelRegistryImpl;
import com.ziggfreed.rpgstations.api.impl.FactorRegistryImpl;
import com.ziggfreed.rpgstations.api.impl.ValidationHookRegistryImpl;
import com.ziggfreed.rpgstations.asset.ActionAsset;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.ContributionScale;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.EffectRef;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.rpgstations.asset.FlairAsset;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.i18n.RpgStationsLangKeys;
import com.ziggfreed.rpgstations.loot.StationLootEngine;
import com.ziggfreed.rpgstations.loot.StationRewardKinds;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Read-only content diagnostic for station assets (design section 4.1), over the shared library's
 * {@code validation/} core and {@code util.Log}.
 *
 * <p><b>What it does NOT check, by construction.</b> A {@code Contribution}'s {@code Param}
 * semantics are the channel owner's business, and are validated by the owning mod - through a
 * registered {@code api.ValidationHook} ({@link #runHooks}) when the rule needs to see content, or
 * in that mod's own validator otherwise. Nothing in here branches on a foreign id. The lang-key
 * presence check runs against this mod's own {@link RpgStationsLangKeys}, and the gate check is a
 * factor-known check over this mod's own {@link Requires} and its shared
 * {@link FactorCondition} gate leaves.
 *
 * <p><b>Scope-2 rewrite (leg A4, design {@code raw/rpg-stations-scope2-unified-design-2026-07-23
 * .md} section 1.9, gate outcomes binding):</b> every check touching the reshaped
 * {@code StationStep} orthogonal-phase model, the unified {@link LootRef}/the weighted factor term/
 * {@link Ingredient} vocabulary, and the {@code StationStep.Stamp.Stats.Caps.Budgets[]} shape was
 * rewritten against the A-SCHEMA leg's rewritten codecs. New checks: {@code ACTION_REF_UNKNOWN},
 * {@code EXTENSION_TARGET_UNKNOWN}, {@code EXTENSION_PAYLOAD_MISMATCH},
 * {@code EXTENSION_KEY_COLLISION}, {@code EXTENSION_ANCHOR_MISSING},
 * {@code EXTENSION_STEP_MISSING_ID}, {@code ANCHOR_STATION_UNKNOWN},
 * {@code WALK_TARGET_UNKNOWN_ANCHOR}, {@code STEP_AT_UNKNOWN_ANCHOR}, {@code WALK_REQUIRES_PUPPET},
 * {@code LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT}, and {@code LOOT_DUPLICATE_FACTOR} (INFO). The
 * multi-station seam ({@code Walk}/{@code At}/
 * {@code Produce.To:Custody}) EXECUTES as of scope-2 wave 3, so the temporary {@code WAVE3_PENDING}
 * warn was removed - these anchor/walk checks are the live discovery-time coverage. Dropped (their reserved fields no longer
 * exist): {@code UNIMPLEMENTED_STEP_TYPE}, {@code UNIMPLEMENTED_CONSUME_SOURCE},
 * {@code UNIMPLEMENTED_PRODUCE_DEST}, {@code WAIT_BOTH_ROUTES}, {@code UNIMPLEMENTED_WAIT_BEATS}
 * (the {@code Type} union, the {@code Wait} type, and the reserved {@code Mount} type are gone).
 *
 * <p><b>Standalone asset types</b> ({@link ActionAsset}, {@link ExtensionAsset}):
 * {@link #validateActionAssets} and {@link #validateExtensions} are pure, singleton-free, fully
 * unit-tested collection validators, and (review minor: validator-standalone-action-unwired) they
 * are now WIRED into the live {@link #validate()} full post-load pass against {@code ActionCatalog}/
 * {@code ExtensionCatalog}, so the flagship standalone {@code prepfish} action Ref'd from
 * {@code CuttingBoard} actually gets its anchor/walk checks at load. They are deliberately NOT run
 * from {@link #validateStructural()} (the per-fold pass defers every cross-layer reference check; a
 * {@code Target:{Station}} extension validated before its target station's layer folds would
 * false-flag {@code EXTENSION_TARGET_UNKNOWN}). The {@code actionAssetKnown} predicate the main
 * per-station walk needs for {@code ACTION_REF_UNKNOWN} is now live-wired in {@link #validate()}
 * too (against {@code ActionCatalog}), alongside {@code stationKnown} for
 * {@code ANCHOR_STATION_UNKNOWN} (against {@link StationCatalog}).
 *
 * <p><b>AV wave:</b> {@code ANCHOR_STATION_NOT_DISCOVERABLE} (warn-only) rides beside
 * {@code ANCHOR_STATION_UNKNOWN} - a declared anchor naming a station that EXISTS but that no block
 * item resolves to (nothing's {@code BlockType.Interactions.Use} runs an {@code rpg_station_use}
 * naming it) can never be found by anchor discovery until a player interacts with such a block. It
 * reads the derived discovery index and fails OPEN on an unseeded one ({@link
 * #stationDiscoverableLive}).
 *
 * <p><b>Schema/DX review round (2026-08-05):</b> {@code EXTENSION_CONTRIBUTION_DUPLICATE} closes
 * the one real gap the review surfaced - {@link ExtensionAsset#getPerCycleContributions()} is
 * append-only ({@code ExtensionCatalog#mergeContributions} appends every extension's entries onto
 * the base array, never overrides by channel), so nothing previously stopped an extension from
 * re-declaring a {@code (Channel, Param)} pair its target already declares and silently SUMMING
 * the effective per-cycle amount (before this check, only a hand-written {@code $Comment} in a
 * pack's own extension JSON warned against it). Both halves are warn-only, never block a load: an
 * extension's own entries colliding with its target's BASE {@code Work.PerCycleContributions}
 * ({@link #resolveBaseContributionKeys}), and two or more extensions declaring the same pair on
 * the same target (both apply and stack, unlike the keyed {@code Actions}/{@code Anchors}
 * collision checks above where the base or the higher-apply-order entry wins - see
 * {@link #reportContributionDuplicates}'s javadoc for why that helper is deliberately separate
 * from {@link #reportCrossExtensionCollisions}).
 *
 * <p><b>Third-party checks</b> ({@link #runHooks}): every registered {@code api.ValidationHook}
 * runs inside the FULL pass over one shared {@code api.ValidationScope}, each inside its own
 * try/catch, its findings folded into the same aggregate report. That is where a rule that needs
 * to know what a specific factor or channel MEANS lives - with the mod that owns the vocabulary.
 *
 * <p>Pure and side-effect-free (apart from {@link #runAndLog} and {@link #runHooks}); never throws.
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
     * placeholder). {@code actionAssetKnown} is wired fail-open ({@code id -> true}) until
     * {@code ActionCatalog} lands (leg A3) - see this class's own header javadoc.
     */
    @Nonnull
    public static List<Finding> validate() {
        try {
            Collection<StationAsset> stations = StationCatalog.getInstance().all().values();
            Collection<ActionAsset> actionAssets = ActionCatalog.getInstance().all().values();
            Collection<ExtensionAsset> extensions = ExtensionCatalog.getInstance().all().values();
            Predicate<String> factorKnown = FactorRegistryImpl.getInstance()::isKnown;
            Predicate<String> lootableKnown = id -> LootableConfig.getInstance().resolve(id) != null;
            Predicate<String> rollPoolKnown = id -> RollPoolConfig.getInstance().resolve(id) != null;
            Predicate<String> stationKnown = id -> StationCatalog.getInstance().getStation(id) != null;
            Predicate<String> actionAssetKnown = id -> ActionCatalog.getInstance().get(id) != null;
            // The drop-list predicate doubles as the reference COLLECTOR for the runtime-resolution
            // probe below: every id the walk resolves is exactly a referenced table, so the probe
            // needs no second traversal of the asset shapes (and picks up any FUTURE reference site
            // for free). Only ids that EXIST are recorded - a missing one already has its own
            // finding, and rolling it would report the same table twice.
            Set<String> referencedDropLists = new LinkedHashSet<>();
            Predicate<String> dropListKnown = dropListId -> {
                boolean known = dropListKnownLive(dropListId);
                if (known) {
                    referencedDropLists.add(dropListId);
                }
                return known;
            };

            List<Finding> out = new ArrayList<>(validate(stations,
                    StationValidator::langKeyKnownLive,
                    dropListKnown,
                    factorKnown,
                    lootableKnown,
                    rollPoolKnown,
                    StationValidator::modelKnownLive,
                    stationKnown,
                    actionAssetKnown));
            out.addAll(validateLootables(LootableConfig.getInstance().all().values(),
                    dropListKnown, factorKnown));
            out.addAll(validateFlairAssets(FlairCatalog.getInstance().all().values(), stationKnown));
            // Review minor (validator-standalone-action-unwired): the flagship standalone prepfish
            // ActionAsset (Ref'd from CuttingBoard) and every ExtensionAsset are validated HERE, in
            // the FULL post-load pass, now that ActionCatalog/ExtensionCatalog exist. Deliberately NOT
            // added to validateStructural(): that per-fold pass defers every cross-layer reference
            // check, and a Target:{Station} extension validated before its target station's layer
            // folds would false-flag EXTENSION_TARGET_UNKNOWN.
            out.addAll(validateActionAssets(actionAssets,
                    dropListKnown, factorKnown, lootableKnown, rollPoolKnown,
                    StationValidator::modelKnownLive, stationKnown));
            out.addAll(validateExtensions(extensions, stations, actionAssets,
                    dropListKnown, factorKnown, lootableKnown, rollPoolKnown));
            out.addAll(checkCustodyInputsResolveLive(stations, actionAssets));
            // AFTER the walk above, which is what filled referencedDropLists.
            out.addAll(checkDropListsResolveLive(referencedDropLists));
            // Third-party checks run LAST, over the same folded content the engine just walked, so
            // a hook's note sits beside the engine's own in one report. FULL pass only.
            out.addAll(runHooks(stations, actionAssets, LootableConfig.getInstance().all().values(), extensions));
            return out;
        } catch (Throwable t) {
            Log.warn("Station validation aborted: " + t.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * The STRUCTURAL-only pass (D4 fix - timing, not checks): every per-station/per-action check
     * EXCEPT the cross-layer reference-existence ones (lang key / native {@code ItemDropList} id /
     * this mod's own {@code Lootable}/{@code RollPool}/station-id/action-asset-id references).
     * Those depend on OTHER asset stores or the merged i18n lang map that may not have finished
     * folding for a LATER pack layer yet at the moment THIS layer's Station/Flair fold callback
     * fires. Safe to run at EVERY per-fold event (never a false positive from an incomplete later
     * layer); {@link #validate()} (still the full set) now only runs from
     * {@code /rpgstations validate} (already post-load) and the ONE deferred post-load audit
     * ({@code RpgStationsPlugin}'s first-{@code PlayerReadyEvent} hook - late enough that every
     * pack layer has settled).
     */
    @Nonnull
    public static List<Finding> validateStructural() {
        try {
            List<Finding> out = new ArrayList<>(validate(StationCatalog.getInstance().all().values(),
                    ALWAYS_KNOWN, ALWAYS_KNOWN, FactorRegistryImpl.getInstance()::isKnown, ALWAYS_KNOWN, ALWAYS_KNOWN,
                    ALWAYS_KNOWN, ALWAYS_KNOWN, ALWAYS_KNOWN));
            out.addAll(validateLootables(LootableConfig.getInstance().all().values(),
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

    /**
     * Runs every registered {@code api.ValidationHook} over ONE shared {@code api.ValidationScope}
     * built from the folded content, folding each hook's findings into the aggregate report.
     *
     * <p>This is the seam that lets a rule which depends on what a specific factor or channel
     * MEANS live with the mod that owns that vocabulary, instead of as a branch in here naming a
     * foreign id. Guard discipline, all three layers deliberate:
     * <ul>
     *   <li>building the scope is try-guarded as a whole - a malformed asset costs the hooks, never
     *   the engine's own findings, which are already in {@code out} by the time this runs;
     *   <li>each hook runs inside its OWN try/catch, so a throwing third-party hook costs only its
     *   own findings and every later hook still runs;
     *   <li>a hook can only report {@code info}/{@code warn} - the never-block posture is absolute,
     *   and there is deliberately no {@code error} on the sink.
     * </ul>
     * With no hook registered this is a cheap no-op: the scope is not even built.
     */
    @Nonnull
    private static List<Finding> runHooks(@Nonnull Collection<StationAsset> stations,
            @Nonnull Collection<ActionAsset> actionAssets, @Nonnull Collection<LootableAsset> lootables,
            @Nonnull Collection<ExtensionAsset> extensions) {
        List<ValidationHook> hooks = ValidationHookRegistryImpl.getInstance().hooks();
        if (hooks.isEmpty()) {
            return List.of();
        }
        ValidationScope scope;
        try {
            scope = StationValidationScope.build(stations, actionAssets, lootables, extensions);
        } catch (Throwable t) {
            Log.warn("Station validation hooks skipped - scope build failed: " + t.getMessage());
            return List.of();
        }
        List<Finding> out = new ArrayList<>();
        for (ValidationHook hook : hooks) {
            if (hook == null) {
                continue;
            }
            try {
                hook.validate(scope, new HookFindingSink(out, hook));
            } catch (Throwable t) {
                Log.warn("Station validation hook " + hook.getClass().getName() + " threw and was skipped: "
                        + t.getMessage());
            }
        }
        return out;
    }

    /**
     * The {@code api.FindingSink} adapter: appends a hook's advisory findings to the pass's own
     * list. The reporting mod owns its {@code code} vocabulary, so the code is recorded verbatim
     * and the finding's DOMAIN names the hook's class instead of {@code "station"}, so anything
     * grouping findings by domain can tell which mod is talking. Blank codes/messages are dropped
     * rather than logged as empty lines.
     */
    private record HookFindingSink(@Nonnull List<Finding> out, @Nonnull ValidationHook hook) implements FindingSink {

        @Override
        public void info(@Nonnull String code, @Nonnull String message, @Nullable String subjectId) {
            add(Severity.INFO, code, message, subjectId);
        }

        @Override
        public void warn(@Nonnull String code, @Nonnull String message, @Nullable String subjectId) {
            add(Severity.WARNING, code, message, subjectId);
        }

        private void add(@Nonnull Severity severity, @Nonnull String code, @Nonnull String message,
                @Nullable String subjectId) {
            if (code.isBlank() || message.isBlank()) {
                return;
            }
            out.add(new Finding(severity, code, message, subjectId == null ? "" : subjectId, hookDomain()));
        }

        @Nonnull
        private String hookDomain() {
            String name = hook.getClass().getSimpleName();
            return name.isBlank() ? DOMAIN : DOMAIN + ":" + name;
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
     * Live {@code ModelAsset} existence check (round-4 puppet design's {@code Puppet.Look.ModelId}
     * reference), over {@code ziggfreed-common}'s {@code entity.PlayerModelService#modelExists}
     * (the same live asset-map lookup {@code Hide.Route: "ModelSwap"}'s predecessor design used -
     * never throws, fails OPEN here to match {@link #dropListKnownLive}'s stance).
     */
    private static boolean modelKnownLive(@Nonnull String modelId) {
        try {
            return PlayerModelService.modelExists(modelId);
        } catch (Throwable t) {
            return true; // a lookup failure is not evidence the id is wrong - don't flag it
        }
    }

    /**
     * Live native-asset existence checks (seam wave, decision 53's typo-detection ride): every id
     * checked below resolves against a Hytale-NATIVE, engine-boot-populated asset map (vanilla or
     * ANY loaded pack's own Sound/Particle/Emote/EntityEffect/RootInteraction content, or a
     * registered NPC Role) - unlike this mod's own asset types (lang/lootable/rollpool/station/
     * actionAsset), these never depend on THIS mod's own pack-fold order, so unlike
     * {@link #dropListKnownLive}/{@link #modelKnownLive} (kept parameterized/deferred purely for
     * uniformity with the two-pass split) these are called DIRECTLY, unconditionally, from both the
     * structural per-fold pass and the full pass - a documented, deliberate scoped deviation from
     * this class's general predicate-threading pattern, since there is no "not loaded yet" window to
     * defer for a native asset map. All fail OPEN on a lookup error, matching every other live check
     * in this file.
     */
    private static boolean soundKnownLive(@Nonnull String soundId) {
        try {
            return SoundEvent.getAssetMap().getAsset(soundId) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean particleKnownLive(@Nonnull String particleId) {
        try {
            return ParticleSystem.getAssetMap().getAsset(particleId) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean emoteKnownLive(@Nonnull String emoteId) {
        try {
            return EmoteAsset.getAssetMap().getAsset(emoteId) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Fails OPEN on an EMPTY store as well as on a lookup error, matching {@code benchIdKnownLive}'s
     * own "an index that has not been seeded yet is not evidence the id is wrong" stance. Station
     * validation can run before the native asset registry has finished loading, and an
     * unpopulated {@code EntityEffect} store would otherwise flag every effect reference in the
     * mod - including this jar's own shipped {@code RPG_Station_Hold}, which resolves perfectly
     * well by the time a session actually applies it.
     */
    private static boolean entityEffectKnownLive(@Nonnull String effectId) {
        try {
            if (EntityEffect.getAssetMap().getAssetCount() == 0) {
                return true;
            }
            return EntityEffect.getAssetMap().getAsset(effectId) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean interactionKnownLive(@Nonnull String interactionId) {
        try {
            return RootInteraction.getAssetMap().getAsset(interactionId) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Live "does any placed-able BLOCK map to this station" check (AV wave): reads the derived
     * anchor-discovery index ({@code StationService#seedStationBlockIndexFromAssets}, the
     * {@code blockItemId -> stationId} seed walked out of the native RootInteraction/BlockType
     * assets). Called DIRECTLY from both passes like the other native-asset checks above; fails OPEN
     * on an EMPTY index exactly as {@link #benchIdKnownLive} does - an index that has not been seeded
     * yet (a per-fold structural pass before the native layers settled, or a cold unit JVM) must
     * never manufacture a false "undiscoverable" finding.
     */
    private static boolean stationDiscoverableLive(@Nonnull String stationId) {
        try {
            Set<String> discoverable = StationService.getInstance().discoverableStationIds();
            return discoverable.isEmpty() || discoverable.contains(stationId.toLowerCase(Locale.ROOT));
        } catch (Throwable t) {
            return true;
        }
    }

    /** {@code NpcRole} full-pass role-id check (R1 handoff, decision 47/48): mirrors the spike's own {@code NPCPlugin.get().hasRoleName(...)} existence gate. */
    private static boolean npcRoleKnownLive(@Nonnull String roleId) {
        try {
            NPCPlugin npc = NPCPlugin.get();
            return npc != null && npc.hasRoleName(roleId);
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Live native {@code BenchRequirement.Id} scan (decision 51c/53's "open-string caveat" - a bench
     * id is a bare, unregistered {@code Codec.STRING} matched by equality, never a real asset-map
     * entry, so this is a best-effort cross-check against every live craftable {@link Item}'s own
     * recipe {@link BenchRequirement}s rather than a true registry lookup). Fails OPEN on any lookup
     * error, and (unlike the other native-ref checks above) on an EMPTY/unreadable asset map too -
     * a cold/unit-JVM Item map must never manufacture a false "unknown bench" finding.
     */
    private static boolean benchIdKnownLive(@Nonnull String benchId) {
        try {
            var assetMap = Item.getAssetMap();
            if (assetMap == null) {
                return true;
            }
            for (Item item : assetMap.getAssetMap().values()) {
                if (item == null || !item.hasRecipesToGenerate()) {
                    continue;
                }
                List<CraftingRecipe> recipes = new ArrayList<>(1);
                item.collectRecipesToGenerate(recipes);
                for (CraftingRecipe recipe : recipes) {
                    if (recipe == null) {
                        continue;
                    }
                    BenchRequirement[] benches = recipe.getBenchRequirement();
                    if (benches == null) {
                        continue;
                    }
                    for (BenchRequirement bench : benches) {
                        if (bench != null && bench.id != null && benchId.equalsIgnoreCase(bench.id)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            return true;
        }
        return false;
    }

    /**
     * Shared native-composition advisories for one authored {@link Presentation} (decision 51b/51d,
     * ridden by decision 53's typo-detection mandate): {@code Sound}/{@code Particles} are INFO-only
     * (a legitimately open, frequently-partial cue vocabulary - an unresolved id simply plays
     * nothing, never a functional break) via {@link #soundKnownLive}/{@link #particleKnownLive};
     * {@code Interaction.Id} is the STANDARD warn severity every other cross-asset reference in this
     * class uses via {@link #interactionKnownLive}; {@link #getEffect()}'s ref runs through the
     * shared {@link #checkEffectRef}.
     */
    private static void checkNativeRefs(@Nullable Presentation p, @Nonnull String label, @Nonnull String id,
                                        @Nonnull List<Finding> out) {
        if (p == null) {
            return;
        }
        Presentation.SoundCue[] sounds = p.getSounds();
        if (sounds != null) {
            for (Presentation.SoundCue cue : sounds) {
                if (cue == null) {
                    continue;
                }
                if (!cue.hasEventId()) {
                    out.add(Finding.warning(DOMAIN, "PRESENTATION_SOUND_MISSING_EVENT_ID",
                            label + " Sounds has an entry with no EventId - it is skipped at play time", id));
                    continue;
                }
                if (!soundKnownLive(cue.getEventId())) {
                    out.add(Finding.info(DOMAIN, "PRESENTATION_UNKNOWN_SOUND",
                            label + " Sounds entry '" + cue.getEventId()
                                    + "' is not a known SoundEvent id - check for a typo", id));
                }
            }
        }
        Presentation.ModelParticle[] particles = p.getParticles();
        if (particles != null) {
            for (Presentation.ModelParticle burst : particles) {
                if (burst == null) {
                    continue;
                }
                if (!burst.hasSystemId()) {
                    out.add(Finding.warning(DOMAIN, "PRESENTATION_PARTICLE_MISSING_SYSTEM_ID",
                            label + " Particles has an entry with no SystemId - the burst is skipped at play time", id));
                    continue;
                }
                if (!particleKnownLive(burst.getSystemId())) {
                    out.add(Finding.info(DOMAIN, "PRESENTATION_UNKNOWN_PARTICLES",
                            label + " Particles SystemId '" + burst.getSystemId()
                                    + "' is not a known ParticleSystem id - check for a typo", id));
                }
            }
        }
        Presentation.Interaction interaction = p.getInteraction();
        if (interaction != null && interaction.hasId() && !interactionKnownLive(interaction.getId())) {
            out.add(Finding.warning(DOMAIN, "PRESENTATION_UNKNOWN_INTERACTION",
                    label + " Interaction.Id '" + interaction.getId() + "' references an unknown RootInteraction", id));
        }
        checkEffectRef(p.getEffect(), label + ".Effect", id, out);
    }

    /** The shared {@link EffectRef} existence check (decision 51d), standard warn severity. */
    private static void checkEffectRef(@Nullable EffectRef effect, @Nonnull String label, @Nonnull String id,
                                       @Nonnull List<Finding> out) {
        if (effect == null || !effect.hasId()) {
            return;
        }
        if (!entityEffectKnownLive(effect.getId())) {
            out.add(Finding.warning(DOMAIN, "UNKNOWN_ENTITY_EFFECT",
                    label + " references unknown EntityEffect '" + effect.getId() + "'", id));
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
     * Singleton-free core (4-arg convenience: {@code lootableKnown}/{@code rollPoolKnown}/
     * {@code modelKnown}/{@code stationKnown}/{@code actionAssetKnown} all default to
     * always-known, for a caller that does not care about those reference checks - e.g. every
     * pre-scope-2 test fixture). {@code langKeyKnown} answers "does this rpgstations lang key
     * exist"; {@code dropListKnown} answers "does this native ItemDropList asset id exist";
     * {@code factorKnown} answers "is this factor id registered" (warn-not-error either way -
     * "providers may register later").
     */
    @Nonnull
    public static List<Finding> validate(@Nonnull Collection<StationAsset> stations,
                                         @Nonnull Predicate<String> langKeyKnown,
                                         @Nonnull Predicate<String> dropListKnown,
                                         @Nonnull Predicate<String> factorKnown) {
        return validate(stations, langKeyKnown, dropListKnown, factorKnown, ALWAYS_KNOWN, ALWAYS_KNOWN);
    }

    /**
     * Singleton-free core, 6-arg convenience - {@code lootableKnown} answers "does this
     * LootableAsset id exist"; {@code rollPoolKnown} (phase 2 leg E) answers "does this RollPool
     * id exist"; {@code modelKnown}/{@code stationKnown}/{@code actionAssetKnown} default to
     * always-known for a caller that does not care about those reference checks.
     */
    @Nonnull
    public static List<Finding> validate(@Nonnull Collection<StationAsset> stations,
                                         @Nonnull Predicate<String> langKeyKnown,
                                         @Nonnull Predicate<String> dropListKnown,
                                         @Nonnull Predicate<String> factorKnown,
                                         @Nonnull Predicate<String> lootableKnown,
                                         @Nonnull Predicate<String> rollPoolKnown) {
        return validate(stations, langKeyKnown, dropListKnown, factorKnown, lootableKnown, rollPoolKnown,
                ALWAYS_KNOWN);
    }

    /**
     * Singleton-free core, 7-arg convenience - {@code modelKnown} (round-4 puppet-presentation
     * design) answers "does this ModelAsset id exist"; {@code stationKnown}/
     * {@code actionAssetKnown} (scope-2) default to always-known.
     */
    @Nonnull
    public static List<Finding> validate(@Nonnull Collection<StationAsset> stations,
                                         @Nonnull Predicate<String> langKeyKnown,
                                         @Nonnull Predicate<String> dropListKnown,
                                         @Nonnull Predicate<String> factorKnown,
                                         @Nonnull Predicate<String> lootableKnown,
                                         @Nonnull Predicate<String> rollPoolKnown,
                                         @Nonnull Predicate<String> modelKnown) {
        return validate(stations, langKeyKnown, dropListKnown, factorKnown, lootableKnown, rollPoolKnown,
                modelKnown, ALWAYS_KNOWN, ALWAYS_KNOWN);
    }

    /**
     * Singleton-free core, FULL form (scope-2, design 1.9/2.2) - {@code stationKnown} answers
     * "does this station id exist" (used by {@code ANCHOR_STATION_UNKNOWN}); {@code actionAssetKnown}
     * answers "does this standalone {@link ActionAsset} id exist" (used by
     * {@code ACTION_REF_UNKNOWN}).
     */
    @Nonnull
    public static List<Finding> validate(@Nonnull Collection<StationAsset> stations,
                                         @Nonnull Predicate<String> langKeyKnown,
                                         @Nonnull Predicate<String> dropListKnown,
                                         @Nonnull Predicate<String> factorKnown,
                                         @Nonnull Predicate<String> lootableKnown,
                                         @Nonnull Predicate<String> rollPoolKnown,
                                         @Nonnull Predicate<String> modelKnown,
                                         @Nonnull Predicate<String> stationKnown,
                                         @Nonnull Predicate<String> actionAssetKnown) {
        List<Finding> out = new ArrayList<>();
        for (StationAsset a : stations) {
            if (a == null) {
                continue;
            }
            String id = a.getId() == null || a.getId().isBlank() ? "(unnamed)" : a.getId();
            String label = "Station '" + id + "'";

            checkIdentity(a, id, label, langKeyKnown, out);
            checkRequiresGroup(a.getRequires(), id, label + " Requires", factorKnown, out);
            checkFlairs(a, id, label, out);
            checkActions(a, id, label, dropListKnown, factorKnown, lootableKnown, rollPoolKnown, modelKnown,
                    stationKnown, actionAssetKnown, out);
        }
        return out;
    }

    /**
     * Standalone {@link ActionAsset} coverage (scope-2 design 1.5): the SAME per-action body
     * checks {@link #checkActions} runs on an inline {@code Actions} map entry, applied to a
     * standalone action's own {@link ActionAsset#getBody()} - no station-level Puppet/Hold/Recipe
     * fallback exists for a standalone action (it IS the base), so the resolved groups are its own
     * only. Wired into the live {@link #validate()} full pass (over {@code ActionCatalog}); NOT
     * into {@link #validateStructural()} - see this class's header javadoc.
     */
    @Nonnull
    public static List<Finding> validateActionAssets(@Nonnull Collection<ActionAsset> actionAssets,
                                                      @Nonnull Predicate<String> dropListKnown,
                                                      @Nonnull Predicate<String> factorKnown,
                                                      @Nonnull Predicate<String> lootableKnown,
                                                      @Nonnull Predicate<String> rollPoolKnown,
                                                      @Nonnull Predicate<String> modelKnown,
                                                      @Nonnull Predicate<String> stationKnown) {
        List<Finding> out = new ArrayList<>();
        for (ActionAsset asset : actionAssets) {
            if (asset == null) {
                continue;
            }
            String id = asset.getId() == null || asset.getId().isBlank() ? "(unnamed)" : asset.getId();
            String label = "ActionAsset '" + id + "'";
            checkActionBody(asset.getBody(), label, id,
                    dropListKnown, factorKnown, lootableKnown, rollPoolKnown, modelKnown, stationKnown, out);
        }
        return out;
    }

    /**
     * {@link ExtensionAsset} coverage (scope-2 design 1.8/1.9): {@code EXTENSION_TARGET_UNKNOWN}
     * (an ambiguous {@code Target}, or a target id resolving against none of {@code stations}/
     * the standalone-plus-inline action ids/{@code lootableKnown}/{@code rollPoolKnown}), {@code
     * EXTENSION_PAYLOAD_MISMATCH} (a payload group the resolved target type cannot carry, via the
     * pure {@link ExtensionAsset#payloadAllowedFor}), {@code EXTENSION_KEY_COLLISION} (a NEW
     * {@code Actions}/{@code Anchors} key colliding with the BASE target's own key - base always
     * wins - OR with another extension's same-target same-key claim - {@link
     * ExtensionAsset#APPLY_ORDER} decides, the later-applying entry wins), {@code
     * EXTENSION_ANCHOR_MISSING} (a {@code Steps} insertion with no unambiguous placement leaf -
     * degrades to {@code AtEnd}), and {@code EXTENSION_STEP_MISSING_ID} (an inserted step with no
     * {@code Id}, so a LATER extension can never anchor on it). Every inline {@code Loot}/
     * {@code Rolls}/{@code Entries} payload is ALSO run through the shared {@link #checkRoll}/
     * {@link #checkFactorTerms} cores, same as everywhere else those vocabularies appear.
     *
     * <p><b>Action targets resolve exactly the way the runtime resolves them</b>
     * ({@link #actionBodiesByTargetId}): a standalone {@link ActionAsset} id, OR a station's own
     * INLINE action id. Both are legal Action targets - with no standalone action assets installed,
     * the inline id is the only shape a pack can target at all - so every base-resolution check
     * below ({@code EXTENSION_KEY_COLLISION} against base anchors,
     * {@code EXTENSION_CONTRIBUTION_DUPLICATE} against base contributions,
     * {@code EXTENSION_ANCHOR_MISSING} against the base step program) reads that ONE union rather
     * than the standalone collection alone.
     *
     * <p><b>The SCOPED Action target</b> ({@code Target:{Station, Action}}) resolves as "that
     * station exists AND resolves an action answering to that id"
     * ({@link #stationResolvesActionTarget}, the runtime's own {@code actionTargetId} rule), and its
     * base body comes from THAT station first ({@link #resolveTargetActionBody}) - the id-keyed
     * union cannot distinguish two stations' same-named inline actions, which is precisely the case
     * the scoped form is authored for. Its cross-extension claims are bucketed by target key alone
     * and partitioned by SCOPE at report time ({@link #claimKey} + {@link #overlapGroups}), so two
     * extensions claiming one key on the same action id but on DIFFERENT stations are still not
     * reported as colliding, while a BARE claim and a scoped one on that key - which genuinely do
     * both apply on the scoped station - now are.
     *
     * <p><b>Documented limitation</b>: {@code EXTENSION_ANCHOR_MISSING} for a dangling
     * {@code After}/{@code Before} step id is checked only when the target action resolves from the
     * passed-in collections and authors a step program (an ambiguous/missing placement leaf is
     * ALWAYS checked regardless). Wired into the live {@link #validate()} full pass (over
     * {@code ExtensionCatalog} + the folded station/action collections); NOT into
     * {@link #validateStructural()} - see this class's header javadoc.
     */
    @Nonnull
    public static List<Finding> validateExtensions(@Nonnull Collection<ExtensionAsset> extensions,
                                                    @Nonnull Collection<StationAsset> stations,
                                                    @Nonnull Collection<ActionAsset> actionAssets,
                                                    @Nonnull Predicate<String> dropListKnown,
                                                    @Nonnull Predicate<String> factorKnown,
                                                    @Nonnull Predicate<String> lootableKnown,
                                                    @Nonnull Predicate<String> rollPoolKnown) {
        List<Finding> out = new ArrayList<>();
        Map<String, StationAsset> stationsById = new HashMap<>();
        for (StationAsset s : stations) {
            if (s != null && s.getId() != null && !s.getId().isBlank()) {
                stationsById.put(s.getId().toLowerCase(Locale.ROOT), s);
            }
        }
        Map<String, ActionAsset> actionAssetsById = new HashMap<>();
        for (ActionAsset act : actionAssets) {
            if (act != null && act.getId() != null && !act.getId().isBlank()) {
                actionAssetsById.put(act.getId().toLowerCase(Locale.ROOT), act);
            }
        }
        Map<String, ActionDef> actionBodiesById = actionBodiesByTargetId(stations, actionAssetsById);

        // Cross-extension key-collision tracking, in APPLY_ORDER (last claimant wins). Each bucket
        // is keyed on the target IDENTITY plus the claimed key with NO station scope in it; the
        // scope rides each Claim and overlapGroups decides which claimants actually meet.
        Map<String, List<Claim>> actionKeyClaims = new LinkedHashMap<>();
        Map<String, List<Claim>> anchorKeyClaims = new LinkedHashMap<>();
        // Cross-extension (Channel, Param) tracking (P8 ruling 75's added check): unlike
        // Actions/Anchors above, PerCycleContributions is an UNKEYED array
        // ExtensionCatalog#mergeContributions APPENDS, so two claimants of the same
        // (target, channel, param) both apply and their amounts genuinely sum - see
        // reportContributionDuplicates for the deliberately different wording.
        Map<String, List<Claim>> channelParamClaims = new LinkedHashMap<>();

        for (ExtensionAsset ext : ExtensionAsset.sortedForApply(extensions)) {
            if (ext == null) {
                continue;
            }
            String extId = ext.getId() == null || ext.getId().isBlank() ? "(unnamed)" : ext.getId();
            String label = "Extension '" + extId + "'";

            ExtensionAsset.Target target = ext.getTarget();
            if (target == null || !target.hasLegalTarget()) {
                out.add(Finding.error(DOMAIN, "EXTENSION_TARGET_UNKNOWN",
                        label + " authors no Target, or a combination that names no single target - author"
                                + " ONE of Station|Action|Lootable|RollPool, or the Station plus Action pair"
                                + " that scopes an action extension to a single station", extId));
                continue;
            }
            String targetType = target.resolvedType();
            String targetId = target.resolvedId();
            // The station a SCOPED Target:{Station, Action} narrows to; null for every other shape.
            String scopedStation = target.scopedStation();
            // The targeted action's base body, resolved ONCE (scope-aware) for the three
            // base-resolution checks below.
            ActionDef targetBody = resolveTargetActionBody(target, stationsById, actionBodiesById);
            boolean targetKnown = switch (targetType) {
                case ExtensionAsset.Target.STATION ->
                        targetId != null && stationsById.containsKey(targetId.toLowerCase(Locale.ROOT));
                case ExtensionAsset.Target.ACTION -> scopedStation != null
                        ? stationResolvesActionTarget(stationsById.get(scopedStation.toLowerCase(Locale.ROOT)), targetId)
                        : targetId != null && actionBodiesById.containsKey(targetId.toLowerCase(Locale.ROOT));
                case ExtensionAsset.Target.LOOTABLE ->
                        targetId != null && lootableKnown.test(targetId.toLowerCase(Locale.ROOT));
                case ExtensionAsset.Target.ROLLPOOL ->
                        targetId != null && rollPoolKnown.test(targetId.toLowerCase(Locale.ROOT));
                default -> false;
            };
            if (!targetKnown) {
                out.add(Finding.warning(DOMAIN, "EXTENSION_TARGET_UNKNOWN",
                        scopedStation != null
                                ? label + " Target scopes Action '" + targetId + "' to station '" + scopedStation
                                        + "', which is unknown or resolves no action with that id"
                                : label + " Target." + targetType + " references unknown "
                                        + targetType.toLowerCase(Locale.ROOT) + " '" + targetId + "'",
                        extId));
            }

            checkExtensionPayload(ext.getPerCycleContributions() != null
                            && ext.getPerCycleContributions().length > 0,
                    ExtensionAsset.PAYLOAD_PER_CYCLE_CONTRIBUTIONS, targetType, label, extId, out);
            checkExtensionPayload(ext.getBonus() != null && !ext.getBonus().isEmpty(), ExtensionAsset.PAYLOAD_BONUS,
                    targetType, label, extId, out);
            checkExtensionPayload(ext.getContributionScale() != null,
                    ExtensionAsset.PAYLOAD_CONTRIBUTION_SCALE, targetType, label, extId, out);
            checkExtensionPayload(ext.getActions() != null && ext.getActions().length > 0,
                    ExtensionAsset.PAYLOAD_ACTIONS, targetType, label, extId, out);
            checkExtensionPayload(ext.getConversions() != null && ext.getConversions().length > 0,
                    ExtensionAsset.PAYLOAD_CONVERSIONS, targetType, label, extId, out);
            checkExtensionPayload(ext.getSteps() != null && ext.getSteps().length > 0, ExtensionAsset.PAYLOAD_STEPS,
                    targetType, label, extId, out);
            checkExtensionPayload(ext.getAnchors() != null && !ext.getAnchors().isEmpty(),
                    ExtensionAsset.PAYLOAD_ANCHORS, targetType, label, extId, out);
            checkExtensionPayload(ext.getRolls() != null && ext.getRolls().length > 0, ExtensionAsset.PAYLOAD_ROLLS,
                    targetType, label, extId, out);
            checkExtensionPayload(ext.getEntries() != null && ext.getEntries().length > 0,
                    ExtensionAsset.PAYLOAD_ENTRIES, targetType, label, extId, out);
            checkExtensionPayload(ext.getPuppet() != null, ExtensionAsset.PAYLOAD_PUPPET,
                    targetType, label, extId, out);
            checkExtensionPayload(ext.getCustody() != null, ExtensionAsset.PAYLOAD_CUSTODY,
                    targetType, label, extId, out);
            // Adversarial-verify F3: the two overlay payloads get the SAME content walk every
            // other structured payload in this block gets (a Look.Source NpcRole overlay without
            // a Role.RoleId must warn here too). A standalone overlay carries no effective
            // recipe/hold context (null), and cross-layer model-id existence stays deferred like
            // this validator's other reference checks - overlay STRUCTURE is what is checkable.
            if (ext.getPuppet() != null) {
                checkPuppet(ext.getPuppet(), null, label + ".Puppet", extId, id -> true, out);
            }
            if (ext.getCustody() != null) {
                checkCustody(ext.getCustody(), null, true, label + ".Custody", extId, out);
            }
            if (ext.getContributionScale() != null) {
                checkContributionScale(ext.getContributionScale(), extId, label + ".ContributionScale",
                        factorKnown, out);
            }

            if (ext.getBonus() != null) {
                checkLootRef(ext.getBonus(), extId, label + ".Bonus", dropListKnown, factorKnown, lootableKnown, out);
            }
            if (ext.getRolls() != null) {
                Roll[] rolls = ext.getRolls();
                for (int i = 0; i < rolls.length; i++) {
                    checkRoll(rolls[i], label + ".Rolls[" + i + "]", extId, dropListKnown, factorKnown, out);
                }
            }
            if (ext.getEntries() != null) {
                StatRollEntry[] entries = ext.getEntries();
                for (int i = 0; i < entries.length; i++) {
                    StatRollEntry e = entries[i];
                    if (e != null && e.getPoints() != null) {
                        checkFactorTerms(e.getPoints().getFactors(),
                                label + ".Entries[" + i + "].Points.Factors", extId, factorKnown, out);
                    }
                }
            }

            if (ext.getActions() != null && ext.getActions().length > 0
                    && ExtensionAsset.Target.STATION.equals(targetType)) {
                StationAsset base = targetId != null ? stationsById.get(targetId.toLowerCase(Locale.ROOT)) : null;
                Set<String> baseKeys = base != null
                        ? lowercaseSet(ActionResolver.actionIds(base)) : Set.of();
                ActionDef[] added = ext.getActions();
                for (int i = 0; i < added.length; i++) {
                    if (added[i] == null) {
                        continue;
                    }
                    String key = ActionResolver.effectiveActionId(added[i], i);
                    String lower = key.toLowerCase(Locale.ROOT);
                    if (baseKeys.contains(lower)) {
                        out.add(Finding.warning(DOMAIN, "EXTENSION_KEY_COLLISION",
                                label + " Actions['" + key + "'] collides with station '" + targetId
                                        + "'s own base action - the base always wins, this entry is skipped", extId));
                    } else {
                        recordClaim(actionKeyClaims, target, lower, ext);
                    }
                }
            }

            if (ext.getAnchors() != null && !ext.getAnchors().isEmpty()) {
                Map<String, ActionDef.Anchor> baseAnchors = resolveBaseAnchors(targetBody);
                Set<String> baseAnchorKeys = baseAnchors != null ? lowercaseKeySet(baseAnchors.keySet()) : Set.of();
                for (String key : ext.getAnchors().keySet()) {
                    if (key == null || key.isBlank()) {
                        continue;
                    }
                    String lower = key.toLowerCase(Locale.ROOT);
                    if (baseAnchorKeys.contains(lower)) {
                        out.add(Finding.warning(DOMAIN, "EXTENSION_KEY_COLLISION",
                                label + " Anchors['" + key + "'] collides with " + targetType + " '" + targetId
                                        + "'s own base anchor - the base always wins, this entry is skipped", extId));
                    } else {
                        recordClaim(anchorKeyClaims, target, lower, ext);
                    }
                }
            }

            if (ext.getPerCycleContributions() != null && ext.getPerCycleContributions().length > 0) {
                checkContributionChannels(ext.getPerCycleContributions(),
                        label + " PerCycleContributions", extId, out);
                Set<String> basePairs = resolveBaseContributionKeys(targetBody);
                Set<String> seenInThisExtension = new HashSet<>();
                for (Contribution post : ext.getPerCycleContributions()) {
                    if (post == null || post.getChannel() == null || post.getChannel().isBlank()) {
                        continue;
                    }
                    String pair = contributionPairKey(post);
                    if (!seenInThisExtension.add(pair)) {
                        continue;
                    }
                    String describe = describeContribution(post);
                    if (basePairs.contains(pair)) {
                        out.add(Finding.warning(DOMAIN, "EXTENSION_CONTRIBUTION_DUPLICATE",
                                label + " appends a Contributions entry for " + describe + " the base "
                                        + targetType + " '" + targetId + "' already declares -"
                                        + " PerCycleContributions arrays are additive (mergeContributions appends,"
                                        + " never overrides by channel), so the amounts SUM unless deliberate",
                                extId));
                    } else {
                        recordClaim(channelParamClaims, target, pair, ext);
                    }
                }
            }

            if (ext.getSteps() != null) {
                Set<String> targetStepIds = resolveTargetStepIds(targetBody);
                ExtensionAsset.StepInsertion[] insertions = ext.getSteps();
                for (int i = 0; i < insertions.length; i++) {
                    ExtensionAsset.StepInsertion insertion = insertions[i];
                    String insLabel = label + ".Steps[" + i + "]";
                    if (insertion == null) {
                        continue;
                    }
                    ExtensionAsset.StepInsertion.Anchor anchor = insertion.getAnchor();
                    if (anchor == null || !anchor.hasExactlyOnePlacement()) {
                        out.add(Finding.warning(DOMAIN, "EXTENSION_ANCHOR_MISSING",
                                insLabel + " authors no Anchor, or more than one placement leaf"
                                        + " (exactly one of After|Before|AtStart|AtEnd) - degrades to AtEnd", extId));
                    } else {
                        String anchorStepId = anchor.anchorStepId();
                        if (anchorStepId != null && targetStepIds != null
                                && !targetStepIds.contains(anchorStepId.toLowerCase(Locale.ROOT))) {
                            out.add(Finding.warning(DOMAIN, "EXTENSION_ANCHOR_MISSING",
                                    insLabel + " anchors on unknown step id '" + anchorStepId
                                            + "' - degrades to AtEnd", extId));
                        }
                    }
                    StationStep[] inserts = insertion.getInsert();
                    if (inserts != null) {
                        for (int j = 0; j < inserts.length; j++) {
                            StationStep step = inserts[j];
                            if (step == null || step.getId() == null || step.getId().isBlank()) {
                                out.add(Finding.warning(DOMAIN, "EXTENSION_STEP_MISSING_ID",
                                        insLabel + ".Insert[" + j + "] has no Id - a later extension"
                                                + " cannot anchor on it", extId));
                            }
                        }
                    }
                }
            }
        }

        reportCrossExtensionCollisions(actionKeyClaims, "Actions", out);
        reportCrossExtensionCollisions(anchorKeyClaims, "Anchors", out);
        reportContributionDuplicates(channelParamClaims, out);
        return out;
    }

    /**
     * ONE cross-extension claim on one payload key: the claiming extension plus the station scope it
     * applies under ({@code null} = every station, the bare-target case). The scope lives HERE rather
     * than in {@link #claimKey} so {@link #overlapGroups} can decide which claimants genuinely meet.
     */
    private record Claim(@Nonnull ExtensionAsset ext, @Nullable String scope) {
    }

    /** Records {@code ext}'s claim on {@code claimed} under its target identity, scope attached. */
    private static void recordClaim(@Nonnull Map<String, List<Claim>> claims,
            @Nonnull ExtensionAsset.Target target, @Nonnull String claimed, @Nonnull ExtensionAsset ext) {
        String scope = target.scopedStation();
        claims.computeIfAbsent(claimKey(target, claimed), k -> new ArrayList<>())
                .add(new Claim(ext, scope == null ? null : scope.toLowerCase(Locale.ROOT)));
    }

    /**
     * The cross-extension claim key for one payload key on one target: the target TYPE and ID plus
     * the claimed key, with NO {@code Station} scope segment.
     *
     * <p>The scope used to be part of the key, which correctly kept two extensions claiming one key
     * on the same action id but on DIFFERENT stations from reading as a collision - and silently
     * bought that by filing a BARE claim and a SCOPED claim on the same target under two different
     * keys, so the pair that genuinely DOES overlap (both apply on the scoped station) never
     * reported at all. The scope rides the {@link Claim} instead: one bucket per target key,
     * partitioned by {@link #overlapGroups} into the sets that actually meet at runtime.
     */
    @Nonnull
    private static String claimKey(@Nonnull ExtensionAsset.Target target, @Nonnull String claimed) {
        return target.resolvedType() + ":"
                + (target.resolvedId() == null ? "" : target.resolvedId().toLowerCase(Locale.ROOT))
                + ":" + claimed;
    }

    /** One set of claims that genuinely meet, and WHERE ({@code null} = on every station). */
    private record ClaimGroup(@Nullable String scope, @Nonnull List<Claim> claims) {
    }

    /**
     * Partitions one bucket of claims into the sets that genuinely MEET at runtime, preserving each
     * set's APPLY_ORDER. A bare (unscoped) claim applies on every station, a scoped one only on its
     * own, so:
     * <ul>
     *   <li>no scoped claim in the bucket - ONE unscoped group, the bucket itself (the common case,
     *   and identical to the pre-scope behavior);
     *   <li>otherwise ONE group per distinct scope, each the bare claims plus that scope's own. Two
     *   claims scoped to different stations therefore never share a group, while a bare claim meets
     *   every scoped one.
     * </ul>
     *
     * <p>Deliberate narrowing in the mixed case: when a bucket holds BOTH two bare claims and a
     * scoped one, the bare pair is reported inside the scoped group rather than a second time on its
     * own. It is still reported, naming both extensions; only the "where" reads as that one station
     * instead of everywhere. One finding per real overlap beats a duplicate pair of them.
     */
    @Nonnull
    private static List<ClaimGroup> overlapGroups(@Nonnull List<Claim> claims) {
        List<String> scopes = new ArrayList<>();
        for (Claim c : claims) {
            if (c.scope() != null && !scopes.contains(c.scope())) {
                scopes.add(c.scope());
            }
        }
        if (scopes.isEmpty()) {
            return List.of(new ClaimGroup(null, claims));
        }
        List<ClaimGroup> groups = new ArrayList<>(scopes.size());
        for (String scope : scopes) {
            List<Claim> group = new ArrayList<>();
            for (Claim c : claims) {
                if (c.scope() == null || c.scope().equals(scope)) {
                    group.add(c);
                }
            }
            groups.add(new ClaimGroup(scope, group));
        }
        return groups;
    }

    /** An extension's id for author-facing text, never blank. */
    @Nonnull
    private static String extLabel(@Nonnull ExtensionAsset ext) {
        return ext.getId() == null || ext.getId().isBlank() ? "(unnamed)" : ext.getId();
    }

    /**
     * Whether {@code station} resolves an action answering to {@code targetId}, by the SAME rule the
     * runtime matches a scoped {@code Target:{Station, Action}} extension with
     * ({@code ActionResolver#actionTargetId} per authored action: the {@code Ref}'d
     * {@link ActionAsset} id when the entry Refs one, else its own effective id). A null station (an
     * unknown scope id) resolves nothing.
     */
    private static boolean stationResolvesActionTarget(@Nullable StationAsset station, @Nullable String targetId) {
        if (station == null || targetId == null || targetId.isBlank()) {
            return false;
        }
        for (String actionId : ActionResolver.actionIds(station)) {
            String resolved = ActionResolver.actionTargetId(station, actionId);
            if (resolved != null && resolved.equalsIgnoreCase(targetId)) {
                return true;
            }
        }
        return false;
    }

    private static void checkExtensionPayload(boolean authored, @Nonnull String payloadKey,
            @Nullable String targetType, @Nonnull String label, @Nonnull String extId, @Nonnull List<Finding> out) {
        if (authored && !ExtensionAsset.payloadAllowedFor(targetType, payloadKey)) {
            out.add(Finding.warning(DOMAIN, "EXTENSION_PAYLOAD_MISMATCH",
                    label + " authors " + payloadKey + ", which Target." + targetType + " cannot carry", extId));
        }
    }

    @Nonnull
    private static Set<String> lowercaseKeySet(@Nonnull Set<String> keys) {
        return lowercaseSet(keys);
    }

    @Nonnull
    private static Set<String> lowercaseSet(@Nonnull Collection<String> keys) {
        Set<String> out = new HashSet<>();
        for (String k : keys) {
            if (k != null && !k.isBlank()) {
                out.add(k.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /**
     * Every {@link ActionDef} body an {@code Action}-targeted extension can address, keyed by the
     * id the RUNTIME resolves that target by ({@code ActionResolver#actionTargetId}): the
     * {@code Ref}'d {@link ActionAsset} id when an inline entry {@code Ref}s one, else the inline
     * entry's OWN effective id.
     *
     * <p>Resolving against the standalone {@link ActionAsset} collection alone is what made the
     * shipped pack's own progression extension report {@code EXTENSION_TARGET_UNKNOWN} against a
     * station's inline action, while applying perfectly at runtime - and, more quietly, made the
     * three base-resolution checks below no-op for the only target shape a pack can currently
     * author at all. A {@code Ref}'d inline entry is deliberately NOT keyed under its own id: its
     * target identity IS the {@code Ref} id, already present from the standalone half (a dangling
     * {@code Ref} is {@code ACTION_REF_UNKNOWN}'s business, not this map's). The standalone body
     * wins if an inline entry happens to reuse a standalone asset's id, matching the runtime's own
     * {@code Ref}-first precedence. The implicit action of an {@code Actions}-less station stays
     * untargetable by design.
     */
    @Nonnull
    private static Map<String, ActionDef> actionBodiesByTargetId(@Nonnull Collection<StationAsset> stations,
            @Nonnull Map<String, ActionAsset> actionAssetsById) {
        Map<String, ActionDef> out = new HashMap<>();
        for (StationAsset s : stations) {
            ActionDef[] defs = s != null ? ActionResolver.effectiveActions(s) : null;
            if (defs == null) {
                continue;
            }
            for (int i = 0; i < defs.length; i++) {
                if (defs[i] != null && !defs[i].hasRef()) {
                    out.put(ActionResolver.effectiveActionId(defs[i], i).toLowerCase(Locale.ROOT), defs[i]);
                }
            }
        }
        for (Map.Entry<String, ActionAsset> e : actionAssetsById.entrySet()) {
            ActionDef body = e.getValue() != null ? e.getValue().getBody() : null;
            if (body != null) {
                out.put(e.getKey(), body);
            }
        }
        return out;
    }

    /**
     * The base target's own Anchors map for a collision check. Only a {@code Target:{Action}}
     * extension can carry an {@code Anchors} payload at all (a station holds none), so this resolves
     * the targeted action's own body - standalone or inline, per
     * {@link #actionBodiesByTargetId} - and nothing else.
     */
    @Nullable
    private static Map<String, ActionDef.Anchor> resolveBaseAnchors(@Nullable ActionDef body) {
        return body != null ? body.getAnchors() : null;
    }

    /**
     * The targeted action's {@link ActionDef} body, or null for a non-Action target / unknown id.
     * Resolved ONCE per extension and handed to the three base-resolution checks below.
     *
     * <p>A SCOPED {@code Target:{Station, Action}} resolves against ITS OWN station first, which is
     * the whole reason the scoped form exists: two stations may each author an inline action under
     * the same id, and {@link #actionBodiesByTargetId}'s id-keyed union can only hold one of them.
     * A scoped entry that {@code Ref}s a standalone {@link ActionAsset} falls through to that shared
     * union, where the {@code Ref}'d body already lives under the id the runtime targets it by.
     */
    @Nullable
    private static ActionDef resolveTargetActionBody(@Nonnull ExtensionAsset.Target target,
            @Nonnull Map<String, StationAsset> stationsById, @Nonnull Map<String, ActionDef> actionBodiesById) {
        String targetId = target.resolvedId();
        if (targetId == null || !ExtensionAsset.Target.ACTION.equals(target.resolvedType())) {
            return null;
        }
        String scope = target.scopedStation();
        if (scope != null) {
            ActionDef own = stationActionBody(stationsById.get(scope.toLowerCase(Locale.ROOT)), targetId);
            if (own != null) {
                return own;
            }
        }
        return actionBodiesById.get(targetId.toLowerCase(Locale.ROOT));
    }

    /**
     * {@code station}'s own INLINE action body answering to {@code targetId}. A {@code Ref}'d entry
     * is skipped: its base is the standalone {@link ActionAsset} the {@code Ref} names, which the
     * caller resolves from the shared union instead.
     */
    @Nullable
    private static ActionDef stationActionBody(@Nullable StationAsset station, @Nonnull String targetId) {
        ActionDef[] defs = station != null ? ActionResolver.effectiveActions(station) : null;
        if (defs == null) {
            return null;
        }
        for (int i = 0; i < defs.length; i++) {
            ActionDef def = defs[i];
            if (def != null && !def.hasRef()
                    && ActionResolver.effectiveActionId(def, i).equalsIgnoreCase(targetId)) {
                return def;
            }
        }
        return null;
    }

    /**
     * The known step ids of the resolved target's step program, for dangling {@code After}/
     * {@code Before} anchor detection - resolvable for a {@code Target:{Action}} extension, whose
     * target action's own {@code Steps} come from {@link #actionBodiesByTargetId} (standalone
     * {@link ActionAsset} or a station's inline entry alike). Returns {@code null} when
     * unresolvable (fails open - a dangling reference simply goes unchecked rather than
     * false-flagging every insertion), which is also the {@code Target:{Station}} case: only
     * {@code Actions} is legal there, so no step insertion reaches this check.
     */
    @Nullable
    private static Set<String> resolveTargetStepIds(@Nullable ActionDef body) {
        StationStep[] steps = body != null ? body.getSteps() : null;
        if (steps == null) {
            return null;
        }
        Set<String> ids = new HashSet<>();
        for (StationStep s : steps) {
            if (s != null && s.getId() != null && !s.getId().isBlank()) {
                ids.add(s.getId().toLowerCase(Locale.ROOT));
            }
        }
        return ids;
    }

    /**
     * The base target's own {@code Work.PerCycleContributions} {@code (Channel, Param)} key set
     * (case-folded, param-null-normalized, never null), for {@code EXTENSION_CONTRIBUTION_DUPLICATE}
     * (P8 ruling 75's added check - the gap the review verdict flagged as worse than described:
     * nothing but a hand-written {@code $Comment} in a pack's own extension stood between an author
     * and a silently doubled amount): for a {@code Target:{Action}} extension, the referenced
     * targeted action's OWN body {@code Work} contributions, resolved through
     * {@link #actionBodiesByTargetId} so a station's INLINE action counts too. Only an Action
     * target can carry a {@code PerCycleContributions} payload, so there is no station arm. An
     * empty result (unresolved target, or a target that posts nothing at all) degrades to "nothing
     * to collide with" rather than skipping the cross-extension half of the check below.
     */
    @Nonnull
    private static Set<String> resolveBaseContributionKeys(@Nullable ActionDef body) {
        StationAsset.Work work = body != null ? body.getWork() : null;
        Contribution[] posts = work != null ? work.getPerCycleContributions() : null;
        if (posts == null) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (Contribution post : posts) {
            if (post != null && post.getChannel() != null && !post.getChannel().isBlank()) {
                out.add(contributionPairKey(post));
            }
        }
        return out;
    }

    /**
     * The identity a duplicate contribution is keyed on: the {@code (Channel, Param)} PAIR, both
     * case-folded, with an absent/blank {@code Param} normalized to the empty string. Keying on the
     * pair rather than the channel alone is what keeps two entries on the SAME channel crediting
     * DIFFERENT subjects (the normal shape - one channel plus a param, never one channel per
     * subject) from reading as a duplicate of each other.
     */
    @Nonnull
    private static String contributionPairKey(@Nonnull Contribution post) {
        String channel = post.getChannel() == null ? "" : post.getChannel().toLowerCase(Locale.ROOT);
        String param = post.getParam() == null ? "" : post.getParam().toLowerCase(Locale.ROOT);
        return channel + "|" + param;
    }

    /** Author-facing rendering of a contribution's identity, e.g. {@code (Channel 'x:y', Param 'Z')}. */
    @Nonnull
    private static String describeContribution(@Nonnull Contribution post) {
        String param = post.getParam();
        return "(Channel '" + post.getChannel() + "'"
                + (param == null || param.isBlank() ? ", no Param" : ", Param '" + param + "'") + ")";
    }

    /**
     * Two or more extensions appending the SAME {@code (Channel, Param)} pair to the SAME target
     * (the cross-extension half of {@code EXTENSION_CONTRIBUTION_DUPLICATE}). Deliberately NOT
     * routed through {@link #reportCrossExtensionCollisions}: that helper's wording ("the higher
     * apply-order entry wins, this one is skipped") is true for the KEYED maps it covers
     * ({@code Actions}/{@code Anchors}, base-wins-collision semantics) but false here -
     * {@code ExtensionCatalog#mergeContributions} APPENDS every extension's entries onto the base
     * array, so every claimant listed below actually applies and their amounts sum.
     *
     * <p>Reported per {@link #overlapGroups} set, so the claimants named in one finding are exactly
     * the ones whose amounts genuinely stack somewhere, and the finding says where when that
     * somewhere is one station.
     */
    private static void reportContributionDuplicates(@Nonnull Map<String, List<Claim>> claims,
            @Nonnull List<Finding> out) {
        for (Map.Entry<String, List<Claim>> entry : claims.entrySet()) {
            String key = entry.getKey();
            // The claim key is "<type>:<targetId>:<pair>" (claimKey), and the pair itself may
            // contain ':' inside a namespaced Channel id - so skip exactly the two identity
            // separators from the front rather than searching from the end, which would truncate
            // the channel to its post-namespace half in the warning text.
            int cut = -1;
            for (int i = 0; i < 2 && cut != key.length(); i++) {
                int next = key.indexOf(':', cut + 1);
                if (next < 0) {
                    cut = -1;
                    break;
                }
                cut = next;
            }
            String pair = cut >= 0 ? key.substring(cut + 1) : key;
            for (ClaimGroup group : overlapGroups(entry.getValue())) {
                List<Claim> claimants = group.claims();
                if (claimants.size() < 2) {
                    continue;
                }
                StringBuilder ids = new StringBuilder();
                for (int i = 0; i < claimants.size(); i++) {
                    if (i > 0) {
                        ids.append(", ");
                    }
                    ids.append('\'').append(extLabel(claimants.get(i).ext())).append('\'');
                }
                out.add(Finding.warning(DOMAIN, "EXTENSION_CONTRIBUTION_DUPLICATE",
                        "Extensions " + ids + " all append a Contributions entry for (Channel|Param) '" + pair
                                + "' to the same target"
                                + (group.scope() == null ? "" : " on station '" + group.scope() + "'")
                                + " - PerCycleContributions arrays are additive, so ALL of them"
                                + " apply and the amounts SUM across every one of them",
                        extLabel(claimants.get(0).ext())));
            }
        }
    }

    /**
     * The keyed-collection half: one finding per losing claimant, naming the LAST claimant that
     * actually applies beside it. Walks {@link #overlapGroups} so a bare and a station-scoped claim
     * on one key report each other while two differently-scoped ones stay silent; a loser that
     * meets the same winner in several groups is reported once.
     */
    private static void reportCrossExtensionCollisions(@Nonnull Map<String, List<Claim>> claims,
            @Nonnull String payloadName, @Nonnull List<Finding> out) {
        for (List<Claim> bucket : claims.values()) {
            Set<String> reported = new HashSet<>();
            for (ClaimGroup group : overlapGroups(bucket)) {
                List<Claim> claimants = group.claims();
                if (claimants.size() < 2) {
                    continue;
                }
                // `claimants` keeps the APPLY_ORDER the extensions were walked in, so its LAST
                // entry is the one that actually wins the key at apply time.
                String winnerId = extLabel(claimants.get(claimants.size() - 1).ext());
                for (int i = 0; i < claimants.size() - 1; i++) {
                    String loserId = extLabel(claimants.get(i).ext());
                    if (!reported.add(loserId + " -> " + winnerId)) {
                        continue;
                    }
                    out.add(Finding.warning(DOMAIN, "EXTENSION_KEY_COLLISION",
                            "Extension '" + loserId + "' " + payloadName + " key collides with a later-applying"
                                    + " extension '" + winnerId + "' targeting the same key - the higher apply-order"
                                    + " entry wins, this one is skipped", loserId));
                }
            }
        }
    }

    /**
     * LIVE check (full pass only, decision 66): a {@code Custody.Input.ResourceTypeId} naming a
     * resource-type FAMILY no live item carries can never accept a placement. The round-3
     * smoke's "Fish vs Foods" gap shipped invisibly because nothing verified the family against
     * the live item map (the cutting board wanted {@code Fish}; the intuitively-named
     * {@code Food_Fish_Raw} carries only {@code Foods}). NOTE this check is deliberately
     * one-sided: it proves the family is CARRIED by some live item, not that the item a player
     * would intuitively bring carries it. Fail-open on a cold/failed item-map walk (the
     * {@code benchIdKnownLive} precedent); warn-only, never blocks.
     */
    @Nonnull
    static List<Finding> checkCustodyInputsResolveLive(@Nonnull Collection<StationAsset> stations,
            @Nonnull Collection<ActionAsset> actionAssets) {
        List<Finding> out = new ArrayList<>();
        Set<String> live = liveItemResourceTypeIds();
        if (live.isEmpty()) {
            return out;
        }
        for (StationAsset asset : stations) {
            if (asset == null) {
                continue;
            }
            String id = asset.getId() != null ? asset.getId() : "?";
            ActionDef[] actions = asset.getActions();
            if (actions != null) {
                for (int i = 0; i < actions.length; i++) {
                    if (actions[i] != null) {
                        warnUnmatchedCustodyInput(actions[i].getCustody(), live,
                                "Station '" + id + "' action '"
                                        + ActionResolver.effectiveActionId(actions[i], i) + "'", id, out);
                    }
                }
            }
        }
        for (ActionAsset a : actionAssets) {
            if (a == null || a.getBody() == null) {
                continue;
            }
            String id = a.getId() != null ? a.getId() : "?";
            warnUnmatchedCustodyInput(a.getBody().getCustody(), live, "Action '" + id + "'", id, out);
        }
        return out;
    }

    private static void warnUnmatchedCustodyInput(@Nullable Custody custody, @Nonnull Set<String> live,
            @Nonnull String label, @Nonnull String id, @Nonnull List<Finding> out) {
        ActionInput input = custody != null ? custody.getInput() : null;
        String family = input != null ? input.getResourceTypeId() : null;
        if (family == null || family.isBlank() || live.contains(family.toLowerCase(Locale.ROOT))) {
            return;
        }
        out.add(Finding.warning(DOMAIN, "CUSTODY_INPUT_RESOURCE_TYPE_UNMATCHED",
                label + " Custody.Input.ResourceTypeId '" + family + "' matches NO live item's"
                        + " ResourceTypes family - nothing can ever be placed there (check the"
                        + " family id against the items meant to load it)", id));
    }

    /**
     * How many times a referenced drop list is rolled before an all-empty run is reported. High
     * enough that a table which merely WEIGHTS an empty branch heavily still pays out at least once
     * in practice, low enough to stay a trivial cost at boot.
     */
    private static final int DROPLIST_PROBE_ROLLS = 20;

    /**
     * LIVE check (full pass only): a referenced {@code ItemDropList} that EXISTS can still resolve
     * to nothing forever. An {@code ItemDropList} whose container tree holds only {@code Droplist}
     * references (with no concrete {@code Single} anywhere in it) hands back an empty roll every
     * time, so the content it was meant to grant is silently dead - existence alone never catches
     * it. Rolling the table {@link #DROPLIST_PROBE_ROLLS} times and finding EVERY roll empty is the
     * resolution-failure signal; an occasional empty roll is normal weighting and never reaches the
     * threshold.
     *
     * <p>Pure compute and world-thread-safe ({@code ItemModule.getRandomItemDrops} is the same
     * native roll boundary the loot engine itself uses). Fail-open on a cold or throwing item module
     * - the same stance {@link #dropListKnownLive} takes, since a lookup failure is not evidence the
     * table is wrong. Warn-only, never blocks.
     *
     * <p>A validate run made before the drop tables have finished resolving can report a table that
     * is in fact fine, which is why this lives in the FULL pass only (the post-load audit and
     * {@code /rpgstations validate}) rather than the per-fold structural one, and why the finding
     * says so.
     */
    @Nonnull
    static List<Finding> checkDropListsResolveLive(@Nonnull Collection<String> dropListIds) {
        List<Finding> out = new ArrayList<>();
        for (String dropListId : dropListIds) {
            if (dropListId == null || dropListId.isBlank()) {
                continue;
            }
            Boolean paidOut = dropListEverPaysOutLive(dropListId);
            if (paidOut == null || paidOut) {
                continue;
            }
            out.add(Finding.warning(DOMAIN, "LOOT_DROPLIST_NEVER_RESOLVES",
                    "ItemDropList '" + dropListId + "' rolled nothing in " + DROPLIST_PROBE_ROLLS
                            + " consecutive rolls, so whatever references it grants nothing. A table whose"
                            + " container tree holds only Droplist references resolves to nothing at runtime -"
                            + " pair every Droplist reference with at least one concrete Single somewhere in"
                            + " the tree. A validate run made before the drop tables have settled can also"
                            + " report this; re-run it once the server is fully up before re-authoring the table.",
                    dropListId));
        }
        return out;
    }

    /**
     * {@code true} when {@code dropListId} paid out at least one stack within
     * {@link #DROPLIST_PROBE_ROLLS} rolls, {@code false} when every roll was empty, {@code null}
     * when the table could not be probed at all (fail open - never a finding).
     */
    @Nullable
    private static Boolean dropListEverPaysOutLive(@Nonnull String dropListId) {
        try {
            for (int i = 0; i < DROPLIST_PROBE_ROLLS; i++) {
                List<ItemStack> drops = ItemModule.get().getRandomItemDrops(dropListId);
                if (drops != null && !drops.isEmpty()) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Every ResourceTypes family id carried by any LIVE item, lowercased; empty on a cold/failed map. */
    @Nonnull
    private static Set<String> liveItemResourceTypeIds() {
        Set<String> out = new HashSet<>();
        try {
            for (Item item : Item.getAssetMap().getAssetMap().values()) {
                if (item == null) {
                    continue;
                }
                ItemResourceType[] types = item.getResourceTypes();
                if (types == null) {
                    continue;
                }
                for (ItemResourceType t : types) {
                    if (t != null && t.id != null && !t.id.isBlank()) {
                        out.add(t.id.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Throwable t) {
            return new HashSet<>();
        }
        return out;
    }

    /**
     * Placed-input custody (design section 9.4, phase-2 leg C): a {@link Custody} group with
     * neither an explicit {@link Custody#getInput()} NOR an {@code effectiveRecipe} to derive
     * placement acceptance from has no way to ever accept a held stack - the state-dependent F
     * interaction can never place anything (a silent dead-content trap, not merely cosmetic).
     * {@code overlay} suppresses ONLY that check (delta re-check, F3 follow-up): an
     * {@code ExtensionAsset} Custody overlay is a DELTA, not a complete group - the canonical
     * Display-only re-skin inherits Input/Recipes from the BASE, so warning "nothing can ever be
     * placed" about it would actively mislead. The value-range checks below still run either way.
     */
    private static void checkCustody(@Nullable Custody custody,
            @Nullable StationAsset.Recipe effectiveRecipe,
            boolean overlay, @Nonnull String label, @Nonnull String id, @Nonnull List<Finding> out) {
        if (custody == null) {
            return;
        }
        if (!overlay && custody.getInput() == null && effectiveRecipe == null) {
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
        // P11 knob (ruling 74): SingleFamily locks the claim to whichever family placed first,
        // refusing a different one "until the claim empties" - but a claim that can only ever hold
        // ONE item at a time (MaxQuantity <= 1) already refuses a second placement on CAPACITY
        // alone before the family check is ever reached, so the knob is dead weight there.
        if (custody.effectiveSingleFamily() && custody.effectiveMaxQuantity() <= 1) {
            out.add(Finding.warning(DOMAIN, "CUSTODY_SINGLE_FAMILY_REDUNDANT",
                    label + " authors Custody.SingleFamily true with an effective MaxQuantity of "
                            + custody.effectiveMaxQuantity() + " - a claim that holds at most one item already"
                            + " refuses a second family on capacity alone, so the family lock never fires", id));
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
                    && (hide.getEffect() == null || !hide.getEffect().hasId())) {
                out.add(Finding.warning(DOMAIN, "PUPPET_HIDE_EFFECT_MISSING_ID",
                        label + " Puppet.Hide.Route is \"Effect\" but Effect.Id is blank - the route is inert"
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
                    && !Puppet.LOOK_SOURCE_MODEL.equalsIgnoreCase(rawSource)
                    && !Puppet.LOOK_SOURCE_NPC_ROLE.equalsIgnoreCase(rawSource)) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_PUPPET_LOOK_SOURCE",
                        label + " Puppet.Look.Source '" + rawSource
                                + "' is not one of PlayerClone/Model/NpcRole - falls back to PlayerClone at runtime", id));
            }
            // Look.Model carries the fixed-model arm's own id; the any-source fallback sits one
            // level up on Look itself, because every arm reads it.
            Puppet.Model model = look.getModel();
            if (Puppet.LOOK_SOURCE_MODEL.equalsIgnoreCase(look.effectiveSource())) {
                String modelId = model != null ? model.getModelId() : null;
                boolean modelIdBlank = modelId == null || modelId.isBlank();
                String fallback = look.getFallbackModelId();
                boolean fallbackAuthored = fallback != null && !fallback.isBlank();
                if (!fallbackAuthored && (modelIdBlank || !modelKnown.test(modelId))) {
                    out.add(Finding.warning(DOMAIN, "PUPPET_LOOK_MODEL_UNKNOWN",
                            label + " Puppet.Look.Source is \"Model\" but Look.Model.ModelId "
                                    + (modelIdBlank ? "is blank" : "'" + modelId + "' is not a known ModelAsset")
                                    + " and no FallbackModelId is authored - falls back to the default rig at runtime",
                            id));
                }
            }
            // The NpcRole performer arm (seam wave, decision 47/48): the R1 handoff's flagged
            // LOOK_ROLE_UNKNOWN check - a dangling RoleId falls back to the bare-Holder/
            // FallbackModelId ladder, never a crash (design's engage-time fail-closed fallback).
            if (Puppet.LOOK_SOURCE_NPC_ROLE.equalsIgnoreCase(look.effectiveSource())) {
                Puppet.Role role = look.getRole();
                boolean roleIdBlank = role == null || !role.hasRoleId();
                if (roleIdBlank) {
                    out.add(Finding.warning(DOMAIN, "PUPPET_LOOK_ROLE_MISSING",
                            label + " Puppet.Look.Source is \"NpcRole\" but Look.Role.RoleId is blank - falls back"
                                    + " to the bare-Holder performer at runtime", id));
                } else if (!npcRoleKnownLive(role.getRoleId())) {
                    out.add(Finding.warning(DOMAIN, "LOOK_ROLE_UNKNOWN",
                            label + " Puppet.Look.Role.RoleId '" + role.getRoleId() + "' does not resolve to a"
                                    + " registered NPC Role - engage falls back to the bare-Holder performer", id));
                }
                if (role != null) {
                    String rawSkinSource = role.getSkinSource();
                    if (rawSkinSource != null && !rawSkinSource.isBlank()
                            && !Puppet.SKIN_SOURCE_PLAYER_CLONE.equalsIgnoreCase(rawSkinSource)
                            && !Puppet.SKIN_SOURCE_ROLE_DEFAULT.equalsIgnoreCase(rawSkinSource)) {
                        out.add(Finding.warning(DOMAIN, "UNKNOWN_PUPPET_ROLE_SKIN_SOURCE",
                                label + " Puppet.Look.Role.SkinSource '" + rawSkinSource
                                        + "' is not one of PlayerClone/RoleDefault - falls back to PlayerClone at runtime", id));
                    }
                }
                // Prop/clip mirroring on NpcRole is UNPROVEN pending the spike (seam design section
                // 2.4/Q5) - advise, never block, on a station authoring both.
                if (puppet.getProp() != null
                        && !Puppet.PROP_SOURCE_NONE.equalsIgnoreCase(puppet.getProp().effectiveSource())) {
                    out.add(Finding.info(DOMAIN, "PUPPET_NPC_ROLE_PROP_UNPROVEN",
                            label + " Puppet.Look.Source is \"NpcRole\" alongside a non-None Puppet.Prop - prop"
                                    + " mirroring on the NpcRole performer is UNPROVEN pending the maintainer's"
                                    + " in-game spike (best-effort/role-authored-only until confirmed)", id));
                }
                // An NPC keeps its pose from its own leash, which carries a heading and a pitch and
                // nothing else - so a banked pose is the one tilt axis this performer cannot hold.
                Rotation rotation = puppet.getRotation();
                if (rotation != null && rotation.getRoll() != null && puppet.effectiveRollDegrees() != 0.0) {
                    out.add(Finding.warning(DOMAIN, "PUPPET_NPC_ROLE_ROLL_DROPPED",
                            label + " Puppet.Look.Source is \"NpcRole\" with Puppet.Rotation.Roll "
                                    + puppet.effectiveRollDegrees() + " - the NpcRole performer has no roll axis"
                                    + " (its leash mirrors yaw and pitch only), so the bank is dropped and the"
                                    + " puppet stands level about that axis. Yaw and Pitch still apply; author"
                                    + " Look.Source \"PlayerClone\" or \"Model\" if the roll matters", id));
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

    /** The reusable {@code Tool} group core, run against each action's one held-tool gate. */
    private static void checkToolGroup(@Nullable StationAsset.Tool tool, @Nonnull String id,
                                       @Nonnull String label, @Nonnull List<Finding> out) {
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
        // Decision 53 (Tags.Family match, the PrepFish re-author): an authored Tags key whose value
        // array is empty can never match ANY-of nothing - a silent no-op for that key specifically,
        // distinct from EMPTY_TOOL_GATE (the whole group being empty).
        if (anyTags) {
            for (Map.Entry<String, String[]> entry : tool.getTags().entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank()) {
                    continue;
                }
                if (!hasNonBlank(entry.getValue())) {
                    out.add(Finding.warning(DOMAIN, "TOOL_TAGS_EMPTY_VALUES",
                            label + " Tool.Tags['" + key + "'] has no non-blank values; this key can never match", id));
                }
            }
        }
        checkDurability(tool, id, label, out);
        checkMinStartPercent(tool, id, label, out);
    }

    /**
     * {@code Tool.Durability.MinStartPercent} (P11 knob, ruling 74): a value outside (0, 100] is
     * almost always an authoring slip, not intent - the leaf documents itself as a PERCENT (0-100),
     * so a fraction like {@code 0.5} silently becomes a near-impossible 0.5% floor rather than the
     * intended 50%, and anything <= 0 is a no-op the reader already treats as "no gate" (author
     * {@code null} instead of a zero/negative sentinel).
     */
    private static void checkMinStartPercent(@Nonnull StationAsset.Tool tool, @Nonnull String id,
                                             @Nonnull String label, @Nonnull List<Finding> out) {
        Double minStart = tool.getMinStartPercent();
        if (minStart != null && (minStart <= 0 || minStart > 100)) {
            out.add(Finding.warning(DOMAIN, "TOOL_MIN_DURABILITY_OUT_OF_RANGE",
                    label + " authors Tool.Durability.MinStartPercent " + minStart + " outside (0, 100] - the"
                            + " gate expects a PERCENT (0-100), not a 0-1 fraction; a value <= 0 is already a"
                            + " no-op (author null instead) and a value > 100 can never pass", id));
        }
    }

    private static void checkDurability(@Nonnull StationAsset.Tool tool, @Nonnull String id,
                                        @Nonnull String label, @Nonnull List<Finding> out) {
        StationAsset.Tool.Durability durability = tool.getDurability();
        if (durability == null) {
            return;
        }
        boolean perSwingOn = durability.getPerSwing() != null && durability.getPerSwing() > 0;
        boolean perCycleOn = durability.getPerCycle() != null && durability.getPerCycle() > 0;
        boolean gateOn = durability.getMinStartPercent() != null;
        if (!perSwingOn && !perCycleOn && !gateOn) {
            out.add(Finding.warning(DOMAIN, "DEAD_DURABILITY_GROUP",
                    label + " authors a Tool.Durability group with no positive PerSwing or PerCycle and no"
                            + " MinStartPercent gate; the whole group is a no-op", id));
            return;
        }
        if (perSwingOn) {
            out.add(Finding.info(DOMAIN, "DURABILITY_PERSWING_ADVISORY",
                    label + " authors Tool.Durability.PerSwing " + durability.getPerSwing()
                            + "; a fast Animation.Swing.IntervalMs multiplies the wear - balance is the author's responsibility", id));
        }
    }


    /**
     * The shared {@link LootRef} core (scope-2 design 1.3, DRY principle 1 - the ONE loot-reference
     * vocabulary): validates {@link LootRef#getLootables()} references, then every
     * {@link LootRef#getRolls()} entry via {@link #checkRoll}. Reused by the station-level
     * an action's own {@code Bonus} group, a {@code StationStep.Roll} phase, and an
     * {@code ExtensionAsset.Bonus} payload.
     */
    private static void checkLootRef(@Nullable LootRef loot, @Nonnull String id, @Nonnull String label,
                                     @Nonnull Predicate<String> dropListKnown, @Nonnull Predicate<String> factorKnown,
                                     @Nonnull Predicate<String> lootableKnown, @Nonnull List<Finding> out) {
        checkLootRef(loot, id, label, false, dropListKnown, factorKnown, lootableKnown, out);
    }

    /**
     * As above, for a {@link LootRef} reached through an action that runs an authored {@code Steps}
     * program ({@code noCycleOutput} - see {@link #checkGrants}).
     */
    private static void checkLootRef(@Nullable LootRef loot, @Nonnull String id, @Nonnull String label,
                                     boolean noCycleOutput, @Nonnull Predicate<String> dropListKnown,
                                     @Nonnull Predicate<String> factorKnown,
                                     @Nonnull Predicate<String> lootableKnown, @Nonnull List<Finding> out) {
        if (loot == null) {
            return;
        }
        String[] lootables = loot.getLootables();
        if (lootables != null) {
            for (String t : lootables) {
                if (t == null || t.isBlank()) {
                    out.add(Finding.warning(DOMAIN, "LOOT_BLANK_TABLE", label + " Lootables has a blank entry", id));
                } else if (!lootableKnown.test(t.toLowerCase(Locale.ROOT))) {
                    out.add(Finding.warning(DOMAIN, "LOOT_UNKNOWN_TABLE",
                            label + " Lootables references unknown lootable '" + t + "'", id));
                }
            }
        }
        Roll[] rolls = loot.getRolls();
        if (rolls != null) {
            for (int i = 0; i < rolls.length; i++) {
                checkRoll(rolls[i], label + " Rolls[" + i + "]", id, noCycleOutput, dropListKnown, factorKnown, out);
            }
        }
    }

    /**
     * The shared {@link Roll} structural core (design 4.8's "validator coverage" + the M3
     * critique fix 5, scope-2 weighted-factor unification): {@code Conditions} factor ids run
     * through {@code factorKnown} via {@link #checkConditionFactors}; {@code Chance.Factors}/
     * {@code Ladder.Factors} (now the weighted factor terms) run through {@code factorKnown} via
     * {@link #checkFactorTerms} - the SAME {@code UNKNOWN_FACTOR} code every factor-reference site
     * in this file uses, one code, one meaning; every {@code Grants.DropLists} entry (top-level or
     * per-floor) runs through {@code dropListKnown}; two floors of one ladder sharing a {@code Min}
     * are flagged {@code LADDER_DUPLICATE_FLOOR_MIN}; and a roll naming the same
     * {@code (Factor, Param)} pair twice across its own reference sites is flagged INFO
     * {@code LOOT_DUPLICATE_FACTOR} via {@link #checkDuplicateFactors}.
     */
    static void checkRoll(@Nullable Roll roll, @Nonnull String label, @Nonnull String id,
                          @Nonnull Predicate<String> dropListKnown, @Nonnull Predicate<String> factorKnown,
                          @Nonnull List<Finding> out) {
        checkRoll(roll, label, id, false, dropListKnown, factorKnown, out);
    }

    /**
     * As above, for a roll reached through an action that runs an authored {@code Steps} program
     * ({@code noCycleOutput} - see {@link #checkGrants}).
     */
    static void checkRoll(@Nullable Roll roll, @Nonnull String label, @Nonnull String id,
                          boolean noCycleOutput, @Nonnull Predicate<String> dropListKnown,
                          @Nonnull Predicate<String> factorKnown, @Nonnull List<Finding> out) {
        if (roll == null) {
            return;
        }
        String trigger = roll.effectiveTrigger();
        // Everything about a roll that is TRUE OF EVERY ROLL ANYWHERE - an impossible or certain
        // chance, an inverted clamp, an unreachable or duplicate ladder floor, a blank grant leaf,
        // a roll that can never hand anything over - is the shared loot validator's, so identical
        // JSON gets identical findings at a station, in a chest, and at a quest turn-in. Do not
        // re-derive any of it here; a second copy is how two engines end up disagreeing about the
        // same file.
        for (Finding shared : atBlock(LootableValidator.auditRoll(roll, id, null), label)) {
            out.add(shared);
        }
        // What stays here is what only a STATION knows: which factor ids this engine can answer,
        // which native drop tables exist, which grants make sense under which trigger, and the
        // native-asset refs on a cue.
        checkConditionFactors(roll.getConditions(), label + ".Conditions", id, factorKnown, out);
        checkDuplicateFactors(roll, label, id, out);

        FactorFormula chance = roll.getChance();
        if (chance != null) {
            checkFactorTerms(chance.getFactors(), label + ".Chance.Factors", id, factorKnown, out);
        }

        checkGrants(roll.getGrants(), label + ".Grants", id, trigger, noCycleOutput, dropListKnown, out);
        checkCueMoment(roll.getCue(), label + ".Cue", id, out);

        Roll.Ladder ladder = roll.getLadder();
        if (ladder != null) {
            checkFactorTerms(ladder.getFactors(), label + ".Ladder.Factors", id, factorKnown, out);
            Roll.Ladder.Floor[] floors = ladder.getFloors();
            if (floors != null) {
                checkFloors(floors, label, id, trigger, noCycleOutput, dropListKnown, out);
            }
        }
    }

    /**
     * The shared loot validator's findings, each re-filed under this engine's domain with
     * {@code label} prefixed onto the message so it still points at the exact authored block. The
     * shared validator speaks in terms of a table and a roll index; a station author is looking at
     * {@code Station[sawmill].Actions[work].Bonus.Rolls[0]}.
     */
    @Nonnull
    private static List<Finding> atBlock(@Nonnull List<Finding> shared, @Nonnull String label) {
        List<Finding> out = new ArrayList<>(shared.size());
        for (Finding f : shared) {
            out.add(new Finding(f.severity(), f.code(), label + ": " + f.message(), f.sourceId(), DOMAIN));
        }
        return out;
    }

    /**
     * A {@code Cue} is a MOMENT id, played through the same funnel every other station moment goes
     * through - so a typo is the same warn-only typo finding an action's own {@code Moments} key
     * gets, and a future engine moment can never break an older pack.
     */
    private static void checkCueMoment(@Nullable String cue, @Nonnull String label, @Nonnull String id,
            @Nonnull List<Finding> out) {
        if (cue == null || cue.isBlank()) {
            return;
        }
        if (!StationFlairs.isKnownMomentId(cue)) {
            out.add(Finding.warning(DOMAIN, "UNKNOWN_MOMENT_ID",
                    label + " names moment '" + cue + "', which no engine moment answers to -"
                            + " nothing will play unless a flair supplies it", id));
        }
    }

    /**
     * The generic redundant-reference lint (INFO-only, best-effort): one {@link Roll} naming the
     * SAME {@code (Factor, Param)} pair more than once across its own {@code Conditions} /
     * {@code Chance.Factors} / {@code Ladder.Factors}. The engine can state that in its own
     * terms - reading one number twice in one formula is nearly always an editing slip - without
     * knowing what any particular factor MEANS.
     *
     * <p><b>Keyed on the PAIR, deliberately, and that is load-bearing.</b> Keying on the factor id
     * alone would fire on correct content by construction: every stat read carries the same
     * {@code "hytale:stat"} factor id, so a ladder composing two different stat channels (the documented
     * equal-weight composition shape) would emit a spurious note at every boot. Only a genuinely
     * repeated pair fires, which in practice means a param-less zero-arg engine factor named twice
     * (e.g. {@code rpgstations:cycle_count} in both a {@code Conditions} entry and a Chance.Factors entry).
     *
     * <p>Composition rules that depend on what specific ids MEAN - "these two ids are two views of
     * the same underlying number, never compose both" - belong to whichever mod owns that
     * vocabulary, and reach content through a registered {@code api.ValidationHook} (see
     * {@link #runHooks}), never a branch in here naming a foreign id.
     */
    private static void checkDuplicateFactors(@Nonnull Roll roll, @Nonnull String label, @Nonnull String id,
                                              @Nonnull List<Finding> out) {
        Set<String> seen = new HashSet<>();
        Set<String> reported = new HashSet<>();
        if (roll.getConditions() != null) {
            for (FactorCondition c : roll.getConditions()) {
                if (c != null) {
                    noteFactorPair(c.getFactor(), c.getParam(), seen, reported, label, id, out);
                }
            }
        }
        if (roll.getChance() != null) {
            noteFactorRefPairs(roll.getChance().getFactors(), seen, reported, label, id, out);
        }
        if (roll.getLadder() != null) {
            noteFactorRefPairs(roll.getLadder().getFactors(), seen, reported, label, id, out);
        }
    }

    private static void noteFactorRefPairs(@Nullable FactorFormula.Term[] terms, @Nonnull Set<String> seen,
                                           @Nonnull Set<String> reported, @Nonnull String label,
                                           @Nonnull String id, @Nonnull List<Finding> out) {
        if (terms == null) {
            return;
        }
        for (FactorFormula.Term t : terms) {
            if (t != null) {
                noteFactorPair(t.getFactor(), t.getParam(), seen, reported, label, id, out);
            }
        }
    }

    /** Records one {@code (Factor, Param)} reference, reporting the FIRST repeat of each pair only. */
    private static void noteFactorPair(@Nullable String factor, @Nullable String param, @Nonnull Set<String> seen,
                                       @Nonnull Set<String> reported, @Nonnull String label, @Nonnull String id,
                                       @Nonnull List<Finding> out) {
        if (factor == null || factor.isBlank()) {
            return;
        }
        String normalizedFactor = factor.trim().toLowerCase(Locale.ROOT);
        String normalizedParam = param == null ? "" : param.trim().toLowerCase(Locale.ROOT);
        String pair = normalizedFactor + "|" + normalizedParam;
        if (seen.add(pair) || !reported.add(pair)) {
            return;
        }
        out.add(Finding.info(DOMAIN, "LOOT_DUPLICATE_FACTOR",
                label + " references (Factor '" + factor.trim() + "'"
                        + (normalizedParam.isEmpty() ? ", no Param" : ", Param '" + param.trim() + "'")
                        + ") more than once across its Conditions / Chance.Factors / Ladder.Factors -"
                        + " the same number is read twice in one roll", id));
    }

    private static void checkFloors(@Nonnull Roll.Ladder.Floor[] floors, @Nonnull String rollLabel,
                                    @Nonnull String id, @Nonnull String trigger, boolean noCycleOutput,
                                    @Nonnull Predicate<String> dropListKnown, @Nonnull List<Finding> out) {
        // Duplicate-Min coverage deliberately does NOT live here: checkDuplicateFloorMins is the
        // ONE shared duplicate-floor check both ladder consumers run (LADDER_DUPLICATE_FLOOR_MIN),
        // and a second local emit here double-reported the same defect under a second code.
        for (int i = 0; i < floors.length; i++) {
            Roll.Ladder.Floor f = floors[i];
            String fLabel = rollLabel + ".Ladder.Floors[" + i + "]";
            if (f == null) {
                continue;
            }
            Double min = f.getMin();
            if (min == null || min <= 0.0) {
                // The shared ladder rule (loot/FactorLadder): Min reader-defaults to 0 and a
                // Min <= 0 floor IS reachable - always, as the ladder's baseline tier. Info-only:
                // legal and engine-honored, just worth a confirm that a baseline was intended.
                out.add(Finding.info(DOMAIN, "LOOT_LADDER_FLOOR_MISSING_MIN",
                        fLabel + " authors no positive Min - it defaults to 0 and is ALWAYS reached,"
                                + " making it the ladder's baseline tier; confirm a baseline is"
                                + " intended", id));
            }
            checkGrants(f.getGrants(), fLabel + ".Grants", id, trigger, noCycleOutput, dropListKnown, out);
            checkCueMoment(f.getCue(), fLabel + ".Cue", id, out);
        }
    }

    private static void checkConditionFactors(@Nullable FactorCondition[] conditions, @Nonnull String label,
                                              @Nonnull String id, @Nonnull Predicate<String> factorKnown,
                                              @Nonnull List<Finding> out) {
        if (conditions == null) {
            return;
        }
        for (FactorCondition c : conditions) {
            if (c == null || c.getFactor() == null || c.getFactor().isBlank()) {
                continue;
            }
            if (!factorKnown.test(c.getFactor())) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_FACTOR",
                        label + " references unknown factor '" + c.getFactor() + "'", id));
            }
        }
    }

    /**
     * The weighted-term sibling of {@link #checkConditionFactors}, reused everywhere a numeric
     * factor channel is SUMMED - {@code Roll.Chance.Factors}, {@code Roll.Ladder.Factors},
     * {@code ContributionScale.Factors}, {@code StatRollEntry.Points.Factors}, a Stamp budget's
     * {@code Factors}, {@code StationStep.Repeat.Factors}. Same {@code UNKNOWN_FACTOR} code as
     * every other factor-reference site (one code, one meaning).
     */
    private static void checkFactorTerms(@Nullable FactorFormula.Term[] factors, @Nonnull String label,
                                        @Nonnull String id,
                                        @Nonnull Predicate<String> factorKnown, @Nonnull List<Finding> out) {
        if (factors == null) {
            return;
        }
        for (FactorFormula.Term f : factors) {
            if (f == null || f.isBlank()) {
                continue;
            }
            if (!factorKnown.test(f.getFactor())) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_FACTOR",
                        label + " references unknown factor '" + f.getFactor() + "'", id));
            }
        }
    }

    /**
     * {@code noCycleOutput}: does the action this roll belongs to run an authored {@code Steps}
     * program? Such a program has no single "cycle output" for a {@code Grants.OutputItems} to add
     * items TO, so the engine drops the grant - see
     * {@code LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT} below. {@code false} at every site with no action
     * context (a lootable table, a standalone extension payload): those are checked where they are
     * REFERENCED from an action instead, since the same table can be shared by both action shapes.
     */
    private static void checkGrants(@Nullable LootGrants grants, @Nonnull String label, @Nonnull String id,
            @Nonnull String trigger, boolean noCycleOutput, @Nonnull Predicate<String> dropListKnown,
            @Nonnull List<Finding> out) {
        if (grants == null) {
            return;
        }
        String[] dropLists = grants.getDropLists();
        if (dropLists != null) {
            for (int i = 0; i < dropLists.length; i++) {
                String dropListId = dropLists[i];
                if (dropListId == null || dropListId.isBlank()) {
                    continue;
                }
                if (!dropListKnown.test(dropListId)) {
                    out.add(Finding.warning(DOMAIN, "LOOT_UNKNOWN_DROPLIST",
                            label + ".DropLists[" + i + "] references unknown ItemDropList '"
                                    + dropListId + "'", id));
                }
            }
        }
        boolean cycleTrigger = StationLootEngine.TRIGGER_CYCLE.equalsIgnoreCase(trigger);
        for (RewardSpec spec : grants.rewardSpecs()) {
            String kind = spec.kind();
            if (StationRewardKinds.KIND_EFFECT.equalsIgnoreCase(kind)) {
                checkEffectRef(EffectRef.of(spec.param("id")), label + " effect reward", id, out);
            } else if (StationRewardKinds.KIND_OUTPUT_ITEMS.equalsIgnoreCase(kind)) {
                checkOutputItemsReward(spec, label, id, trigger, cycleTrigger, noCycleOutput, out);
            } else if (StationRewardKinds.KIND_CONTRIBUTION.equalsIgnoreCase(kind)) {
                checkContributionReward(spec, label, id, trigger, cycleTrigger, out);
            }
        }
    }

    /**
     * Extra units of the cycle's own primary output only mean something where there IS one: a
     * Completion roll fires from inside session stop with the cycle already paid out, and an
     * authored {@code Steps} program produces whatever its phases individually author rather than
     * one recipe-driven output. Either way the reward evaluates and then has nothing to add to, so
     * without this the content is dead with no diagnostic.
     */
    private static void checkOutputItemsReward(@Nonnull RewardSpec spec, @Nonnull String label,
            @Nonnull String id, @Nonnull String trigger, boolean cycleTrigger, boolean noCycleOutput,
            @Nonnull List<Finding> out) {
        if (spec.doubleParam("count", 0.0) <= 0.0) {
            out.add(Finding.warning(DOMAIN, "LOOT_OUTPUT_ITEMS_NONPOSITIVE",
                    label + " grants an output-items reward with no positive Count", id));
            return;
        }
        if (!cycleTrigger) {
            out.add(Finding.warning(DOMAIN, "LOOT_OUTPUT_ITEMS_WRONG_TRIGGER",
                    label + " grants extra cycle output under a non-Cycle Trigger ('" + trigger
                            + "') - there is no cycle output to add items to, so the grant is dropped", id));
        } else if (noCycleOutput) {
            out.add(Finding.warning(DOMAIN, "LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT",
                    label + " grants extra cycle output on an action that runs an authored Steps"
                            + " program - such a program has no single cycle output to add items to,"
                            + " so the grant is dropped; author a Produce phase or a drop list instead", id));
        }
    }

    /** A one-shot contribution rides the cycle-completed event, which only a Cycle trigger has. */
    private static void checkContributionReward(@Nonnull RewardSpec spec, @Nonnull String label,
            @Nonnull String id, @Nonnull String trigger, boolean cycleTrigger, @Nonnull List<Finding> out) {
        String channel = spec.param("channel");
        if (channel == null || channel.isBlank()) {
            out.add(Finding.warning(DOMAIN, "LOOT_CONTRIBUTION_MISSING_CHANNEL",
                    label + " grants a contribution with no Channel - nothing can interpret it", id));
            return;
        }
        if (!cycleTrigger) {
            out.add(Finding.warning(DOMAIN, "LOOT_CONTRIBUTION_WRONG_TRIGGER",
                    label + " grants a contribution under a non-Cycle Trigger ('" + trigger
                            + "') - there is no cycle event to forward the post on", id));
        }
        if (spec.doubleParam("amount", 0.0) <= 0.0) {
            out.add(Finding.warning(DOMAIN, "LOOT_CONTRIBUTION_NONPOSITIVE_AMOUNT",
                    label + " grants a contribution on '" + channel + "' with no positive Amount", id));
        }
        checkContributionChannels(new Contribution[] {Contribution.of(channel, spec.param("param"),
                spec.doubleParam("amount", 0.0))}, label + " contribution", id, out);
    }

    /**
     * The channel-vocabulary backstop, the exact mirror of {@code UNKNOWN_FACTOR} on the read side:
     * a {@code Channel} nobody declared through {@code api.ContributionChannelRegistry} is almost
     * always a typo, and a typo'd channel posts into a void forever without saying so.
     *
     * <p><b>WARN, fail-open, absolutely.</b> An undeclared channel is still forwarded verbatim on
     * the cycle event - this note never blocks an asset load or a grant. It also fails open on an
     * EMPTY declared set (no progression mod installed, or a unit JVM), so a jar-only server never
     * manufactures a wall of notes about content that is working exactly as intended. The message
     * echoes the declared set so a near-miss is obvious at a glance.
     */
    private static void checkContributionChannels(@Nullable Contribution[] posts, @Nonnull String label,
                                                  @Nonnull String id, @Nonnull List<Finding> out) {
        if (posts == null || posts.length == 0) {
            return;
        }
        List<String> declared = ContributionChannelRegistryImpl.getInstance().registeredIds();
        if (declared.isEmpty()) {
            return;
        }
        Set<String> reported = new HashSet<>();
        for (Contribution post : posts) {
            if (post == null || post.getChannel() == null || post.getChannel().isBlank()) {
                continue;
            }
            String channel = post.getChannel().trim();
            if (ContributionChannelRegistryImpl.getInstance().isDeclared(channel)
                    || !reported.add(channel.toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(Finding.warning(DOMAIN, "UNKNOWN_CHANNEL",
                    label + " posts to undeclared channel '" + channel + "' - it is still forwarded, but"
                            + " nothing is listening for it by that name; declared channels are "
                            + String.join(", ", declared), id));
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

    /** One action's own {@code Recipe} group (the whole of "what this action makes"). */
    private static void checkRecipe(@Nullable StationAsset.Recipe recipe, @Nonnull String id,
                                    @Nonnull String label, @Nonnull List<Finding> out) {
        if (recipe == null) {
            return;
        }
        String rLabel = label + " Recipe";
        StationAsset.Conversion[] conversions = recipe.getConversions();
        StationAsset.FromCrafting fromCrafting = recipe.getFromCrafting();
        boolean hasConversions = conversions != null && conversions.length > 0;
        if (!hasConversions && fromCrafting == null) {
            out.add(Finding.warning(DOMAIN, "RECIPE_ENTRY_EMPTY",
                    rLabel + " authors neither Conversions nor FromCrafting - it can never run a cycle", id));
        }
        if (fromCrafting != null) {
            checkFromCrafting(fromCrafting, id, rLabel, out);
        }
        if (hasConversions) {
            checkConversions(conversions, id, rLabel, out);
        }
        if (recipe.getYield() != null) {
            checkYield(recipe.getYield(), id, rLabel, out);
        }
    }

    /**
     * {@code Recipe.Yield} coverage - every finding warn-or-info, per this validator's never-block
     * posture: a nonsensical yield still loads and the engine's own reader defaults absorb it.
     */
    private static void checkYield(@Nonnull StationAsset.Yield y, @Nonnull String id,
                                   @Nonnull String label, @Nonnull List<Finding> out) {
        if (y.getBase() != null && y.getBase() <= 0) {
            out.add(Finding.warning(DOMAIN, "YIELD_NONPOSITIVE_BASE",
                    label + " Yield.Base is not positive - it is ignored and each conversion's"
                            + " own authored output quantity is used instead", id));
        }
        if (y.getScale() != null && (!Double.isFinite(y.getScale()) || y.getScale() <= 0.0)) {
            out.add(Finding.warning(DOMAIN, "YIELD_NONPOSITIVE_SCALE",
                    label + " Yield.Scale is not a positive finite number - it reader-defaults to 1.0", id));
        }
        if (y.getMin() != null && y.getMax() != null && y.getMin() > y.getMax()) {
            out.add(Finding.warning(DOMAIN, "YIELD_MIN_ABOVE_MAX",
                    label + " Yield.Min (" + y.getMin() + ") exceeds Max (" + y.getMax()
                            + ") - Max wins, so every cycle produces exactly Max", id));
        }
    }

    /**
     * An action's {@code ContributionScale} ladder: the same shape checks a loot {@code Ladder} gets,
     * since both resolve through the ONE shared {@code loot.FactorLadder} core. Warn-only - an
     * unreachable or empty ladder simply resolves to the neutral 1.0.
     */
    private static void checkContributionScale(@Nonnull ContributionScale scale, @Nonnull String id,
                                               @Nonnull String label, @Nonnull Predicate<String> factorKnown,
                                               @Nonnull List<Finding> out) {
        boolean hasFactors = scale.getFactors() != null && scale.getFactors().length > 0;
        ContributionScale.Floor[] floors = scale.getFloors();
        boolean hasFloors = floors != null && floors.length > 0;
        if (!hasFactors && !hasFloors) {
            out.add(Finding.warning(DOMAIN, "CONTRIBUTION_SCALE_EMPTY",
                    label + " authors neither Factors nor Floors - the whole group resolves to the"
                            + " neutral 1.0 and can be removed", id));
            return;
        }
        if (hasFloors && !hasFactors) {
            out.add(Finding.warning(DOMAIN, "CONTRIBUTION_SCALE_FLOORS_WITHOUT_FACTORS",
                    label + " authors Floors but no Factors - the ladder value is a constant 0, so"
                            + " only a Min<=0 floor can ever be reached", id));
        }
        if (hasFactors && !hasFloors) {
            out.add(Finding.warning(DOMAIN, "CONTRIBUTION_SCALE_FACTORS_WITHOUT_FLOORS",
                    label + " authors Factors but no Floors - nothing consumes the summed value, so"
                            + " the multiplier is always the neutral 1.0", id));
        }
        checkFactorTerms(scale.getFactors(), label + ".Factors", id, factorKnown, out);
        if (hasFloors) {
            double[] mins = new double[floors.length];
            for (int i2 = 0; i2 < floors.length; i2++) {
                mins[i2] = floors[i2] != null ? floors[i2].effectiveMin() : Double.NaN;
            }
            warnDuplicateMins(mins, label, id, out);
        }
    }

    /**
     * Two ladder floors sharing a {@code Min} is always an authoring slip: only ONE of them can ever
     * be the reached floor (the LAST authored wins, per the shared {@code loot.FactorLadder} rule),
     * so the other silently never grants. Warned for both ladder consumers from their own overload.
     */
    private static void checkDuplicateFloorMins(@Nullable Roll.Ladder.Floor[] floors, @Nonnull String label,
                                                @Nonnull String id, @Nonnull List<Finding> out) {
        if (floors == null) {
            return;
        }
        double[] mins = new double[floors.length];
        for (int i = 0; i < floors.length; i++) {
            mins[i] = floors[i] != null ? floors[i].effectiveMin() : Double.NaN;
        }
        warnDuplicateMins(mins, label, id, out);
    }

    /** The shared duplicate-threshold report both ladder consumers feed. */
    private static void warnDuplicateMins(@Nonnull double[] mins, @Nonnull String label, @Nonnull String id,
                                          @Nonnull List<Finding> out) {
        Set<Double> seen = new HashSet<>();
        Set<Double> reported = new HashSet<>();
        for (double min : mins) {
            if (Double.isNaN(min)) {
                continue;
            }
            if (!seen.add(min) && reported.add(min)) {
                out.add(Finding.warning(DOMAIN, "LADDER_DUPLICATE_FLOOR_MIN",
                        label + " authors more than one floor at Min " + min + " - only the LAST authored one"
                                + " can ever be reached, so the earlier duplicate never grants", id));
            }
        }
    }

    /**
     * {@code Recipe.FromCrafting} coverage. The live deriver ({@code
     * StationRecipeDeriver.deriveFromCrafting}) matches on {@code Categories} OR {@code Benches}
     * (seam wave decision 51c), filtered by {@code Types}, and bakes the {@code NativeTime}
     * transform into each derived conversion's {@code DurationMs} (decision 52), so a station
     * scoping by {@code Benches} alone derives a live conversion - the only hard error left is
     * authoring NEITHER {@code Categories} NOR {@code Benches} (nothing to derive from).
     */
    private static void checkFromCrafting(@Nonnull StationAsset.FromCrafting fc, @Nonnull String id,
                                          @Nonnull String label, @Nonnull List<Finding> out) {
        boolean hasCategories = hasNonBlank(fc.getCategories());
        boolean hasBenches = hasNonBlank(fc.getBenches());
        if (!hasCategories && !hasBenches) {
            out.add(Finding.error(DOMAIN, "FROMCRAFTING_NO_CATEGORIES",
                    label + " Recipe.FromCrafting has neither non-blank Categories nor Benches - it can derive nothing", id));
        }
        if (fc.getBenches() != null) {
            for (String bench : fc.getBenches()) {
                if (bench != null && !bench.isBlank() && !benchIdKnownLive(bench)) {
                    out.add(Finding.info(DOMAIN, "FROMCRAFTING_UNKNOWN_BENCH",
                            label + " Recipe.FromCrafting.Benches '" + bench + "' does not match any live native"
                                    + " BenchRequirement.Id - Bench ids are open (unregistered) strings, so this is"
                                    + " a best-effort typo check, not a definitive registry lookup", id));
                }
            }
        }
        if (fc.getTypes() != null) {
            for (String type : fc.getTypes()) {
                if (type != null && !type.isBlank()
                        && !StationAsset.FromCrafting.TYPE_CRAFTING.equalsIgnoreCase(type)
                        && !StationAsset.FromCrafting.TYPE_PROCESSING.equalsIgnoreCase(type)) {
                    out.add(Finding.warning(DOMAIN, "FROMCRAFTING_UNKNOWN_TYPE",
                            label + " Recipe.FromCrafting.Types '" + type
                                    + "' is not one of Crafting/Processing", id));
                }
            }
        }
        StationAsset.FromCrafting.NativeTime nativeTime = fc.getNativeTime();
        if (nativeTime != null) {
            if (nativeTime.getScale() != null && nativeTime.getScale() <= 0) {
                out.add(Finding.warning(DOMAIN, "FROMCRAFTING_NATIVETIME_NONPOSITIVE_SCALE",
                        label + " Recipe.FromCrafting.NativeTime.Scale is not positive ("
                                + nativeTime.getScale() + ") - the reader defaults it to "
                                + StationAsset.FromCrafting.NativeTime.DEFAULT_SCALE, id));
            }
            if (nativeTime.getOffsetMs() != null && nativeTime.getOffsetMs() < 0) {
                out.add(Finding.warning(DOMAIN, "FROMCRAFTING_NATIVETIME_NEGATIVE_OFFSET",
                        label + " Recipe.FromCrafting.NativeTime.OffsetMs is negative ("
                                + nativeTime.getOffsetMs() + ") - the reader defaults it to "
                                + StationAsset.FromCrafting.NativeTime.DEFAULT_OFFSET_MS, id));
            }
        }
    }

    /**
     * Per-conversion structure over the decision-73 {@code Ingredient[]} Input/Output arrays: EVERY
     * input entry must carry exactly one route and a positive quantity, EVERY output entry must be an
     * exact item id. The duplicate-input check keys on the conversion's PRIMARY (first) input, which
     * is what the runnable scan's first-match-wins ordering actually turns on.
     */
    private static void checkConversions(@Nonnull StationAsset.Conversion[] conversions, @Nonnull String id,
                                         @Nonnull String label, @Nonnull List<Finding> out) {
        Set<String> seenInputs = new HashSet<>();
        for (int i = 0; i < conversions.length; i++) {
            StationAsset.Conversion c = conversions[i];
            String cLabel = label + " conversion[" + i + "]";
            if (c == null || c.getInput() == null || c.getInput().length == 0) {
                out.add(Finding.error(DOMAIN, "MISSING_CONVERSION_INPUT",
                        cLabel + " has no Input", id));
                continue;
            }
            boolean inputsOk = true;
            for (Ingredient in : c.getInput()) {
                boolean hasItemId = in != null && in.getItemId() != null && !in.getItemId().isBlank();
                boolean hasResource = in != null && in.getResourceTypeId() != null
                        && !in.getResourceTypeId().isBlank();
                if (!hasItemId && !hasResource) {
                    out.add(Finding.error(DOMAIN, "MISSING_CONVERSION_INPUT",
                            cLabel + " has an Input entry with neither ItemId nor ResourceTypeId", id));
                    inputsOk = false;
                    break;
                }
                if (hasItemId && hasResource) {
                    out.add(Finding.error(DOMAIN, "AMBIGUOUS_CONVERSION_INPUT",
                            cLabel + " has an Input entry setting both ItemId and ResourceTypeId (exactly one is required)", id));
                    inputsOk = false;
                    break;
                }
                if (in.getQuantity() != null && in.getQuantity() <= 0) {
                    out.add(Finding.error(DOMAIN, "NONPOSITIVE_CONVERSION_COUNT",
                            cLabel + " has a nonpositive Input Quantity", id));
                }
            }
            if (!inputsOk) {
                continue;
            }
            Ingredient[] outputs = c.getOutput();
            if (outputs == null || outputs.length == 0) {
                out.add(Finding.error(DOMAIN, "MISSING_CONVERSION_OUTPUT",
                        cLabel + " has no Output", id));
                continue;
            }
            boolean outputsOk = true;
            for (Ingredient outIng : outputs) {
                if (outIng == null || outIng.getItemId() == null || outIng.getItemId().isBlank()) {
                    out.add(Finding.error(DOMAIN, "MISSING_CONVERSION_OUTPUT",
                            cLabel + " has an Output entry with no ItemId", id));
                    outputsOk = false;
                    break;
                }
                if (outIng.getResourceTypeId() != null && !outIng.getResourceTypeId().isBlank()) {
                    out.add(Finding.warning(DOMAIN, "OUTPUT_RESOURCE_TYPE",
                            cLabel + " has an Output entry setting ResourceTypeId; an output must be an exact ItemId (the ResourceTypeId is ignored)", id));
                }
                if (outIng.getQuantity() != null && outIng.getQuantity() <= 0) {
                    out.add(Finding.error(DOMAIN, "NONPOSITIVE_CONVERSION_COUNT",
                            cLabel + " has a nonpositive Output Quantity", id));
                }
            }
            if (!outputsOk) {
                continue;
            }
            Ingredient primary = c.primaryInput();
            String inputRef = primary.getResourceTypeId() != null && !primary.getResourceTypeId().isBlank()
                    ? primary.getResourceTypeId() : primary.getItemId();
            if (!seenInputs.add(inputRef.toLowerCase(Locale.ROOT))) {
                out.add(Finding.warning(DOMAIN, "DUPLICATE_CONVERSION_INPUT",
                        cLabel + " repeats input '" + inputRef
                                + "' (first match wins; this entry is dead)", id));
            }
        }
    }

    private static void checkWork(@Nullable StationAsset.Work work, @Nonnull String id, @Nonnull String label,
                                  @Nonnull List<Finding> out) {
        if (work == null) {
            return;
        }
        if (work.getCycleMs() != null && work.getCycleMs() <= 0) {
            out.add(Finding.error(DOMAIN, "NONPOSITIVE_CYCLE_MS",
                    label + " has a nonpositive Work.CycleMs", id));
        }
        checkIdle(work.getIdle(), effectiveCycleMs(work), id, label, out);
        Contribution[] posts = work.getPerCycleContributions();
        if (posts == null) {
            return;
        }
        checkContributionChannels(posts, label + " Work.PerCycleContributions", id, out);
        for (int i = 0; i < posts.length; i++) {
            Contribution post = posts[i];
            if (post == null) {
                continue;
            }
            if (post.getChannel() == null || post.getChannel().isBlank()) {
                out.add(Finding.warning(DOMAIN, "MISSING_CONTRIBUTION_CHANNEL",
                        label + " has a Work.PerCycleContributions entry with no Channel", id));
                continue;
            }
            if (post.getAmount() != null && post.getAmount() <= 0) {
                out.add(Finding.warning(DOMAIN, "NONPOSITIVE_CONTRIBUTION_AMOUNT",
                        label + " Work.PerCycleContributions[" + i + "].Amount should be positive for "
                                + describeContribution(post) + " (the entry posts nothing)", id));
            }
        }
    }

    /** The cycle cadence a {@code Work} group actually runs at: its own {@code CycleMs}, else the engine default. */
    private static long effectiveCycleMs(@Nonnull StationAsset.Work work) {
        return work.getCycleMs() != null && work.getCycleMs() > 0
                ? work.getCycleMs() : StationService.DEFAULT_CYCLE_MS;
    }

    /**
     * The {@code Moments.Cycle} sibling of {@link #checkImpact}'s {@code IMPACT_OVERLAPS_NEXT_SWING}:
     * a cycle cue held for at least a whole cycle never lands inside the cycle that emitted it,
     * because the next completed cycle re-plays the same moment first - so the offset reads as a cue
     * that belongs to the wrong cycle rather than as a late one.
     *
     * <p>Checked only where {@code Work.CycleMs} is genuinely what paces the cycle: a REPEATING
     * action running the implicit convert loop. An action running an authored {@code Steps} program
     * is paced by its own step durations instead (so its cadence is not readable here), and a
     * non-looping action completes its session on its single cycle, leaving no next cycle to overlap.
     */
    private static void checkCycleMomentDelay(@Nullable StationAsset.Work work, @Nullable Presentation cycle,
                                              boolean authoredStepProgram, @Nonnull String label,
                                              @Nonnull String id, @Nonnull List<Finding> out) {
        if (cycle == null || work == null || authoredStepProgram || !work.effectiveLooping()) {
            return;
        }
        long delayMs = cycle.effectiveDelayMs();
        long cycleMs = effectiveCycleMs(work);
        if (delayMs > 0 && delayMs >= cycleMs) {
            out.add(Finding.warning(DOMAIN, "CYCLE_DELAY_OVERLAPS_NEXT_CYCLE",
                    label + " Moments.Cycle DelayMs " + delayMs + " is >= the action's effective Work.CycleMs "
                            + cycleMs + " (the held cue lands at or after the next completed cycle replays the"
                            + " same moment, so it reads as belonging to the wrong cycle); keep the delay"
                            + " comfortably under one cycle", id));
        }
    }

    /**
     * The per-step sibling of the two rules above: a step whose own {@code Presentation} is held for
     * at least as long as the step's authored hold plays its cue after that step has already handed
     * over, so the cue lands on top of whatever the program moved on to. Only checked when the step
     * authors a {@code Duration} - a step with no hold has no window for the cue to land inside, and
     * a delay there is a deliberate offset into the steps that follow.
     */
    private static void checkStepPresentationDelay(@Nonnull StationStep step, @Nonnull String stepLabel,
                                                   @Nonnull String id, @Nonnull List<Finding> out) {
        Presentation presentation = step.getPresentation();
        StationStep.Duration duration = step.getDuration();
        if (presentation == null || duration == null || duration.getMs() == null) {
            return;
        }
        long delayMs = presentation.effectiveDelayMs();
        long holdMs = duration.effectiveMs();
        if (delayMs > 0 && holdMs > 0 && delayMs >= holdMs) {
            out.add(Finding.warning(DOMAIN, "STEP_DELAY_OVERLAPS_ITS_DURATION",
                    stepLabel + ".Presentation DelayMs " + delayMs + " is >= the step's own Duration.Ms "
                            + holdMs + " (the held cue plays after this step has already handed over, landing"
                            + " over whatever the program does next); lower the delay or lengthen the hold", id));
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
        if (idle.getFraction() != null && (idle.getFraction() > 0.25 || idle.getFraction() <= 0)) {
            out.add(Finding.warning(DOMAIN, "IDLE_FRACTION_RANGE",
                    label + " authors a Work.Idle.Fraction outside the tiny-value contract (0, 0.25]", id));
        }
    }

    /**
     * The shared {@code Requires} check, run against BOTH the station's entry gate and each
     * action's own (the two are ANDed at engage, neither defaults the other): a {@code FactorCondition}
     * referencing an unregistered factor id warns ({@code UNKNOWN_FACTOR} - fail-open at validate
     * time since providers may register later, matching {@link #validate()}'s live entry point). No
     * permission-existence check is possible (permission nodes are free text).
     */
    private static void checkRequiresGroup(@Nullable Requires reqs, @Nonnull String id, @Nonnull String label,
                                           @Nonnull Predicate<String> factorKnown, @Nonnull List<Finding> out) {
        if (reqs == null || reqs.getConditions() == null) {
            return;
        }
        for (FactorCondition c : reqs.getConditions()) {
            if (c == null || c.getFactor() == null || c.getFactor().isBlank()) {
                continue;
            }
            if (!factorKnown.test(c.getFactor())) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_FACTOR",
                        label + ".Conditions references unknown factor '" + c.getFactor()
                                + "' (the gate fails closed at runtime until a provider registers it)", id));
            }
        }
    }

    private static void checkPresentationRefs(@Nullable StationAsset.Animation animation,
                                              @Nullable StationAsset.Hold hold, @Nonnull String id,
                                              @Nonnull String label, @Nonnull List<Finding> out) {
        String emoteId = animation != null ? animation.getEmoteId() : null;
        if (emoteId != null && emoteId.isBlank()) {
            out.add(Finding.warning(DOMAIN, "BLANK_EMOTE_ID",
                    label + " authors an empty Animation.EmoteId", id));
        } else if (notBlank(emoteId) && !emoteKnownLive(emoteId)) {
            out.add(Finding.info(DOMAIN, "PRESENTATION_UNKNOWN_EMOTE",
                    label + " Animation.EmoteId '" + emoteId + "' is not a known Emote id - check for a typo", id));
        }
        String holdEffectId = hold != null ? hold.getEffectId() : null;
        if (holdEffectId != null && holdEffectId.isBlank()) {
            out.add(Finding.warning(DOMAIN, "BLANK_EFFECT_ID",
                    label + " authors an empty Hold.EffectId", id));
        } else if (notBlank(holdEffectId) && !entityEffectKnownLive(holdEffectId)) {
            out.add(Finding.warning(DOMAIN, "UNKNOWN_ENTITY_EFFECT",
                    label + " Hold.EffectId '" + holdEffectId + "' references unknown EntityEffect", id));
        }
    }

    private static void checkAnimation(@Nullable StationAsset.Animation animation, @Nonnull String id,
                                       @Nonnull String label, @Nonnull List<Finding> out) {
        if (animation == null) {
            return;
        }
        StationAsset.Animation.Swing swing = animation.getSwing();
        if (swing == null) {
            if (animation.getActionClip() != null && !animation.getActionClip().isBlank()) {
                out.add(Finding.warning(DOMAIN, "ACTION_CLIP_WITHOUT_SWING",
                        label + " authors Animation.ActionClip with no Animation.Swing group;"
                                + " the Action-slot re-fire only happens per swing tick, so ActionClip never fires", id));
            }
            return;
        }
        String swingLabel = label + " Worker.Animation.Swing";
        if (swing.getIntervalMs() == null || swing.getIntervalMs() <= 0) {
            out.add(Finding.error(DOMAIN, "NONPOSITIVE_SWING_INTERVAL",
                    swingLabel + " has a null or nonpositive IntervalMs - the swing timer stays off", id));
        } else if (swing.getIntervalMs() < 250) {
            out.add(Finding.warning(DOMAIN, "SWING_INTERVAL_SPAM",
                    swingLabel + " has an IntervalMs under 250ms (sound spam; faster than any vanilla swing clip)", id));
        }
        if (animation.getEmoteId() == null || animation.getEmoteId().isBlank()) {
            // ADVISORY, not a mistake: a swing with no EmoteId is the Action-slot route, which is
            // the normal authoring choice (EmoteId is the opt-in full-body override). The note
            // states which clip actually plays, since that depends on how the worker is held.
            out.add(Finding.info(DOMAIN, "SWING_WITHOUT_EMOTE",
                    swingLabel + " authors no Animation.EmoteId: a SEAT-mounted or PUPPET worker rides"
                            + " the Action-slot clip against the held item's own animation set"
                            + " (Animation.ActionClip, defaulting to 'Chop'), while an effect-mode"
                            + " session plays no clip at all and the swing is a pure sound/particle cue", id));
        }
    }

    /**
     * The timing sibling of {@code CYCLE_DELAY_OVERLAPS_NEXT_CYCLE}/
     * {@code STEP_DELAY_OVERLAPS_ITS_DURATION}, over the {@code impact} moment: a strike cue held
     * for at least a whole swing interval lands on (or after) the swing that replays the same
     * moment, so it reads as belonging to the wrong swing.
     */
    private static void checkImpactMomentDelay(@Nullable StationAsset.Animation animation,
                                               @Nullable Map<String, Presentation> moments,
                                               @Nonnull String label, @Nonnull String id,
                                               @Nonnull List<Finding> out) {
        StationAsset.Animation.Swing swing = animation != null ? animation.getSwing() : null;
        Long intervalMs = swing != null ? swing.getIntervalMs() : null;
        Presentation impact = moment(moments, StationFlairs.MOMENT_IMPACT);
        if (impact == null || intervalMs == null || intervalMs <= 0) {
            return;
        }
        long delayMs = impact.effectiveDelayMs();
        if (delayMs > 0 && delayMs >= intervalMs) {
            out.add(Finding.warning(DOMAIN, "IMPACT_OVERLAPS_NEXT_SWING",
                    label + " Moments impact DelayMs " + delayMs + " is >= Animation.Swing.IntervalMs "
                            + intervalMs + " (the held strike cue lands at or after the next swing replays the"
                            + " same moment); keep the delay comfortably under one swing", id));
        }
    }

    /**
     * The {@code rare_find} moment is the one well-known id an ACTION cannot author: it is only ever
     * emitted WITH the earning {@code Roll}/{@code Ladder.Floor} cue already in hand, and the
     * site-supplied presentation always outranks the map entry, so an entry keyed by it decodes,
     * reads as a known moment, and then never plays. Warn-only, and deliberately not part of the
     * shared map walk - a FLAIR keyed {@code rare_find} is meaningful (it overlays the earning cue).
     */
    private static void checkRareFindNotActionAuthored(@Nullable Map<String, Presentation> moments,
                                                       @Nonnull String label, @Nonnull String id,
                                                       @Nonnull List<Finding> out) {
        if (moment(moments, StationFlairs.MOMENT_RARE_FIND) == null) {
            return;
        }
        out.add(Finding.warning(DOMAIN, "RARE_FIND_MOMENT_NEVER_PLAYS",
                label + " Moments authors rare_find, which an action can never supply - that cue comes"
                        + " from the Roll or Ladder.Floor that earned it. Move the presentation onto the"
                        + " roll/floor (a flair can still overlay it under the rare_find id)", id));
    }

    /**
     * One entry of an action's own {@code Moments} map, matched case-insensitively - the same
     * lookup rule the engine applies at play time, so a validator finding and a runtime emission
     * never disagree about which key resolved.
     */
    @Nullable
    private static Presentation moment(@Nullable Map<String, Presentation> moments, @Nonnull String momentId) {
        if (moments == null) {
            return null;
        }
        for (Map.Entry<String, Presentation> e : moments.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(momentId)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static void checkCamera(@Nullable StationAsset.Camera camera, @Nullable StationAsset.Hold hold,
                                    @Nonnull String id, @Nonnull String label, @Nonnull List<Finding> out) {
        if (camera == null) {
            return;
        }
        boolean fixedLook = camera.hasRecipe();
        if (fixedLook && !camera.effectiveEnabled()) {
            out.add(Finding.warning(DOMAIN, "CAMERA_RECIPE_WITHOUT_CAMERA",
                    label + " authors a Camera.Recipe with Camera.Enabled false - the fixed look can never take effect", id));
        }
        String recipe = camera.getRecipe();
        if (fixedLook && StationCameraPreset.fromId(recipe) == null) {
            out.add(Finding.warning(DOMAIN, "UNKNOWN_CAMERA_RECIPE",
                    label + " authors Camera.Recipe '" + recipe
                            + "' which is not a known camera preset id - falls back to '"
                            + StationCameraPreset.LOOK_ROT.id() + "' at runtime", id));
        }
        StationAsset.Hold.Mount mount = hold != null ? hold.getMount() : null;
        if (mount != null && fixedLook) {
            out.add(Finding.warning(DOMAIN, "MOUNT_FACE_BLOCK_CONFLICT",
                    label + " authors both Hold.Mount (a native Block or Entity mount) and a Camera.Recipe"
                            + " fixed look - the mount already locks facing while keeping the camera free; the"
                            + " packet-level look lock on top is redundant (or conflicting) with it", id));
        }
    }

    /**
     * The Mount knob family (design section 9.2, phase 2 leg D): an unrecognized
     * {@code Surface} value, an {@code Entity} group authored under a Block surface (ignored at
     * runtime), and the untested {@code Steerable true} combo - all warn-only, per the maintainer
     * ruling ("validator warns on odd combos, never blocks").
     */
    private static void checkMount(@Nullable StationAsset.Hold hold, @Nonnull String id, @Nonnull String label,
                                   @Nonnull List<Finding> out) {
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
        checkMomentsMap(moments, label + " Moments", id, out);
    }

    /**
     * The ONE {@code momentId -> Presentation} map walk, shared by BOTH map-shaped moment surfaces
     * in this schema - an action's own {@code Moments} and a flair's - because they key by the exact
     * same open vocabulary and a finding phrased for one reads correctly for the other. A blank key
     * warns; an unrecognized one (typo'd against the 5 well-known ids or the {@code step:} prefix -
     * {@link StationFlairs#isKnownMomentId}) warns ONLY, never blocks: a future engine moment must
     * not fail an older pack's validation. Each authored Presentation then gets the standard
     * native-reference advisories.
     */
    private static void checkMomentsMap(@Nonnull Map<String, Presentation> moments, @Nonnull String label,
                                        @Nonnull String id, @Nonnull List<Finding> out) {
        for (Map.Entry<String, Presentation> entry : moments.entrySet()) {
            String momentId = entry.getKey();
            if (momentId == null || momentId.isBlank()) {
                out.add(Finding.warning(DOMAIN, "BLANK_MOMENT_ID",
                        label + " has a blank moment id", id));
                continue;
            }
            if (!StationFlairs.isKnownMomentId(momentId)) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_MOMENT_ID",
                        label + "['" + momentId + "'] is not a recognized moment id (cycle/swing/impact/"
                                + "rare_find/completion, or a step:<actionId>:<stepId> id) - check for a typo", id));
            }
            checkNativeRefs(entry.getValue(), label + "['" + momentId + "']", id, out);
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
     * The station's ORDERED {@code Actions} list: the id contract ({@code ACTION_MISSING_ID}/
     * {@code ACTION_DUPLICATE_ID}), the selection-order contract ({@code UNREACHABLE_ACTION}/
     * {@code AMBIGUOUS_ACTION_INPUT} - authored order IS priority, so a later action shadowed by an
     * earlier one can never run), the {@code Ref} reference, and every per-body check via
     * {@link #checkActionBody} (the SAME core {@link #validateActionAssets} runs on a standalone
     * {@link ActionAsset}).
     *
     * <p><b>The one ERROR here</b> is a station with no actions at all: every group that makes a
     * station DO something lives inside an action, so an empty list is an inert station rather than
     * an odd combination, and the never-block posture does not extend to content that cannot run.
     */
    private static void checkActions(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
            @Nonnull Predicate<String> dropListKnown, @Nonnull Predicate<String> factorKnown,
            @Nonnull Predicate<String> lootableKnown, @Nonnull Predicate<String> rollPoolKnown,
            @Nonnull Predicate<String> modelKnown, @Nonnull Predicate<String> stationKnown,
            @Nonnull Predicate<String> actionAssetKnown, @Nonnull List<Finding> out) {
        ActionDef[] actions = a.getActions();
        if (actions == null || actions.length == 0) {
            out.add(Finding.error(DOMAIN, "STATION_NO_ACTIONS",
                    label + " authors no Actions - every job a station offers lives in an action, so"
                            + " this station can never run a cycle", id));
            return;
        }
        boolean sawCatchAll = false;
        Set<String> seenActionIds = new HashSet<>();
        Set<String> seenItemIds = new HashSet<>();
        Set<String> seenResourceTypeIds = new HashSet<>();
        for (int index = 0; index < actions.length; index++) {
            ActionDef def = actions[index];
            String actionId = ActionResolver.effectiveActionId(def, index);
            String actionLabel = label + " Actions[" + actionId + "]";
            if (def == null) {
                out.add(Finding.warning(DOMAIN, "EMPTY_ACTION_ENTRY", actionLabel + " has no body", id));
                continue;
            }
            if (def.getId() == null || def.getId().isBlank()) {
                out.add(Finding.warning(DOMAIN, "ACTION_MISSING_ID",
                        actionLabel + " authors no Id - the engine falls back to its Ref id or its"
                                + " position, but only an explicit Id can be targeted by an Extension"
                                + " or a step insertion", id));
            }
            if (!seenActionIds.add(actionId.toLowerCase(Locale.ROOT))) {
                out.add(Finding.warning(DOMAIN, "ACTION_DUPLICATE_ID",
                        actionLabel + " repeats an earlier action's Id - ids match case-insensitively,"
                                + " so every lookup resolves the FIRST one and this entry is unreachable", id));
            }
            if (def.hasRef() && !actionAssetKnown.test(def.getRef().toLowerCase(Locale.ROOT))) {
                out.add(Finding.warning(DOMAIN, "ACTION_REF_UNKNOWN",
                        actionLabel + " Ref '" + def.getRef() + "' does not resolve to a known ActionAsset - "
                                + "engage will deny with ui.station.action_unavailable", id));
            }
            ActionInput select = def.getSelect();
            boolean catchAll = select == null || select.isCatchAll();
            if (catchAll) {
                if (sawCatchAll) {
                    out.add(Finding.warning(DOMAIN, "UNREACHABLE_ACTION",
                            actionLabel + " authors no Select matcher (or an all-blank one) AFTER an"
                                    + " earlier catch-all action - authored order IS selection priority,"
                                    + " so this action can never be reached", id));
                }
                sawCatchAll = true;
            } else {
                // AMBIGUOUS_ACTION_INPUT: an exact ItemId/ResourceTypeId collision with an EARLIER
                // action - "first match wins" means this action's matching route is unreachable via
                // that exact id (a Tags/Function overlap is not flagged - too fuzzy to call an
                // authoring mistake outright, so this stays a targeted check).
                String itemId = select.getItemId();
                if (itemId != null && !itemId.isBlank() && !seenItemIds.add(itemId.toLowerCase(Locale.ROOT))) {
                    out.add(Finding.warning(DOMAIN, "AMBIGUOUS_ACTION_INPUT",
                            actionLabel + " Select.ItemId '" + itemId + "' repeats an earlier action's exact"
                                    + " ItemId - 'first match wins' makes this route unreachable via that id", id));
                }
                String resourceTypeId = select.getResourceTypeId();
                if (resourceTypeId != null && !resourceTypeId.isBlank()
                        && !seenResourceTypeIds.add(resourceTypeId.toLowerCase(Locale.ROOT))) {
                    out.add(Finding.warning(DOMAIN, "AMBIGUOUS_ACTION_INPUT",
                            actionLabel + " Select.ResourceTypeId '" + resourceTypeId + "' repeats an earlier"
                                    + " action's exact ResourceTypeId - 'first match wins' makes this route"
                                    + " unreachable via that id", id));
                }
            }
            String function = select != null ? select.getFunction() : null;
            if (function != null && !function.isBlank() && !isKnownFunction(function)) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_ACTION_FUNCTION",
                        actionLabel + " Select.Function '" + function
                                + "' is not one of Weapon/Armor/Tool", id));
            }

            checkActionBody(def, actionLabel, id, dropListKnown, factorKnown, lootableKnown, rollPoolKnown,
                    modelKnown, stationKnown, out);
        }
    }

    /**
     * The per-action BODY core, shared by an inline {@code Actions} entry ({@link #checkActions}) AND
     * a standalone {@link ActionAsset} ({@link #validateActionAssets}) - one check, two authoring
     * sites. An action is SELF-CONTAINED, so every group it is checked against is its own; there is
     * no caller-resolved fallback to thread in any more.
     */
    private static void checkActionBody(@Nonnull ActionDef def, @Nonnull String actionLabel, @Nonnull String id,
            @Nonnull Predicate<String> dropListKnown, @Nonnull Predicate<String> factorKnown,
            @Nonnull Predicate<String> lootableKnown, @Nonnull Predicate<String> rollPoolKnown,
            @Nonnull Predicate<String> modelKnown, @Nonnull Predicate<String> stationKnown,
            @Nonnull List<Finding> out) {
        boolean hasBody = def.hasRef() || def.getRecipe() != null
                || (def.getSteps() != null && def.getSteps().length > 0);
        if (!hasBody) {
            out.add(Finding.warning(DOMAIN, "ACTION_NO_BODY",
                    actionLabel + " authors neither Ref, Recipe (for the implicit convert-loop program),"
                            + " nor Steps - this action can never run a cycle", id));
        }
        ActionDef.Worker worker = def.getWorker();
        Map<String, Presentation> moments = def.getMoments();
        StationAsset.Hold hold = worker != null ? worker.getHold() : null;
        StationAsset.Animation animation = worker != null ? worker.getAnimation() : null;

        // An action running an authored Steps program has no single cycle output, so a
        // Grants.OutputItems anywhere under it is dropped at runtime - see checkGrants. Read off the
        // EFFECTIVE program (its own Steps, else the Ref'd ActionAsset base's, the runtime's own
        // rule) rather than the entry's own array: an inline entry that Refs a stepped action and
        // overrides only Bonus runs that program too, and used to escape this warning entirely.
        StationStep[] effectiveSteps = ActionResolver.effectiveStepsOf(def);
        boolean noCycleOutput = effectiveSteps != null && effectiveSteps.length > 0;

        checkRecipe(def.getRecipe(), id, actionLabel, out);
        checkWork(def.getWork(), id, actionLabel, out);
        checkToolGroup(def.getTool(), id, actionLabel + " Tool", out);
        checkRequiresGroup(def.getRequires(), id, actionLabel + " Requires", factorKnown, out);
        if (def.getBonus() != null) {
            checkLootRef(def.getBonus(), id, actionLabel + " Bonus", noCycleOutput, dropListKnown, factorKnown,
                    lootableKnown, out);
        }
        if (def.getContributionScale() != null) {
            checkContributionScale(def.getContributionScale(), id, actionLabel + " ContributionScale",
                    factorKnown, out);
        }
        if (def.getCustody() != null) {
            checkCustody(def.getCustody(), def.getRecipe(), false, actionLabel, id, out);
        }
        if (worker != null) {
            checkAnimation(animation, id, actionLabel, out);
            checkPresentationRefs(animation, hold, id, actionLabel, out);
            checkCamera(worker.getCamera(), hold, id, actionLabel, out);
            checkMount(hold, id, actionLabel, out);
            checkPuppet(worker.getPuppet(), hold, actionLabel, id, modelKnown, out);
        }
        if (moments != null) {
            // The same open-vocabulary walk a flair's Moments map gets - typo detection plus the
            // per-Presentation native-reference advisories, on every moment id at once.
            checkMomentsMap(moments, actionLabel + " Moments", id, out);
            // Timing, not references: a cue held for the whole window it plays inside lands in the
            // next one. Completion is deliberately exempt - there is no next cycle for it to overlap.
            checkCycleMomentDelay(def.getWork(), moment(moments, StationFlairs.MOMENT_CYCLE), noCycleOutput,
                    actionLabel, id, out);
            checkImpactMomentDelay(animation, moments, actionLabel, id, out);
            checkRareFindNotActionAuthored(moments, actionLabel, id, out);
        }
        checkAnchorsMap(def.getAnchors(), actionLabel, id, stationKnown, out);
        Set<String> knownAnchorIds = new HashSet<>();
        if (def.getAnchors() != null) {
            for (String anchorId : def.getAnchors().keySet()) {
                if (anchorId != null && !anchorId.isBlank()) {
                    knownAnchorIds.add(anchorId.toLowerCase(Locale.ROOT));
                }
            }
        }
        StationStep[] steps = def.getSteps();
        if (steps != null && steps.length > 0) {
            Puppet puppet = worker != null ? worker.getPuppet() : null;
            boolean puppetActive = puppet != null && puppet.effectiveEnabled();
            checkSteps(steps, actionLabel, id, dropListKnown, factorKnown, lootableKnown, rollPoolKnown,
                    puppetActive, knownAnchorIds, out);
        }
    }

    private static boolean isKnownFunction(@Nonnull String function) {
        return "Weapon".equalsIgnoreCase(function) || "Armor".equalsIgnoreCase(function)
                || "Tool".equalsIgnoreCase(function);
    }

    /**
     * An action's own {@code Anchors} map coverage (scope-2 design 2.2): every declared anchor's
     * {@code Station} must be blank-free and resolve against {@code stationKnown} ({@code
     * ANCHOR_STATION_UNKNOWN} covers both a blank and an unresolved station id - the reserved
     * anchor id {@code "self"} is never declared here, it is implicit). A station that resolves but
     * that NO block item maps to gets the separate warn-only {@code ANCHOR_STATION_NOT_DISCOVERABLE}
     * (AV wave, see {@link #stationDiscoverableLive}) - it decodes fine and simply can never be found
     * in the world.
     */
    private static void checkAnchorsMap(@Nullable Map<String, ActionDef.Anchor> anchors, @Nonnull String label,
            @Nonnull String id, @Nonnull Predicate<String> stationKnown, @Nonnull List<Finding> out) {
        if (anchors == null || anchors.isEmpty()) {
            return;
        }
        for (Map.Entry<String, ActionDef.Anchor> entry : anchors.entrySet()) {
            String anchorId = entry.getKey() == null || entry.getKey().isBlank() ? "(blank)" : entry.getKey();
            ActionDef.Anchor anchor = entry.getValue();
            if (anchor == null) {
                continue;
            }
            String station = anchor.getStation();
            if (station == null || station.isBlank() || !stationKnown.test(station.toLowerCase(Locale.ROOT))) {
                out.add(Finding.warning(DOMAIN, "ANCHOR_STATION_UNKNOWN",
                        label + " Anchors['" + anchorId + "'] references unknown station '" + station + "'", id));
            } else if (!stationDiscoverableLive(station)) {
                out.add(Finding.warning(DOMAIN, "ANCHOR_STATION_NOT_DISCOVERABLE",
                        label + " Anchors['" + anchorId + "'] targets station '" + station + "', but no block item"
                                + " maps to it (no BlockType's Interactions.Use runs an rpg_station_use naming it) -"
                                + " the anchor is undiscoverable until a player interacts with such a block", id));
            }
        }
    }

    /**
     * The authored step-program coverage (design 9.3/9.5, scope-2 reshape decisions 34/38): the
     * ORTHOGONAL-PHASE {@link StationStep} record replaces the old {@code Type} union - this walk
     * checks base fields (duplicate {@code Id}s, an {@code OnConditionFail.Goto} referencing an
     * unknown sibling step id, a per-step {@code Puppet} override), then EVERY phase group present
     * on the step: {@code Consume}/{@code Produce} emptiness, a {@code Roll} phase's inline
     * {@link Roll}s through the SAME shared {@link #checkRoll} core every other Roll site uses (via
     * {@link #checkLootRef}), a {@code Repeat.Factors}/{@code Walk} check, and (design 9.5) a
     * {@code Stamp} phase's own coverage. Also flags {@code WALK_TARGET_UNKNOWN_ANCHOR}/
     * {@code STEP_AT_UNKNOWN_ANCHOR} (a {@code Walk.To}/{@code At} not matching {@code
     * knownAnchorIds} or the reserved {@code "self"}), and {@code WALK_REQUIRES_PUPPET} (any step
     * authoring {@code Walk} when the resolved Puppet is not active - flagged once per action).
     * The multi-station seam (Walk/At/Produce.To:Custody) EXECUTES, so there is no
     * warn gating those phases; the anchor/walk checks above are the live coverage.
     */
    private static void checkSteps(@Nonnull StationStep[] steps,
            @Nonnull String actionLabel, @Nonnull String id, @Nonnull Predicate<String> dropListKnown,
            @Nonnull Predicate<String> factorKnown, @Nonnull Predicate<String> lootableKnown,
            @Nonnull Predicate<String> rollPoolKnown, boolean puppetActive, @Nonnull Set<String> knownAnchorIds,
            @Nonnull List<Finding> out) {
        Set<String> seenIds = new HashSet<>();
        Set<String> knownIds = new HashSet<>();
        for (StationStep s : steps) {
            if (s != null && s.getId() != null && !s.getId().isBlank()) {
                knownIds.add(s.getId().toLowerCase(Locale.ROOT));
            }
        }
        boolean walkWithoutPuppetWarned = false;
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
                String clip = puppetOverride.getClip();
                if (notBlank(clip) && !emoteKnownLive(clip)) {
                    out.add(Finding.info(DOMAIN, "PRESENTATION_UNKNOWN_EMOTE",
                            stepLabel + ".Puppet.Clip '" + clip + "' is not a known Emote id - check for a typo", id));
                }
            }
            // Seam wave (decision 51b/51d): a step's own Presentation fires at iteration entry
            // (station/CLAUDE.md's per-step Presentation rule) - gets the SAME native-composition
            // advisory coverage every other Presentation site does.
            checkNativeRefs(step.getPresentation(), stepLabel + ".Presentation", id, out);
            checkStepPresentationDelay(step, stepLabel, id, out);

            StationStep.Repeat repeat = step.getRepeat();
            if (repeat != null) {
                checkFactorTerms(repeat.getFactors(), stepLabel + ".Repeat.Factors", id, factorKnown, out);
            }

            StationStep.Walk walk = step.getWalk();
            if (walk != null) {
                if (!puppetActive && !walkWithoutPuppetWarned) {
                    out.add(Finding.warning(DOMAIN, "WALK_REQUIRES_PUPPET",
                            actionLabel + " authors a Walk phase but the resolved Puppet is not active - Walk"
                                    + " moves the puppet, which does not exist without one; engage will deny", id));
                    walkWithoutPuppetWarned = true;
                }
                String to = walk.getTo();
                if (!isKnownAnchorTarget(to, knownAnchorIds)) {
                    out.add(Finding.warning(DOMAIN, "WALK_TARGET_UNKNOWN_ANCHOR",
                            stepLabel + ".Walk.To '" + to + "' is not a declared anchor id (or 'self')", id));
                }
            }
            String at = step.getAt();
            if (at != null && !at.isBlank() && !isKnownAnchorTarget(at, knownAnchorIds)) {
                out.add(Finding.warning(DOMAIN, "STEP_AT_UNKNOWN_ANCHOR",
                        stepLabel + ".At '" + at + "' is not a declared anchor id (or 'self')", id));
            }

            StationStep.Consume consume = step.getConsume();
            if (consume != null) {
                if (consume.isEmpty()) {
                    out.add(Finding.warning(DOMAIN, "CONSUME_STEP_EMPTY",
                            stepLabel + " authors a Consume phase with no Items", id));
                } else {
                    Map<String, Integer> consumeRefCounts = new LinkedHashMap<>();
                    for (Ingredient item : consume.getItems()) {
                        boolean hasItemId = item != null && item.getItemId() != null && !item.getItemId().isBlank();
                        boolean hasResourceTypeId = item != null && item.getResourceTypeId() != null
                                && !item.getResourceTypeId().isBlank();
                        if (!hasItemId && !hasResourceTypeId) {
                            out.add(Finding.warning(DOMAIN, "CONSUME_STEP_EMPTY",
                                    stepLabel + " Consume has an item with neither ItemId nor ResourceTypeId", id));
                        } else if (hasItemId && hasResourceTypeId) {
                            out.add(Finding.warning(DOMAIN, "AMBIGUOUS_CONVERSION_INPUT",
                                    stepLabel + " Consume has an item setting both ItemId and ResourceTypeId (exactly one is expected)", id));
                        } else {
                            String ref = hasResourceTypeId ? item.getResourceTypeId() : item.getItemId();
                            consumeRefCounts.merge(ref.toLowerCase(Locale.ROOT), 1, Integer::sum);
                        }
                    }
                    for (Map.Entry<String, Integer> e : consumeRefCounts.entrySet()) {
                        if (e.getValue() > 1) {
                            out.add(Finding.warning(DOMAIN, "CONSUME_DUPLICATE_ITEM_REF",
                                    stepLabel + " Consume authors '" + e.getKey() + "' across " + e.getValue()
                                            + " Items entries; their quantities are summed - combine them into one entry", id));
                        }
                    }
                }
            }
            StationStep.Produce produce = step.getProduce();
            if (produce != null) {
                if (produce.isEmpty()) {
                    out.add(Finding.warning(DOMAIN, "PRODUCE_STEP_EMPTY",
                            stepLabel + " authors a Produce phase with no Items", id));
                } else {
                    for (Ingredient item : produce.getItems()) {
                        if (item == null || item.getItemId() == null || item.getItemId().isBlank()) {
                            out.add(Finding.warning(DOMAIN, "PRODUCE_STEP_EMPTY",
                                    stepLabel + " Produce has an item with no ItemId", id));
                        }
                    }
                }
            }
            LootRef roll = step.getRoll();
            if (roll != null) {
                // Always true here: this walk only runs for an action that authors a Steps program.
                checkLootRef(roll, id, stepLabel + ".Roll", true, dropListKnown, factorKnown, lootableKnown, out);
            }
            StationStep.Stamp stamp = step.getStamp();
            if (stamp != null) {
                checkStamp(stamp, stepLabel, id, factorKnown, rollPoolKnown, out);
            }
        }
    }

    /** True when {@code value} is the reserved {@code "self"} anchor or a member of {@code knownAnchorIds}. */
    private static boolean isKnownAnchorTarget(@Nullable String value, @Nonnull Set<String> knownAnchorIds) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim();
        if (ActionDef.Anchor.RESERVED_SELF.equalsIgnoreCase(v)) {
            return true;
        }
        return knownAnchorIds.contains(v.toLowerCase(Locale.ROOT));
    }

    /**
     * A Stamp phase's own coverage (design 9.5, scope-2 3.8's {@code Budgets[]} reshape): no
     * {@code Reagents} (a free ritual - warn, not an error, some future station may genuinely want
     * that), a {@code Stats.Pool} reference to an unknown {@code RollPool}, each inline entry's
     * {@code Points.Factors} through the shared {@link #checkFactorTerms} core, and each
     * {@code Caps.Budgets[]} entry: {@code STAMP_BUDGET_BAD_ROUTE} when neither/both of the
     * exactly-one-of {@code {Points}}/{@code {PointsPer,Factors}} routes are authored (see {@link StampSpec.Budget#hasExactlyOneRoute()}'s own javadoc), {@code
     * STAMP_NONPOSITIVE_BUDGET} for a non-positive value on whichever route IS authored,
     * {@code STAMP_BUDGET_STRAY_FACTORS} for a {@code Factors[]} authored on a flat {@code Points}
     * route (silently ignored - only {@code PointsPer} engages {@code Factors}), and a
     * {@code Factors[]} unknown-factor check via {@link #checkFactorTerms} (the SAME
     * {@code UNKNOWN_FACTOR} code every other factor reference reports through - one code, one
     * meaning, never a per-site {@code STAMP_UNKNOWN_FACTOR} twin).
     */
    private static void checkStamp(@Nonnull StationStep.Stamp stamp, @Nonnull String stepLabel,
            @Nonnull String id, @Nonnull Predicate<String> factorKnown, @Nonnull Predicate<String> rollPoolKnown,
            @Nonnull List<Finding> out) {
        if (stamp.getReagents() == null || stamp.getReagents().length == 0) {
            out.add(Finding.warning(DOMAIN, "STAMP_NO_REAGENTS", stepLabel + " authors no Reagents (a free ritual)", id));
        }
        StampSpec stats = stamp.getStats();
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
        StatRollEntry[] entries = stats.getEntries();
        if ((entries == null || entries.length == 0) && (pool == null || pool.isBlank())) {
            out.add(Finding.warning(DOMAIN, "STAMP_STATS_NO_ENTRIES",
                    stepLabel + " authors Stats with neither Pool nor inline Entries", id));
        }
        if (entries != null) {
            for (int i = 0; i < entries.length; i++) {
                StatRollEntry e = entries[i];
                if (e != null && e.getPoints() != null) {
                    checkFactorTerms(e.getPoints().getFactors(),
                            stepLabel + ".Stats.Entries[" + i + "].Points.Factors", id, factorKnown, out);
                }
            }
        }
        StampSpec.Caps caps = stats.getCaps();
        if (caps == null) {
            return;
        }
        StampSpec.Budget[] budgets = caps.getBudgets();
        if (budgets != null) {
            for (int i = 0; i < budgets.length; i++) {
                StampSpec.Budget b = budgets[i];
                if (b == null) {
                    continue;
                }
                String bLabel = stepLabel + ".Caps.Budgets[" + i + "]";
                if (!b.hasExactlyOneRoute()) {
                    out.add(Finding.error(DOMAIN, "STAMP_BUDGET_BAD_ROUTE",
                            bLabel + " authors neither exactly a flat Points route nor a factor-scaled"
                                    + " PointsPer+Factors route (exactly one is required)", id));
                } else if (b.isFlat() && b.getPoints() != null && b.getPoints() <= 0.0) {
                    out.add(Finding.warning(DOMAIN, "STAMP_NONPOSITIVE_BUDGET",
                            bLabel + " Points is not positive (" + b.getPoints() + ")", id));
                } else if (b.isFactorScaled() && b.getPointsPer() != null && b.getPointsPer() <= 0.0) {
                    out.add(Finding.warning(DOMAIN, "STAMP_NONPOSITIVE_BUDGET",
                            bLabel + " PointsPer is not positive (" + b.getPointsPer() + ")", id));
                }
                // A flat {Points} route passes hasExactlyOneRoute() even with a stray Factors[] (only
                // PointsPer engages Factors), so the array would be silently dropped - warn the author (m3).
                if (b.isFlat() && b.getFactors() != null && b.getFactors().length > 0) {
                    out.add(Finding.warning(DOMAIN, "STAMP_BUDGET_STRAY_FACTORS",
                            bLabel + " authors Factors on a flat Points route - Factors is ignored"
                                    + " (author PointsPer to use them)", id));
                }
                if (b.isFactorScaled()) {
                    checkFactorTerms(b.getFactors(), bLabel + ".Factors", id, factorKnown, out);
                }
            }
        }
    }


    private static boolean notBlank(@Nullable String s) {
        return s != null && !s.isBlank();
    }

    // ==================== Reporting (thin delegators over the shared core) ====================

    /** The label every station report is filed under, in chat and in the log. */
    private static final String LABEL = "Station validation";

    @Nonnull
    public static String summarize(@Nonnull List<Finding> findings) {
        return ValidationReport.summarize(LABEL, findings);
    }

    public static int problemCount(@Nonnull List<Finding> findings) {
        return ValidationReport.problemCount(findings);
    }

    /**
     * The headline first, then every finding on its own line. An error is worth a WARN line in a
     * server log; a warning about a mod that may not be installed is not, so the shared core's own
     * error-versus-note split routes the two sinks.
     */
    private static void logReport(@Nonnull List<Finding> findings) {
        if (ValidationReport.problemCount(findings) > 0) {
            Log.warn(summarize(findings));
        } else {
            Log.info(summarize(findings));
        }
        ValidationReport.logAll(LABEL, findings, Log::warn, Log::info);
    }

    /**
     * Validate the live catalog (full set, incl. cross-layer reference checks) and log a summary
     * (+ per-finding detail). Never throws. Callers: {@code /rpgstations validate} (on-demand,
     * already post-load) and {@code RpgStationsPlugin}'s ONE deferred post-load audit (first
     * {@code PlayerReadyEvent}, D4 fix). Per-fold auto-logging uses {@link #runStructuralAndLog}
     * instead - see {@link #validateStructural}'s javadoc for why.
     *
     * <p><b>On moving this trigger later than first {@code PlayerReadyEvent}:</b> the ONLY other
     * candidate timing is {@code AllWorldsLoadedEvent} (it fires strictly before the first player
     * can connect, so it would let a pack author see the full findings without needing a client to
     * connect at all). The move is NOT safe to make on code inspection alone - it is gated on
     * empirically re-running this full pass under that earlier event and confirming none of this
     * validator's cross-layer reference checks (the ones structural-pass defers for exactly this
     * reason) false-positive there, since an asset layer that has not finished folding by that
     * point would manufacture a spurious finding. That confirmation needs a live server boot with
     * every installed pack layer present, which cannot be produced from a build/unit-test run -
     * until it has been run and shown clean, this method's caller stays on first
     * {@code PlayerReadyEvent}.
     */
    public static void runAndLog() {
        logReport(validate());
    }

    /**
     * Validate the live catalog (STRUCTURAL-only, D4 fix) and log a summary (+ per-finding
     * detail). Never throws. Safe to call from every per-fold {@code LoadedAssetsEvent} handler -
     * see {@link #validateStructural}'s javadoc.
     */
    public static void runStructuralAndLog() {
        logReport(validateStructural());
    }
}
