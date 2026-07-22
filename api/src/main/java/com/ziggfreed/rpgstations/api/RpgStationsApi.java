package com.ziggfreed.rpgstations.api;

/**
 * Placeholder for the RPG Stations extension surface (native outbound events on the
 * shared Hytale event bus, plus request/response registries on a static holder - the
 * kweebec {@code RoundEvents} recipe). The real surface (station factor providers,
 * flair unlock providers, summary enrichers, and the observe-only lifecycle events)
 * lands in leg 4 per the unified design doc, section 5.
 *
 * <p>This class exists only so the {@code :api} module compiles and produces a
 * non-empty {@code rpg-stations-api-1.0.0.jar} during scaffold (leg 0).
 */
public final class RpgStationsApi {

    private RpgStationsApi() {
    }
}
