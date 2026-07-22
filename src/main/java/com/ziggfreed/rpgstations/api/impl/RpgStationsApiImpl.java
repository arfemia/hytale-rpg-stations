package com.ziggfreed.rpgstations.api.impl;

import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.Nonnull;

import com.ziggfreed.rpgstations.api.EnhanceStamperRegistry;
import com.ziggfreed.rpgstations.api.FactorRegistry;
import com.ziggfreed.rpgstations.api.FlairUnlockRegistry;
import com.ziggfreed.rpgstations.api.RpgStationsApi;
import com.ziggfreed.rpgstations.api.StationView;
import com.ziggfreed.rpgstations.api.SummaryEnricherRegistry;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.station.StationCatalog;

/**
 * The one {@link RpgStationsApi} implementation, installed via {@link RpgStationsApi#set} from
 * {@code RpgStationsPlugin.setup()} (design section 3.2). Delegates every registry accessor to
 * its own concrete singleton; engine-internal code reads those singletons DIRECTLY (see each
 * impl class's javadoc) rather than going back through this narrow public interface.
 */
public final class RpgStationsApiImpl implements RpgStationsApi {

    private static final RpgStationsApiImpl INSTANCE = new RpgStationsApiImpl();

    private RpgStationsApiImpl() {
    }

    @Nonnull
    public static RpgStationsApiImpl getInstance() {
        return INSTANCE;
    }

    @Override
    @Nonnull
    public FactorRegistry factors() {
        return FactorRegistryImpl.getInstance();
    }

    @Override
    @Nonnull
    public FlairUnlockRegistry flairUnlocks() {
        return FlairUnlockRegistryImpl.getInstance();
    }

    @Override
    @Nonnull
    public SummaryEnricherRegistry summaryEnrichers() {
        return SummaryEnricherRegistryImpl.getInstance();
    }

    @Override
    @Nonnull
    public EnhanceStamperRegistry enhanceStampers() {
        return EnhanceStamperRegistryImpl.getInstance();
    }

    @Override
    @Nonnull
    public Collection<StationView> stations() {
        Collection<StationAsset> assets = StationCatalog.getInstance().all().values();
        Collection<StationView> out = new ArrayList<>(assets.size());
        for (StationAsset a : assets) {
            if (a != null) {
                out.add(new StationViewImpl(a));
            }
        }
        return out;
    }
}
