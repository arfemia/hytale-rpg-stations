package com.ziggfreed.rpgstations.asset;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditor;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.ziggfreed.common.asset.EditorSchema;
import com.ziggfreed.common.codec.TagMatch;

/**
 * The shared INPUT MATCHER: which held or placed material an action selects, a custody socket
 * accepts, or a fallback route applies to. Generalizes the {@code StationAsset.Tool} gate grammar
 * from tools to any item: match = ANY non-blank/non-empty route satisfied (orthogonal routes, the
 * same "match = ANY route" convention as {@code Tool}). A null/all-blank {@link ActionInput}
 * matches EVERYTHING (a catch-all; {@code StationValidator} flags an unreachable later catch-all
 * action - {@code UNREACHABLE_ACTION}).
 *
 * <p>{@link #function} is the FUNCTIONAL route: {@code "Weapon"}/{@code "Armor"}/{@code "Tool"},
 * tested against the held item's native shape ({@code item.getWeapon() != null} and so on).
 *
 * <p>{@link #quality} is the QUALITY route: one or more native {@code ItemQuality} asset ids (an
 * open list, since a pack can ship its own tier), matched without regard to case against the
 * quality the STACK carries ({@code ItemStack#getQualityIndex}: the index the stack was made or
 * re-qualified with, which is its item's own when it carries none). A material with no stack
 * behind it (a counted pile entry) reads its item's quality, and a placed BLOCK reads none, so a
 * {@code Quality} route never matches a block socket or a structure cell (the validator warns).
 * In the Asset Editor each entry offers the engine's own quality picker
 * ({@code EditorSchema.assetRef}); nothing is refused at decode, so a typo or a tier a later pack
 * ships still loads and {@code QUALITY_UNKNOWN} names it.
 *
 * <p><b>{@link #excepts} carve holes in the match.</b> Each is this same matcher one level down,
 * without an {@code Except} of its own: a material the outer routes accept is REFUSED when any
 * {@code Except} matcher accepts it too. So {@code {"Tags": {"Type": ["Weapon", "Tool"]}, "Except":
 * {"Tags": {"Type": ["Ammo"]}}}} takes weapons and tools but never ammunition, without listing
 * every id. {@code Except} is authored as ONE matcher or as an ARRAY of them (the same hole either
 * way: a material any entry accepts is refused), and an extension's overlay ADDS its entries
 * beside the base's rather than replacing them, so a pack can protect its own item from a station
 * without restating what the jar already protects. An absent {@code Except} excludes nothing, and
 * so does an entry that is itself catch-all (no route at all): it matches no route, so it carves
 * no hole and the matcher accepts exactly what it would without it. Such an entry is inert, almost
 * always an authoring slip, and the validator warns {@code EXCEPT_CATCH_ALL}.
 */
public final class ActionInput {

    @Nullable protected String itemId;
    @Nullable protected String resourceTypeId;
    @Nullable protected Map<String, String[]> tags;
    @Nullable protected String function;
    @Nullable protected String[] quality;
    @Nullable protected ActionInput[] excepts;

    /** The nested {@code Except} matcher's codec: the same five routes, no further nesting. */
    public static final BuilderCodec<ActionInput> EXCEPT_CODEC = codec(false);

    /** The {@code Except} leaf's codec: one matcher, or an array of them, decoded to the same array. */
    public static final ObjectOrArrayCodec<ActionInput> EXCEPTS_CODEC =
            new ObjectOrArrayCodec<>(EXCEPT_CODEC, ActionInput[]::new);

    /** The full matcher: the five routes plus the {@code Except} hole. */
    public static final BuilderCodec<ActionInput> CODEC = codec(true);

    /**
     * ONE codec definition for both nesting levels: {@code withExcept} adds the {@code Except} leaf
     * (typed as the nested {@link #EXCEPT_CODEC}, one or several), and the nested level omits it,
     * so an exclusion cannot carve a hole in an exclusion.
     */
    @Nonnull
    private static BuilderCodec<ActionInput> codec(boolean withExcept) {
        BuilderCodec.Builder<ActionInput> builder = BuilderCodec.builder(ActionInput.class, ActionInput::new)
                .appendInherited(new KeyedCodec<>("ItemId", Codec.STRING, false),
                        (o, v) -> o.itemId = v, o -> o.itemId, (o, p) -> o.itemId = p.itemId)
                .documentation("Match an exact held item id (one of several optional routes; match = ANY route satisfied).").add()
                .appendInherited(new KeyedCodec<>("ResourceTypeId", Codec.STRING, false),
                        (o, v) -> o.resourceTypeId = v, o -> o.resourceTypeId,
                        (o, p) -> o.resourceTypeId = p.resourceTypeId)
                .documentation("Match a native resource-type family of the held item.").add()
                .appendInherited(new KeyedCodec<>("Tags", TagMatch.CODEC, false),
                        (o, v) -> o.tags = v, o -> o.tags, (o, p) -> o.tags = p.tags)
                .documentation("Match the held item's native tags (tag family -> accepted values).").add()
                .appendInherited(new KeyedCodec<>("Function", Codec.STRING, false),
                        (o, v) -> o.function = v, o -> o.function, (o, p) -> o.function = p.function)
                .documentation("Match the held item's live function: 'Weapon' | 'Armor' | 'Tool'.")
                .metadata(new UIEditor(new UIEditor.Dropdown("rpgstations:action-function"))).add()
                .appendInherited(new KeyedCodec<>("Quality", Codec.STRING_ARRAY, false),
                        (o, v) -> o.quality = v, o -> o.quality, (o, p) -> o.quality = p.quality)
                .documentation("Match the quality the held or placed stack carries: native ItemQuality ids (Common, Rare, Developer, or a pack's own tier), any one of them, without regard to case. A stack reads the quality it was made or re-qualified with; a counted pile entry reads its item's; a placed block reads none, so this route never matches a block.")
                .metadata(EditorSchema.assetRef(ItemQuality.class)).add();
        if (withExcept) {
            builder = builder.appendInherited(new KeyedCodec<>("Except", EXCEPTS_CODEC, false),
                            (o, v) -> o.excepts = v, o -> o.excepts, (o, p) -> o.excepts = p.excepts)
                    .documentation("A material the routes above accept is REFUSED when a nested matcher here (the same ItemId | ResourceTypeId | Tags | Function | Quality routes, match = ANY) accepts it too. One matcher, or an array of them; an extension's overlay adds its entries beside these. Carves a hole in a broad match without listing every id; absent excludes nothing, and an entry authoring no route matches nothing, so it excludes nothing either. On a Custody.Input or a socket Match with no route of its own, the holes are carved out of what the station derives from its recipe and fallback routes.").add();
        }
        return builder.build();
    }

    public ActionInput() {
    }

    /** Java-side factory; sets the same fields the codec fills. */
    @Nonnull
    public static ActionInput of(@Nullable String itemId, @Nullable String resourceTypeId,
            @Nullable Map<String, String[]> tags, @Nullable String function) {
        return of(itemId, resourceTypeId, tags, function, (ActionInput[]) null);
    }

    /** As above, plus ONE {@link #excepts} hole. */
    @Nonnull
    public static ActionInput of(@Nullable String itemId, @Nullable String resourceTypeId,
            @Nullable Map<String, String[]> tags, @Nullable String function, @Nullable ActionInput except) {
        return of(itemId, resourceTypeId, tags, function, except == null ? null : new ActionInput[] {except});
    }

    /** As above, plus the {@link #excepts} holes. */
    @Nonnull
    public static ActionInput of(@Nullable String itemId, @Nullable String resourceTypeId,
            @Nullable Map<String, String[]> tags, @Nullable String function, @Nullable ActionInput[] excepts) {
        return of(itemId, resourceTypeId, tags, function, null, excepts);
    }

    /** Every route, the {@link #quality} ids included, plus the {@link #excepts} holes. */
    @Nonnull
    public static ActionInput of(@Nullable String itemId, @Nullable String resourceTypeId,
            @Nullable Map<String, String[]> tags, @Nullable String function, @Nullable String[] quality,
            @Nullable ActionInput[] excepts) {
        ActionInput i = new ActionInput();
        i.itemId = itemId;
        i.resourceTypeId = resourceTypeId;
        i.tags = tags;
        i.function = function;
        i.quality = quality;
        i.excepts = excepts;
        return i;
    }

    /** A matcher authoring the {@link #quality} route alone: any of these quality ids. */
    @Nonnull
    public static ActionInput ofQuality(@Nonnull String... quality) {
        return of(null, null, null, null, quality, null);
    }

    @Nullable
    public String getItemId() {
        return itemId;
    }

    @Nullable
    public String getResourceTypeId() {
        return resourceTypeId;
    }

    @Nullable
    public Map<String, String[]> getTags() {
        return tags;
    }

    /** {@code "Weapon"|"Armor"|"Tool"}; unrecognized values are a content authoring mistake (validator warns). */
    @Nullable
    public String getFunction() {
        return function;
    }

    /** The accepted native {@code ItemQuality} ids, in authored order; null or empty = no quality route. */
    @Nullable
    public String[] getQuality() {
        return quality;
    }

    /** True when at least one non-blank quality id is authored. */
    public boolean hasQualityRoute() {
        if (quality == null) {
            return false;
        }
        for (String id : quality) {
            if (id != null && !id.isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** The nested exclusion matchers, in authored order; null or empty = nothing is excluded. */
    @Nullable
    public ActionInput[] getExcepts() {
        return excepts;
    }

    /** True when at least one exclusion matcher is authored. */
    public boolean hasExcept() {
        return excepts != null && excepts.length > 0;
    }

    /** True when NO route is authored (a catch-all matcher - matches any held stack). */
    public boolean isCatchAll() {
        boolean hasItemId = itemId != null && !itemId.isBlank();
        boolean hasResourceTypeId = resourceTypeId != null && !resourceTypeId.isBlank();
        boolean hasTags = tags != null && !tags.isEmpty();
        boolean hasFunction = function != null && !function.isBlank();
        return !hasItemId && !hasResourceTypeId && !hasTags && !hasFunction && !hasQualityRoute();
    }
}
