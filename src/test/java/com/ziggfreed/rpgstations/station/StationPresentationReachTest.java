package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.loot.LootGrants;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.common.validation.Finding;
import com.ziggfreed.common.validation.Severity;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.EffectRef;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.loot.StationRewardKinds;

/**
 * Where a moment lands and what may ride an entity: the target decision
 * ({@link StationService#aimSource}: a {@code Display} target with no prop standing falls back to
 * the socket's resting position, a {@code Puppet} target with no double to the block, never the
 * worker's own body), the effect's own wearer ({@link StationService#effectGoesOnDouble}: the worker
 * when no double stands), the rule that only a system that provably ends on its own rides an entity
 * ({@link ParticleLifetimes}) with its validator findings, the block-state move a beat makes
 * ({@link StationService#workingMove}: the same block under a new name re-flips in place), an
 * effect's {@code Target} read on a presentation's {@code Effect} only ({@code EFFECT_TARGET_IGNORED}
 * elsewhere), extension overlays that keep every leaf ({@code States}' {@code Ready} and
 * {@code Overdone}, an effect's {@code Target}), and how a grant pass's items reach the rows (read
 * off the source: expected items feed the ordinary rows, found items the gold find rows). Every
 * fixture is authored here.
 */
class StationPresentationReachTest {

    // ==================== the target decision ====================

    @Test
    void aDisplayTarget_withNoPropStanding_playsAtTheSocketsRestingPosition() {
        assertEquals(StationService.AimSource.DISPLAY_RESTING,
                StationService.aimSource(Presentation.Target.Kind.DISPLAY, true, false, true),
                "no display handle (a Convert beat consumed the piece): where the prop stood");
        assertEquals(StationService.AimSource.DISPLAY_PROP,
                StationService.aimSource(Presentation.Target.Kind.DISPLAY, true, true, false));
        assertEquals(StationService.AimSource.BLOCK,
                StationService.aimSource(Presentation.Target.Kind.DISPLAY, false, false, false),
                "an action whose sockets show no prop plays at the block");
    }

    @Test
    void aPuppetTarget_withNoDouble_playsAtTheBlock_neverOnTheWorkersOwnBody() {
        assertEquals(StationService.AimSource.BLOCK,
                StationService.aimSource(Presentation.Target.Kind.PUPPET, true, true, false));
        assertEquals(StationService.AimSource.DOUBLE,
                StationService.aimSource(Presentation.Target.Kind.PUPPET, false, false, true));
        assertEquals(StationService.AimSource.BLOCK,
                StationService.aimSource(Presentation.Target.Kind.BLOCK, true, true, true));
    }

    @Test
    void aPuppetTargetedEffect_withNoDouble_goesOnTheWorker() {
        EffectRef onDouble = EffectRef.of("Fixture_Aura", null, "Puppet");
        assertTrue(StationService.effectGoesOnDouble(onDouble, true));
        assertFalse(StationService.effectGoesOnDouble(onDouble, false), "no double: the worker wears it");
        assertFalse(StationService.effectGoesOnDouble(EffectRef.of("Fixture_Sting", null, "Player"), true),
                "a Player-targeted sting stays on the worker whatever stands");
        assertFalse(StationService.effectGoesOnDouble(EffectRef.of("Fixture_Sting"), true), "the default is the worker");
    }

    // ==================== what may ride an entity ====================

    @Test
    void onlyAPositiveOwnLifeSpan_provesASystemEnds() {
        assertTrue(ParticleLifetimes.provablyEnds(2.0f));
        assertFalse(ParticleLifetimes.provablyEnds(0f), "zero is the engine's unlimited");
        assertFalse(ParticleLifetimes.provablyEnds(-1f));
        assertFalse(ParticleLifetimes.provablyEnds(null), "an unreadable system is unproven");
        assertFalse(ParticleLifetimes.systemProvablyEnds("Fixture_Not_Loaded"),
                "a system the server cannot read never rides");
    }

    @Test
    void theBursts_splitIntoTheOnesThatRideAndTheOnesThatPlayAtAPosition() {
        Presentation.ModelParticle bounded = Presentation.ModelParticle.of("Fixture_Bounded");
        Presentation.ModelParticle endless = Presentation.ModelParticle.of("Fixture_Endless");
        Presentation.ModelParticle[] all = {bounded, endless, Presentation.ModelParticle.of(" "), null};
        List<Presentation.ModelParticle> riding = ParticleLifetimes.bursts(true, all, "Fixture_Bounded"::equals);
        List<Presentation.ModelParticle> positional = ParticleLifetimes.bursts(false, all, "Fixture_Bounded"::equals);
        assertEquals(List.of(bounded), riding);
        assertEquals(List.of(endless), positional, "an endless system never rides; a blank entry is in neither");
    }

    private static List<Finding> entityTargetFindings(Presentation.ModelParticle burst, Float lifeSpan) {
        List<Finding> out = new ArrayList<>();
        StationValidator.checkEntityTargetBurst(burst, lifeSpan, "fixture", "fixture", out);
        return out;
    }

    @Test
    void theValidator_warnsOnAnUnprovenSystemAtAnEntityTarget_andNotesAnInertCap() {
        List<Finding> unproven = entityTargetFindings(Presentation.ModelParticle.of("Fixture_Endless"), null);
        assertEquals(1, unproven.size());
        assertEquals("PRESENTATION_ENTITY_TARGET_UNBOUNDED", unproven.get(0).code());
        assertEquals(Severity.WARNING, unproven.get(0).severity(), "a WARNING, not a note");
        assertEquals("PRESENTATION_ENTITY_TARGET_UNBOUNDED",
                entityTargetFindings(Presentation.ModelParticle.of("Fixture_Endless"), 0f).get(0).code());

        List<Finding> cap = entityTargetFindings(
                Presentation.ModelParticle.of("Fixture_Bounded", null, 2.0, null, null), 1.5f);
        assertEquals(1, cap.size());
        assertEquals("PRESENTATION_ENTITY_TARGET_CAP_IGNORED", cap.get(0).code());
        assertEquals(Severity.INFO, cap.get(0).severity());

        assertTrue(entityTargetFindings(Presentation.ModelParticle.of("Fixture_Bounded"), 1.5f).isEmpty(),
                "a bounded system with no cap authored rides quietly");
    }

    // ==================== the block state a beat wears ====================

    @Test
    void theSameBlockUnderADifferentName_reflipsInPlace() {
        assertEquals(StationService.WorkingMove.REFLIP,
                StationService.workingMove("w:1:2:3", "Working", "w:1:2:3", "Drawing"));
        assertEquals(StationService.WorkingMove.KEEP,
                StationService.workingMove("w:1:2:3", "Drawing", "w:1:2:3", "drawing"),
                "the same name, whatever its case, holds a steady look");
        assertEquals(StationService.WorkingMove.KEEP,
                StationService.workingMove("w:1:2:3", "Working", "w:1:2:3", null));
        assertEquals(StationService.WorkingMove.ENTER,
                StationService.workingMove("w:1:2:3", "Working", "w:9:9:9", "Working"), "another block");
        assertEquals(StationService.WorkingMove.ENTER, StationService.workingMove(null, null, "w:1:2:3", "Working"));
        assertEquals(StationService.WorkingMove.EXIT, StationService.workingMove("w:1:2:3", "Working", "w:9:9:9", null));
    }

    // ==================== an effect's Target outside a Presentation ====================

    private static Set<String> codesFor(ActionDef action) {
        StationAsset station = StationAsset.of("fixture_station",
                StationAsset.Identity.of("rpgstations.station.fixture_station.name",
                        "rpgstations.station.fixture_station.desc", "Fixture_Icon"),
                action);
        return StationValidator.validate(List.of(station), id -> true, id -> true, id -> true).stream()
                .map(Finding::code).collect(Collectors.toSet());
    }

    @Test
    void aTargetOnAPuppetHideEffect_isFlaggedAsMeaningNothing() throws Exception {
        Puppet puppet = Puppet.CODEC.decodeJson(RawJsonReader.fromJsonString(
                "{ \"Enabled\": true, \"Hide\": { \"Route\": \"Effect\","
                        + " \"Effect\": { \"Id\": \"Fixture_Hide\", \"Target\": \"Puppet\" } } }"),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
        Set<String> codes = codesFor(ActionDef.of("Fixture_Action")
                .withWorker(ActionDef.Worker.of(null, null, null, puppet)));
        assertTrue(codes.contains("EFFECT_TARGET_IGNORED"), codes.toString());
    }

    @Test
    void aTargetOnAnEffectReward_isFlaggedAsMeaningNothing_butAPresentationsEffectReadsIt() {
        LootGrants withTarget = LootGrants.of(null, null, null, new LootGrants.Reward[] {
                LootGrants.Reward.of(StationRewardKinds.KIND_EFFECT, Map.of("Id", "Fixture_Aura", "Target", "Puppet"))});
        Set<String> reward = codesFor(ActionDef.of("Fixture_Action").withBonus(LootRef.of(null,
                new Roll[] {Roll.of("Cycle", null, null, null, withTarget, null)})));
        assertTrue(reward.contains("EFFECT_TARGET_IGNORED"), reward.toString());

        Set<String> moment = codesFor(ActionDef.of("Fixture_Action").withMoments(Map.of("Cycle",
                Presentation.of(null, null, null, null, null, EffectRef.of("Fixture_Aura", null, "Player"), null))));
        assertFalse(moment.contains("EFFECT_TARGET_IGNORED"), "a presentation's Effect is where Target is read: "
                + moment);
    }

    // ==================== extension overlays keep every leaf ====================

    @Test
    void anExtensionOverlay_keepsTheReadyAndOverdoneNames_andAnEffectsTarget() {
        Custody.States base = Custody.States.of("Empty", "Loaded", "Working", "Ready", "Overdone");
        Custody.States merged = ExtensionCatalog.overlayStates(base, Custody.States.of(null, null, "Lit"));
        assertEquals("Lit", merged.getWorking());
        assertEquals("Ready", merged.getReady(), "an overlay authoring States keeps the base's Ready name");
        assertEquals("Overdone", merged.getOverdone());
        assertEquals("Burnt", ExtensionCatalog.overlayStates(base,
                Custody.States.of(null, null, null, null, "Burnt")).getOverdone());

        EffectRef effect = ExtensionCatalog.overlayEffectRef(EffectRef.of("Fixture_Aura", 500L, "Puppet"),
                EffectRef.of(null, 900L));
        assertEquals("Puppet", effect.getTarget(), "the base's Target rides through an overlay of another leaf");
        assertEquals(900L, effect.getDurationMs());
        assertEquals("Player", ExtensionCatalog.overlayEffectRef(EffectRef.of("Fixture_Aura", null, "Puppet"),
                EffectRef.of(null, null, "Player")).getTarget());
    }

    // ==================== expected items feed the ordinary rows, found items the find rows ====================

    private static void routesByOrigin(String body, String ordinarySink, String findSink) {
        List<String> expected = SourcePins.loopBodies(body, "result.getExpectedItems()");
        List<String> found = SourcePins.loopBodies(body, "result.getFoundItems()");
        assertFalse(expected.isEmpty(), "the expected items are routed");
        assertFalse(found.isEmpty(), "the found items are routed");
        for (String loop : expected) {
            assertFalse(loop.contains("luckItems") || loop.contains("notifyLuckyFind"),
                    "an expected item never reaches a find row: " + loop);
        }
        for (String loop : found) {
            assertFalse(loop.contains("producedItems") || loop.contains("notifyItemGain"),
                    "a found item never reaches an ordinary row: " + loop);
        }
        assertTrue(expected.stream().anyMatch(loop -> loop.contains(ordinarySink)),
                "expected items reach " + ordinarySink);
        assertTrue(found.stream().anyMatch(loop -> loop.contains(findSink)), "found items reach " + findSink);
    }

    @Test
    void anAttendedPass_routesExpectedItemsToOrdinaryRows_andFindsToGoldRows() throws IOException {
        String body = SourcePins.methodBody(SourcePins.read("StationService.java"), "static void applyGrantResult(");
        routesByOrigin(body, "s.producedItems.merge", "s.luckItems.merge");
        routesByOrigin(body, "notifyItemGain(", "notifyLuckyFind(");
    }

    @Test
    void aGather_routesExpectedItemsToOrdinaryRows_andFindsToGoldRows() throws IOException {
        String body = SourcePins.methodBody(SourcePins.read("StationService.java"),
                "private void applyGatherGrantResult(");
        routesByOrigin(body, "notifyItemGain(", "notifyLuckyFind(");
    }
}
