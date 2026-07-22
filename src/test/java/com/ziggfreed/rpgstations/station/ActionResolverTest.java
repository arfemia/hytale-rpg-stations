package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The PURE whole-group-override resolution (design section 9.1) and diegetic action-selection
 * core, unit-tested with plain {@link StationAsset}/{@link ActionDef} value objects - zero
 * engine/store touch.
 */
public class ActionResolverTest {

    private static StationAsset.Work work(long cycleMs) {
        return StationAsset.Work.of(cycleMs, null, null, null, null);
    }

    // ==================== actionIds ====================

    @Test
    void actionIds_absentActionsMap_resolvesTheImplicitWorkAction() {
        StationAsset a = new StationAsset();
        assertEquals(List.of(ActionResolver.ACTION_WORK), ActionResolver.actionIds(a));
    }

    @Test
    void actionIds_emptyActionsMap_resolvesTheImplicitWorkAction() {
        StationAsset a = new StationAsset().withActions(new LinkedHashMap<>());
        assertEquals(List.of(ActionResolver.ACTION_WORK), ActionResolver.actionIds(a));
    }

    @Test
    void actionIds_authoredOrderPreserved() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("convert", ActionDef.of(null, null, null, null, null, null, null, null, null, null, null, null, null));
        actions.put("enhance", ActionDef.of(null, null, null, null, null, null, null, null, null, null, null, null, null));
        StationAsset a = new StationAsset().withActions(actions);
        assertEquals(List.of("convert", "enhance"), ActionResolver.actionIds(a));
    }

    // ==================== resolve (whole-group override) ====================

    @Test
    void resolve_noActionsMap_everyGroupFallsBackToTheStationItself() {
        StationAsset.Work stationWork = work(5000L);
        StationAsset a = StationAsset.of("sawmill", null, stationWork, null, null, null, null, null, null, null);
        ActionResolver.ResolvedAction resolved = ActionResolver.resolve(a, ActionResolver.ACTION_WORK);
        assertSame(stationWork, resolved.getWork());
    }

    @Test
    void resolve_actionOverridesWork_ownGroupWinsWholesale() {
        StationAsset.Work stationWork = work(5000L);
        StationAsset.Work actionWork = work(3800L);
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("convert", ActionDef.of(null, null, actionWork, null, null, null, null, null, null, null, null, null, null));
        StationAsset a = StationAsset.of("anvil", null, stationWork, null, null, null, null, null, null, null);
        a.withActions(actions);

        ActionResolver.ResolvedAction resolved = ActionResolver.resolve(a, "convert");
        assertSame(actionWork, resolved.getWork(), "the action's own Work REPLACES the station-level default wholesale");
    }

    @Test
    void resolve_actionOmitsGroup_inheritsTheStationDefault() {
        StationAsset.Work stationWork = work(5000L);
        StationAsset.Loot stationLoot = StationAsset.Loot.of(new String[]{"sawmillfinds"}, null);
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        // "convert" authors ONLY an Input matcher - every other group must fall through to the station.
        actions.put("convert", ActionDef.of(null, ActionInput.of(null, "Metal_Ingot", null, null),
                null, null, null, null, null, null, null, null, null, null, null));
        StationAsset a = StationAsset.of("anvil", null, stationWork, null, null, null, null, null, null, null,
                stationLoot);
        a.withActions(actions);

        ActionResolver.ResolvedAction resolved = ActionResolver.resolve(a, "convert");
        assertSame(stationWork, resolved.getWork(), "Work omitted on the action - falls back to the station's own");
        assertSame(stationLoot, resolved.getLoot(), "Loot omitted on the action - falls back to the station's own");
    }

    @Test
    void resolve_unknownActionId_everyGroupFallsBackToTheStation() {
        StationAsset.Work stationWork = work(5000L);
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("convert", ActionDef.of(null, null, work(3800L), null, null, null, null, null, null, null, null, null, null));
        StationAsset a = StationAsset.of("anvil", null, stationWork, null, null, null, null, null, null, null);
        a.withActions(actions);

        ActionResolver.ResolvedAction resolved = ActionResolver.resolve(a, "does-not-exist");
        assertSame(stationWork, resolved.getWork());
        assertEquals("does-not-exist", resolved.getActionId());
    }

    // ==================== selectAction (diegetic, first match wins) ====================

    @Test
    void selectAction_noActionsMap_alwaysResolvesWork() {
        StationAsset a = new StationAsset();
        assertEquals(ActionResolver.ACTION_WORK, ActionResolver.selectAction(a, "Anything", null, null, null));
    }

    @Test
    void selectAction_firstMatchingRouteWins() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("convert", ActionDef.of(null, ActionInput.of(null, "Metal_Ingot", null, null),
                null, null, null, null, null, null, null, null, null, null, null));
        actions.put("enhance", ActionDef.of(null, ActionInput.of(null, null, null, "Weapon"),
                null, null, null, null, null, null, null, null, null, null, null));
        StationAsset a = new StationAsset().withActions(actions);

        assertEquals("convert", ActionResolver.selectAction(a, null, "Metal_Ingot", null, null));
        assertEquals("enhance", ActionResolver.selectAction(a, "Iron_Sword", null, null, "Weapon"));
        assertNull(ActionResolver.selectAction(a, "Dirt", null, null, null), "nothing matches, no catch-all authored");
    }

    @Test
    void selectAction_catchAllMatchesAnything() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("convert", ActionDef.of(null, ActionInput.of(null, "Metal_Ingot", null, null),
                null, null, null, null, null, null, null, null, null, null, null));
        actions.put("misc", ActionDef.of(null, null, null, null, null, null, null, null, null, null, null, null, null));
        StationAsset a = new StationAsset().withActions(actions);

        assertEquals("misc", ActionResolver.selectAction(a, "Dirt", null, null, null),
                "the trailing catch-all (no Input group) matches anything the earlier route missed");
    }

    // ==================== selectActionForBlockState (R5 fix - restart-orphan recovery) ====================

    private static Custody custodyLoaded(String loadedStateName) {
        return Custody.of(100, null, Custody.States.of("Default", loadedStateName));
    }

    /** The exact anvil shape (design 9.5): convert=BarsPlaced, enhance=WeaponPlaced. */
    private static StationAsset anvilShapedAsset() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("convert", ActionDef.of(null, null, null, null, null, null, null, null, null, null, null, null, null)
                .withCustody(custodyLoaded("BarsPlaced")));
        actions.put("enhance", ActionDef.of(null, null, null, null, null, null, null, null, null, null, null, null, null)
                .withCustody(custodyLoaded("WeaponPlaced")));
        return new StationAsset().withActions(actions);
    }

    @Test
    void selectActionForBlockState_matchesTheFirstActionWhoseLoadedStateNameMatches() {
        StationAsset a = anvilShapedAsset();
        assertEquals("convert", ActionResolver.selectActionForBlockState(a, "BarsPlaced"));
        assertEquals("enhance", ActionResolver.selectActionForBlockState(a, "WeaponPlaced"));
    }

    @Test
    void selectActionForBlockState_caseInsensitive() {
        StationAsset a = anvilShapedAsset();
        assertEquals("convert", ActionResolver.selectActionForBlockState(a, "barsplaced"));
    }

    @Test
    void selectActionForBlockState_genuinelyIdleStateName_noFalseRescue() {
        StationAsset a = anvilShapedAsset();
        assertNull(ActionResolver.selectActionForBlockState(a, "Default"),
                "a genuinely-idle (never-loaded) state name must not falsely resolve an action");
    }

    @Test
    void selectActionForBlockState_nullOrBlankStateName_returnsNull() {
        StationAsset a = anvilShapedAsset();
        assertNull(ActionResolver.selectActionForBlockState(a, null));
        assertNull(ActionResolver.selectActionForBlockState(a, ""));
    }

    @Test
    void selectActionForBlockState_noActionAuthorsCustody_alwaysNull() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("work", ActionDef.of(null, null, null, null, null, null, null, null, null, null, null, null, null));
        StationAsset a = new StationAsset().withActions(actions);
        assertNull(ActionResolver.selectActionForBlockState(a, "BarsPlaced"));
        assertNull(ActionResolver.selectActionForBlockState(a, "Default"));
    }

    @Test
    void selectActionForBlockState_singleActionStation_stationLevelCustodyStillResolvesImplicitWork() {
        // No Actions map at all - the implicit ACTION_WORK action inherits the station-level
        // Custody wholesale (ActionResolver.resolve's own byte-stable-regression framing).
        StationAsset a = new StationAsset().withCustody(custodyLoaded("Loaded"));
        assertEquals(ActionResolver.ACTION_WORK, ActionResolver.selectActionForBlockState(a, "Loaded"));
        assertNull(ActionResolver.selectActionForBlockState(a, "Default"));
    }
}
