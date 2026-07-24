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
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.LootRef;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The PURE whole-group-override resolution (design 9.1) + scope-2 {@code Ref} overlay (design 1.5)
 * + diegetic action-selection core, unit-tested with plain value objects and an injected
 * {@code refLookup} - zero engine/store touch.
 */
public class ActionResolverTest {

    private static StationAsset.Work work(long cycleMs) {
        return StationAsset.Work.of(cycleMs, null, null, null, null);
    }

    private static ActionDef def() {
        return new ActionDef();
    }

    private static Map<String, ActionDef> actions(String k1, ActionDef v1) {
        Map<String, ActionDef> m = new LinkedHashMap<>();
        m.put(k1, v1);
        return m;
    }

    /** A refLookup that never resolves a Ref (the no-standalone-actions case). */
    private static final Function<String, ActionDef> NO_REFS = id -> null;

    // ==================== actionIds ====================

    @Test
    void actionIds_absentActionsMap_resolvesTheImplicitWorkAction() {
        assertEquals(List.of(ActionResolver.ACTION_WORK), ActionResolver.actionIds(new StationAsset()));
    }

    @Test
    void actionIds_authoredOrderPreserved() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("convert", def());
        actions.put("enhance", def());
        assertEquals(List.of("convert", "enhance"), ActionResolver.actionIds(new StationAsset().withActions(actions)));
    }

    // ==================== resolve (whole-group override) ====================

    @Test
    void resolve_noActionsMap_everyGroupFallsBackToTheStationItself() {
        StationAsset.Work stationWork = work(5000L);
        StationAsset a = StationAsset.of("sawmill", null, stationWork, null, null, null, null, null, null, null);
        assertSame(stationWork, ActionResolver.resolve(a, ActionResolver.ACTION_WORK, NO_REFS).getWork());
    }

    @Test
    void resolve_actionOverridesWork_ownGroupWinsWholesale() {
        StationAsset.Work stationWork = work(5000L);
        StationAsset.Work actionWork = work(3800L);
        StationAsset a = StationAsset.of("anvil", null, stationWork, null, null, null, null, null, null, null)
                .withActions(actions("convert", def().withWork(actionWork)));
        assertSame(actionWork, ActionResolver.resolve(a, "convert", NO_REFS).getWork());
    }

    @Test
    void resolve_actionOmitsGroup_inheritsTheStationDefault() {
        StationAsset.Work stationWork = work(5000L);
        LootRef stationLoot = LootRef.of(new String[]{"sawmillfinds"}, null);
        StationAsset a = StationAsset.of("anvil", null, stationWork, null, null, null, null, null, null, null, stationLoot)
                .withActions(actions("convert", def().withInput(ActionInput.of(null, "Metal_Bars", null, null))));
        ActionResolver.ResolvedAction resolved = ActionResolver.resolve(a, "convert", NO_REFS);
        assertSame(stationWork, resolved.getWork(), "Work omitted on the action - falls back to the station's own");
        assertSame(stationLoot, resolved.getLoot(), "Loot omitted on the action - falls back to the station's own");
    }

    // ==================== resolve (Ref overlay, scope-2 1.5) ====================

    @Test
    void resolve_refOnly_baseActionSuppliesEveryGroup() {
        StationAsset.Work baseWork = work(4200L);
        LootRef baseLoot = LootRef.of(new String[]{"fishfinds"}, null);
        ActionDef base = def().withWork(baseWork).withLoot(baseLoot);
        StationAsset a = new StationAsset().withActions(actions("prepfish", def().withRef("prepfish")));
        Function<String, ActionDef> refs = id -> "prepfish".equals(id) ? base : null;

        ActionResolver.ResolvedAction resolved = ActionResolver.resolve(a, "prepfish", refs);
        assertSame(baseWork, resolved.getWork(), "a Ref-only entry inherits the base action's Work");
        assertSame(baseLoot, resolved.getLoot(), "a Ref-only entry inherits the base action's Loot");
    }

    @Test
    void resolve_refPlusOverlay_inlineGroupWinsOverTheBase() {
        StationAsset.Work baseWork = work(4200L);
        StationAsset.Work overlayWork = work(9000L);
        ActionDef base = def().withWork(baseWork);
        StationAsset a = new StationAsset()
                .withActions(actions("quickfix", def().withRef("prepfish").withWork(overlayWork)));
        Function<String, ActionDef> refs = id -> "prepfish".equals(id) ? base : null;

        assertSame(overlayWork, ActionResolver.resolve(a, "quickfix", refs).getWork(),
                "the inline overlay Work REPLACES the Ref base's Work wholesale");
    }

    @Test
    void resolve_danglingRef_fallsBackToInlineThenStation() {
        StationAsset.Work stationWork = work(5000L);
        StationAsset a = StationAsset.of("anvil", null, stationWork, null, null, null, null, null, null, null)
                .withActions(actions("prepfish", def().withRef("missing")));
        // The refLookup returns null (dangling) - resolution proceeds as if no ref existed.
        assertSame(stationWork, ActionResolver.resolve(a, "prepfish", NO_REFS).getWork(),
                "a dangling Ref degrades gracefully to the station default");
    }

    // ==================== resolve (Puppet whole-group override) ====================

    @Test
    void resolve_actionOverridesPuppet_ownGroupWinsWholesale() {
        Puppet stationPuppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_SCALE, null), null, null, null, null);
        Puppet actionPuppet = Puppet.of(false, null, null, null, null, null);
        StationAsset a = new StationAsset().withPuppet(stationPuppet)
                .withActions(actions("enhance", def().withPuppet(actionPuppet)));
        assertSame(actionPuppet, ActionResolver.resolve(a, "enhance", NO_REFS).getPuppet());
    }

    // ==================== selectAction (diegetic, first match wins) ====================

    @Test
    void selectAction_noActionsMap_alwaysResolvesWork() {
        assertEquals(ActionResolver.ACTION_WORK,
                ActionResolver.selectAction(new StationAsset(), "Anything", null, null, null));
    }

    @Test
    void selectAction_firstMatchingRouteWins_andCatchAll() {
        Map<String, ActionDef> map = new LinkedHashMap<>();
        map.put("convert", def().withInput(ActionInput.of(null, "Metal_Bars", null, null)));
        map.put("enhance", def().withInput(ActionInput.of(null, null, null, "Weapon")));
        StationAsset a = new StationAsset().withActions(map);
        assertEquals("convert", ActionResolver.selectAction(a, null, "Metal_Bars", null, null));
        assertEquals("enhance", ActionResolver.selectAction(a, "Iron_Sword", null, null, "Weapon"));
        assertNull(ActionResolver.selectAction(a, "Dirt", null, null, null));
    }

    // ==================== selectActionForBlockState (R5 fix) ====================

    private static Custody custodyLoaded(String loadedStateName) {
        return Custody.of(100, null, Custody.States.of("Default", loadedStateName));
    }

    private static StationAsset anvilShapedAsset() {
        Map<String, ActionDef> map = new LinkedHashMap<>();
        map.put("convert", def().withCustody(custodyLoaded("BarsPlaced")));
        map.put("enhance", def().withCustody(custodyLoaded("WeaponPlaced")));
        return new StationAsset().withActions(map);
    }

    @Test
    void selectActionForBlockState_matchesLoadedStateName_caseInsensitive() {
        StationAsset a = anvilShapedAsset();
        assertEquals("convert", ActionResolver.selectActionForBlockState(a, "BarsPlaced"));
        assertEquals("enhance", ActionResolver.selectActionForBlockState(a, "WeaponPlaced"));
        assertEquals("convert", ActionResolver.selectActionForBlockState(a, "barsplaced"));
    }

    @Test
    void selectActionForBlockState_idleOrBlankOrUnmatched_returnsNull() {
        StationAsset a = anvilShapedAsset();
        assertNull(ActionResolver.selectActionForBlockState(a, "Default"));
        assertNull(ActionResolver.selectActionForBlockState(a, null));
        assertNull(ActionResolver.selectActionForBlockState(a, ""));
    }

    @Test
    void selectActionForBlockState_singleActionStation_stationLevelCustodyResolvesImplicitWork() {
        StationAsset a = new StationAsset().withCustody(custodyLoaded("Loaded"));
        assertEquals(ActionResolver.ACTION_WORK, ActionResolver.selectActionForBlockState(a, "Loaded"));
        assertNull(ActionResolver.selectActionForBlockState(a, "Default"));
    }
}
