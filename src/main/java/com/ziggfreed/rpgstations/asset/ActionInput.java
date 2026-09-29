package com.ziggfreed.rpgstations.asset;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditor;
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
 * <p><b>{@link #except} carves a hole in the match.</b> It is this same matcher one level down,
 * without an {@code Except} of its own: a material the outer routes accept is REFUSED when the
 * {@code Except} matcher accepts it too. So {@code {"Tags": {"Type": ["Weapon", "Tool"]}, "Except":
 * {"Tags": {"Type": ["Ammo"]}}}} takes weapons and tools but never ammunition, without listing
 * every id. An absent {@code Except} excludes nothing, and so does one that is itself catch-all
 * (no route at all): it matches no route, so it carves no hole and the matcher accepts exactly
 * what it would without it. Such an {@code Except} is inert, almost always an authoring slip, and
 * the validator warns {@code EXCEPT_CATCH_ALL}.
 */
public final class ActionInput {

    @Nullable protected String itemId;
    @Nullable protected String resourceTypeId;
    @Nullable protected Map<String, String[]> tags;
    @Nullable protected String function;
    @Nullable protected ActionInput except;

    /** The nested {@code Except} matcher's codec: the same four routes, no further nesting. */
    public static final BuilderCodec<ActionInput> EXCEPT_CODEC = codec(false);

    /** The full matcher: the four routes plus the {@code Except} hole. */
    public static final BuilderCodec<ActionInput> CODEC = codec(true);

    /**
     * ONE codec definition for both nesting levels: {@code withExcept} adds the {@code Except} leaf
     * (typed as the nested {@link #EXCEPT_CODEC}), and the nested level omits it, so an exclusion
     * cannot carve a hole in an exclusion.
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
                .metadata(new UIEditor(new UIEditor.Dropdown("rpgstations:action-function"))).add();
        if (withExcept) {
            builder = builder.appendInherited(new KeyedCodec<>("Except", EXCEPT_CODEC, false),
                            (o, v) -> o.except = v, o -> o.except, (o, p) -> o.except = p.except)
                    .documentation("A material the routes above accept is REFUSED when this nested matcher (the same ItemId | ResourceTypeId | Tags | Function routes, match = ANY) accepts it too. Carves a hole in a broad match without listing every id; absent excludes nothing, and an Except authoring no route matches nothing, so it excludes nothing either.").add();
        }
        return builder.build();
    }

    public ActionInput() {
    }

    /** Java-side factory; sets the same fields the codec fills. */
    @Nonnull
    public static ActionInput of(@Nullable String itemId, @Nullable String resourceTypeId,
            @Nullable Map<String, String[]> tags, @Nullable String function) {
        return of(itemId, resourceTypeId, tags, function, null);
    }

    /** As above, plus the {@link #except} hole. */
    @Nonnull
    public static ActionInput of(@Nullable String itemId, @Nullable String resourceTypeId,
            @Nullable Map<String, String[]> tags, @Nullable String function, @Nullable ActionInput except) {
        ActionInput i = new ActionInput();
        i.itemId = itemId;
        i.resourceTypeId = resourceTypeId;
        i.tags = tags;
        i.function = function;
        i.except = except;
        return i;
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

    /** The nested exclusion matcher; null = nothing is excluded. */
    @Nullable
    public ActionInput getExcept() {
        return except;
    }

    /** True when NO route is authored (a catch-all matcher - matches any held stack). */
    public boolean isCatchAll() {
        boolean hasItemId = itemId != null && !itemId.isBlank();
        boolean hasResourceTypeId = resourceTypeId != null && !resourceTypeId.isBlank();
        boolean hasTags = tags != null && !tags.isEmpty();
        boolean hasFunction = function != null && !function.isBlank();
        return !hasItemId && !hasResourceTypeId && !hasTags && !hasFunction;
    }
}
