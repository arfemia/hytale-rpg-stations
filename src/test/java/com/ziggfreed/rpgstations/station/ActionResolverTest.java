package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.ContributionScale;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.LootRef;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The PURE action resolution (a self-contained action plus its optional {@code Ref} base, with NO
 * station-level fallback) and the ordered-array selection core, unit-tested with plain value objects
 * and an injected {@code refLookup} - zero engine/store touch.
 */
public class ActionResolverTest {

    private static StationAsset.Work work(long cycleMs) {
        return StationAsset.Work.of(cycleMs, null, null, null);
    }

    private static ActionDef def(String id) {
        return ActionDef.of(id);
    }

    private static StationAsset station(ActionDef... actions) {
        return StationAsset.of("fixture", null, actions);
    }

    /** A refLookup that never resolves a Ref (the no-standalone-actions case). */
    private static final Function<String, ActionDef> NO_REFS = id -> null;

    // ==================== actionIds ====================

    @Test
    void actionIds_noActions_isEmpty_soTheStationIsInert() {
        assertEquals(List.of(), ActionResolver.actionIds(new StationAsset()));
        assertNull(ActionResolver.firstActionId(new StationAsset()));
    }

    @Test
    void actionIds_authoredOrderPreserved() {
        StationAsset a = station(def("Convert"), def("Enhance"));
        assertEquals(List.of("Convert", "Enhance"), ActionResolver.actionIds(a));
        assertEquals("Convert", ActionResolver.firstActionId(a));
    }

    @Test
    void effectiveActionId_fallsBackToRefThenPosition() {
        assertEquals("Mill", ActionResolver.effectiveActionId(def("Mill"), 3));
        assertEquals("PrepFish", ActionResolver.effectiveActionId(ActionDef.of(null, "PrepFish"), 3));
        assertEquals("3", ActionResolver.effectiveActionId(new ActionDef(), 3));
    }

    @Test
    void findAction_matchesCaseInsensitively() {
        StationAsset a = station(def("Convert"), def("Enhance"));
        assertSame(a.getActions()[1], ActionResolver.findAction(a, "enhance"));
        assertNull(ActionResolver.findAction(a, "nothing"));
    }

    // ==================== resolve: no station fallback exists ====================

    @Test
    void resolve_readsTheActionsOwnGroups() {
        StationAsset.Work actionWork = work(3800L);
        LootRef bonus = LootRef.of(new String[] {"FixtureFinds"}, null);
        StationAsset a = station(def("Convert").withWork(actionWork).withBonus(bonus));
        ActionResolver.ResolvedAction resolved = ActionResolver.resolve(a, "Convert", NO_REFS);
        assertSame(actionWork, resolved.getWork());
        assertSame(bonus, resolved.getBonus());
        assertEquals("Convert", resolved.getActionId());
    }

    @Test
    void resolve_unknownActionId_resolvesEveryGroupToNull() {
        StationAsset a = station(def("Convert").withWork(work(3800L)));
        ActionResolver.ResolvedAction resolved = ActionResolver.resolve(a, "Missing", NO_REFS);
        assertEquals("Missing", resolved.getActionId());
        assertNull(resolved.getWork());
        assertNull(resolved.getRecipe());
    }

    @Test
    void resolve_flattensWorkerAndMomentsOntoTheOneQuestionPerCallAccessors() {
        StationAsset.Hold hold = StationAsset.Hold.of(true, "Fixture_Hold", true);
        StationAsset.Camera camera = StationAsset.Camera.of(true, true, "LookRot");
        StationAsset.Animation animation = StationAsset.Animation.of("Fixture_Emote");
        Puppet puppet = Puppet.of(true, null, null, null, null, null);
        com.ziggfreed.rpgstations.asset.Presentation cycle =
                com.ziggfreed.rpgstations.asset.Presentation.ofSound("Fixture_Cycle");
        com.ziggfreed.rpgstations.asset.Presentation completion =
                com.ziggfreed.rpgstations.asset.Presentation.ofSound("Fixture_Done");
        StationAsset a = station(def("Mill")
                .withWorker(ActionDef.Worker.of(hold, camera, animation, puppet))
                .withMoments(ActionDef.Moments.of(cycle, completion)));

        ActionResolver.ResolvedAction resolved = ActionResolver.resolve(a, "Mill", NO_REFS);
        assertSame(hold, resolved.getHold());
        assertSame(camera, resolved.getCamera());
        assertSame(animation, resolved.getAnimation());
        assertSame(puppet, resolved.getPuppet());
        assertSame(cycle, resolved.getPresentation());
        assertSame(completion, resolved.getCompletion());
    }

    // ==================== resolve: Ref overlay ====================

    @Test
    void resolve_refOnly_baseActionSuppliesEveryGroup() {
        StationAsset.Work baseWork = work(4200L);
        LootRef baseBonus = LootRef.of(new String[] {"FishFinds"}, null);
        ContributionScale baseScale = ContributionScale.of(null,
                new ContributionScale.Floor[] {ContributionScale.Floor.of(1.0, 2.0)});
        ActionDef base = new ActionDef().withWork(baseWork).withBonus(baseBonus)
                .withContributionScale(baseScale);
        StationAsset a = station(ActionDef.of("Prep", "PrepFish"));
        Function<String, ActionDef> refs = id -> "PrepFish".equals(id) ? base : null;

        ActionResolver.ResolvedAction resolved = ActionResolver.resolve(a, "Prep", refs);
        assertSame(baseWork, resolved.getWork(), "a Ref-only entry inherits the base action's Work");
        assertSame(baseBonus, resolved.getBonus(), "a Ref-only entry inherits the base action's Bonus");
        assertSame(baseScale, resolved.getContributionScale());
    }

    @Test
    void resolve_refPlusOverlay_inlineGroupReplacesTheBaseWholesale() {
        StationAsset.Work baseWork = work(4200L);
        StationAsset.Work overlayWork = work(9000L);
        ActionDef base = new ActionDef().withWork(baseWork);
        StationAsset a = station(ActionDef.of("QuickPrep", "PrepFish").withWork(overlayWork));
        Function<String, ActionDef> refs = id -> "PrepFish".equals(id) ? base : null;

        assertSame(overlayWork, ActionResolver.resolve(a, "QuickPrep", refs).getWork(),
                "the inline overlay Work REPLACES the Ref base's Work wholesale");
    }

    @Test
    void resolve_danglingRef_resolvesAsIfNoRefExisted() {
        StationAsset.Work ownWork = work(5000L);
        StationAsset a = station(ActionDef.of("Prep", "Missing").withWork(ownWork));
        assertSame(ownWork, ActionResolver.resolve(a, "Prep", NO_REFS).getWork(),
                "a dangling Ref degrades gracefully to the entry's own groups");
        assertNull(ActionResolver.resolve(a, "Prep", NO_REFS).getBonus());
    }

    @Test
    void actionTargetId_prefersTheRefIdThenTheOwnId() {
        StationAsset a = station(ActionDef.of("Prep", "PrepFish"), def("Mill"));
        assertEquals("PrepFish", ActionResolver.actionTargetId(a, "Prep"));
        assertEquals("Mill", ActionResolver.actionTargetId(a, "Mill"));
        assertNull(ActionResolver.actionTargetId(a, "Nothing"));
    }

    // ==================== selection: authored order IS priority ====================

    @Test
    void selectAction_firstAuthoredMatchWins() {
        StationAsset a = station(
                def("Convert").withSelect(ActionInput.of(null, "Fixture_Bars", null, null)),
                def("Enhance").withSelect(ActionInput.of(null, null, null, "Weapon")));
        assertEquals("Convert", ActionResolver.selectAction(a, null, "Fixture_Bars", null, null));
        assertEquals("Enhance", ActionResolver.selectAction(a, "Fixture_Sword", null, null, "Weapon"));
        assertNull(ActionResolver.selectAction(a, "Dirt", null, null, null));
    }

    @Test
    void selectAction_amongSeveralMatchingActionsTheFirstAuthoredWins() {
        // Both actions match the same held family; only the authored ORDER separates them.
        ActionInput sameRoute = ActionInput.of(null, "Fixture_Family", null, null);
        StationAsset first = station(def("Alpha").withSelect(sameRoute), def("Beta").withSelect(sameRoute));
        assertEquals("Alpha", ActionResolver.selectAction(first, null, "Fixture_Family", null, null));

        StationAsset flipped = station(def("Beta").withSelect(sameRoute), def("Alpha").withSelect(sameRoute));
        assertEquals("Beta", ActionResolver.selectAction(flipped, null, "Fixture_Family", null, null),
                "reordering the array is the whole mechanism for changing selection priority");
    }

    @Test
    void selectAction_anAbsentSelectMatchesAnything() {
        StationAsset a = station(def("Anything"));
        assertEquals("Anything", ActionResolver.selectAction(a, "Whatever", null, null, null));
        assertEquals("Anything", ActionResolver.selectAction(a, null, null, null, null));
    }

    @Test
    void selectAction_catchAllShadowsEverythingAfterIt() {
        StationAsset a = station(def("Any"),
                def("Specific").withSelect(ActionInput.of("Fixture_Item", null, null, null)));
        assertEquals("Any", ActionResolver.selectAction(a, "Fixture_Item", null, null, null),
                "the catch-all is authored first, so nothing after it is ever reached");
    }

    @Test
    void selectAction_noActions_selectsNothing() {
        assertNull(ActionResolver.selectAction(new StationAsset(), "Anything", null, null, null));
        assertNull(ActionResolver.selectActionByFamily(new StationAsset(), "Anything",
                new String[] {"Fixture_Family"}, null, null));
    }

    @Test
    void selectActionByFamily_matchesAnyOfTheHeldResourceFamilies() {
        StationAsset a = station(def("Mill").withSelect(ActionInput.of(null, "Fixture_Trunk", null, null)));
        assertEquals("Mill", ActionResolver.selectActionByFamily(a, "Fixture_Oak_Trunk",
                new String[] {"Fixture_Wood", "Fixture_Trunk"}, null, null));
        assertNull(ActionResolver.selectActionByFamily(a, "Fixture_Stone",
                new String[] {"Fixture_Rock"}, null, null));
    }

    // ==================== selectActionForBlockState (restart-orphan recovery) ====================

    private static Custody custodyLoaded(String loadedStateName) {
        return Custody.of(100, null, Custody.States.of("Default", loadedStateName));
    }

    private static StationAsset twoStateStation() {
        return station(def("Convert").withCustody(custodyLoaded("BarsPlaced")),
                def("Enhance").withCustody(custodyLoaded("WeaponPlaced")));
    }

    @Test
    void selectActionForBlockState_matchesLoadedStateName_caseInsensitive() {
        StationAsset a = twoStateStation();
        assertEquals("Convert", ActionResolver.selectActionForBlockState(a, "BarsPlaced"));
        assertEquals("Enhance", ActionResolver.selectActionForBlockState(a, "WeaponPlaced"));
        assertEquals("Convert", ActionResolver.selectActionForBlockState(a, "barsplaced"));
    }

    @Test
    void selectActionForBlockState_idleOrBlankOrUnmatched_returnsNull() {
        StationAsset a = twoStateStation();
        assertNull(ActionResolver.selectActionForBlockState(a, "Default"));
        assertNull(ActionResolver.selectActionForBlockState(a, null));
        assertNull(ActionResolver.selectActionForBlockState(a, ""));
    }

    @Test
    void selectActionForBlockState_singleActionStation_resolvesItsOwnCustody() {
        StationAsset a = station(def("Mill").withCustody(custodyLoaded("Loaded")));
        assertEquals("Mill", ActionResolver.selectActionForBlockState(a, "Loaded"));
        assertNull(ActionResolver.selectActionForBlockState(a, "Default"));
    }

    /** Kept so the unused-import lint stays honest about the LinkedHashMap-era fixtures being gone. */
    @Test
    void anchors_areAPerActionMap() {
        Map<String, ActionDef.Anchor> anchors = new LinkedHashMap<>();
        anchors.put("Fire", ActionDef.Anchor.of("CookingFire", 8.0));
        StationAsset a = station(def("Prep").withAnchors(anchors));
        assertSame(anchors, ActionResolver.resolve(a, "Prep", NO_REFS).getAnchors());
    }
}
