package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.ziggfreed.rpgstations.asset.Presentation;

/**
 * Whether a particle system provably ENDS ON ITS OWN, the one question that decides if a burst may
 * ride an entity. An entity-attached system carries no playback cap (the engine's attached-particle
 * leaf has no duration), so it lives until its own lifetime ends or its entity is removed; a system
 * that never ends would ride its entity for as long as the entity stands. The proof is the native
 * {@code ParticleSystem} asset's own {@code LifeSpan}: a positive value is how long the system
 * lasts, and zero or less means unlimited (the engine's own documented meaning). A system whose
 * spawners happen to run dry is not provable from the server, so only the {@code LifeSpan} counts.
 */
final class ParticleLifetimes {

    private ParticleLifetimes() {
    }

    /** PURE: does a system whose own {@code LifeSpan} is this ({@code null} = unknown) end on its own? */
    static boolean provablyEnds(@Nullable Float lifeSpanSeconds) {
        return lifeSpanSeconds != null && lifeSpanSeconds > 0f;
    }

    /** Whether the loaded system {@code systemId} ends on its own ({@link #provablyEnds} over {@link #lifeSpanOf}). */
    static boolean systemProvablyEnds(@Nullable String systemId) {
        return provablyEnds(lifeSpanOf(systemId));
    }

    /**
     * The loaded native system's own {@code LifeSpan} in seconds, or {@code null} when it cannot be
     * read (a blank or unknown id, or no asset map loaded), which counts as unproven.
     */
    @Nullable
    static Float lifeSpanOf(@Nullable String systemId) {
        if (systemId == null || systemId.isBlank()) {
            return null;
        }
        try {
            ParticleSystem system = ParticleSystem.getAssetMap().getAsset(systemId);
            return system != null ? system.getLifeSpan() : null;
        } catch (Throwable t) {
            return null;
        }
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
