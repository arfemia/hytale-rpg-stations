package com.ziggfreed.rpgstations.asset;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

/**
 * One file of the server-wide PROTECT-LIST: what no consuming station may take as placed input,
 * whatever its own matcher accepts. Loaded from {@code Server/RpgStations/ProtectLists/<Name>.json}
 * (Pattern A, id = lowercased filename, the same registration shape as every other store here).
 *
 * <pre>{@code
 * { "Protects": [ { "ItemId": "Weapon_Sword_Heirloom" },
 *                 { "Tags": { "Type": ["Trophy"] }, "Except": { "ItemId": "Trophy_Common_Plaque" } } ],
 *   "Stations": ["Disenchanting_Table"] }
 * }</pre>
 *
 * <p><b>Every loaded file counts.</b> The files ADD UP into one server-wide list, whichever layer
 * each comes from (this jar, a pack, the server owner's own pack): a pack's list never silently
 * replaces another's. Overriding a file BY ID (the same file name in a later layer) is how an entry
 * is taken back, since the later file is then the one the store holds.
 *
 * <p>{@link #protects} is the shared input matcher ({@link ActionInput}: the {@code ItemId} /
 * {@code ResourceTypeId} / {@code Tags} / {@code Function} routes and the {@code Except} hole), one
 * or several. An entry protects what its routes match, minus its holes; an entry authoring no route
 * protects NOTHING (it is never read as "everything", which would lock every station).
 *
 * <p>{@link #stations} and {@link #actions} SCOPE the file, independently: absent or empty means
 * every consuming station (or every action), both authored means both must match, and ids are
 * matched without regard to case. A press that offers a protected piece is refused with its own
 * reason, {@code Refused:Protected}.
 */
public final class ProtectListAsset implements JsonAssetWithMap<String, DefaultAssetMap<String, ProtectListAsset>> {

    private String id;
    private AssetExtraInfo.Data data;

    @Nullable private ActionInput[] protects;
    @Nullable private String[] stations;
    @Nullable private String[] actions;

    public static final AssetBuilderCodec<String, ProtectListAsset> CODEC = AssetBuilderCodec.builder(
                    ProtectListAsset.class,
                    ProtectListAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id == null ? null : id.toLowerCase(Locale.ROOT),
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .documentation("Ignored - the protect-list id comes from the asset filename, not this key. Kept as a schema field for editor display only.").add()
            .appendInherited(new KeyedCodec<>("Protects",
                            new ObjectOrArrayCodec<>(ActionInput.CODEC, ActionInput[]::new), false),
                    (a, v) -> a.protects = v, a -> a.protects, (a, p) -> a.protects = p.protects)
            .documentation("What this file protects: one input matcher or an array of them (ItemId | ResourceTypeId | Tags | Function, match = ANY route, minus the entry's own Except holes). A protected piece is refused by every consuming station this file applies to, with its own reason (Refused:Protected). An entry authoring no route protects nothing.").add()
            .appendInherited(new KeyedCodec<>("Stations", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                    (a, v) -> a.stations = v, a -> a.stations, (a, p) -> a.stations = p.stations)
            .documentation("Station ids this file applies to, matched without regard to case; absent or empty = every consuming station. Authored beside Actions, both must match.").add()
            .appendInherited(new KeyedCodec<>("Actions", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                    (a, v) -> a.actions = v, a -> a.actions, (a, p) -> a.actions = p.actions)
            .documentation("Action ids this file applies to, at any station in scope, matched without regard to case; absent or empty = every action. Authored beside Stations, both must match.").add()
            .build();

    public ProtectListAsset() {
    }

    /** Java-side construction path; sets the same fields the codec fills. */
    @Nonnull
    public static ProtectListAsset of(@Nonnull String id, @Nullable ActionInput[] protects,
            @Nullable String[] stations, @Nullable String[] actions) {
        ProtectListAsset a = new ProtectListAsset();
        a.id = id.toLowerCase(Locale.ROOT);
        a.protects = protects;
        a.stations = stations;
        a.actions = actions;
        return a;
    }

    @Override
    public String getId() {
        return id;
    }

    /** What this file protects, in authored order; null or empty protects nothing. */
    @Nullable
    public ActionInput[] getProtects() {
        return protects;
    }

    /** Station ids this file is scoped to; null or empty = every station. */
    @Nullable
    public String[] getStations() {
        return stations;
    }

    /** Action ids this file is scoped to; null or empty = every action. */
    @Nullable
    public String[] getActions() {
        return actions;
    }

    /**
     * Whether this file applies at {@code stationId}'s action {@code actionId}: each scope list
     * that is authored must name the id (without regard to case), and an unauthored list admits
     * everything, so a file with neither applies at every consuming station and action.
     */
    public boolean appliesTo(@Nullable String stationId, @Nullable String actionId) {
        return scopeAdmits(stations, stationId) && scopeAdmits(actions, actionId);
    }

    /** PURE: an unauthored (null or empty) scope admits every id; an authored one only the ids it names. */
    private static boolean scopeAdmits(@Nullable String[] scope, @Nullable String candidate) {
        if (scope == null || scope.length == 0) {
            return true;
        }
        if (candidate == null) {
            return false;
        }
        for (String id : scope) {
            if (id != null && id.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }
}
