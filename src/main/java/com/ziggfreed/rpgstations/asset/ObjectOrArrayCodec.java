package com.ziggfreed.rpgstations.asset;

import java.io.IOException;
import java.util.function.IntFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonValue;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.WrappedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.util.RawJsonReader;

/**
 * The dual-shape LIST leaf codec: a value authored EITHER as one nested object or as an array of
 * them, decided per value by the raw JSON/BSON type, both decoding to the same {@code T[]}. The
 * sibling of {@link StringOrObjectCodec} (a string or an object) for a leaf that is naturally one
 * thing in the common case and several in the composed one: an {@code Except} hole is one matcher
 * in a file and several once an extension adds its own beside it.
 *
 * <p>Decoding follows the engine's own dual-shape precedent exactly as {@link StringOrObjectCodec}
 * does: {@code decode} tests {@link BsonValue#isArray()}, {@code decodeJson} peeks for a leading
 * bracket ({@code RawJsonReader#peekFor} does not consume, so the branch not taken reads the value
 * from the untouched reader), and {@link #toSchema} publishes {@code anyOf(<the object schema>,
 * <the array schema>)}. The array shape rides the engine's own {@link ArrayCodec}.
 *
 * <p><b>Encoding round-trips the SINGLE shape</b>: a one-element array encodes as the bare object,
 * so a file that authored one object re-encodes as one object rather than inflating into a list,
 * which is what keeps an encode-based parity test and a hand-authored file on the same bytes.
 *
 * <p>It implements {@link WrappedCodec} over the OBJECT body codec, so every codec walk in this
 * project (the documentation-coverage guard, the schema-reference writer) descends into the body's
 * own leaves instead of stopping at an opaque terminal.
 */
public final class ObjectOrArrayCodec<T> implements Codec<T[]>, WrappedCodec<T> {

    private final BuilderCodec<T> body;
    private final ArrayCodec<T> array;
    private final IntFunction<T[]> newArray;

    /**
     * @param body     the OBJECT form's codec (also the {@link #getChildCodec() child} every codec
     *                 walk descends into)
     * @param newArray the array constructor ({@code T[]::new})
     */
    public ObjectOrArrayCodec(@Nonnull BuilderCodec<T> body, @Nonnull IntFunction<T[]> newArray) {
        this.body = body;
        this.array = new ArrayCodec<>(body, newArray);
        this.newArray = newArray;
    }

    /** The OBJECT form's codec, the element codec every codec walk descends into (the array codec's own child shape). */
    @Override
    public Codec<T> getChildCodec() {
        return body;
    }

    @Nullable
    @Override
    public T[] decode(BsonValue bsonValue, ExtraInfo extraInfo) {
        if (bsonValue != null && bsonValue.isArray()) {
            return array.decode(bsonValue, extraInfo);
        }
        T one = body.decode(bsonValue, extraInfo);
        return one == null ? null : single(one);
    }

    @Override
    public BsonValue encode(T[] value, ExtraInfo extraInfo) {
        if (value != null && value.length == 1 && value[0] != null) {
            return body.encode(value[0], extraInfo);
        }
        return array.encode(value, extraInfo);
    }

    @Nullable
    @Override
    public T[] decodeJson(@Nonnull RawJsonReader reader, ExtraInfo extraInfo) throws IOException {
        if (reader.peekFor('[')) {
            return array.decodeJson(reader, extraInfo);
        }
        T one = body.decodeJson(reader, extraInfo);
        return one == null ? null : single(one);
    }

    @Nonnull
    @Override
    public Schema toSchema(@Nonnull SchemaContext context) {
        return Schema.anyOf(context.refDefinition(body), array.toSchema(context));
    }

    @Nonnull
    private T[] single(@Nonnull T one) {
        T[] out = newArray.apply(1);
        out[0] = one;
        return out;
    }
}
