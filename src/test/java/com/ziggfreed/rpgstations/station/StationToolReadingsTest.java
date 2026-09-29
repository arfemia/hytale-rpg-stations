package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.entity.ItemReadings;

/**
 * The station's tool readings over the ONE shared item reader ({@link StationToolReadings} over
 * the library's {@link ItemReadings}): the numbers the api {@code FactorContext} has always carried
 * for {@code hytale:tool_quality} / {@code hytale:tool_item_level} /
 * {@code hytale:tool_durability_percent}.
 *
 * <p>The station keeps NO arithmetic of its own any more: every 1.0.0 branch now lives in the
 * shared reader, and the station's one contribution is the FOLD of that reader's "cannot answer"
 * (null) onto its own defaults ({@link StationToolReadings#orDefault}), which is what this test
 * pins, since a live stack cannot be built in this JVM. Each branch of the three removed 1.0.0
 * readers ({@code StationService#resolveHeldToolQuality} / {@code #resolveHeldToolItemLevel} /
 * {@code #resolveHeldToolDurabilityPercent} at RPG Stations {@code fb206bb}) is covered as
 * follows, the library's cases being Ziggfreed Common's {@code ItemReadingsTest}:
 *
 * <ul>
 *   <li><b>Quality, nothing held or no item</b> (1.0.0: {@code 0}): the reader answers null
 *       ({@code nothingToAskAboutReadsNothing}), the fold reads {@link StationToolReadings#NONE}
 *       (pinned here).</li>
 *   <li><b>Quality, the index resolves to no asset</b> (1.0.0: {@code 0}) and <b>the engine's
 *       default quality authoring {@code -1}</b> (1.0.0: floored to {@code 0}):
 *       {@code anUnresolvedOrNegativeQualityReadsZero}.</li>
 *   <li><b>Quality, the authored {@code QualityValue} of the held ITEM's current index</b> (1.0.0:
 *       {@code max(0, value)} through {@code Item#getQualityIndex()}, never the stack's copied
 *       index): {@code theHeldToolPathsAreByteIdenticalTo21x} walks every index, and
 *       {@code theToolReadingFollowsTheItemAndTheItemReadingFollowsTheStack} pins the item-side
 *       read against a re-qualified stack (the C11 rule {@link StationToolReadings#quality}
 *       follows by asking {@code ItemReadings.quality(Item)}).</li>
 *   <li><b>Quality, a lookup that throws</b> (1.0.0: {@code 0}): the reader's try-guard answers
 *       null, the fold reads {@code NONE} (pinned here through the fold).</li>
 *   <li><b>Item level, nothing held</b> (1.0.0: {@code 0}): null, folded to {@code NONE} (pinned
 *       here). The 1.0.0 reader had no {@code isEmpty} check and read the empty stack's item,
 *       which the engine resolves to its {@code UNKNOWN} item authoring no level, so it read
 *       {@code 0} there too; the shared reader answers null for an empty stack
 *       ({@code nothingToAskAboutReadsNothing}) and the fold reads {@code NONE}: the same number
 *       either way.</li>
 *   <li><b>Item level, {@code max(0, ItemLevel)}</b>: {@code itemLevelComesFromTheStacksItem}
 *       (a level of 12 and a level of -3 floored to 0) and {@code theHeldToolPathsAreByteIdenticalTo21x}.</li>
 *   <li><b>Durability, nothing held or an empty stack</b> (1.0.0: {@code 100}): null, folded to
 *       {@link StationToolReadings#UNWORN_PERCENT} (pinned here).</li>
 *   <li><b>Durability, an item that tracks none ({@code max <= 0})</b> (1.0.0: {@code 100}):
 *       {@code aStackThatTracksNoDurabilityReadsAsUnworn}.</li>
 *   <li><b>Durability, {@code clamp((durability / max) x 100, 0, 100)}</b>:
 *       {@code wearReadsAsAPercentOfTheStack} (25, 100 and 0 percent) and
 *       {@code theHeldToolPathsAreByteIdenticalTo21x} (50 percent).</li>
 * </ul>
 */
public class StationToolReadingsTest {

    @Test
    void anEmptyHand_readsZeroQuality_zeroLevel_andFullDurability() {
        assertEquals(0.0, StationToolReadings.quality(null));
        assertEquals(0.0, StationToolReadings.itemLevel(null));
        assertEquals(100.0, StationToolReadings.durabilityPercent(null));
    }

    @Test
    void theSharedReadersCannotAnswer_foldsOntoTheStationsOwnDefault() {
        assertEquals(0.0, StationToolReadings.orDefault(null, StationToolReadings.NONE));
        assertEquals(100.0, StationToolReadings.orDefault(null, StationToolReadings.UNWORN_PERCENT));
        assertEquals(3.0, StationToolReadings.orDefault(3.0, StationToolReadings.NONE), "a real reading passes through");
        assertEquals(0.0, StationToolReadings.orDefault(0.0, StationToolReadings.UNWORN_PERCENT),
                "a real zero (a broken tool) is a reading, never folded onto the default");
        assertEquals(100.0, StationToolReadings.orDefault(Double.NaN, StationToolReadings.UNWORN_PERCENT),
                "a non-finite reading is no reading");
        assertEquals(0.0, StationToolReadings.orDefault(Double.POSITIVE_INFINITY, StationToolReadings.NONE));
    }

    @Test
    void theStationsDefaultsMatchTheContractsTheContextDocuments() {
        assertEquals(0.0, StationToolReadings.NONE, "nothing held names no quality and no level");
        assertEquals(100.0, StationToolReadings.UNWORN_PERCENT, "nothing held is never worn");
    }
}
