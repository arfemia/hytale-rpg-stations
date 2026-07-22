package com.ziggfreed.rpgstations.api.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import com.ziggfreed.rpgstations.api.StationView;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.station.FlairCatalog;

/** Read-only {@link StationView} adapter over a live {@link StationAsset}. */
final class StationViewImpl implements StationView {

    @Nonnull private final String id;
    @Nonnull private final String nameKey;
    @Nonnull private final List<String> xpSkills;
    @Nonnull private final Set<String> flairIds;

    StationViewImpl(@Nonnull StationAsset asset) {
        this.id = asset.getId();
        StationAsset.Identity identity = asset.getIdentity();
        String key = identity != null ? identity.getNameKey() : null;
        this.nameKey = key != null && !key.isBlank() ? key : "rpgstations.station." + id + ".name";

        List<String> skills = new ArrayList<>();
        StationAsset.Work work = asset.getWork();
        StationAsset.WorkXp[] xp = work != null ? work.getXp() : null;
        if (xp != null) {
            for (StationAsset.WorkXp x : xp) {
                if (x != null && x.getSkill() != null && !x.getSkill().isBlank()) {
                    skills.add(x.getSkill());
                }
            }
        }
        this.xpSkills = List.copyOf(skills);

        // Leg F (design section 9.6): the effective flair-id set for a station is the SAME union
        // FlairCatalog#effectiveFlairsFor resolves at moment-playback time (inline Flairs UNIONED
        // with every applicable standalone FlairAsset) - reused here rather than re-deriving a
        // second, narrower "inline-only" view that would silently miss third-party flair content.
        this.flairIds = Set.copyOf(FlairCatalog.getInstance().effectiveFlairsFor(id, asset).keySet());
    }

    @Override
    @Nonnull
    public String id() {
        return id;
    }

    @Override
    @Nonnull
    public String nameKey() {
        return nameKey;
    }

    @Override
    @Nonnull
    public List<String> xpSkills() {
        return xpSkills;
    }

    @Override
    @Nonnull
    public Set<String> flairIds() {
        return flairIds;
    }
}
