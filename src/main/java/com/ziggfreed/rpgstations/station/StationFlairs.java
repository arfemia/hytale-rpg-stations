package com.ziggfreed.rpgstations.station;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.api.impl.FlairUnlockRegistryImpl;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The achievement/reward flair override seam. A station may author NAMED cosmetic flair
 * layers ({@link StationAsset#getFlairs}); a grantor (any run-a-command reward system)
 * unlocks a flair id for a player, and {@link #effective} overlays every unlocked flair's
 * non-null leaves onto the station's base moment presentation, per LEAF. Ported verbatim from
 * the MMO's {@code station.StationFlairs} (RPG Stations extraction leg 2); {@code
 * asset.type.Presentation} severed to RpgStations' own {@link Presentation} (which drops
 * {@code Feedback} and gains {@link Presentation.Shake} - the overlay now folds {@code Shake}
 * instead of {@code Feedback}).
 *
 * <p><b>Leg 4:</b> the single-provider {@code UnlockProvider}/{@code setProvider} seam is
 * RETIRED (design section 3.2/9.6) in favor of the api's {@link FlairUnlockRegistryImpl} UNION -
 * every registered {@code FlairUnlockProvider} answers "which flair ids has this player
 * unlocked, across every station"; THIS class does the per-station filtering by checking each
 * unlocked id against the station's own authored {@code Flairs} map (unchanged). No provider
 * registered = empty union = base presentations only, the same zero-content starting state the
 * old default provider gave.
 *
 * <p><b>Overlay semantics:</b> per LEAF, an unlocked flair's non-null value replaces the
 * current value; a leaf the flair omits falls through untouched. A flair on a slot with NO
 * base presentation ADDS one. Multiple unlocked flairs STACK, applied in SORTED flair-id
 * order (a later id's non-null leaf wins over an earlier id's on the same leaf). An unlocked
 * id with no matching entry in the station's authored {@code Flairs} map is silently ignored.
 */
public final class StationFlairs {

    /** The station moments a flair can overlay. */
    public enum Slot { CYCLE, SWING, RARE_FIND, COMPLETION }

    private StationFlairs() {
    }

    /**
     * Leaf-overlay resolution: {@code base} leaves, overridden by each of the player's
     * unlocked flairs' non-null leaves for {@code slot}, applied in sorted flair-id order.
     * Returns {@code base} (possibly {@code null}) UNCHANGED whenever there is nothing to
     * overlay - the zero-cost common path under the default provider.
     */
    @Nullable
    public static Presentation effective(@Nullable Presentation base, @Nullable Map<String, StationAsset.Flair> flairs,
                                         @Nonnull Slot slot, @Nonnull UUID playerUuid, @Nonnull String stationId) {
        if (flairs == null || flairs.isEmpty()) {
            return base;
        }
        Set<String> unlockedIds = FlairUnlockRegistryImpl.getInstance().unlockedFlairIds(playerUuid);
        if (unlockedIds == null || unlockedIds.isEmpty()) {
            return base;
        }

        String sound = base != null ? base.getSound() : null;
        String particles = base != null ? base.getParticles() : null;
        String animation = base != null ? base.getAnimation() : null;
        String animationItem = base != null ? base.getAnimationItem() : null;
        String animationSlot = base != null ? base.getAnimationSlot() : null;
        String camera = base != null ? base.getCamera() : null;
        Presentation.Shake shake = base != null ? base.getShake() : null;
        boolean overlaidAny = false;

        for (String flairId : new TreeSet<>(unlockedIds)) {
            if (flairId == null) {
                continue;
            }
            StationAsset.Flair flair = flairs.get(flairId);
            if (flair == null) {
                continue; // unlocked id with no matching authored flair - ignored, never an error
            }
            Presentation slotPresentation = switch (slot) {
                case SWING -> flair.getSwing();
                case CYCLE -> flair.getCycle();
                case RARE_FIND -> flair.getRareFind();
                case COMPLETION -> flair.getCompletion();
            };
            if (slotPresentation == null) {
                continue;
            }
            overlaidAny = true;
            if (slotPresentation.getSound() != null) {
                sound = slotPresentation.getSound();
            }
            if (slotPresentation.getParticles() != null) {
                particles = slotPresentation.getParticles();
            }
            if (slotPresentation.getAnimation() != null) {
                animation = slotPresentation.getAnimation();
            }
            if (slotPresentation.getAnimationItem() != null) {
                animationItem = slotPresentation.getAnimationItem();
            }
            if (slotPresentation.getAnimationSlot() != null) {
                animationSlot = slotPresentation.getAnimationSlot();
            }
            if (slotPresentation.getCamera() != null) {
                camera = slotPresentation.getCamera();
            }
            if (slotPresentation.getShake() != null) {
                shake = slotPresentation.getShake();
            }
        }

        if (!overlaidAny) {
            return base;
        }
        return Presentation.of(sound, particles, animation, animationItem, animationSlot, camera, shake);
    }
}
