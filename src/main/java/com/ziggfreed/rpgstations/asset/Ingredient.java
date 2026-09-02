package com.ziggfreed.rpgstations.asset;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.ziggfreed.common.codec.TagMatch;

/**
 * The ONE item-quantity leaf (scope-2 design section 1.3, DRY principle 1): a native-shaped
 * recipe/material reference mirroring vanilla {@code MaterialQuantity}, reused at EVERY authoring
 * site that names an item and an amount (a {@code StationAsset.Conversion}'s Input/Output, an
 * {@code ExtensionAsset} appended conversion). Replaces the three near-identical item-quantity
 * triples E1 found (the old nested {@code StationAsset.Ingredient}, the inline
 * {@code StationStep.Consume} triple, {@code Stamp.Reagent}).
 *
 * <p><b>At most one of {@link #itemId} | {@link #resourceTypeId} | {@link #tags}.</b>
 * {@link #itemId} is an exact item id; {@link #resourceTypeId} is a native
 * {@code Item.ResourceTypes} family (the "any log" route); {@link #tags} is a native item-tag
 * matcher (the shared {@link TagMatch} map, mirroring vanilla {@code MaterialQuantity.ItemTag}).
 * The family and tag routes are meaningful only on an INPUT; an OUTPUT authors {@link #itemId}
 * only. On an INPUT, authoring NO route at all is legal and means MATCH-ANY - the row accepts
 * whatever its custody pile holds (the set-recipe "anything in the pot" form; it never draws from
 * a player's open inventory). Authoring MORE than one route is a content mistake the codec and
 * validator flag ({@code AMBIGUOUS_CONVERSION_INPUT}) - see {@link #routeCount()}.
 */
public final class Ingredient {

    @Nullable protected String itemId;
    @Nullable protected String resourceTypeId;
    @Nullable protected Map<String, String[]> tags;
    @Nullable protected Integer quantity;
    @Nullable protected String socket;

    public static final BuilderCodec<Ingredient> CODEC = BuilderCodec.builder(Ingredient.class, Ingredient::new)
            .appendInherited(new KeyedCodec<>("ItemId", Codec.STRING, false),
                    (o, v) -> o.itemId = v, o -> o.itemId, (o, p) -> o.itemId = p.itemId)
            .documentation("Exact item id (at most one of ItemId | ResourceTypeId | Tags). An OUTPUT uses only ItemId.").add()
            .appendInherited(new KeyedCodec<>("ResourceTypeId", Codec.STRING, false),
                    (o, v) -> o.resourceTypeId = v, o -> o.resourceTypeId,
                    (o, p) -> o.resourceTypeId = p.resourceTypeId)
            .documentation("A native Item.ResourceTypes family id (the 'any log' route); INPUT only. At most one of ItemId | ResourceTypeId | Tags.").add()
            .appendInherited(new KeyedCodec<>("Tags", TagMatch.CODEC, false),
                    (o, v) -> o.tags = v, o -> o.tags, (o, p) -> o.tags = p.tags)
            .documentation("Match by native item tags (tag family -> accepted values; an empty value list matches on the family key alone); INPUT only. At most one of ItemId | ResourceTypeId | Tags; an INPUT authoring none matches any placed material.").add()
            .appendInherited(new KeyedCodec<>("Quantity", Codec.INTEGER, false),
                    (o, v) -> o.quantity = v, o -> o.quantity, (o, p) -> o.quantity = p.quantity)
            .documentation("The item count; reader-defaults to 1 when omitted or non-positive.")
            .addValidator(CodecWarnValidators.positive("Ingredient.Quantity should be positive; it reader-defaults to 1 otherwise.")).add()
            .appendInherited(new KeyedCodec<>("Socket", Codec.STRING, false),
                    (o, v) -> o.socket = v, o -> o.socket, (o, p) -> o.socket = p.socket)
            .documentation("The custody socket THIS entry draws from / lands in, overriding the phase's own Socket ('meat from the meat rack, greens from the basket' in one row). Absent = the phase's Socket, else the first Item socket. Only meaningful on a Custody-routed phase.").add()
            .afterDecode((Ingredient ingredient, ExtraInfo extraInfo) -> {
                if (ingredient.routeCount() > 1) {
                    extraInfo.getValidationResults().warn(
                            "Ingredient should author at most one of ItemId | ResourceTypeId | Tags, not several.");
                }
            })
            .build();

    public Ingredient() {
    }

    @Nonnull
    public static Ingredient of(@Nullable String itemId, @Nullable String resourceTypeId,
            @Nullable Integer quantity) {
        return of(itemId, resourceTypeId, quantity, null);
    }

    /** As above, plus the per-entry custody {@link #socket} address. */
    @Nonnull
    public static Ingredient of(@Nullable String itemId, @Nullable String resourceTypeId,
            @Nullable Integer quantity, @Nullable String socket) {
        Ingredient i = new Ingredient();
        i.itemId = itemId;
        i.resourceTypeId = resourceTypeId;
        i.quantity = quantity;
        i.socket = socket;
        return i;
    }

    /** Convenience: an exact-item ingredient ({@code ItemId}). */
    @Nonnull
    public static Ingredient item(@Nullable String itemId, @Nullable Integer quantity) {
        return of(itemId, null, quantity);
    }

    /** Convenience: a native resource-type family ingredient ({@code ResourceTypeId}); INPUT only. */
    @Nonnull
    public static Ingredient resource(@Nullable String resourceTypeId, @Nullable Integer quantity) {
        return of(null, resourceTypeId, quantity);
    }

    /** Convenience: a native-tag ingredient ({@code Tags}); INPUT only. */
    @Nonnull
    public static Ingredient tagged(@Nullable Map<String, String[]> tags, @Nullable Integer quantity) {
        Ingredient i = of(null, null, quantity);
        i.tags = tags;
        return i;
    }

    /** Convenience: the route-less MATCH-ANY ingredient (accepts anything; custody INPUT only). */
    @Nonnull
    public static Ingredient matchAny(@Nullable Integer quantity) {
        return of(null, null, quantity);
    }

    @Nullable
    public String getItemId() {
        return itemId;
    }

    @Nullable
    public String getResourceTypeId() {
        return resourceTypeId;
    }

    /** The native-tag route map (family -&gt; accepted values, empty list = key presence), or null. */
    @Nullable
    public Map<String, String[]> getTags() {
        return tags;
    }

    @Nullable
    public Integer getQuantity() {
        return quantity;
    }

    /** The per-entry custody socket address (lowercased at use; only meaningful on a Custody-routed phase), or null for the phase default. */
    @Nullable
    public String getSocket() {
        return socket;
    }

    /** {@link #quantity}, reader-defaulted to 1 when null/non-positive. */
    public int effectiveQuantity() {
        return quantity != null && quantity > 0 ? quantity : 1;
    }

    /** True when the exact-{@code ItemId} route is authored (non-blank). */
    public boolean hasItemRoute() {
        return itemId != null && !itemId.isBlank();
    }

    /** True when the native resource-type FAMILY route is authored (non-blank). */
    public boolean hasResourceRoute() {
        return resourceTypeId != null && !resourceTypeId.isBlank();
    }

    /** True when the native-tag route is authored (at least one family key). */
    public boolean hasTagsRoute() {
        return tags != null && !tags.isEmpty();
    }

    /** How many of the three routes are authored (0 = match-any on an input; 2+ = a content mistake). */
    public int routeCount() {
        return (hasItemRoute() ? 1 : 0) + (hasResourceRoute() ? 1 : 0) + (hasTagsRoute() ? 1 : 0);
    }

    /** True when NO route is authored - legal on an INPUT (match-any), never on an output. */
    public boolean isMatchAny() {
        return routeCount() == 0;
    }

    /** True when EXACTLY one of the three routes is authored (the well-formed single-route contract). */
    public boolean hasExactlyOneRoute() {
        return routeCount() == 1;
    }
}
