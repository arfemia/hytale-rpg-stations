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
 * The diegetic action-selection matcher (design section 9.1): which of a multi-action station's
 * {@link ActionDef}s a pressed player's HELD stack (or placed custody, a later leg) selects.
 * Generalizes the {@code StationAsset.Tool} gate grammar from tools to any item: match = ANY
 * non-blank/non-empty leaf satisfied (orthogonal routes, same "match = ANY route" convention as
 * {@code Tool}). A null/all-blank {@link ActionInput} matches EVERYTHING (a catch-all action;
 * {@code StationValidator} flags an unreachable later catch-all - {@code UNREACHABLE_ACTION}).
 *
 * <p>{@link #function} is the new FUNCTIONAL route (design 9.1): {@code "Weapon"}/{@code "Armor"}/
 * {@code "Tool"}, tested against the held item's native shape (the {@code ItemEnhanceRoll} gate
 * precedent - {@code item.getWeapon() != null} etc.). Resolving {@link #function} against the
 * live engine is a phase-2 EXECUTION concern (the anvil arc, leg E); this leg lands the SCHEMA
 * only, decodable and validator-checked, so a later leg's content authors against a stable shape.
 */
public final class ActionInput {

    @Nullable protected String itemId;
    @Nullable protected String resourceTypeId;
    @Nullable protected Map<String, String[]> tags;
    @Nullable protected String function;

    public static final BuilderCodec<ActionInput> CODEC = BuilderCodec.builder(ActionInput.class, ActionInput::new)
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
            .build();

    public ActionInput() {
    }

    /** Java-side factory; sets the same fields the codec fills. */
    @Nonnull
    public static ActionInput of(@Nullable String itemId, @Nullable String resourceTypeId,
            @Nullable Map<String, String[]> tags, @Nullable String function) {
        ActionInput i = new ActionInput();
        i.itemId = itemId;
        i.resourceTypeId = resourceTypeId;
        i.tags = tags;
        i.function = function;
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

    /** True when NO route is authored (a catch-all matcher - matches any held stack). */
    public boolean isCatchAll() {
        boolean hasItemId = itemId != null && !itemId.isBlank();
        boolean hasResourceTypeId = resourceTypeId != null && !resourceTypeId.isBlank();
        boolean hasTags = tags != null && !tags.isEmpty();
        boolean hasFunction = function != null && !function.isBlank();
        return !hasItemId && !hasResourceTypeId && !hasTags && !hasFunction;
    }
}
