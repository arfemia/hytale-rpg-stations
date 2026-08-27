package com.ziggfreed.rpgstations.station;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.entity.flair.ZigFlairComponent;
import com.ziggfreed.rpgstations.api.FlairUnlockProvider;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The built-in {@link FlairUnlockProvider}: unlocked flair ids read straight off ziggfreed-common's
 * persisted {@link ZigFlairComponent}, the per-player unlocked-flair set the library itself
 * registers, attaches to every player and persists. Registering it at setup is what makes flair
 * unlocks WORK with this jar and the library alone; the union registry stays open, so a mod keeping
 * its unlocks in a genuinely foreign store registers its own provider beside this one, and a second
 * provider answering the same ids costs nothing (the answers union).
 *
 * <p>Which mod WRITES the set is deliberately not this engine's business: this provider only ever
 * reads. Called synchronously from the engine's own moment-resolution path, which already runs on
 * the target player's world thread, so a direct {@code store.getComponent} read is safe (no
 * {@code world.execute} hop needed).
 */
public final class ZigFlairUnlockProvider implements FlairUnlockProvider {

    @Override
    @Nonnull
    public Set<String> unlockedFlairIds(@Nonnull UUID playerId) {
        try {
            if (ZigFlairComponent.TYPE == null) {
                return Set.of();
            }
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null) {
                return Set.of();
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return Set.of();
            }
            ZigFlairComponent flairs = ref.getStore().getComponent(ref, ZigFlairComponent.TYPE);
            if (flairs == null || flairs.unlockedFlairs.isEmpty()) {
                return Set.of();
            }
            return Set.copyOf(flairs.unlockedFlairs);
        } catch (Throwable t) {
            Log.fine("STATION built-in flair unlock read failed: " + t.getMessage());
            return Set.of();
        }
    }
}
