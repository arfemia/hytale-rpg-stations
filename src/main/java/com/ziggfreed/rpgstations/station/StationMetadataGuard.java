package com.ziggfreed.rpgstations.station;

import java.util.Set;

import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.ziggfreed.common.entity.ItemReadings;
import com.ziggfreed.common.inventory.DisposableItemMetadata;

/**
 * The METADATA GUARD: may a placed stack be consumed by a route that never looked at its item id?
 * The fallback routes take a piece on its shape alone (a weapon, a tool), so they must refuse a
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
 * <p>This class is the station's POLICY only: the key read and the declared-key list are the
 * library's ({@link ItemReadings#undeclaredMetadataKeys}), and the policy over that reading
 * ({@link #acceptsReading}) is pure, so it pins in a unit JVM.
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
     * May {@code stack} be consumed on its shape alone? True for a stack with no metadata or whose
     * every key some mod declared disposable; false for a missing or unreadable stack, or one
     * carrying a key nobody declared.
     */
    public static boolean accepts(@Nullable ItemStack stack) {
        return acceptsReading(ItemReadings.undeclaredMetadataKeys(stack));
    }
}
