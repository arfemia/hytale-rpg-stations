package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * ONE stack a consume took, with the pile it came out of: the REAL stack (the metadata-bearing
 * piece when a single-item socket gave it up, else a bare stack of the drained id) and the custody
 * socket it left ({@code null} for the inventory route). What the ONE consumption hook
 * ({@code StationService#onInputConsumed}) reports per stack, so a listener can say which socket
 * each piece was unmade from rather than the pass's addressed socket.
 */
record ConsumedInput(@Nonnull ItemStack stack, @Nullable String socketId) {

    /** A stack drained out of the custody socket {@code socketId}. */
    @Nonnull
    static ConsumedInput fromSocket(@Nonnull ItemStack stack, @Nonnull String socketId) {
        return new ConsumedInput(stack, socketId);
    }

    /** A stack drained out of the worker's inventory (no socket). */
    @Nonnull
    static ConsumedInput fromInventory(@Nonnull ItemStack stack) {
        return new ConsumedInput(stack, null);
    }

    /**
     * What ONE drain took out of the socket pile {@code socketId}: the piece's own stack when the
     * drain took a single-item socket's last ({@code unique}, reported only when the drain counts
     * its item, {@link StationCustodyLedger#drainTookPiece}), then a bare stack per drained id with
     * that piece's share netted off ({@link StationCustodyLedger#countsBesideUnique}, the split the
     * refund uses), so the piece is reported once, as itself. The attended custody consume and the
     * unattended settle both report through here.
     */
    @Nonnull
    static List<ConsumedInput> fromPile(@Nonnull String socketId, @Nonnull Map<String, Integer> drained,
            @Nullable ItemStack unique) {
        List<ConsumedInput> out = new ArrayList<>();
        ItemStack piece = StationCustodyLedger.pieceTaken(drained, unique);
        if (piece != null) {
            out.add(fromSocket(piece, socketId));
        }
        for (Map.Entry<String, Integer> e : StationCustodyLedger.countsBesideUnique(drained, piece).entrySet()) {
            if (e.getKey() != null && e.getValue() != null && e.getValue() > 0) {
                out.add(fromSocket(new ItemStack(e.getKey(), e.getValue()), socketId));
            }
        }
        return out;
    }
}
