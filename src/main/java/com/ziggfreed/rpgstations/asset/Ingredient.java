package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.ExtraInfo;

/**
 * The ONE item-quantity leaf (scope-2 design section 1.3, DRY principle 1): a native-shaped
 * recipe/material reference mirroring vanilla {@code MaterialQuantity}, reused at EVERY authoring
 * site that names an item and an amount (a {@code StationAsset.Conversion}'s Input/Output, an
 * {@code ExtensionAsset} appended conversion). Replaces the three near-identical item-quantity
 * triples E1 found (the old nested {@code StationAsset.Ingredient}, the inline
 * {@code StationStep.Consume} triple, {@code Stamp.Reagent}).
 *
 * <p><b>Exactly one of {@link #itemId} | {@link #resourceTypeId}.</b> {@link #itemId} is an exact
 * item id; {@link #resourceTypeId} is a native {@code Item.ResourceTypes} family (the "any log"
 * route), meaningful only on an INPUT. An OUTPUT authors {@link #itemId} only. Authoring both, or
 * neither, is a content mistake the validator flags ({@code INGREDIENT_ROUTE_AMBIGUOUS}) - see
 * {@link #hasExactlyOneRoute()}.
 */
public final class Ingredient {

    @Nullable protected String itemId;
    @Nullable protected String resourceTypeId;
    @Nullable protected Integer quantity;
    @Nullable protected String socket;

    public static final BuilderCodec<Ingredient> CODEC = BuilderCodec.builder(Ingredient.class, Ingredient::new)
            .appendInherited(new KeyedCodec<>("ItemId", Codec.STRING, false),
                    (o, v) -> o.itemId = v, o -> o.itemId, (o, p) -> o.itemId = p.itemId)
            .documentation("Exact item id (exactly one of ItemId | ResourceTypeId). An OUTPUT uses only ItemId.").add()
            .appendInherited(new KeyedCodec<>("ResourceTypeId", Codec.STRING, false),
                    (o, v) -> o.resourceTypeId = v, o -> o.resourceTypeId,
                    (o, p) -> o.resourceTypeId = p.resourceTypeId)
            .documentation("A native Item.ResourceTypes family id (the 'any log' route); INPUT only. Exactly one of ItemId | ResourceTypeId.").add()
            .appendInherited(new KeyedCodec<>("Quantity", Codec.INTEGER, false),
                    (o, v) -> o.quantity = v, o -> o.quantity, (o, p) -> o.quantity = p.quantity)
            .documentation("The item count; reader-defaults to 1 when omitted or non-positive.")
            .addValidator(CodecWarnValidators.positive("Ingredient.Quantity should be positive; it reader-defaults to 1 otherwise.")).add()
            .appendInherited(new KeyedCodec<>("Socket", Codec.STRING, false),
                    (o, v) -> o.socket = v, o -> o.socket, (o, p) -> o.socket = p.socket)
            .documentation("The custody socket THIS entry draws from / lands in, overriding the phase's own Socket ('meat from the meat rack, greens from the basket' in one row). Absent = the phase's Socket, else the first Item socket. Only meaningful on a Custody-routed phase.").add()
            .afterDecode((Ingredient ingredient, ExtraInfo extraInfo) -> {
                if (!ingredient.hasExactlyOneRoute()) {
                    extraInfo.getValidationResults().warn(
                            "Ingredient should author exactly one of ItemId | ResourceTypeId, not both or neither.");
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

    @Nullable
    public String getItemId() {
        return itemId;
    }

    @Nullable
    public String getResourceTypeId() {
        return resourceTypeId;
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

    /** True when EXACTLY one of {@link #itemId}/{@link #resourceTypeId} is authored (the exactly-one-of contract). */
    public boolean hasExactlyOneRoute() {
        boolean item = itemId != null && !itemId.isBlank();
        boolean res = resourceTypeId != null && !resourceTypeId.isBlank();
        return item ^ res;
    }
}
