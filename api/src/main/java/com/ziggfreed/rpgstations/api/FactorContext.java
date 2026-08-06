package com.ziggfreed.rpgstations.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Immutable per-evaluation numeric context every {@link StationFactorProvider} resolves against
 *. Built fresh per evaluation batch (one per real/idle cycle, one per
 * session-completion loot pass, or one per pre-session {@code Requires} gate check) by the
 * engine; never retained by a provider past the {@code resolve} call that receives it.
 *
 * <p><b>Plain data</b> ({@link #playerId()}, {@link #stationId()}, {@link #actionId()},
 * {@link #sessionSeconds()}, {@link #cycleIndex()}, {@link #toolPower()},
 * {@link #toolDurabilityPercent()}, {@link #contributionChannels()},
 * {@link #contributionParams(String)}) is always safe to retain. <b>Live world-thread context</b>
 * ({@link #store()}, {@link #playerRef()}) is valid ONLY synchronously during the resolve call; a
 * provider that defers work must capture the plain fields and re-resolve.
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
    @Nonnull private final Map<String, List<String>> contributionParams;

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
        this.contributionParams = copyChannelMap(b.contributionParams);
    }

    @Nonnull
    private static Map<String, List<String>> copyChannelMap(@Nullable Map<String, List<String>> src) {
        if (src == null || src.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>(src.size());
        for (Map.Entry<String, List<String>> e : src.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                continue;
            }
            List<String> params = e.getValue();
            out.put(e.getKey().toLowerCase(Locale.ROOT), params == null ? List.of() : List.copyOf(params));
        }
        return Collections.unmodifiableMap(out);
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

    /** The resolved action id ({@code "work"} for a station with no {@code Actions} map). */
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

    /**
     * Every contribution channel the resolved action posts to, lowercased - a provider's cheap
     * "does this station have anything to do with me" test.
     */
    @Nonnull
    public Set<String> contributionChannels() {
        return contributionParams.keySet();
    }

    /**
     * The {@code Param}s the resolved action's contributions name on {@code channel}, in authoring
     * order; EMPTY when the station posts to that channel with no params, or does not post to it
     * at all. This is how an aggregating provider (say, a bonus factor that depends on WHAT is
     * being credited) learns the default subject set when the authored {@code Condition}/
     * {@code FactorRef} carried no {@code Param} of its own. Channel-scoped on purpose: a flat
     * list would mix two mods' vocabularies into one indistinguishable pile.
     */
    @Nonnull
    public List<String> contributionParams(@Nonnull String channel) {
        List<String> params = contributionParams.get(channel.toLowerCase(Locale.ROOT));
        return params == null ? List.of() : params;
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
        @Nullable private Map<String, List<String>> contributionParams;

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

        /**
         * The resolved action's contribution {@code Param}s, keyed by channel id (lowercased on
         * copy; a blank key is dropped). Null/empty is fine - it simply means every
         * {@link #contributionParams(String)} lookup answers empty.
         */
        @Nonnull
        public Builder contributions(@Nullable Map<String, List<String>> contributionParams) {
            this.contributionParams = contributionParams;
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
