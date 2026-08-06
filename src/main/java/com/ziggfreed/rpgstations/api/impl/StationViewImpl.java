package com.ziggfreed.rpgstations.api.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.api.StationContribution;
import com.ziggfreed.rpgstations.api.StationView;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.station.ActionResolver;
import com.ziggfreed.rpgstations.station.ExtensionCatalog;
import com.ziggfreed.rpgstations.station.FlairCatalog;

/** Read-only {@link StationView} adapter over a live {@link StationAsset}. */
final class StationViewImpl implements StationView {

    @Nonnull private final String id;
    @Nonnull private final String nameKey;
    @Nonnull private final List<StationContribution> contributions;
    @Nonnull private final Map<String, List<String>> contributionParams;
    @Nonnull private final Set<String> flairIds;

    StationViewImpl(@Nonnull StationAsset asset) {
        this.id = asset.getId();
        StationAsset.Identity identity = asset.getIdentity();
        String key = identity != null ? identity.getNameKey() : null;
        this.nameKey = key != null && !key.isBlank() ? key : "rpgstations.station." + id + ".name";

        // The raw list keeps every entry a listener could care about, INCLUDING one with no Param
        // (which the derived per-channel map below cannot represent) - that shape is exactly what
        // a channel owner's own validation wants to flag, so it must stay visible here.
        //
        // The EFFECTIVE union, not the station-level Work alone: every resolved action's own Work
        // (a whole-group override can author its own contributions) plus the Station- and
        // Action-targeted extension appends - the same reads the cycle event forwards from. An
        // action inheriting the station Work whole-group re-yields the same entries, so the walk
        // de-duplicates on the (Channel, Param, Amount) triple.
        List<StationContribution> posts = new ArrayList<>();
        Map<String, List<String>> params = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        ExtensionCatalog extensions = ExtensionCatalog.getInstance();
        StationAsset.Work stationWork = asset.getWork();
        collect(extensions.applyToStationContributions(id,
                stationWork != null ? stationWork.getPerCycleContributions() : null), posts, params, seen);
        Map<String, ActionDef> actions = asset.getActions();
        if (actions != null) {
            for (String actionId : actions.keySet()) {
                ActionResolver.ResolvedAction resolved = ActionResolver.resolve(asset, actionId);
                StationAsset.Work actionWork = resolved != null ? resolved.getWork() : null;
                Contribution[] merged = extensions.applyToStationContributions(id,
                        actionWork != null ? actionWork.getPerCycleContributions() : null);
                String target = ActionResolver.actionTargetId(asset, actionId);
                if (target != null) {
                    merged = extensions.applyToActionContributions(target, merged);
                }
                collect(merged, posts, params, seen);
            }
        }
        this.contributions = List.copyOf(posts);
        Map<String, List<String>> frozen = new LinkedHashMap<>(params.size());
        for (Map.Entry<String, List<String>> e : params.entrySet()) {
            frozen.put(e.getKey(), List.copyOf(e.getValue()));
        }
        this.contributionParams = Collections.unmodifiableMap(frozen);

        // Leg F (design section 9.6): the effective flair-id set for a station is the SAME union
        // FlairCatalog#effectiveFlairsFor resolves at moment-playback time (inline Flairs UNIONED
        // with every applicable standalone FlairAsset) - reused here rather than re-deriving a
        // second, narrower "inline-only" view that would silently miss third-party flair content.
        this.flairIds = Set.copyOf(FlairCatalog.getInstance().effectiveFlairsFor(id, asset).keySet());
    }

    /** One authored source's entries into the accumulating union (dedupe key: channel|param|amount). */
    private static void collect(@Nullable Contribution[] authored, @Nonnull List<StationContribution> posts,
            @Nonnull Map<String, List<String>> params, @Nonnull Set<String> seen) {
        if (authored == null) {
            return;
        }
        for (Contribution c : authored) {
            if (c == null || c.getChannel() == null || c.getChannel().isBlank()) {
                continue;
            }
            Double amount = c.getAmount();
            double effective = amount == null ? 0.0 : amount;
            String param = c.getParam();
            String key = c.getChannel().toLowerCase(Locale.ROOT) + "|"
                    + (param == null ? "" : param) + "|" + effective;
            if (!seen.add(key)) {
                continue;
            }
            posts.add(new StationContribution(c.getChannel(), param, effective));
            List<String> forChannel = params.computeIfAbsent(
                    c.getChannel().toLowerCase(Locale.ROOT), k -> new ArrayList<>());
            if (param != null && !param.isBlank()) {
                forChannel.add(param);
            }
        }
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
    public List<StationContribution> contributions() {
        return contributions;
    }

    @Override
    @Nonnull
    public Set<String> contributionChannels() {
        return contributionParams.keySet();
    }

    @Override
    @Nonnull
    public List<String> contributionParams(@Nonnull String channel) {
        List<String> forChannel = contributionParams.get(channel.toLowerCase(Locale.ROOT));
        return forChannel == null ? List.of() : forChannel;
    }

    @Override
    @Nonnull
    public Set<String> flairIds() {
        return flairIds;
    }
}
