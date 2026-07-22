package com.ziggfreed.rpgstations.api;

import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

/**
 * Package-private static holder backing {@link RpgStationsApi#get()}/{@link RpgStationsApi#set}
 * (design section 3.2, mirroring the MMO's {@code MMOSkillTreeAPI} singleton discipline). Kept
 * out of the public interface deliberately: an interface field is always implicitly {@code
 * public static final}, so a raw {@code AtomicReference} on {@link RpgStationsApi} itself would
 * let any caller overwrite the installed implementation directly. This class is invisible
 * outside the {@code com.ziggfreed.rpgstations.api} package.
 */
final class RpgStationsApiHolder {

    private static final AtomicReference<RpgStationsApi> INSTANCE = new AtomicReference<>();

    private RpgStationsApiHolder() {
    }

    static void set(@Nonnull RpgStationsApi impl) {
        INSTANCE.set(impl);
    }

    @Nonnull
    static RpgStationsApi get() {
        RpgStationsApi api = INSTANCE.get();
        if (api == null) {
            throw new IllegalStateException(
                    "RpgStationsApi.get() called before RpgStations setup() (or RpgStations is not installed)");
        }
        return api;
    }
}
