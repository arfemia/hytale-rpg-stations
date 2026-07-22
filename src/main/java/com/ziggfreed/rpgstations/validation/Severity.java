package com.ziggfreed.rpgstations.validation;

/**
 * How serious a {@link Finding} is. Ported verbatim from the MMO's {@code validation.Severity}
 * mini-core (RPG Stations extraction leg 2, design section 4.1: "RpgStations-local
 * validation/ mini-core").
 */
public enum Severity {
    ERROR,
    WARNING,
    INFO
}
