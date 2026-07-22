package com.ziggfreed.rpgstations.api.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import com.ziggfreed.rpgstations.api.StationView;
import com.ziggfreed.rpgstations.asset.StationAsset;

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

        Set<String> flairs = new LinkedHashSet<>();
        Map<String, StationAsset.Flair> flairMap = asset.getFlairs();
        if (flairMap != null) {
            flairs.addAll(flairMap.keySet());
        }
        this.flairIds = Set.copyOf(flairs);
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
