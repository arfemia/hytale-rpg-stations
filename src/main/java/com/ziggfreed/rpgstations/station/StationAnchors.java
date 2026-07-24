package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * PURE decision cores for the multi-station anchor seam (scope-2 wave 3, design 2.2/2.4,
 * decisions 28/41 + gate m4/m5): anchor discovery bounds, claim precedence, and block-key parsing,
 * all unit-testable with no live server (mirroring {@link StationStepDecisions}'s role for the step
 * engine). The LIVE glue (the {@code knownStationBlocks} index scan, the bounded ring scan over
 * {@code world.getBlock}, the atomic {@code custodyByBlock}/{@code byBlock} claim) lives in
 * {@link StationService}; this class owns only the math + rules it can verify in a unit JVM.
 */
final class StationAnchors {

    /** The hard discovery-radius cap (design 2.2: a bounded ring scan never exceeds radius 16). */
    static final int MAX_SCAN_RADIUS = 16;

    /** The vertical spread the bounded ring scan searches around the primary block's Y (design 2.2: y +/-2). */
    static final int SCAN_Y_SPREAD = 2;

    private StationAnchors() {
    }

    /**
     * The effective discovery radius: an authored {@code MaxRadius} rounded up, floored at 0 and
     * capped at {@link #MAX_SCAN_RADIUS} (design 2.2's "MaxRadius capped 16").
     */
    static int cappedRadius(double maxRadius) {
        int r = (int) Math.ceil(maxRadius);
        if (r < 0) {
            return 0;
        }
        return Math.min(MAX_SCAN_RADIUS, r);
    }

    /**
     * The claim precedence rule (gate m5, decision 28c): an incoming anchor claim is REFUSED when
     * the target block is busy with its OWN work session OR carries a non-empty custody claim.
     * {@code true} = the claim may proceed. Contested = deny-with-hint, never queue (Q22).
     */
    static boolean claimAllowed(boolean busyWithOwnSession, boolean custodyNonEmpty) {
        return !busyWithOwnSession && !custodyNonEmpty;
    }

    /** The square of the horizontal (x,z) distance between two block columns - the discovery ranking metric. */
    static long horizontalDistSq(int ax, int az, int bx, int bz) {
        long dx = (long) ax - bx;
        long dz = (long) az - bz;
        return dx * dx + dz * dz;
    }

    /**
     * The bounded ring-scan offsets around the primary block (design 2.2's last-resort scan),
     * ORDERED by ascending horizontal distance then ascending {@code |dy|}, so a caller taking the
     * FIRST resolving+matching offset gets the NEAREST station block. Excludes the {@code (0,0,0)}
     * origin (the primary block itself is never its own anchor). For a POSITIVE radius the whole
     * pure-vertical column ({@code dx==0 && dz==0}, any {@code dy}) is ALSO excluded: a
     * directly-stacked block sits at horizontal distance 0 but the puppet cannot walk to it on the
     * ground plane (design 28d), so it must never out-rank a real horizontal neighbour. A ZERO
     * radius keeps that column as the degenerate fallback (no horizontal candidate can exist).
     * Every returned offset satisfies {@code dx^2+dz^2 <= radius^2} and {@code |dy| <= ySpread}.
     */
    @Nonnull
    static List<int[]> ringOffsets(int radius, int ySpread) {
        List<int[]> offsets = new ArrayList<>();
        int r = Math.max(0, radius);
        int ys = Math.max(0, ySpread);
        long rSq = (long) r * r;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if ((long) dx * dx + (long) dz * dz > rSq) {
                    continue;
                }
                if (dx == 0 && dz == 0 && r > 0) {
                    continue; // a directly-stacked block is not a reachable walk anchor
                }
                for (int dy = -ys; dy <= ys; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    offsets.add(new int[] {dx, dy, dz});
                }
            }
        }
        offsets.sort((a, b) -> {
            long da = (long) a[0] * a[0] + (long) a[2] * a[2];
            long db = (long) b[0] * b[0] + (long) b[2] * b[2];
            if (da != db) {
                return Long.compare(da, db);
            }
            return Integer.compare(Math.abs(a[1]), Math.abs(b[1]));
        });
        return offsets;
    }

    /** The occupancy block key encoding {@code StationService} uses (world uuid + block coords). */
    @Nonnull
    static String blockKey(@Nonnull String worldUuid, int x, int y, int z) {
        return worldUuid + ":" + x + ":" + y + ":" + z;
    }

    /**
     * Parses the {@code x/y/z} block coordinates out of a {@code "<worldUuid>:<x>:<y>:<z>"} key
     * (the world uuid may itself contain no colon-free guarantee, so the LAST three colon-delimited
     * fields are the coords). Returns {@code null} on a malformed key (never throws).
     */
    @Nullable
    static int[] parseCoords(@Nullable String blockKey) {
        if (blockKey == null) {
            return null;
        }
        int z = blockKey.lastIndexOf(':');
        if (z <= 0) {
            return null;
        }
        int y = blockKey.lastIndexOf(':', z - 1);
        if (y <= 0) {
            return null;
        }
        int x = blockKey.lastIndexOf(':', y - 1);
        if (x <= 0) {
            return null;
        }
        try {
            return new int[] {
                Integer.parseInt(blockKey.substring(x + 1, y)),
                Integer.parseInt(blockKey.substring(y + 1, z)),
                Integer.parseInt(blockKey.substring(z + 1))
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
