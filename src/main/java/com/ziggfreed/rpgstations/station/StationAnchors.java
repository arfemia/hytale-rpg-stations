package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * PURE decision cores for the multi-station anchor seam (scope-2 wave 3, design 2.2/2.4,
 * decisions 28/41 + gate m4/m5): anchor discovery bounds, claim precedence, and block-key parsing,
 * all unit-testable with no live server (mirroring {@link StationStepDecisions}'s role for the step
 * engine). The LIVE glue (the {@code knownStationBlocks} index scan, the bounded ring scan over
 * the live block ids, the atomic {@code byBlock} occupancy claim, the stash-backed custody read)
 * lives in {@link StationService}; this class owns only the math + rules it can verify in a unit JVM.
 */
final class StationAnchors {

    /** The hard discovery-radius cap (design 2.2: a bounded ring scan never exceeds radius 16). */
    static final int MAX_SCAN_RADIUS = 16;

    /** The vertical spread the bounded ring scan searches around the primary block's Y (design 2.2: y +/-2). */
    static final int SCAN_Y_SPREAD = 2;

    private StationAnchors() {
    }

    /**
     * The effective discovery radius: an authored {@code MaxRadiusMeters} rounded up, floored at 0 and
     * capped at {@link #MAX_SCAN_RADIUS} (design 2.2's "MaxRadiusMeters capped 16").
     */
    static int cappedRadius(double maxRadiusMeters) {
        int r = (int) Math.ceil(maxRadiusMeters);
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

    /**
     * The block-gone rule for a running session's PRIMARY block (AV-wave fix): compare by the
     * block's ITEM id whenever the session captured one at engage, and fall back to the registered
     * block-TYPE id only for a block that resolves to no containing Item at all.
     *
     * <p>Why: the engine's state flip does not annotate a block in place, it REPLACES it - the
     * interaction-state write resolves {@code blockType.getBlockForState(state)} and sets the
     * block to that variant, and a state variant is a DISTINCT generated {@code BlockType} key.
     * So every custody state flip this engine performs ({@code Empty}/{@code Loaded}/
     * {@code Working}) changes the block-TYPE id read back, and a type-id compare against the
     * engage-time snapshot reads the engine's OWN flip as "the station is gone". Every state
     * variant of one block shares the same containing Item ({@code BlockType#getItem()} walks the
     * container-key chain), so the item-id compare is stable across a flip while a real break (no
     * item-backed block left) or a replace (a different item id) still ends the session.
     *
     * @param startBlockItemId the item id captured at engage, or {@code null} when the block had none
     * @param currentBlockItemId the item id read live this heartbeat
     * @param startBlockTypeId the registered block-type id captured at engage (fallback comparand)
     * @param currentBlockTypeId the registered block-type id read live this heartbeat (fallback comparand)
     * @return {@code true} when the session must stop with {@code STATION_GONE}
     */
    static boolean blockGone(@Nullable String startBlockItemId, @Nullable String currentBlockItemId,
            @Nullable String startBlockTypeId, @Nullable String currentBlockTypeId) {
        if (startBlockItemId == null) {
            return !Objects.equals(startBlockTypeId, currentBlockTypeId);
        }
        return currentBlockItemId == null || !startBlockItemId.equalsIgnoreCase(currentBlockItemId);
    }

    /**
     * The PURE core of the derived station-block discovery seed (AV-wave fix for "discovery is dead
     * on a cold server"): joins {@code rootInteractionId -> stationId} (collected from every
     * RootInteraction whose entries include this mod's own {@code rpg_station_use} interaction) with
     * every {@code (blockItemId, Interactions.Use rootInteractionId)} pair walked off the live block
     * assets, producing the {@code blockItemId -> stationId} index anchor discovery reads.
     *
     * <p>Both sides are matched case-insensitively and both the returned keys and values are
     * lowercased (the same normalization {@code StationService#registerKnownStationBlock} applies to
     * its learned entries, so the derived and learned halves of the index are interchangeable). A
     * pair with a blank item id, a blank interaction id, or an interaction that names no station is
     * skipped. FIRST-wins on a repeated item id, so a stable input order gives a stable index (a
     * block's own state variants all resolve to the same item id and the same interaction, so they
     * agree by construction).
     */
    @Nonnull
    static Map<String, String> deriveBlockItemIndex(@Nonnull Map<String, String> interactionToStation,
            @Nonnull List<BlockUse> blockUses) {
        Map<String, String> normalizedInteractions = new HashMap<>();
        for (Map.Entry<String, String> e : interactionToStation.entrySet()) {
            String interactionId = e.getKey();
            String stationId = e.getValue();
            if (interactionId == null || interactionId.isBlank() || stationId == null || stationId.isBlank()) {
                continue;
            }
            normalizedInteractions.putIfAbsent(interactionId.toLowerCase(Locale.ROOT),
                    stationId.toLowerCase(Locale.ROOT));
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (BlockUse use : blockUses) {
            if (use == null || use.blockItemId() == null || use.blockItemId().isBlank()
                    || use.useRootInteractionId() == null || use.useRootInteractionId().isBlank()) {
                continue;
            }
            String stationId = normalizedInteractions.get(use.useRootInteractionId().toLowerCase(Locale.ROOT));
            if (stationId != null) {
                out.putIfAbsent(use.blockItemId().toLowerCase(Locale.ROOT), stationId);
            }
        }
        return out;
    }

    /**
     * One block asset's contribution to {@link #deriveBlockItemIndex}: the item id a placed block of
     * this type resolves to (what {@code StationService#blockItemIdAt} reads back in the world) and
     * the RootInteraction id its {@code BlockType.Interactions.Use} names.
     */
    record BlockUse(@Nullable String blockItemId, @Nullable String useRootInteractionId) {
    }

    /** The occupancy block key encoding {@code StationService} uses (world uuid + block coords). */
    @Nonnull
    static String blockKey(@Nonnull String worldUuid, int x, int y, int z) {
        return worldUuid + ":" + x + ":" + y + ":" + z;
    }

    /**
     * The world-scoping PREFIX of every block key belonging to {@code worldUuid} (the uuid plus its
     * trailing separator). The one place that spelling is built, so a per-world sweep of any
     * block-keyed map - display handles, occupancy, the discovered-block index - and the
     * world-scoped custody-retrieval match all agree on it by construction.
     */
    @Nonnull
    static String worldPrefix(@Nonnull String worldUuid) {
        return worldUuid + ":";
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
