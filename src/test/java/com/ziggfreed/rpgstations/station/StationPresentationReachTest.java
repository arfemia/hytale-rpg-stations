package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.codec.Vec3;
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
 * when no double stands), the rule that only a system that provably ends on its own rides an entity,
 * and only an entity that is the standing prop or the double ({@link MomentBursts}), with its
 * validator findings, where a cue aimed at a consumed piece lands ({@link StationService#restingPosition}:
 * under the piece's last per-beat look), the socket the placed-piece preview names
 * ({@link StationService#placedPiece}), the block-state move a beat makes
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
    void theBursts_splitIntoTheOnesThatRideAndTheOnesThatPlayAtAPosition() {
        Presentation.ModelParticle bounded = Presentation.ModelParticle.of("Fixture_Bounded");
        Presentation.ModelParticle endless = Presentation.ModelParticle.of("Fixture_Endless");
        Presentation.ModelParticle[] all = {bounded, endless, Presentation.ModelParticle.of(" "), null};
        List<Presentation.ModelParticle> riding = MomentBursts.bursts(true, all, "Fixture_Bounded"::equals);
        List<Presentation.ModelParticle> positional = MomentBursts.bursts(false, all, "Fixture_Bounded"::equals);
        assertEquals(List.of(bounded), riding);
        assertEquals(List.of(endless), positional, "an endless system never rides; a blank entry is in neither");
    }

    // ==================== the moment player's ride-versus-position split ====================

    private static final Presentation.ModelParticle BOUNDED = Presentation.ModelParticle.of("Fixture_Bounded");
    private static final Presentation.ModelParticle ENDLESS = Presentation.ModelParticle.of("Fixture_Endless");
    private static final Presentation.ModelParticle[] BOTH = {ENDLESS, BOUNDED, null};

    @Test
    void atAnEntityAim_aBoundedSystemRides_andAnEndlessOnePlaysAtTheEntitysPosition() {
        for (StationService.AimSource entity : List.of(StationService.AimSource.DISPLAY_PROP,
                StationService.AimSource.DOUBLE)) {
            MomentBursts split = MomentBursts.plan(entity, BOTH, "Fixture_Bounded"::equals);
            assertEquals(List.of(BOUNDED), split.riding(), entity + ": the bounded system rides");
            assertEquals(List.of(ENDLESS), split.positional(), entity + ": the endless one plays capped at a spot");
            assertEquals(List.of(ENDLESS), split.atPosition(true), "delivered: only the endless one plays at the spot");
            assertEquals(List.of(BOUNDED, ENDLESS), split.atPosition(false),
                    "nobody received the attach: the riding burst plays at the spot too, so no cue is lost");
        }
    }

    @Test
    void atAPositionAim_nothingRides_andThePredicateIsNeverAsked() {
        for (StationService.AimSource position : List.of(StationService.AimSource.BLOCK,
                StationService.AimSource.DISPLAY_RESTING)) {
            MomentBursts split = MomentBursts.plan(position, BOTH, id -> {
                throw new AssertionError("a position aim asked whether " + id + " ends");
            });
            assertTrue(split.riding().isEmpty(), position + ": nothing rides");
            assertEquals(List.of(ENDLESS, BOUNDED), split.positional(), position + ": every burst, in authored order");
            assertEquals(List.of(ENDLESS, BOUNDED), split.atPosition(false));
        }
    }

    @Test
    void noAimEverRidesTheWorkersOwnBody() {
        // Every target kind over every standing combination: a burst rides only a standing prop or a
        // standing double, and a Puppet target with no double rides nothing (it plays at the block),
        // so the hidden player is never the entity a burst is attached to.
        for (Presentation.Target.Kind kind : Presentation.Target.Kind.values()) {
            for (int bits = 0; bits < 8; bits++) {
                boolean showsProp = (bits & 1) != 0;
                boolean propStands = (bits & 2) != 0;
                boolean doubleStands = (bits & 4) != 0;
                StationService.AimSource source = StationService.aimSource(kind, showsProp, propStands, doubleStands);
                boolean rides = !MomentBursts.plan(source, BOTH, id -> true).riding().isEmpty();
                boolean entityStands = source == StationService.AimSource.DISPLAY_PROP && propStands
                        || source == StationService.AimSource.DOUBLE && doubleStands;
                assertEquals(entityStands, rides, kind + " " + showsProp + "/" + propStands + "/" + doubleStands);
            }
        }
    }

    @Test
    void theMomentPlayer_splitsThroughThePlan_andItsAimNeverHoldsThePlayer() throws IOException {
        String source = SourcePins.read("StationService.java");
        String play = flat(SourcePins.methodBody(source, "private static void playMoment("));
        assertEquals(1, SourcePins.count(play,
                "MomentBursts.plan(aim.source(), p.getParticles(), ParticleLifetimes::systemProvablyEnds);"),
                "the moment player splits its bursts by where the aim came from, and only a system that "
                        + "provably ends may ride");
        assertEquals(1, SourcePins.count(play, "ParticleLifetimes::systemProvablyEnds"),
                "the one predicate that decides which bursts ride is the engine's own lifetime read");
        assertEquals(1, SourcePins.count(play,
                "boolean attached = spawnAttachedParticles(store, aim, bursts.riding());"),
                "the riding half is attached, and whether anyone received it is kept");
        assertEquals(1, SourcePins.count(play, "spawnAttachedParticles("),
                "only the riding half is ever attached");
        assertEquals(1, SourcePins.count(play, "bursts.atPosition(attached)"),
                "the positional half, led by an undelivered riding half, plays at the aim's position");
        String resolve = SourcePins.methodBody(source, "private static Aim resolveAim(");
        assertTrue(resolve.contains("s.puppetRef"), "the pin reads the real resolver");
        assertFalse(resolve.matches("(?s).*\\bs\\.ref\\b.*"),
                "the aim is built from the prop and the double, never the player's own ref");
    }

    // ==================== where a cue aimed at a consumed piece lands ====================

    private static final Custody.Display SOCKET_OWN = Custody.Display.of(Vec3.of(0.0, 0.55, 0.0), null, null);
    private static final Custody.Display LIFTED = Custody.Display.overlaid(SOCKET_OWN,
            Custody.Display.of(Vec3.of(0.0, 1.25, 0.2), null, null));

    @Test
    void aCueAtAConsumedPiece_landsUnderItsLastPerBeatLook() {
        Map<String, Custody.Display> shown = new LinkedHashMap<>();
        StationService.rememberShownDisplay(shown, "Main", LIFTED);
        assertEquals(LIFTED, StationService.restingDisplay(shown, "MAIN", () -> SOCKET_OWN),
                "the remembered overlay wins, whatever the socket id's spelling");
        assertArrayEquals(StationCustodyDisplay.resolvePosition(LIFTED, 4, 64, -7, 1.5),
                StationService.restingPosition(shown, "main", () -> SOCKET_OWN, 4, 64, -7, () -> 1.5), 1e-9,
                "where the lifted prop stood, through the spawn's own placement");
        assertNotEquals(StationCustodyDisplay.resolvePosition(SOCKET_OWN, 4, 64, -7, 1.5)[1],
                StationService.restingPosition(shown, "main", () -> SOCKET_OWN, 4, 64, -7, () -> 1.5)[1],
                "not the socket's un-lifted spot");
    }

    @Test
    void aSocketWithNoRememberedLook_restsUnderItsOwnDisplay_elseNowhere() {
        Map<String, Custody.Display> shown = new LinkedHashMap<>();
        StationService.rememberShownDisplay(shown, "Other", LIFTED);
        assertEquals(SOCKET_OWN, StationService.restingDisplay(shown, "main", () -> SOCKET_OWN),
                "another socket's overlay never leaks onto this one");
        assertArrayEquals(StationCustodyDisplay.resolvePosition(SOCKET_OWN, 0, 0, 0, 0.0),
                StationService.restingPosition(shown, "main", () -> SOCKET_OWN, 0, 0, 0, () -> 0.0), 1e-9);
        assertNull(StationService.restingPosition(shown, "main", () -> null, 0, 0, 0, () -> {
            throw new AssertionError("no look to place: the block's facing is never read");
        }), "a socket that shows no prop has no resting spot, and the caller plays at the block");
    }

    @Test
    void theRememberedLook_isWrittenWhereTheOverlaidPropSpawns_andReadWhereTheCueRests() throws IOException {
        String source = SourcePins.read("StationService.java");
        String apply = SourcePins.methodBody(source, "void applyStepDisplay(");
        assertEquals(1, SourcePins.count(apply, "rememberShownDisplay(s.shownDisplays, socketId, effective)"),
                "the step's overlaid look is what is remembered");
        String resting = SourcePins.methodBody(source, "private static Vector3d displayRestingPosition(");
        assertEquals(1, SourcePins.count(flat(resting),
                "restingPosition(s.shownDisplays, socketId, () -> socketDisplayFor(s, socketId),"),
                "the resting position reads the remembered look first, else the socket's own Display");
        assertFalse(source.contains("shownDisplays.put(") || source.contains("shownDisplays.get("),
                "the remembered looks are only ever touched through the one keyed pair");
    }

    // ==================== the socket the placed-piece preview names ====================

    private static Custody.ResolvedSocket socket(String id) {
        return new Custody.ResolvedSocket(id, true, null, null, null, 4, false, false, null, false, false, false, null);
    }

    @Test
    void thePreview_namesTheSocketThePieceActuallyWentInto() {
        Custody.ResolvedSocket held = socket("held_socket");
        Custody.ResolvedSocket found = socket("found_socket");
        StationService.PlacedPiece viaHeld = StationService.placedPiece(2, held, 0, null);
        assertTrue(viaHeld.placed());
        assertEquals(held, viaHeld.socket(), "the held route placed it");
        assertEquals(2, viaHeld.moved());

        StationService.PlacedPiece viaBackpack = StationService.placedPiece(0, held, 3, found);
        assertTrue(viaBackpack.placed());
        assertEquals(found, viaBackpack.socket(),
                "a held route that moved nothing never lends its socket to a piece the backpack scan placed");
        assertEquals(3, viaBackpack.moved());

        StationService.PlacedPiece nothing = StationService.placedPiece(0, held, 0, found);
        assertFalse(nothing.placed(), "nothing moved: no preview");
        assertNull(nothing.socket());
    }

    @Test
    void thePressSite_previewsThroughThePlacementItSettled() throws IOException {
        String toggle = flat(SourcePins.methodBody(SourcePins.read("StationService.java"), "public void toggle("));
        assertEquals(1, SourcePins.count(toggle, "heldMoved = placeIntoCustody("),
                "the held count is what the held placement moved");
        assertEquals(1, SourcePins.count(toggle, "foundMoved = placeIntoCustody("),
                "the backpack count is what the backpack placement moved");
        assertEquals(1, SourcePins.count(toggle, "foundSocket = found.socket();"),
                "the backpack route's socket is the one its scan found");
        assertEquals(1, SourcePins.count(toggle,
                "placedPiece(heldMoved, heldRoute.socket(), foundMoved, foundSocket)"));
        assertEquals(1, SourcePins.count(toggle, "if (placed.placed()) {"));
        assertEquals(1, SourcePins.count(toggle, "placed.socket());"), "the preview reads the settled socket");
    }

    /** A method body with every whitespace run folded to one space, so a pin survives a rewrap. */
    private static String flat(String body) {
        return body.replaceAll("\\s+", " ");
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
