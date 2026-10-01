package com.ziggfreed.rpgstations.station;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.ziggfreed.common.entity.ItemReadings;
import com.ziggfreed.common.inventory.DisposableItemMetadata;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The METADATA GUARD: may a placed stack be consumed when consuming it destroys whatever data it
 * carries? A row that consumes a single-item socket's real stack (an authored row, a row derived
 * from a salvage recipe, or a fallback route that takes a piece on its shape alone) must refuse a
 * stack carrying data its owner never said may be destroyed with it (a container with something
 * inside it, a mark another mod put there for a reason of its own) while accepting what worn gear
 * legitimately carries. Wear is not metadata at all (it rides the stack's own durability leaves),
 * so it never counts.
 *
 * <p><b>The rule.</b> A stack is accepted when every metadata key it carries is one some mod
 * declared disposable through the library's {@link DisposableItemMetadata} list (a bare stack
 * carries none, so it is accepted), and refused when it carries any key nobody declared, or when
 * its keys cannot be read at all. The library's stamper declares the keys it writes when it
 * registers, and any other mod declares its own keys at setup; this mod declares none and names
 * none, and a stamped stack vouches for nothing beyond its declared keys.
 *
 * <p><b>Where it applies.</b> Every path that would consume a single-item socket's real stack.
 * Placement comes first: a single-item socket refuses a guarded piece outright when every action
 * at the station that reads the socket would consume it ({@link #socketUse},
 * {@link #consumedByEveryReader}), and takes it only when some action reads it without consuming
 * it (a {@code Stamp}, or custody no phase draws from). Then every consuming path: the runnable
 * scan and the unattended settle skip any row, whatever its route, that would consume a guarded
 * piece ({@link #consumesRefusedPiece}), so it answers as no input at all; an authored
 * {@code Consume} phase drawing from custody refuses it the same way; and the ritual queue passes
 * over a socket holding one ({@link #workable}), so it never stalls the other sockets. A count
 * pile never holds a real stack (it refuses any stack carrying metadata at placement), so the
 * guard never reaches one.
 *
 * <p>This class is the station's POLICY only: the key read and the declared-key list are the
 * library's ({@link ItemReadings#undeclaredMetadataKeys}), and the policy over that reading
 * ({@link #acceptsReading}, {@link #consumesRefusedPiece}) is pure, so it pins in a unit JVM.
 */
public final class StationMetadataGuard {

    private StationMetadataGuard() {
    }

    /**
     * PURE, the station's policy over the library's reading: {@code undeclaredKeys} is what
     * {@link ItemReadings#undeclaredMetadataKeys} answers, null when the stack could not be read
     * (refused, never accepted by accident) and otherwise the keys nobody declared (accepted only
     * when there are none).
     */
    public static boolean acceptsReading(@Nullable Set<String> undeclaredKeys) {
        return undeclaredKeys != null && undeclaredKeys.isEmpty();
    }

    /**
     * May {@code stack} be consumed? True for a stack with no metadata or whose every key some mod
     * declared disposable; false for a missing or unreadable stack, or one carrying a key nobody
     * declared.
     */
    public static boolean accepts(@Nullable ItemStack stack) {
        return acceptsReading(ItemReadings.undeclaredMetadataKeys(stack));
    }

    /**
     * PURE, per socket: the item id of the socket's real stack when the guard REFUSES it, else
     * null. {@code uniqueItemOf} answers the item id of the real stack a socket holds (null when it
     * holds none), and {@code undeclaredKeysOf} the library's reading of that stack's undeclared
     * keys ({@link ItemReadings#undeclaredMetadataKeys}); the live form is
     * {@link #refusedPieces(StationCustodyClaim)}.
     */
    @Nonnull
    static Function<String, String> refusedPieces(@Nonnull Function<String, String> uniqueItemOf,
            @Nonnull Function<String, Set<String>> undeclaredKeysOf) {
        return socketId -> {
            String itemId = uniqueItemOf.apply(socketId);
            if (itemId == null || itemId.isBlank()) {
                return null;
            }
            return acceptsReading(undeclaredKeysOf.apply(socketId)) ? null : itemId;
        };
    }

    /**
     * {@link #refusedPieces(Function, Function)} over a live claim's real stacks, each socket read
     * once per answer however many rows ask about it (the key read encodes the stack).
     */
    @Nonnull
    static Function<String, String> refusedPieces(@Nonnull StationCustodyClaim claim) {
        Function<String, String> read = refusedPieces(socketId -> {
            ItemStack unique = claim.uniqueStack(socketId);
            return unique != null ? unique.getItemId() : null;
        }, socketId -> ItemReadings.undeclaredMetadataKeys(claim.uniqueStack(socketId)));
        Map<String, Optional<String>> memo = new HashMap<>();
        return socketId -> memo.computeIfAbsent(socketId, id -> Optional.ofNullable(read.apply(id))).orElse(null);
    }

    /**
     * PURE, the guard on a ROW: does {@code row} consume a single-item socket's real stack the
     * guard refuses? An input does when the socket it draws from ({@link StationCustody#socketIdFor}
     * over {@code socketOverride}, the ritual queue's current socket) holds a refused piece
     * ({@code refusedPieceOf}, see {@link #refusedPieces(Function, Function)}) and the input's own
     * matcher accepts that piece's item, since the drain that takes it takes the real stack with
     * it. The route the row came by does not matter: an authored row, a derived salvage row and a
     * fallback row are judged alike. A null row, or one with no input, consumes nothing.
     */
    static boolean consumesRefusedPiece(@Nullable StationAsset.Conversion row,
            @Nonnull List<Custody.ResolvedSocket> sockets, @Nullable String socketOverride,
            @Nonnull Function<String, String> refusedPieceOf,
            @Nonnull Function<String, String[]> resourceTypesOf,
            @Nonnull Function<String, Map<String, String[]>> tagsOf) {
        return consumesRefusedPiece(row != null ? row.getInput() : null, socketOverride, sockets, refusedPieceOf,
                resourceTypesOf, tagsOf);
    }

    /**
     * PURE, the guard on a group of custody inputs (a row's, or an authored {@code Consume}
     * phase's {@code Items}): does any entry draw a refused piece? Each entry draws from its own
     * {@code Socket}, else {@code groupSocket} (the phase's own, or the ritual queue's current
     * socket), else the first Item socket ({@link StationCustody#socketIdFor}).
     */
    static boolean consumesRefusedPiece(@Nullable Ingredient[] inputs, @Nullable String groupSocket,
            @Nonnull List<Custody.ResolvedSocket> sockets, @Nonnull Function<String, String> refusedPieceOf,
            @Nonnull Function<String, String[]> resourceTypesOf,
            @Nonnull Function<String, Map<String, String[]>> tagsOf) {
        if (inputs == null) {
            return false;
        }
        for (Ingredient in : inputs) {
            if (in == null) {
                continue;
            }
            String refused = refusedPieceOf.apply(StationCustody.socketIdFor(in.getSocket(), groupSocket, sockets));
            if (refused != null
                    && StationCustody.ingredientEntryMatcher(in, resourceTypesOf, tagsOf).test(refused)) {
                return true;
            }
        }
        return false;
    }

    /**
     * PURE (the ritual queue): {@code filled} with the guard applied. A socket holding a refused
     * piece is passed over, since no pass could consume it, so the queue works the other sockets
     * and ends when only refused pieces are left instead of stalling on the first of them.
     */
    @Nonnull
    static Predicate<String> workable(@Nonnull Predicate<String> filled,
            @Nonnull Function<String, String> refusedPieceOf) {
        return socketId -> filled.test(socketId) && refusedPieceOf.apply(socketId) == null;
    }

    /** How one action at a station reads a socket's piece ({@link #socketUse}). */
    enum SocketUse {
        /** The action's custody has no Item socket of that id. */
        NONE,
        /** Every path of the action that reads the socket consumes the piece. */
        CONSUMES,
        /** The action reads the piece and leaves it standing: it stamps it, or no phase draws it. */
        KEEPS
    }

    /**
     * PURE: how one action reads Item socket {@code socketId}, from its resolved custody
     * {@code sockets}, whether it runs the ritual queue ({@code queue}), its recipe rows
     * ({@code rows}, authored and derived), whether its recipe authors a {@code Fallback}
     * ({@code hasFallback}) and the program it runs ({@code steps}, empty for the implicit
     * convert loop). A {@code Stamp} step reads the {@value Custody#MAIN_SOCKET_ID} piece and keeps
     * it. A conversion draws a socket when the program converts (the implicit loop, or a step with
     * an enabled {@code Convert}) and a row's input addresses it: by its own {@code Socket}, or,
     * with none, the queue's current socket (any Item socket) or the first Item socket; the
     * fallback rows draw the same unaddressed way. An authored {@code Consume} phase from custody
     * draws the socket its entries address. An action that reads the socket but neither stamps nor
     * draws it keeps the piece (custody shown, never spent).
     */
    @Nonnull
    static SocketUse socketUse(@Nonnull String socketId, @Nonnull List<Custody.ResolvedSocket> sockets,
            boolean queue, @Nullable StationAsset.Conversion[] rows, boolean hasFallback,
            @Nullable List<StationStep> steps) {
        boolean declared = false;
        for (Custody.ResolvedSocket socket : sockets) {
            if (socket.itemRoute() && socket.id().equalsIgnoreCase(socketId)) {
                declared = true;
                break;
            }
        }
        if (!declared) {
            return SocketUse.NONE;
        }
        boolean converts = steps == null || steps.isEmpty();
        boolean consumeDraws = false;
        if (steps != null) {
            for (StationStep step : steps) {
                if (step == null) {
                    continue;
                }
                if (step.getStamp() != null && Custody.MAIN_SOCKET_ID.equalsIgnoreCase(socketId)) {
                    return SocketUse.KEEPS;
                }
                if (step.getConvert() != null && step.getConvert().effectiveEnabled()) {
                    converts = true;
                }
                StationStep.Consume consume = step.getConsume();
                if (consume != null && !consume.isEmpty()
                        && StationStep.Consume.FROM_CUSTODY.equalsIgnoreCase(consume.effectiveFrom())
                        && drawsSocket(consume.getItems(), consume.getSocket(), false, socketId, sockets)) {
                    consumeDraws = true;
                }
            }
        }
        boolean convertDraws = false;
        if (converts && rows != null) {
            for (StationAsset.Conversion row : rows) {
                if (row != null && drawsSocket(row.getInput(), null, queue, socketId, sockets)) {
                    convertDraws = true;
                    break;
                }
            }
        }
        if (converts && hasFallback && (queue || StationCustody.firstItemSocketId(sockets).equalsIgnoreCase(socketId))) {
            convertDraws = true;
        }
        return convertDraws || consumeDraws ? SocketUse.CONSUMES : SocketUse.KEEPS;
    }

    /**
     * Does any of {@code inputs} draw {@code socketId}? An entry addressing a socket draws that
     * one; an unaddressed entry draws {@code groupSocket}, else any Item socket under the ritual
     * queue ({@code queue}), else the first Item socket.
     */
    private static boolean drawsSocket(@Nullable Ingredient[] inputs, @Nullable String groupSocket, boolean queue,
            @Nonnull String socketId, @Nonnull List<Custody.ResolvedSocket> sockets) {
        if (inputs == null) {
            return false;
        }
        boolean groupAddressed = groupSocket != null && !groupSocket.isBlank();
        for (Ingredient in : inputs) {
            if (in == null) {
                continue;
            }
            boolean addressed = groupAddressed || (in.getSocket() != null && !in.getSocket().isBlank());
            if (!addressed && queue) {
                return true;
            }
            if (StationCustody.socketIdFor(in.getSocket(), groupSocket, sockets).equalsIgnoreCase(socketId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * PURE: may a single-item socket refuse a guarded piece at placement? Only when at least one
     * action reads the socket and every one that does consumes the piece ({@code uses}, one
     * {@link #socketUse} per action at the station); one that keeps it (a {@code Stamp}, custody
     * shown and never spent) leaves the piece to the consuming paths' own guard.
     */
    static boolean consumedByEveryReader(@Nonnull Iterable<SocketUse> uses) {
        boolean consumed = false;
        for (SocketUse use : uses) {
            if (use == SocketUse.KEEPS) {
                return false;
            }
            if (use == SocketUse.CONSUMES) {
                consumed = true;
            }
        }
        return consumed;
    }
}
