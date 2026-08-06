package com.ziggfreed.rpgstations.api;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

/** A read-only view of one folded station. */
public interface StationView {

    @Nonnull
    String id();

    /** The FULL client-resolvable message id (e.g. {@code "rpgstations.station.sawmill.name"}), never a bare key fragment. */
    @Nonnull
    String nameKey();

    /**
     * The station's authored {@code Work.PerCycleContributions} entries VERBATIM, in authoring
     * order, with their amounts unscaled.
     *
     * <p>This is the raw view on purpose. The two derived accessors below drop anything that has
     * no {@code Param}, which is exactly the shape a channel owner most wants to flag ("this
     * contribution names my channel but credits nothing"), so a validating consumer walks THIS
     * list and a consumer that only wants the subject set walks the derived ones.
     */
    @Nonnull
    List<StationContribution> contributions();

    /** Every contribution channel the station's {@code Work} posts to, lowercased. */
    @Nonnull
    Set<String> contributionChannels();

    /**
     * The {@code Param}s the station's {@code Work} contributions name on {@code channel}, in
     * authoring order; empty when it posts to that channel with no params, or not at all.
     */
    @Nonnull
    List<String> contributionParams(@Nonnull String channel);

    /** The station's authored {@code Flairs} map keys. */
    @Nonnull
    Set<String> flairIds();
}
