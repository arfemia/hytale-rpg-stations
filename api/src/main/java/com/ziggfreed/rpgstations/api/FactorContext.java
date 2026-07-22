package com.ziggfreed.rpgstations.api;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Immutable per-evaluation numeric context every {@link StationFactorProvider} resolves against
 * (design section 3.2). Built fresh per evaluation batch (one per real/idle cycle, one per
 * session-completion loot pass, or one per pre-session {@code Requires} gate check) by the
 * engine; never retained by a provider past the {@code resolve} call that receives it.
 *
 * <p><b>Plain data</b> ({@link #playerId()}, {@link #stationId()}, {@link #actionId()},
 * {@link #sessionSeconds()}, {@link #cycleIndex()}, {@link #toolPower()},
 * {@link #toolDurabilityPercent()}, {@link #progressionSkills()}) is always safe to retain.
 * <b>Live world-thread context</b> ({@link #store()}, {@link #playerRef()}) is valid ONLY
 * synchronously during the resolve call; a provider that defers work must capture the plain
 * fields and re-resolve.
 */
public final class FactorContext {

    @Nullable private final Store<EntityStore> store;
    @Nullable private final PlayerRef playerRef;
    @Nonnull private final UUID playerId;
    @Nonnull private final String stationId;
    @Nonnull private final String actionId;
    private final long sessionSeconds;
    private final int cycleIndex;
    private final double toolPower;
    private final double toolDurabilityPercent;
    @Nonnull private final List<String> progressionSkills;

    private FactorContext(@Nonnull Builder b) {
        this.store = b.store;
        this.playerRef = b.playerRef;
        this.playerId = b.playerId;
        this.stationId = b.stationId;
        this.actionId = b.actionId;
        this.sessionSeconds = b.sessionSeconds;
        this.cycleIndex = b.cycleIndex;
        this.toolPower = b.toolPower;
        this.toolDurabilityPercent = b.toolDurabilityPercent;
        this.progressionSkills = b.progressionSkills == null ? List.of() : List.copyOf(b.progressionSkills);
    }

    @Nullable
    public Store<EntityStore> store() {
        return store;
    }

    @Nullable
    public PlayerRef playerRef() {
        return playerRef;
    }

    @Nonnull
    public UUID playerId() {
        return playerId;
    }

    @Nonnull
    public String stationId() {
        return stationId;
    }

    /** The one action id phase 1 ever forwards ({@code "work"}); phase 2 adds multi-action ids. */
    @Nonnull
    public String actionId() {
        return actionId;
    }

    /** Whole seconds elapsed since the session started ({@code rpgstations:session_seconds}); 0 for a pre-session gate check. */
    public long sessionSeconds() {
        return sessionSeconds;
    }

    /** The 1-based cycle index this batch belongs to ({@code rpgstations:cycle_count}); 0 for a pre-session gate check. */
    public int cycleIndex() {
        return cycleIndex;
    }

    /** The held tool's resolved gather power for the station's effective gather type ({@code rpgstations:tool_power}); 0 when none. */
    public double toolPower() {
        return toolPower;
    }

    /** The held tool's durability percent [0,100] ({@code rpgstations:tool_durability_percent}); 100 when no durability tracked or none held. */
    public double toolDurabilityPercent() {
        return toolDurabilityPercent;
    }

    /** The station's authored {@code Work.Xp} skill ids, so an aggregating provider (e.g. a luck factor) knows the default skill set without a {@code Param}. */
    @Nonnull
    public List<String> progressionSkills() {
        return progressionSkills;
    }

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        @Nullable private Store<EntityStore> store;
        @Nullable private PlayerRef playerRef;
        @Nullable private UUID playerId;
        @Nullable private String stationId;
        @Nonnull private String actionId = "work";
        private long sessionSeconds;
        private int cycleIndex;
        private double toolPower;
        private double toolDurabilityPercent = 100.0;
        @Nullable private List<String> progressionSkills;

        private Builder() {
        }

        @Nonnull
        public Builder store(@Nullable Store<EntityStore> store) {
            this.store = store;
            return this;
        }

        @Nonnull
        public Builder playerRef(@Nullable PlayerRef playerRef) {
            this.playerRef = playerRef;
            return this;
        }

        @Nonnull
        public Builder playerId(@Nonnull UUID playerId) {
            this.playerId = playerId;
            return this;
        }

        @Nonnull
        public Builder stationId(@Nonnull String stationId) {
            this.stationId = stationId;
            return this;
        }

        @Nonnull
        public Builder actionId(@Nonnull String actionId) {
            this.actionId = actionId;
            return this;
        }

        @Nonnull
        public Builder sessionSeconds(long sessionSeconds) {
            this.sessionSeconds = sessionSeconds;
            return this;
        }

        @Nonnull
        public Builder cycleIndex(int cycleIndex) {
            this.cycleIndex = cycleIndex;
            return this;
        }

        @Nonnull
        public Builder toolPower(double toolPower) {
            this.toolPower = toolPower;
            return this;
        }

        @Nonnull
        public Builder toolDurabilityPercent(double toolDurabilityPercent) {
            this.toolDurabilityPercent = toolDurabilityPercent;
            return this;
        }

        @Nonnull
        public Builder progressionSkills(@Nullable List<String> progressionSkills) {
            this.progressionSkills = progressionSkills;
            return this;
        }

        @Nonnull
        public FactorContext build() {
            if (playerId == null) {
                throw new IllegalStateException("FactorContext requires a playerId");
            }
            if (stationId == null) {
                throw new IllegalStateException("FactorContext requires a stationId");
            }
            return new FactorContext(this);
        }
    }
}
