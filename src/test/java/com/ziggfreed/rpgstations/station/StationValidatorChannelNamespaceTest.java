package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The {@code UNKNOWN_CHANNEL} decision, {@link StationValidator#warnsUndeclared}, fails open per
 * namespace: an undeclared channel warns only when a declared channel shares its namespace, so
 * content written for a mod that is not installed stays quiet while a typo inside an installed
 * mod's namespace still warns. The helper takes the declared ids as a parameter because the live
 * channel registry is a global, append-only singleton a test must not declare into.
 */
public class StationValidatorChannelNamespaceTest {

    private static final List<String> DECLARED = List.of("yourmod:crop_quality");

    @Test
    void anUndeclaredChannelInANamespaceNobodyDeclaredIntoIsSkipped() {
        assertFalse(StationValidator.warnsUndeclared("othermod:harvest", DECLARED),
                "no declared channel is in othermod:, so its owner is not installed and the channel is skipped");
    }

    @Test
    void aMisspeltChannelInsideADeclaredNamespaceWarns() {
        assertTrue(StationValidator.warnsUndeclared("yourmod:crop_qualty", DECLARED));
    }

    @Test
    void aDeclaredChannelNeverWarns() {
        assertFalse(StationValidator.warnsUndeclared("yourmod:crop_quality", DECLARED));
        assertFalse(StationValidator.warnsUndeclared("YourMod:Crop_Quality", DECLARED));
    }

    @Test
    void anEmptyDeclaredSetNeverWarns() {
        assertFalse(StationValidator.warnsUndeclared("yourmod:crop_qualty", List.of()));
        assertFalse(StationValidator.warnsUndeclared("bare_channel", List.of()));
    }

    @Test
    void theNamespaceMatchIgnoresCase() {
        assertTrue(StationValidator.warnsUndeclared("YourMod:x", List.of("yourmod:y")));
        assertTrue(StationValidator.warnsUndeclared("yourmod:x", List.of("YOURMOD:y")));
    }

    @Test
    void aNamespaceIsItsWholePrefixUpToTheColon() {
        assertFalse(StationValidator.warnsUndeclared("yourmod2:x", List.of("yourmod:y")),
                "yourmod2: is another mod's namespace, not yourmod:'s");
        assertFalse(StationValidator.warnsUndeclared("your:x", List.of("yourmod:y")));
        assertTrue(StationValidator.warnsUndeclared("yourmod:x", List.of("othermod:a", "yourmod:y")),
                "with several mods declared, the channel's own namespace decides");
    }

    @Test
    void aChannelWithNoNamespaceWarnsAgainstANonEmptyDeclaredSet() {
        assertTrue(StationValidator.warnsUndeclared("crop_quality", DECLARED));
    }
}
