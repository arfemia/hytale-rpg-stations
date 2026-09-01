package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.ziggfreed.common.world.record.BlockRecordSection;
import com.ziggfreed.common.world.stash.BlockStash;

/**
 * Custody persistence in STATION vocabulary: what {@code placeIntoCustody} writes into a block's
 * stash goes through the exact section codec a chunk save and load runs
 * ({@code BlockRecordSection.buildCodec(BlockStash.CODEC)}, the same public route ziggfreed-common's
 * own {@code BlockStashCodecTest} pins), comes back readable as a {@link StationCustodyClaim}, and
 * still drains oldest-placed-first. This is the "leave the logs in the sawmill, restart the
 * server, mill them" contract at the payload level.
 *
 * <p><b>In-JVM boundary (established by the zc stash tests):</b> the {@code Unique} leaf delegates
 * to the engine's own {@code ItemStack} codec, and a bare unit-test JVM cannot initialize that
 * class at all (its codec chain forces engine statics that need the running server's log manager).
 * So no fixture here authors {@code Unique}: the anvil's metadata-preserving placed weapon
 * surviving a restart is owned by in-game smoke, alongside the live chunk save/load round trip
 * itself (a real {@code ChunkStore} section cannot exist in a unit JVM either). What THIS test
 * owns is everything between: the payload written in station vocabulary surviving the section
 * codec byte-faithfully.
 */
class CustodyPersistenceTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    /** The zc-established injected family resolver - no live {@code Item} lookup in a unit JVM. */
    private static Function<String, String[]> families(Map<String, String[]> table) {
        return id -> table.getOrDefault(id, new String[0]);
    }

    @Test
    void placementShapedStashSurvivesTheSectionRoundTripAndDrainsOldestFirst() {
        // 1) PLACE: the same writes placeIntoCustody performs - stamp the new stash (tag + owner,
        //    whole-stash and main pile), then tally two placements in order.
        BlockStash placed = new BlockStash();
        StationCustodyClaim.stampNewStash(placed, OWNER, "sawmill", "work");
        StationCustodyClaim liveClaim = StationCustodyClaim.of(placed, 3, 64, 5, () -> { });
        assertNotNull(liveClaim);
        liveClaim.add("Wood_Oak_Log", 3);
        liveClaim.add("Wood_Pine_Log", 4);

        // 2) SAVE + LOAD: the whole section through the public buildCodec route - the exact
        //    payload wire a chunk save serializes and a chunk load decodes.
        BuilderCodec<BlockRecordSection<BlockStash>> sectionCodec =
                BlockRecordSection.buildCodec(BlockStash.CODEC);
        BlockRecordSection<BlockStash> section = sectionCodec.getDefaultValue();
        section.put(ChunkUtil.indexBlock(3, 4, 5), placed);
        ExtraInfo info = new ExtraInfo();
        BlockRecordSection<BlockStash> restoredSection =
                sectionCodec.decode(sectionCodec.encode(section, info), info);
        BlockStash restored = restoredSection.get(ChunkUtil.indexBlock(3, 4, 5));
        assertNotNull(restored, "the stash record survives the section round trip");

        // 3) RESOLVE: the restored stash reads back as the SAME claim - owner, station, action.
        StationCustodyClaim claim = StationCustodyClaim.of(restored, 3, 64, 5, () -> { });
        assertNotNull(claim, "a restored stash resolves to a claim view");
        assertEquals(OWNER, claim.ownerId);
        assertEquals("sawmill", claim.stationId, "the committed action survives via the stash tag");
        assertEquals("work", claim.actionId);
        assertEquals(7, claim.totalQuantity());
        assertEquals(List.of("Wood_Oak_Log", "Wood_Pine_Log"), List.copyOf(claim.items().keySet()),
                "placement order (the drain order) survives the round trip");

        // 4) DRAIN: oldest-placed-first in station vocabulary, exactly as a post-restart session's
        //    Consume phase would - 3 oak (the older entry) then 2 pine.
        Function<String, String[]> resolver = families(Map.of(
                "Wood_Oak_Log", new String[] {"Wood_Hardwood_Trunk"},
                "Wood_Pine_Log", new String[] {"Wood_Hardwood_Trunk"}));
        Map<String, Integer> drainedOut = new LinkedHashMap<>();
        int drained = StationCustody.drain(claim, null, "Wood_Hardwood_Trunk", 5, resolver, drainedOut);
        assertEquals(5, drained);
        assertEquals(3, drainedOut.get("Wood_Oak_Log"), "the oldest-placed entry drains first");
        assertEquals(2, drainedOut.get("Wood_Pine_Log"));
        assertEquals(2, claim.totalQuantity(), "the remainder stays in the stash");
    }

    @Test
    void anEmptiedClaimStillSurvivesASave() {
        // A claim drained to zero keeps its owner + tag (stampNewStash records both precisely so
        // the pile always carries a leaf and cannot be dropped by the save), so an
        // empty-but-standing stash still resolves to a claim with its identity intact.
        BlockStash placed = new BlockStash();
        StationCustodyClaim.stampNewStash(placed, OWNER, "sawmill", "work");

        BuilderCodec<BlockRecordSection<BlockStash>> sectionCodec =
                BlockRecordSection.buildCodec(BlockStash.CODEC);
        BlockRecordSection<BlockStash> section = sectionCodec.getDefaultValue();
        section.put(ChunkUtil.indexBlock(0, 0, 0), placed);
        ExtraInfo info = new ExtraInfo();
        BlockStash restored = sectionCodec.decode(sectionCodec.encode(section, info), info)
                .get(ChunkUtil.indexBlock(0, 0, 0));

        StationCustodyClaim claim = StationCustodyClaim.of(restored, 0, 0, 0, () -> { });
        assertNotNull(claim, "an empty claim's identity survives (owner keeps the pile alive)");
        assertTrue(claim.isEmpty());
        assertEquals(OWNER, claim.ownerId);
    }

    @Test
    void aStashDroppedFromTheSectionResolvesToNoClaim() {
        BuilderCodec<BlockRecordSection<BlockStash>> sectionCodec =
                BlockRecordSection.buildCodec(BlockStash.CODEC);
        BlockRecordSection<BlockStash> section = sectionCodec.getDefaultValue();
        BlockStash placed = new BlockStash();
        StationCustodyClaim.stampNewStash(placed, OWNER, "sawmill", "work");
        section.put(ChunkUtil.indexBlock(1, 2, 3), placed);
        assertTrue(section.removeRecord(ChunkUtil.indexBlock(1, 2, 3)),
                "the break path's removal takes the record out");
        assertNull(section.get(ChunkUtil.indexBlock(1, 2, 3)),
                "a removed stash answers null, so the block reads as claim-free afterwards");
    }
}
