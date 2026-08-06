package com.ziggfreed.rpgstations.api.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.api.ContributionChannelRegistry;

/**
 * The concrete {@link ContributionChannelRegistry}, exposed to third parties via
 * {@link com.ziggfreed.rpgstations.api.RpgStationsApi#get()}{@code .channels()}. Deliberately the
 * thinnest registry in the mod: a set of ids and nothing else, because a declared channel has
 * nothing to resolve - the engine forwards contributions naming it and never asks this class what
 * one means.
 *
 * <p>Mirrors {@link FactorRegistryImpl}'s discipline exactly (concurrent, id lowercased,
 * re-declaring harmless) minus the provider value. Engine-internal readers ({@code
 * station.StationValidator}'s {@code UNKNOWN_CHANNEL} check, {@code asset.AssetEditorDataSets}'
 * {@code rpgstations:channels} dropdown) call {@link #isDeclared}/{@link #registeredIds} directly
 * against THIS singleton rather than through the narrow declare-only public interface - the same
 * engine-internal extension of the frozen contract {@code FactorRegistryImpl} documents.
 *
 * <p><b>Fail-open, absolutely</b> (decision 75): {@link #isDeclared} answering {@code false}
 * produces a WARN and nothing more. An undeclared channel is still forwarded verbatim.
 */
public final class ContributionChannelRegistryImpl implements ContributionChannelRegistry {

    private static final ContributionChannelRegistryImpl INSTANCE = new ContributionChannelRegistryImpl();

    /** A concurrent SET (map-backed): declaration order is irrelevant, the read is always sorted. */
    private final Set<String> channels = ConcurrentHashMap.newKeySet();

    private ContributionChannelRegistryImpl() {
    }

    @Nonnull
    public static ContributionChannelRegistryImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public void declare(@Nonnull String channelId) {
        if (channelId.isBlank()) {
            return;
        }
        channels.add(channelId.toLowerCase(Locale.ROOT));
    }

    /** True when {@code channelId} has been declared; the validator's known-channel check. */
    public boolean isDeclared(@Nullable String channelId) {
        return channelId != null && !channelId.isBlank()
                && channels.contains(channelId.toLowerCase(Locale.ROOT));
    }

    /**
     * Every currently-declared channel id, lowercased and sorted - the engine-internal read beside
     * {@link #isDeclared} (never part of the frozen declare-only api contract). Backs the
     * {@code rpgstations:channels} Asset-Editor dropdown dataset and the {@code UNKNOWN_CHANNEL}
     * warn's "declared channels are ..." echo, which is why it is a snapshot: a mod declaring a
     * channel later simply widens the next answer.
     */
    @Nonnull
    public List<String> registeredIds() {
        List<String> ids = new ArrayList<>(channels);
        Collections.sort(ids);
        return ids;
    }
}
