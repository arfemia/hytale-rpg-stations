package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.Presentation;

/**
 * Which of a moment's particle bursts RIDE the entity the moment is aimed at and which play at a
 * position, the station's policy over ziggfreed-common's {@code ParticleLifetimes} read. An
 * entity-attached system has no playback cap, so a burst rides only when its system provably ends
 * on its own; every other burst plays at the aim's position, where its {@code DurationSeconds} cap
 * stops it. Only the worked piece's standing prop and the worker's double are ridden: at the block
 * and at a consumed piece's resting spot nothing rides, and the worker's own body is never an aim
 * at all ({@link StationService#aimSource}), so no burst can ride the player.
 *
 * @param riding     the bursts to attach to the aim's entity, in authored order
 * @param positional the bursts that play at the aim's position whatever the attach answers, in
 *                   authored order
 */
record MomentBursts(@Nonnull List<Presentation.ModelParticle> riding,
        @Nonnull List<Presentation.ModelParticle> positional) {

    /**
     * PURE: the split for a moment aimed at {@code source}. An aim that is an entity
     * ({@link #ridesAnEntity}) rides every burst whose system {@code endsOnItsOwn} and plays the rest
     * at its position; any other aim plays every burst at its position and never asks the predicate.
     * A null or id-less entry is in neither list.
     */
    @Nonnull
    static MomentBursts plan(@Nonnull StationService.AimSource source,
            @Nullable Presentation.ModelParticle[] particles, @Nonnull Predicate<String> endsOnItsOwn) {
        if (!ridesAnEntity(source)) {
            return new MomentBursts(List.of(), bursts(false, particles, id -> false));
        }
        return new MomentBursts(bursts(true, particles, endsOnItsOwn), bursts(false, particles, endsOnItsOwn));
    }

    /**
     * PURE: whether an aim from {@code source} is an entity a burst may ride: the worked piece's
     * standing prop or the worker's double. The block and a consumed piece's resting spot are
     * positions, and no source names the worker's own body.
     */
    static boolean ridesAnEntity(@Nonnull StationService.AimSource source) {
        return switch (source) {
            case DISPLAY_PROP, DOUBLE -> true;
            case BLOCK, DISPLAY_RESTING -> false;
        };
    }

    /**
     * What plays at the aim's position once the attach has answered: the positional bursts, led by
     * the riding ones when nobody received them ({@code attached} false: a fresh entity the tracker
     * has shown nobody yet, nobody near), so no cue is lost.
     */
    @Nonnull
    List<Presentation.ModelParticle> atPosition(boolean attached) {
        if (attached || riding.isEmpty()) {
            return positional;
        }
        List<Presentation.ModelParticle> out = new ArrayList<>(riding.size() + positional.size());
        out.addAll(riding);
        out.addAll(positional);
        return out;
    }

    /**
     * PURE: the authored bursts that may ride an entity ({@code riding} true: their system ends on
     * its own) or the ones that may not ({@code riding} false), in authored order; a null or
     * id-less entry is in neither.
     */
    @Nonnull
    static List<Presentation.ModelParticle> bursts(boolean riding, @Nullable Presentation.ModelParticle[] particles,
            @Nonnull Predicate<String> endsOnItsOwn) {
        List<Presentation.ModelParticle> out = new ArrayList<>();
        if (particles == null) {
            return out;
        }
        for (Presentation.ModelParticle burst : particles) {
            if (burst != null && burst.hasSystemId() && endsOnItsOwn.test(burst.getSystemId()) == riding) {
                out.add(burst);
            }
        }
        return out;
    }
}
