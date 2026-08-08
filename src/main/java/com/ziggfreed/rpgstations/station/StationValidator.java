package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.ziggfreed.rpgstations.api.FindingSink;
import com.ziggfreed.rpgstations.api.ValidationHook;
import com.ziggfreed.rpgstations.api.ValidationScope;
import com.ziggfreed.rpgstations.api.impl.ContributionChannelRegistryImpl;
import com.ziggfreed.rpgstations.api.impl.FactorRegistryImpl;
import com.ziggfreed.rpgstations.api.impl.ValidationHookRegistryImpl;
import com.ziggfreed.rpgstations.asset.ActionAsset;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.EffectRef;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.rpgstations.asset.FactorRef;
import com.ziggfreed.rpgstations.asset.FlairAsset;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.LootRef;
import com.ziggfreed.rpgstations.asset.LootableAsset;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StatRollEntry;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.i18n.RpgStationsLangKeys;
import com.ziggfreed.rpgstations.loot.LootableCatalog;
import com.ziggfreed.rpgstations.loot.RollPoolCatalog;
import com.ziggfreed.rpgstations.util.Log;
import com.ziggfreed.rpgstations.validation.Finding;
import com.ziggfreed.rpgstations.validation.Report;
import com.ziggfreed.rpgstations.validation.Severity;

/**
 * Read-only content diagnostic for station assets (design section 4.1), over the local
 * {@code validation/} mini-core and {@code util.Log}.
 *
 * <p><b>What it does NOT check, by construction.</b> A {@code Contribution}'s {@code Param}
 * semantics are the channel owner's business, and are validated by the owning mod - through a
 * registered {@code api.ValidationHook} ({@link #runHooks}) when the rule needs to see content, or
 * in that mod's own validator otherwise. Nothing in here branches on a foreign id. The lang-key
 * presence check runs against this mod's own {@link RpgStationsLangKeys}, and the gate check is a
 * factor-known check over this mod's own {@link Requires}/{@link Condition}.
 *
 * <p><b>Scope-2 rewrite (leg A4, design {@code raw/rpg-stations-scope2-unified-design-2026-07-23
 * .md} section 1.9, gate outcomes binding):</b> every check touching the reshaped
 * {@code StationStep} orthogonal-phase model, the unified {@link LootRef}/{@link FactorRef}/
 * {@link Ingredient} vocabulary, and the {@code StationStep.Stamp.Stats.Caps.Budgets[]} shape was
 * rewritten against the A-SCHEMA leg's rewritten codecs. New checks: {@code ACTION_REF_UNKNOWN},
 * {@code EXTENSION_TARGET_UNKNOWN}, {@code EXTENSION_PAYLOAD_MISMATCH},
 * {@code EXTENSION_KEY_COLLISION}, {@code EXTENSION_ANCHOR_MISSING},
 * {@code EXTENSION_STEP_MISSING_ID}, {@code ANCHOR_STATION_UNKNOWN},
 * {@code WALK_TARGET_UNKNOWN_ANCHOR}, {@code STEP_AT_UNKNOWN_ANCHOR}, {@code WALK_REQUIRES_PUPPET},
 * and {@code LOOT_DUPLICATE_FACTOR} (INFO). The multi-station seam ({@code Walk}/{@code At}/
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
            Predicate<String> lootableKnown = id -> LootableCatalog.getInstance().get(id) != null;
            Predicate<String> rollPoolKnown = id -> RollPoolCatalog.getInstance().get(id) != null;
            Predicate<String> stationKnown = id -> StationCatalog.getInstance().getStation(id) != null;
            Predicate<String> actionAssetKnown = id -> ActionCatalog.getInstance().get(id) != null;

            List<Finding> out = new ArrayList<>(validate(stations,
                    StationValidator::langKeyKnownLive,
                    StationValidator::dropListKnownLive,
                    factorKnown,
                    lootableKnown,
                    rollPoolKnown,
                    StationValidator::modelKnownLive,
                    stationKnown,
                    actionAssetKnown));
            out.addAll(validateLootables(LootableCatalog.getInstance().all().values(),
                    StationValidator::dropListKnownLive, factorKnown));
            out.addAll(validateFlairAssets(FlairCatalog.getInstance().all().values(), stationKnown));
            // Review minor (validator-standalone-action-unwired): the flagship standalone prepfish
            // ActionAsset (Ref'd from CuttingBoard) and every ExtensionAsset are validated HERE, in
            // the FULL post-load pass, now that ActionCatalog/ExtensionCatalog exist. Deliberately NOT
            // added to validateStructural(): that per-fold pass defers every cross-layer reference
            // check, and a Target:{Station} extension validated before its target station's layer
            // folds would false-flag EXTENSION_TARGET_UNKNOWN.
            out.addAll(validateActionAssets(actionAssets,
                    StationValidator::dropListKnownLive, factorKnown, lootableKnown, rollPoolKnown,
                    StationValidator::modelKnownLive, stationKnown));
            out.addAll(validateExtensions(extensions, stations, actionAssets,
                    StationValidator::dropListKnownLive, factorKnown, lootableKnown, rollPoolKnown));
            out.addAll(checkCustodyInputsResolveLive(stations, actionAssets));
            // Third-party checks run LAST, over the same folded content the engine just walked, so
            // a hook's note sits beside the engine's own in one report. FULL pass only.
            out.addAll(runHooks(stations, actionAssets, LootableCatalog.getInstance().all().values(), extensions));
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
     * and the finding's DOMAIN names the hook's class instead of {@code "station"} - a server owner
     * reading the log can tell at a glance which mod is talking. Blank codes/messages are dropped
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
            out.add(new Finding(severity, hookDomain(), code, message, subjectId));
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
            return com.ziggfreed.common.entity.PlayerModelService.modelExists(modelId);
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

    private static boolean entityEffectKnownLive(@Nonnull String effectId) {
        try {
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
        if (notBlank(p.getSound()) && !soundKnownLive(p.getSound())) {
            out.add(Finding.info(DOMAIN, "PRESENTATION_UNKNOWN_SOUND",
                    label + " Sound '" + p.getSound() + "' is not a known SoundEvent id - check for a typo", id));
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
            checkRecipe(a, id, label, factorKnown, out);
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
            checkCustody(a.getCustody(), a.getRecipe(), false, label, id, out);
            checkPuppet(a.getPuppet(), a.getHold(), label, id, modelKnown, out);
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
            ActionDef body = asset.getBody();
            checkActionBody(body, body.getPuppet(), body.getHold(), body.getRecipe(), label, id,
                    dropListKnown, factorKnown, lootableKnown, rollPoolKnown, modelKnown, stationKnown, out);
        }
        return out;
    }

    /**
     * {@link ExtensionAsset} coverage (scope-2 design 1.8/1.9): {@code EXTENSION_TARGET_UNKNOWN}
     * (an ambiguous {@code Target} or an unresolved target id against {@code stations}/
     * {@code actionAssets}/{@code lootableKnown}/{@code rollPoolKnown}), {@code
     * EXTENSION_PAYLOAD_MISMATCH} (a payload group the resolved target type cannot carry, via the
     * pure {@link ExtensionAsset#payloadAllowedFor}), {@code EXTENSION_KEY_COLLISION} (a NEW
     * {@code Actions}/{@code Anchors} key colliding with the BASE target's own key - base always
     * wins - OR with another extension's same-target same-key claim - {@link
     * ExtensionAsset#APPLY_ORDER} decides, the later-applying entry wins), {@code
     * EXTENSION_ANCHOR_MISSING} (a {@code Steps} insertion with no unambiguous placement leaf -
     * degrades to {@code AtEnd}), and {@code EXTENSION_STEP_MISSING_ID} (an inserted step with no
     * {@code Id}, so a LATER extension can never anchor on it). Every inline {@code Loot}/
     * {@code Rolls}/{@code Entries} payload is ALSO run through the shared {@link #checkRoll}/
     * {@link #checkFactorRefs} cores, same as everywhere else those vocabularies appear.
     *
     * <p><b>Documented limitation</b>: the BASE-collision half of {@code EXTENSION_KEY_COLLISION}
     * for a {@code Target:{Station}} extension's {@code Anchors} payload only checks against that
     * station's own explicit {@code "work"} action entry (the implicit-action anchor set a
     * {@code Actions}-less station would resolve to is an engine-fold concern, leg A3's
     * {@code ExtensionCatalog}). {@code EXTENSION_ANCHOR_MISSING} for a dangling {@code After}/
     * {@code Before} step id is checked ONLY when the target step program is resolvable from the
     * passed-in {@code stations}/{@code actionAssets} collections (an ambiguous/missing placement
     * leaf is ALWAYS checked regardless). Wired into the live {@link #validate()} full pass (over
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

        // Cross-extension key-collision tracking, in APPLY_ORDER (last claimant wins).
        Map<String, List<ExtensionAsset>> actionKeyClaims = new LinkedHashMap<>();
        Map<String, List<ExtensionAsset>> anchorKeyClaims = new LinkedHashMap<>();
        // Cross-extension (Channel, Param) tracking (P8 ruling 75's added check): unlike
        // Actions/Anchors above, PerCycleContributions is an UNKEYED array
        // ExtensionCatalog#mergeContributions APPENDS, so two claimants of the same
        // (target, channel, param) both apply and their amounts genuinely sum - see
        // reportContributionDuplicates for the deliberately different wording.
        Map<String, List<ExtensionAsset>> channelParamClaims = new LinkedHashMap<>();

        for (ExtensionAsset ext : ExtensionAsset.sortedForApply(extensions)) {
            if (ext == null) {
                continue;
            }
            String extId = ext.getId() == null || ext.getId().isBlank() ? "(unnamed)" : ext.getId();
            String label = "Extension '" + extId + "'";

            ExtensionAsset.Target target = ext.getTarget();
            if (target == null || !target.hasExactlyOneTarget()) {
                out.add(Finding.error(DOMAIN, "EXTENSION_TARGET_UNKNOWN",
                        label + " authors no Target or more than one target leaf - exactly one of"
                                + " Station|Action|Lootable|RollPool is required", extId));
                continue;
            }
            String targetType = target.resolvedType();
            String targetId = target.resolvedId();
            boolean targetKnown = switch (targetType) {
                case ExtensionAsset.Target.STATION ->
                        targetId != null && stationsById.containsKey(targetId.toLowerCase(Locale.ROOT));
                case ExtensionAsset.Target.ACTION ->
                        targetId != null && actionAssetsById.containsKey(targetId.toLowerCase(Locale.ROOT));
                case ExtensionAsset.Target.LOOTABLE ->
                        targetId != null && lootableKnown.test(targetId.toLowerCase(Locale.ROOT));
                case ExtensionAsset.Target.ROLLPOOL ->
                        targetId != null && rollPoolKnown.test(targetId.toLowerCase(Locale.ROOT));
                default -> false;
            };
            if (!targetKnown) {
                out.add(Finding.warning(DOMAIN, "EXTENSION_TARGET_UNKNOWN",
                        label + " Target." + targetType + " references unknown " + targetType.toLowerCase(Locale.ROOT)
                                + " '" + targetId + "'", extId));
            }

            checkExtensionPayload(ext.getPerCycleContributions() != null
                            && ext.getPerCycleContributions().length > 0,
                    ExtensionAsset.PAYLOAD_PER_CYCLE_CONTRIBUTIONS, targetType, label, extId, out);
            checkExtensionPayload(ext.getLoot() != null && !ext.getLoot().isEmpty(), ExtensionAsset.PAYLOAD_LOOT,
                    targetType, label, extId, out);
            checkExtensionPayload(ext.getActions() != null && !ext.getActions().isEmpty(),
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

            if (ext.getLoot() != null) {
                checkLootRef(ext.getLoot(), extId, label + ".Loot", dropListKnown, factorKnown, lootableKnown, out);
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
                        checkFactorRefs(e.getPoints().getAddFactors(),
                                label + ".Entries[" + i + "].Points.AddFactors", extId, factorKnown, out);
                    }
                }
            }

            if (ext.getActions() != null && !ext.getActions().isEmpty()
                    && ExtensionAsset.Target.STATION.equals(targetType)) {
                StationAsset base = targetId != null ? stationsById.get(targetId.toLowerCase(Locale.ROOT)) : null;
                Set<String> baseKeys = base != null && base.getActions() != null
                        ? lowercaseKeySet(base.getActions().keySet()) : Set.of();
                for (String key : ext.getActions().keySet()) {
                    if (key == null || key.isBlank()) {
                        continue;
                    }
                    String lower = key.toLowerCase(Locale.ROOT);
                    if (baseKeys.contains(lower)) {
                        out.add(Finding.warning(DOMAIN, "EXTENSION_KEY_COLLISION",
                                label + " Actions['" + key + "'] collides with station '" + targetId
                                        + "'s own base action - the base always wins, this entry is skipped", extId));
                    } else {
                        actionKeyClaims.computeIfAbsent(targetType + ":" + targetId + ":" + lower,
                                k -> new ArrayList<>()).add(ext);
                    }
                }
            }

            if (ext.getAnchors() != null && !ext.getAnchors().isEmpty()) {
                Map<String, ActionDef.Anchor> baseAnchors =
                        resolveBaseAnchors(targetType, targetId, stationsById, actionAssetsById);
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
                        anchorKeyClaims.computeIfAbsent(targetType + ":" + targetId + ":" + lower,
                                k -> new ArrayList<>()).add(ext);
                    }
                }
            }

            if (ext.getPerCycleContributions() != null && ext.getPerCycleContributions().length > 0) {
                checkContributionChannels(ext.getPerCycleContributions(),
                        label + " PerCycleContributions", extId, out);
                Set<String> basePairs = resolveBaseContributionKeys(targetType, targetId, stationsById,
                        actionAssetsById);
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
                        channelParamClaims.computeIfAbsent(targetType + ":" + targetId + ":" + pair,
                                k -> new ArrayList<>()).add(ext);
                    }
                }
            }

            if (ext.getSteps() != null) {
                Set<String> targetStepIds = resolveTargetStepIds(targetType, targetId, stationsById, actionAssetsById);
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

    private static void checkExtensionPayload(boolean authored, @Nonnull String payloadKey,
            @Nullable String targetType, @Nonnull String label, @Nonnull String extId, @Nonnull List<Finding> out) {
        if (authored && !ExtensionAsset.payloadAllowedFor(targetType, payloadKey)) {
            out.add(Finding.warning(DOMAIN, "EXTENSION_PAYLOAD_MISMATCH",
                    label + " authors " + payloadKey + ", which Target." + targetType + " cannot carry", extId));
        }
    }

    @Nonnull
    private static Set<String> lowercaseKeySet(@Nonnull Set<String> keys) {
        Set<String> out = new HashSet<>();
        for (String k : keys) {
            if (k != null && !k.isBlank()) {
                out.add(k.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /**
     * The base target's own Anchors map for a collision check: for a {@code Target:{Action}}
     * extension, the referenced {@link ActionAsset}'s own body; for a {@code Target:{Station}}
     * extension, that station's explicit {@code "work"} action entry ONLY (see this method's
     * caller's documented limitation - the implicit-action anchor set is an engine-fold concern).
     */
    @Nullable
    private static Map<String, ActionDef.Anchor> resolveBaseAnchors(@Nullable String targetType,
            @Nullable String targetId, @Nonnull Map<String, StationAsset> stationsById,
            @Nonnull Map<String, ActionAsset> actionAssetsById) {
        if (targetId == null) {
            return null;
        }
        String lower = targetId.toLowerCase(Locale.ROOT);
        if (ExtensionAsset.Target.ACTION.equals(targetType)) {
            ActionAsset a = actionAssetsById.get(lower);
            return a != null ? a.getBody().getAnchors() : null;
        }
        if (ExtensionAsset.Target.STATION.equals(targetType)) {
            StationAsset s = stationsById.get(lower);
            if (s == null || s.getActions() == null) {
                return null;
            }
            ActionDef work = s.getActions().get("work");
            return work != null ? work.getAnchors() : null;
        }
        return null;
    }

    /**
     * The known step ids of the resolved target's step program, for dangling {@code After}/
     * {@code Before} anchor detection - resolvable only for a {@code Target:{Action}} extension
     * (the referenced {@link ActionAsset}'s own {@code Steps}) or a {@code Target:{Station}}
     * extension whose {@code StepInsertion.Action} names one of that station's authored actions.
     * Returns {@code null} when unresolvable (fails open - a dangling reference simply goes
     * unchecked rather than false-flagging every insertion).
     */
    @Nullable
    private static Set<String> resolveTargetStepIds(@Nullable String targetType, @Nullable String targetId,
            @Nonnull Map<String, StationAsset> stationsById, @Nonnull Map<String, ActionAsset> actionAssetsById) {
        if (targetId == null) {
            return null;
        }
        String lower = targetId.toLowerCase(Locale.ROOT);
        StationStep[] steps = null;
        if (ExtensionAsset.Target.ACTION.equals(targetType)) {
            ActionAsset a = actionAssetsById.get(lower);
            steps = a != null ? a.getBody().getSteps() : null;
        }
        // A Target:{Station} extension's StepInsertion.Action names WHICH station action to
        // insert into; without that per-insertion context here, the station form is left
        // unresolved (null) rather than guessed - the ambiguous/missing-placement check above
        // still runs regardless.
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
     * {@link ActionAsset}'s OWN body {@code Work} contributions (an action's {@code Work} group is a
     * WHOLE-GROUP override over the station's, per {@code ActionResolver} - it does not inherit the
     * station's list); for a {@code Target:{Station}} extension, that station's own. An empty result
     * (unresolved target, or a target that posts nothing at all) degrades to "nothing to collide
     * with" rather than skipping the cross-extension half of the check below.
     */
    @Nonnull
    private static Set<String> resolveBaseContributionKeys(@Nullable String targetType, @Nullable String targetId,
            @Nonnull Map<String, StationAsset> stationsById, @Nonnull Map<String, ActionAsset> actionAssetsById) {
        if (targetId == null) {
            return Set.of();
        }
        String lower = targetId.toLowerCase(Locale.ROOT);
        Contribution[] posts = null;
        if (ExtensionAsset.Target.ACTION.equals(targetType)) {
            ActionAsset a = actionAssetsById.get(lower);
            StationAsset.Work work = a != null ? a.getBody().getWork() : null;
            posts = work != null ? work.getPerCycleContributions() : null;
        } else if (ExtensionAsset.Target.STATION.equals(targetType)) {
            StationAsset s = stationsById.get(lower);
            StationAsset.Work work = s != null ? s.getWork() : null;
            posts = work != null ? work.getPerCycleContributions() : null;
        }
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
     */
    private static void reportContributionDuplicates(@Nonnull Map<String, List<ExtensionAsset>> claims,
            @Nonnull List<Finding> out) {
        for (Map.Entry<String, List<ExtensionAsset>> entry : claims.entrySet()) {
            List<ExtensionAsset> claimants = entry.getValue();
            if (claimants.size() < 2) {
                continue;
            }
            String key = entry.getKey();
            String pair = key.substring(key.lastIndexOf(':') + 1);
            StringBuilder ids = new StringBuilder();
            for (int i = 0; i < claimants.size(); i++) {
                ExtensionAsset ext = claimants.get(i);
                String extId = ext.getId() == null ? "(unnamed)" : ext.getId();
                if (i > 0) {
                    ids.append(", ");
                }
                ids.append('\'').append(extId).append('\'');
            }
            String firstId = claimants.get(0).getId() == null ? "(unnamed)" : claimants.get(0).getId();
            out.add(Finding.warning(DOMAIN, "EXTENSION_CONTRIBUTION_DUPLICATE",
                    "Extensions " + ids + " all append a Contributions entry for (Channel|Param) '" + pair
                            + "' to the same target - PerCycleContributions arrays are additive, so ALL of them"
                            + " apply and the amounts SUM across every one of them", firstId));
        }
    }

    private static void reportCrossExtensionCollisions(@Nonnull Map<String, List<ExtensionAsset>> claims,
            @Nonnull String payloadName, @Nonnull List<Finding> out) {
        for (List<ExtensionAsset> claimants : claims.values()) {
            if (claimants.size() < 2) {
                continue;
            }
            // `claimants` was appended while walking extensions in APPLY_ORDER, so the LAST
            // entry is the one that actually wins the key at apply time.
            ExtensionAsset winner = claimants.get(claimants.size() - 1);
            String winnerId = winner.getId() == null ? "(unnamed)" : winner.getId();
            for (int i = 0; i < claimants.size() - 1; i++) {
                ExtensionAsset loser = claimants.get(i);
                String loserId = loser.getId() == null ? "(unnamed)" : loser.getId();
                out.add(Finding.warning(DOMAIN, "EXTENSION_KEY_COLLISION",
                        "Extension '" + loserId + "' " + payloadName + " key collides with a later-applying"
                                + " extension '" + winnerId + "' targeting the same key - the higher apply-order"
                                + " entry wins, this one is skipped", loserId));
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
            warnUnmatchedCustodyInput(asset.getCustody(), live, "Station '" + id + "'", id, out);
            Map<String, ActionDef> actions = asset.getActions();
            if (actions != null) {
                for (Map.Entry<String, ActionDef> e : actions.entrySet()) {
                    ActionDef def = e.getValue();
                    if (def != null) {
                        warnUnmatchedCustodyInput(def.getCustody(), live,
                                "Station '" + id + "' action '" + e.getKey() + "'", id, out);
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
     * Display-only re-skin inherits Input/Recipe from the BASE, so warning "nothing can ever be
     * placed" about it would actively mislead. The value-range checks below still run either way.
     */
    private static void checkCustody(@Nullable Custody custody, @Nullable StationAsset.Recipe effectiveRecipe,
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
            // Decision 47's Look nesting symmetry retro-nested the flat ModelId/FallbackModelId
            // leaves into the Look.Model group (R1 schema handoff) - resolve through it here.
            Puppet.Model model = look.getModel();
            if (Puppet.LOOK_SOURCE_MODEL.equalsIgnoreCase(look.effectiveSource())) {
                String modelId = model != null ? model.getModelId() : null;
                boolean modelIdBlank = modelId == null || modelId.isBlank();
                String fallback = model != null ? model.getFallbackModelId() : null;
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
        checkPowerScale(tool, gatherTypeSet ? gather.getGatherType() : null, id, label, out);
        checkDurability(tool, id, label, out);
        checkMinDurabilityPercent(tool, id, label, out);
    }

    /**
     * {@code Tool.MinDurabilityPercent} (P11 knob, ruling 74): a value outside (0, 100] is almost
     * always an authoring slip, not intent - the leaf documents itself as a PERCENT (0-100), so a
     * fraction like {@code 0.5} silently becomes a near-impossible 0.5% floor rather than the
     * intended 50%, and anything <= 0 is a no-op the reader already treats as "no gate" (author
     * {@code null} instead of a zero/negative sentinel).
     */
    private static void checkMinDurabilityPercent(@Nonnull StationAsset.Tool tool, @Nonnull String id,
                                                   @Nonnull String label, @Nonnull List<Finding> out) {
        Double minDurability = tool.getMinDurabilityPercent();
        if (minDurability != null && (minDurability <= 0 || minDurability > 100)) {
            out.add(Finding.warning(DOMAIN, "TOOL_MIN_DURABILITY_OUT_OF_RANGE",
                    label + " authors Tool.MinDurabilityPercent " + minDurability + " outside (0, 100] - the"
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

    private static void checkPowerScale(@Nonnull StationAsset.Tool tool, @Nullable String gatherFallback,
                                        @Nonnull String id, @Nonnull String label, @Nonnull List<Finding> out) {
        StationAsset.Tool.PowerScale scale = tool.getPowerScale();
        if (scale == null) {
            return;
        }
        if (scale.getReferencePower() == null || scale.getReferencePower() <= 0) {
            out.add(Finding.warning(DOMAIN, "DEAD_POWER_SCALE",
                    label + " authors a Tool.PowerScale with a null or nonpositive ReferencePower; the multiplier stays 1.0 forever", id));
        }
        String scaleGather = scale.getGatherType();
        boolean scaleGatherSet = scaleGather != null && !scaleGather.isBlank();
        if (!scaleGatherSet && (gatherFallback == null || gatherFallback.isBlank())) {
            out.add(Finding.warning(DOMAIN, "POWER_SCALE_NO_GATHER_TYPE",
                    label + " authors a Tool.PowerScale but neither PowerScale.GatherType nor Tool.Gather.GatherType resolves; the scale never applies", id));
        }
        if (scale.getMinMult() != null && scale.getMaxMult() != null && scale.getMinMult() > scale.getMaxMult()) {
            out.add(Finding.error(DOMAIN, "POWER_SCALE_BAD_CLAMP",
                    label + " Tool.PowerScale has MinMult > MaxMult (the clamp is inverted)", id));
        }
        if (scale.getExponent() != null && scale.getExponent() <= 0) {
            out.add(Finding.warning(DOMAIN, "POWER_SCALE_BAD_EXPONENT",
                    label + " Tool.PowerScale authors a nonpositive Exponent", id));
        }
    }

    /**
     * The station-level conditional-lootable declaration (scope-2: {@link LootRef} - delegates to
     * the shared {@link #checkLootRef} core also used by {@code ActionDef.Loot}, an
     * {@code ExtensionAsset.Loot} payload, and {@link StationStep#getRoll()}).
     */
    private static void checkLoot(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                  @Nonnull Predicate<String> dropListKnown, @Nonnull Predicate<String> factorKnown,
                                  @Nonnull Predicate<String> lootableKnown, @Nonnull List<Finding> out) {
        checkLootRef(a.getLoot(), id, label, dropListKnown, factorKnown, lootableKnown, out);
    }

    /**
     * The shared {@link LootRef} core (scope-2 design 1.3, DRY principle 1 - the ONE loot-reference
     * vocabulary): validates {@link LootRef#getLootables()} references, then every
     * {@link LootRef#getRolls()} entry via {@link #checkRoll}. Reused by the station-level
     * {@code Loot} group, a per-action {@code ActionDef.Loot} override, a {@code StationStep.Roll}
     * phase, and an {@code ExtensionAsset.Loot} payload.
     */
    private static void checkLootRef(@Nullable LootRef loot, @Nonnull String id, @Nonnull String label,
                                     @Nonnull Predicate<String> dropListKnown, @Nonnull Predicate<String> factorKnown,
                                     @Nonnull Predicate<String> lootableKnown, @Nonnull List<Finding> out) {
        if (loot == null) {
            return;
        }
        String[] lootables = loot.getLootables();
        if (lootables != null) {
            for (String t : lootables) {
                if (t == null || t.isBlank()) {
                    out.add(Finding.warning(DOMAIN, "LOOT_BLANK_TABLE", label + " Loot.Lootables has a blank entry", id));
                } else if (!lootableKnown.test(t.toLowerCase(Locale.ROOT))) {
                    out.add(Finding.warning(DOMAIN, "LOOT_UNKNOWN_TABLE",
                            label + " Loot.Lootables references unknown lootable '" + t + "'", id));
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
     * critique fix 5, scope-2 weighted-factor unification): {@code Conditions} factor ids run
     * through {@code factorKnown} via {@link #checkConditionFactors}; {@code Chance.AddFactors}/
     * {@code Ladder.Values} (now {@link FactorRef}s) run through {@code factorKnown} via
     * {@link #checkFactorRefs} - the SAME {@code UNKNOWN_FACTOR} code every factor-reference site
     * in this file uses, one code, one meaning; every {@code Grants.DropList} (top-level or
     * per-floor) runs through {@code dropListKnown}; a {@code Grants.BonusOutputCopies} authored
     * under a non-{@code Cycle} {@link Roll#effectiveTrigger()} is flagged
     * {@code LOOT_BONUS_COPIES_WRONG_TRIGGER}; and a roll naming the same
     * {@code (Factor, Param)} pair twice across its own reference sites is flagged INFO
     * {@code LOOT_DUPLICATE_FACTOR} via {@link #checkDuplicateFactors}.
     */
    static void checkRoll(@Nullable Roll roll, @Nonnull String label, @Nonnull String id,
                          @Nonnull Predicate<String> dropListKnown, @Nonnull Predicate<String> factorKnown,
                          @Nonnull List<Finding> out) {
        if (roll == null) {
            return;
        }
        String trigger = roll.effectiveTrigger();
        checkConditionFactors(roll.getConditions(), label + ".Conditions", id, factorKnown, out);
        checkDuplicateFactors(roll, label, id, out);

        Roll.Chance chance = roll.getChance();
        if (chance != null) {
            checkFactorRefs(chance.getAddFactors(), label + ".Chance.AddFactors", id, factorKnown, out);
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
            FactorRef[] values = ladder.getValues();
            if (values == null || values.length == 0) {
                out.add(Finding.error(DOMAIN, "LOOT_LADDER_MISSING_VALUE",
                        label + ".Ladder has no Values - it can never resolve a floor", id));
            } else {
                checkFactorRefs(values, label + ".Ladder.Values", id, factorKnown, out);
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

    /**
     * The generic redundant-reference lint (INFO-only, best-effort): one {@link Roll} naming the
     * SAME {@code (Factor, Param)} pair more than once across its own {@code Conditions} /
     * {@code Chance.AddFactors} / {@code Ladder.Values}. The engine can state that in its own
     * terms - reading one number twice in one formula is nearly always an editing slip - without
     * knowing what any particular factor MEANS.
     *
     * <p><b>Keyed on the PAIR, deliberately, and that is load-bearing.</b> Keying on the factor id
     * alone would fire on correct content by construction: every stat read carries the same
     * {@code "hytale:stat"} factor id, so a ladder composing two different stat channels (the documented
     * equal-weight composition shape) would emit a spurious note at every boot. Only a genuinely
     * repeated pair fires, which in practice means a param-less zero-arg engine factor named twice
     * (e.g. {@code rpgstations:cycle_count} in both a Condition and a Chance.AddFactors entry).
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
            for (Condition c : roll.getConditions()) {
                if (c != null) {
                    noteFactorPair(c.getFactor(), c.getParam(), seen, reported, label, id, out);
                }
            }
        }
        if (roll.getChance() != null) {
            noteFactorRefPairs(roll.getChance().getAddFactors(), seen, reported, label, id, out);
        }
        if (roll.getLadder() != null) {
            noteFactorRefPairs(roll.getLadder().getValues(), seen, reported, label, id, out);
        }
    }

    private static void noteFactorRefPairs(@Nullable FactorRef[] refs, @Nonnull Set<String> seen,
                                           @Nonnull Set<String> reported, @Nonnull String label,
                                           @Nonnull String id, @Nonnull List<Finding> out) {
        if (refs == null) {
            return;
        }
        for (FactorRef f : refs) {
            if (f != null) {
                noteFactorPair(f.getFactor(), f.getParam(), seen, reported, label, id, out);
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
                        + ") more than once across its Conditions / Chance.AddFactors / Ladder.Values -"
                        + " the same number is read twice in one roll", id));
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
            checkNativeRefs(f.getPresentation(), fLabel + ".Presentation", id, out);
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

    /**
     * The shared {@link FactorRef} check (scope-2's weighted-factor vocabulary, design 1.3/4.2):
     * the {@code FactorRef}-array sibling of {@link #checkConditionFactors}, reused everywhere a
     * numeric factor channel is SUMMED - {@code Roll.Chance.AddFactors}, {@code Roll.Ladder.Values},
     * {@code StatRollEntry.Points.AddFactors}, {@code StationStep.Stamp.Stats.Caps.Budgets[]
     * .Factors}, {@code StationStep.Repeat.AddFactors}. Same {@code UNKNOWN_FACTOR} code as every
     * other factor-reference site (one code, one meaning).
     */
    private static void checkFactorRefs(@Nullable FactorRef[] factors, @Nonnull String label, @Nonnull String id,
                                        @Nonnull Predicate<String> factorKnown, @Nonnull List<Finding> out) {
        if (factors == null) {
            return;
        }
        for (FactorRef f : factors) {
            if (f == null || f.getFactor() == null || f.getFactor().isBlank()) {
                continue;
            }
            if (!factorKnown.test(f.getFactor())) {
                out.add(Finding.warning(DOMAIN, "UNKNOWN_FACTOR",
                        label + " references unknown factor '" + f.getFactor() + "'", id));
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
        // Decision 51d (seam wave): a Grants.Effects[] entry is the SAME EffectRef id-ref-only
        // vocabulary Presentation.Effect uses - the shared checkEffectRef core covers both.
        EffectRef[] effects = grants.getEffects();
        if (effects != null) {
            for (int i = 0; i < effects.length; i++) {
                checkEffectRef(effects[i], label + ".Effects[" + i + "]", id, out);
            }
        }
        // A one-shot contribution rides the cycle-completed event, which only a Cycle trigger has -
        // the same wrong-trigger shape BonusOutputCopies already warns for.
        Contribution[] posts = grants.getContributions();
        if (posts != null && posts.length > 0) {
            if (!Roll.TRIGGER_CYCLE.equalsIgnoreCase(trigger)) {
                out.add(Finding.warning(DOMAIN, "LOOT_CONTRIBUTION_WRONG_TRIGGER",
                        label + " authors Grants.Contributions under a non-Cycle Trigger ('" + trigger
                                + "') - there is no cycle event to forward the post on", id));
            }
            checkContributionChannels(posts, label + ".Contributions", id, out);
            for (int i = 0; i < posts.length; i++) {
                Contribution post = posts[i];
                String postLabel = label + ".Contributions[" + i + "]";
                if (post == null || post.getChannel() == null || post.getChannel().isBlank()) {
                    out.add(Finding.warning(DOMAIN, "LOOT_CONTRIBUTION_MISSING_CHANNEL",
                            postLabel + " has no Channel - the post is skipped", id));
                    continue;
                }
                if (post.getAmount() == null || post.getAmount() <= 0) {
                    out.add(Finding.warning(DOMAIN, "LOOT_CONTRIBUTION_NONPOSITIVE_AMOUNT",
                            postLabel + " has a null or nonpositive Amount - the post is skipped", id));
                }
            }
        }
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

    private static void checkRecipe(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
                                    @Nonnull Predicate<String> factorKnown, @Nonnull List<Finding> out) {
        StationAsset.Recipe recipe = a.getRecipe();
        StationAsset.Conversion[] conversions = recipe != null ? recipe.getConversions() : null;
        StationAsset.FromCrafting fromCrafting = recipe != null ? recipe.getFromCrafting() : null;
        boolean hasConversions = conversions != null && conversions.length > 0;
        if (!hasConversions && fromCrafting == null && !anyActionProvidesRunSource(a.getActions())) {
            // Multi-action stations (design 9.1) author per-action Recipe/Steps/Ref instead of a
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
        if (recipe != null && recipe.getYield() != null) {
            checkYield(recipe.getYield(), id, label, factorKnown, out);
        }
    }

    /**
     * {@code Recipe.Yield} coverage - every finding warn-or-info, per this validator's never-block
     * posture: a nonsensical yield still loads and the engine's own reader defaults absorb it.
     */
    private static void checkYield(@Nonnull StationAsset.Yield y, @Nonnull String id,
                                   @Nonnull String label, @Nonnull Predicate<String> factorKnown,
                                   @Nonnull List<Finding> out) {
        if (y.getBase() != null && y.getBase() <= 0) {
            out.add(Finding.warning(DOMAIN, "YIELD_NONPOSITIVE_BASE",
                    label + " Recipe.Yield.Base is not positive - it is ignored and each conversion's"
                            + " own authored output quantity is used instead", id));
        }
        if (y.getScale() != null && (!Double.isFinite(y.getScale()) || y.getScale() <= 0.0)) {
            out.add(Finding.warning(DOMAIN, "YIELD_NONPOSITIVE_SCALE",
                    label + " Recipe.Yield.Scale is not a positive finite number - it reader-defaults to 1.0", id));
        }
        if (y.getMin() != null && y.getMax() != null && y.getMin() > y.getMax()) {
            out.add(Finding.warning(DOMAIN, "YIELD_MIN_ABOVE_MAX",
                    label + " Recipe.Yield.Min (" + y.getMin() + ") exceeds Max (" + y.getMax()
                            + ") - Max wins, so every cycle produces exactly Max", id));
        }
        StationAsset.Yield.Bonus bonus = y.getBonus();
        if (bonus == null) {
            return;
        }
        boolean hasFloors = bonus.getFloors() != null && bonus.getFloors().length > 0;
        boolean hasValues = bonus.getValues() != null && bonus.getValues().length > 0;
        if (hasFloors && !hasValues) {
            out.add(Finding.warning(DOMAIN, "YIELD_BONUS_FLOORS_WITHOUT_VALUES",
                    label + " Recipe.Yield.Bonus authors Floors but no Values - the ladder value is a"
                            + " constant 0, so only a Min<=0 floor can ever be reached", id));
        }
        if (hasValues && !hasFloors) {
            out.add(Finding.warning(DOMAIN, "YIELD_BONUS_VALUES_WITHOUT_FLOORS",
                    label + " Recipe.Yield.Bonus authors Values but no Floors - nothing consumes the"
                            + " summed value, so the bonus can never add anything", id));
        }
        if (bonus.getValues() != null) {
            for (FactorRef ref : bonus.getValues()) {
                if (ref == null || ref.getFactor() == null || ref.getFactor().isBlank()) {
                    out.add(Finding.warning(DOMAIN, "YIELD_BONUS_BLANK_FACTOR",
                            label + " Recipe.Yield.Bonus.Values has an entry with no Factor id - it contributes 0", id));
                } else if (!factorKnown.test(ref.getFactor())) {
                    out.add(Finding.warning(DOMAIN, "YIELD_BONUS_UNKNOWN_FACTOR",
                            label + " Recipe.Yield.Bonus.Values references unregistered factor '"
                                    + ref.getFactor() + "' - it resolves to 0 (fail-closed)", id));
                }
            }
        }
        if (bonus.getFloors() != null) {
            for (StationAsset.Yield.Floor floor : bonus.getFloors()) {
                if (floor != null && floor.effectiveAdd() == 0) {
                    out.add(Finding.info(DOMAIN, "YIELD_BONUS_FLOOR_ADDS_NOTHING",
                            label + " Recipe.Yield.Bonus has a floor at Min " + floor.effectiveMin()
                                    + " whose Add is 0 - reaching it changes nothing", id));
                }
            }
        }
    }

    /**
     * True when at least one authored {@code Actions} entry supplies its OWN runnable recipe/
     * program source - a {@code Ref} to a standalone {@link ActionAsset} (which owns its own
     * body), a per-action {@code Recipe} (Conversions or FromCrafting), or a {@code Steps} program
     * (the anvil's {@code enhance} action runs entirely off a Stamp-step ritual, no Recipe at
     * all). Mirrors {@link #checkActions}'s {@code ACTION_NO_BODY} per-action check, but answers
     * the station-wide question {@link #checkRecipe} needs: "can THIS station ever run a cycle
     * through ANY route".
     */
    private static boolean anyActionProvidesRunSource(@Nullable Map<String, ActionDef> actions) {
        if (actions == null || actions.isEmpty()) {
            return false;
        }
        for (ActionDef def : actions.values()) {
            if (def == null) {
                continue;
            }
            if (def.hasRef()) {
                return true;
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
        String emoteId = a.getAnimation() != null ? a.getAnimation().getEmoteId() : null;
        if (emoteId != null && emoteId.isBlank()) {
            out.add(Finding.warning(DOMAIN, "BLANK_EMOTE_ID",
                    label + " authors an empty Animation.EmoteId", id));
        } else if (notBlank(emoteId) && !emoteKnownLive(emoteId)) {
            out.add(Finding.info(DOMAIN, "PRESENTATION_UNKNOWN_EMOTE",
                    label + " Animation.EmoteId '" + emoteId + "' is not a known Emote id - check for a typo", id));
        }
        String holdEffectId = a.getHold() != null ? a.getHold().getEffectId() : null;
        if (holdEffectId != null && holdEffectId.isBlank()) {
            out.add(Finding.warning(DOMAIN, "BLANK_EFFECT_ID",
                    label + " authors an empty Hold.EffectId", id));
        } else if (notBlank(holdEffectId) && !entityEffectKnownLive(holdEffectId)) {
            out.add(Finding.warning(DOMAIN, "UNKNOWN_ENTITY_EFFECT",
                    label + " Hold.EffectId '" + holdEffectId + "' references unknown EntityEffect", id));
        }
        // Seam wave (decision 51b/51d, decision 53): the station's own cycle-moment Presentation
        // gains the SAME native-composition advisory coverage every other Presentation site gets -
        // this was a pre-existing gap (only Completion/Swing/Impact/flair Moments were checked).
        checkNativeRefs(a.getPresentation(), label + ".Presentation", id, out);
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
        checkNativeRefs(swing.getPresentation(), swingLabel + ".Presentation", id, out);
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
        checkNativeRefs(impact.getPresentation(), impactLabel + ".Presentation", id, out);
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
        checkNativeRefs(a.getCompletion(), label + ".Completion", id, out);
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
            checkNativeRefs(entry.getValue(), label + ".Moments['" + momentId + "']", id, out);
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
     * Multi-action station coverage (design section 9.1, scope-2 additions Ref/Anchors) - per
     * table-entry structure: "warn on odd combos, never block" (every finding here is
     * WARNING/INFO, never ERROR, matching the design's binding note). A station with no
     * {@code Actions} map is a no-op call (nothing to iterate) - the implicit single-{@code "work"}
     * -action path is validated entirely by the existing station-level checks above it. Delegates
     * every per-body structural check (hasBody/Custody/Puppet/Anchors/Steps) to
     * {@link #checkActionBody}, the SAME core {@link #validateActionAssets} uses for a standalone
     * {@link ActionAsset}.
     */
    private static void checkActions(@Nonnull StationAsset a, @Nonnull String id, @Nonnull String label,
            @Nonnull Predicate<String> dropListKnown, @Nonnull Predicate<String> factorKnown,
            @Nonnull Predicate<String> lootableKnown, @Nonnull Predicate<String> rollPoolKnown,
            @Nonnull Predicate<String> modelKnown, @Nonnull Predicate<String> stationKnown,
            @Nonnull Predicate<String> actionAssetKnown, @Nonnull List<Finding> out) {
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
            if (def.hasRef() && !actionAssetKnown.test(def.getRef().toLowerCase(Locale.ROOT))) {
                out.add(Finding.warning(DOMAIN, "ACTION_REF_UNKNOWN",
                        actionLabel + " Ref '" + def.getRef() + "' does not resolve to a known ActionAsset - "
                                + "engage will deny with ui.station.action_unavailable", id));
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

            Puppet resolvedPuppet = def.getPuppet() != null ? def.getPuppet() : a.getPuppet();
            StationAsset.Hold effectiveHold = def.getHold() != null ? def.getHold() : a.getHold();
            StationAsset.Recipe effectiveRecipe = def.getRecipe() != null ? def.getRecipe() : a.getRecipe();
            checkActionBody(def, resolvedPuppet, effectiveHold, effectiveRecipe, actionLabel, id,
                    dropListKnown, factorKnown, lootableKnown, rollPoolKnown, modelKnown, stationKnown, out);
        }
    }

    /**
     * The per-action BODY structural core (scope-2), shared by an inline {@code Actions} map
     * entry ({@link #checkActions}) AND a standalone {@link ActionAsset} ({@link
     * #validateActionAssets}) - one check, two authoring sites. {@code resolvedPuppet}/
     * {@code effectiveHold}/{@code effectiveRecipeForCustody} are the CALLER's already-resolved
     * groups (a station-map entry falls back to the station's own group; a standalone action has
     * no fallback - it IS the base), so this method never re-derives a fallback itself.
     */
    private static void checkActionBody(@Nonnull ActionDef def, @Nullable Puppet resolvedPuppet,
            @Nullable StationAsset.Hold effectiveHold, @Nullable StationAsset.Recipe effectiveRecipeForCustody,
            @Nonnull String actionLabel, @Nonnull String id,
            @Nonnull Predicate<String> dropListKnown, @Nonnull Predicate<String> factorKnown,
            @Nonnull Predicate<String> lootableKnown, @Nonnull Predicate<String> rollPoolKnown,
            @Nonnull Predicate<String> modelKnown, @Nonnull Predicate<String> stationKnown,
            @Nonnull List<Finding> out) {
        boolean hasBody = def.hasRef() || def.getRecipe() != null || (def.getSteps() != null && def.getSteps().length > 0);
        if (!hasBody) {
            out.add(Finding.warning(DOMAIN, "ACTION_NO_BODY",
                    actionLabel + " authors neither Ref, Recipe (for the implicit convert-loop program), nor"
                            + " Steps - this action can never run a cycle", id));
        }
        if (def.getLoot() != null) {
            checkLootRef(def.getLoot(), id, actionLabel + ".Loot", dropListKnown, factorKnown, lootableKnown, out);
        }
        if (def.getCustody() != null) {
            checkCustody(def.getCustody(), effectiveRecipeForCustody, false, actionLabel, id, out);
        }
        if (def.getPuppet() != null) {
            checkPuppet(def.getPuppet(), effectiveHold, actionLabel, id, modelKnown, out);
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
            boolean puppetActive = resolvedPuppet != null && resolvedPuppet.effectiveEnabled();
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
     * {@link #checkLootRef}), a {@code Repeat.AddFactors}/{@code Walk} check, and (design 9.5) a
     * {@code Stamp} phase's own coverage. Also flags {@code WALK_TARGET_UNKNOWN_ANCHOR}/
     * {@code STEP_AT_UNKNOWN_ANCHOR} (a {@code Walk.To}/{@code At} not matching {@code
     * knownAnchorIds} or the reserved {@code "self"}), and {@code WALK_REQUIRES_PUPPET} (any step
     * authoring {@code Walk} when the resolved Puppet is not active - flagged once per action).
     * The multi-station seam (Walk/At/Produce.To:Custody) EXECUTES as of wave 3, so there is no
     * longer a {@code WAVE3_PENDING}-style warn gating those phases - {@link
     * StationStep#authorsWave3OnlyPhase()} is unused here now.
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

            StationStep.Repeat repeat = step.getRepeat();
            if (repeat != null) {
                checkFactorRefs(repeat.getAddFactors(), stepLabel + ".Repeat.AddFactors", id, factorKnown, out);
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
                checkLootRef(roll, id, stepLabel + ".Roll", dropListKnown, factorKnown, lootableKnown, out);
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
     * {@code Points.AddFactors} through the shared {@link #checkFactorRefs} core, and each
     * {@code Caps.Budgets[]} entry: {@code STAMP_BUDGET_BAD_ROUTE} when neither/both of the
     * exactly-one-of {@code {Points}}/{@code {PointsPer,Factors}} routes are authored (see {@link
     * StationStep.Stamp.Stats.Caps.Budget#hasExactlyOneRoute()}'s own javadoc), {@code
     * STAMP_NONPOSITIVE_BUDGET} for a non-positive value on whichever route IS authored,
     * {@code STAMP_BUDGET_STRAY_FACTORS} for a {@code Factors[]} authored on a flat {@code Points}
     * route (silently ignored - only {@code PointsPer} engages {@code Factors}), and a
     * {@code Factors[]} unknown-factor check via {@link #checkFactorRefs} (the SAME
     * {@code UNKNOWN_FACTOR} code every other factor reference reports through - one code, one
     * meaning, never a per-site {@code STAMP_UNKNOWN_FACTOR} twin).
     */
    private static void checkStamp(@Nonnull StationStep.Stamp stamp, @Nonnull String stepLabel,
            @Nonnull String id, @Nonnull Predicate<String> factorKnown, @Nonnull Predicate<String> rollPoolKnown,
            @Nonnull List<Finding> out) {
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
        StatRollEntry[] entries = stats.getEntries();
        if ((entries == null || entries.length == 0) && (pool == null || pool.isBlank())) {
            out.add(Finding.warning(DOMAIN, "STAMP_STATS_NO_ENTRIES",
                    stepLabel + " authors Stats with neither Pool nor inline Entries", id));
        }
        if (entries != null) {
            for (int i = 0; i < entries.length; i++) {
                StatRollEntry e = entries[i];
                if (e != null && e.getPoints() != null) {
                    checkFactorRefs(e.getPoints().getAddFactors(),
                            stepLabel + ".Stats.Entries[" + i + "].Points.AddFactors", id, factorKnown, out);
                }
            }
        }
        StationStep.Stamp.Stats.Caps caps = stats.getCaps();
        if (caps == null) {
            return;
        }
        StationStep.Stamp.Stats.Budget[] budgets = caps.getBudgets();
        if (budgets != null) {
            for (int i = 0; i < budgets.length; i++) {
                StationStep.Stamp.Stats.Budget b = budgets[i];
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
                    checkFactorRefs(b.getFactors(), bLabel + ".Factors", id, factorKnown, out);
                }
            }
        }
    }

    /**
     * Shared leaf check: station-scale playback ({@code StationService.emitMoment}) renders
     * {@code Sound} + {@code Particles} + {@code Shake} + the two native-composition groups - see
     * that method's javadoc - so an authored {@code Animation}/{@code AnimationItem}/
     * {@code AnimationSlot}/{@code CameraEffect} leaf is dead weight.
     */
    private static void warnUnplayedPresentationLeaves(@Nullable Presentation p, @Nonnull String label,
                                                        @Nonnull String id, @Nonnull String code,
                                                        @Nonnull List<Finding> out) {
        if (p == null) {
            return;
        }
        boolean hasUnplayedLeaf = notBlank(p.getAnimation()) || notBlank(p.getAnimationItem())
                || notBlank(p.getAnimationSlot()) || notBlank(p.getCameraEffect());
        if (hasUnplayedLeaf) {
            out.add(Finding.warning(DOMAIN, code,
                    label + " authors an Animation/AnimationItem/AnimationSlot/CameraEffect leaf;"
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
