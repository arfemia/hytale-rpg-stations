package com.ziggfreed.rpgstations.asset;

import java.io.IOException;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonString;
import org.bson.BsonValue;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.WrappedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.schema.config.StringSchema;
import com.hypixel.hytale.codec.util.RawJsonReader;

/**
 * The ONE dual-shape leaf codec in this schema: a value that may be authored EITHER as a bare
 * string SHORTHAND or as a full nested object, decided per value by the raw JSON/BSON type. Both
 * shapes decode to the same {@code T}, so every reader sees one normalized record and never
 * branches on how it was written.
 *
 * <p><b>The engine's own dual-shape precedent, followed exactly.</b> Two first-party codecs
 * dispatch on the raw element type this way: {@code ContainedAssetCodec} (a string is an asset id,
 * an object is an inline body) and {@code Message}'s {@code ParamValue} codec. Both do it the same
 * three ways, and so does this one - {@code decode} tests {@link BsonValue#isString()},
 * {@code decodeJson} peeks for a leading quote ({@code RawJsonReader#peekFor} does not consume, so
 * the branch not taken reads the value from the untouched reader), and {@link #toSchema} publishes
 * {@code anyOf(string, <the object schema>)}.
 *
 * <p><b>Encoding round-trips the SHORTHAND</b> whenever {@code toShorthand} yields a non-null
 * string for the value (typically: the object carries nothing beyond the one field the shorthand
 * expresses), so an asset authored {@code ["X"]} re-encodes as {@code ["X"]} rather than silently
 * inflating into an object. That property is what keeps an encode-based parity test and a
 * hand-authored file agreeing on the same bytes.
 *
 * <p>It implements {@link WrappedCodec} over the OBJECT body codec, so every codec walk in this
 * project (the documentation-coverage guard, the schema-reference writer) descends into the body's
 * own leaves instead of stopping at an opaque terminal - a dual leaf is documented and rendered
 * exactly like the nested group it wraps.
 */
public final class StringOrObjectCodec<T> implements Codec<T>, WrappedCodec<T> {

    private final BuilderCodec<T> body;
    private final Function<String, T> fromShorthand;
    private final Function<T, String> toShorthand;

    /**
     * @param body          the OBJECT form's codec (also the {@link #getChildCodec() child} every
     *                      codec walk descends into)
     * @param fromShorthand builds a value from the bare-string form
     * @param toShorthand   the bare-string form of a value, or {@code null} when it can only be
     *                      written as an object (the value carries a field the shorthand cannot)
     */
    public StringOrObjectCodec(@Nonnull BuilderCodec<T> body, @Nonnull Function<String, T> fromShorthand,
            @Nonnull Function<T, String> toShorthand) {
        this.body = body;
        this.fromShorthand = fromShorthand;
        this.toShorthand = toShorthand;
    }

    /** The OBJECT form's codec: the schema authority for every field the long form carries. */
    @Override
    public Codec<T> getChildCodec() {
        return body;
    }

    @Nullable
    @Override
    public T decode(BsonValue bsonValue, ExtraInfo extraInfo) {
        if (bsonValue != null && bsonValue.isString()) {
            return fromShorthand.apply(bsonValue.asString().getValue());
        }
        return body.decode(bsonValue, extraInfo);
    }

    @Override
    public BsonValue encode(T value, ExtraInfo extraInfo) {
        String shorthand = value == null ? null : toShorthand.apply(value);
        return shorthand != null ? new BsonString(shorthand) : body.encode(value, extraInfo);
    }

    @Nullable
    @Override
    public T decodeJson(@Nonnull RawJsonReader reader, ExtraInfo extraInfo) throws IOException {
        if (reader.peekFor('"')) {
            return fromShorthand.apply(reader.readString());
        }
        return body.decodeJson(reader, extraInfo);
    }

    @Nonnull
    @Override
    public Schema toSchema(@Nonnull SchemaContext context) {
        StringSchema shorthand = new StringSchema();
        shorthand.setTitle("Shorthand for " + body.getInnerClass().getSimpleName());
        return Schema.anyOf(shorthand, context.refDefinition(body));
    }
}
