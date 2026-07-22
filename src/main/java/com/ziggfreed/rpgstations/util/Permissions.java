package com.ziggfreed.rpgstations.util;

import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * RpgStations' own admin-permission check (a port of the MMO's {@code util.PermissionUtil}
 * shape, minus its {@code SkillConfig}-driven on/off toggle - RpgStations has no equivalent
 * config surface yet, so permission checking is always active): OP (or the wildcard
 * {@code "*"}) always passes; otherwise the caller must hold {@link #ADMIN_PERMISSION}.
 */
public final class Permissions {

    public static final String ADMIN_PERMISSION = "rpgstations.admin";

    private Permissions() {
    }

    /**
     * True when {@code ref} has admin access: OP/{@code "*"} always qualifies, otherwise the
     * {@link #ADMIN_PERMISSION} node is checked. A null ref (console with no ref, or an
     * unresolved sender) returns false.
     */
    public static boolean hasAdminPermission(@Nullable PlayerRef ref) {
        if (ref == null) {
            return false;
        }
        if (ref.hasPermission("OP") || ref.hasPermission("*")) {
            return true;
        }
        return ref.hasPermission(ADMIN_PERMISSION);
    }
}
