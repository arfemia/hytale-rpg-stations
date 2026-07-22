package com.ziggfreed.rpgstations.validation;

import java.util.List;

import javax.annotation.Nonnull;

import com.ziggfreed.rpgstations.util.Log;

/**
 * The shared summarize/count/log shape every validator uses. Ported verbatim from the MMO's
 * {@code validation.ValidationReport} mini-core (RPG Stations extraction leg 2), renamed
 * {@code Report} (RpgStations owns only ONE domain today so a shorter name reads cleanly),
 * {@code SafeLog} severed to RpgStations' own {@code util.Log}. Summary lines and per-finding
 * detail are raw English (admin/log surface).
 */
public final class Report {

    private Report() {
    }

    /** One-line summary, e.g. {@code "Station validation: 1 error(s), 3 warning(s), 0 note(s)"}. */
    @Nonnull
    public static String summarize(@Nonnull String label, @Nonnull List<Finding> findings) {
        if (findings.isEmpty()) {
            return label + ": no issues";
        }
        int errors = 0;
        int warnings = 0;
        int notes = 0;
        for (Finding f : findings) {
            switch (f.severity()) {
                case ERROR -> errors++;
                case WARNING -> warnings++;
                case INFO -> notes++;
            }
        }
        return label + ": " + errors + " error(s), " + warnings + " warning(s), " + notes + " note(s)";
    }

    /** Count of actionable findings (errors + warnings; advisory notes excluded). */
    public static int problemCount(@Nonnull List<Finding> findings) {
        int n = 0;
        for (Finding f : findings) {
            if (f.severity() != Severity.INFO) {
                n++;
            }
        }
        return n;
    }

    /** Log the summary (WARN when problems exist) plus per-finding detail. Never throws. */
    public static void logTo(@Nonnull String domainTag, @Nonnull String label, @Nonnull List<Finding> findings) {
        if (problemCount(findings) > 0) {
            Log.warn(summarize(label, findings));
        } else {
            Log.info(summarize(label, findings));
        }
        for (Finding f : findings) {
            String line = "[" + domainTag + "] " + f.severity() + " " + f.code() + ": " + f.message();
            if (f.severity() == Severity.INFO) {
                Log.fine(line);
            } else {
                Log.info(line);
            }
        }
    }
}
