package com.ziggfreed.rpgstations.api;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

/** A read-only view of one folded station (design section 3.2). */
public interface StationView {

    @Nonnull
    String id();

    /** The FULL client-resolvable message id (e.g. {@code "rpgstations.station.sawmill.name"}), never a bare key fragment. */
    @Nonnull
    String nameKey();

    /** The station's authored {@code Work.Xp} skill ids, in authoring order. */
    @Nonnull
    List<String> xpSkills();

    /** The station's authored {@code Flairs} map keys. */
    @Nonnull
    Set<String> flairIds();
}
