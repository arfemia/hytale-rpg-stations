package com.ziggfreed.rpgstations.asset;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditor;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditorSectionStart;
import com.ziggfreed.common.codec.InheritMapCodec;
import com.ziggfreed.common.codec.Vec3i;

/**
 * A multiblock structure pattern: an authored arrangement of world blocks that, once a player has
 * built it completely, turns its one ANCHOR block into a working station. Loaded from
 * {@code Server/RpgStations/Patterns/<Name>.json} (Pattern A, id = lowercased filename, native
 * {@code Parent} per leaf).
 *
 * <p>Recognition works from the placed blocks alone: when a placement completes the arrangement
 * (any build order), the anchor cell's block is swapped to {@link Activate#getBlock()}, which is an
 * ordinary station block from then on. Breaking any block of the standing shape reverts the anchor
 * to {@link Activate#getRevertBlock()} (default: the anchor cell's own authored block), drops any
 * placed materials at the block once, and stops whoever was working there. Authoring
 * {@code Activate.Block} EQUAL to the anchor cell's own block id means "no swap": the anchor is
 * already a custom station block and completion simply arms it - both the pure-vanilla arrangement
 * and the custom-core-block style are plain authoring, never a mode.
 *
 * <p><b>Exactly one anchor cell.</b> One cell authors {@code IsAnchor: true} (any cell, the
 * author's choice); with none authored, the cell at offset {@code (0,0,0)} stands in (a decode
 * warning names the default). The anchor cell must author an exact {@code Block.ItemId}, because
 * detection seeds its placement index from exact ids - a family- or tag-matched anchor is
 * undiscoverable and warns at decode.
 *
 * <p><b>Not extensible.</b> This type is deliberately NOT an {@code ExtensionAsset} target: the
 * cell list IS the pattern's identity, and a cell appended by another pack would instantly
 * deactivate every standing build of the original shape on its next re-check. Reuse goes through
 * native {@code Parent} instead - and note the array caveat documented at {@code Cells}.
 */
public final class StructurePatternAsset
        implements JsonAssetWithMap<String, DefaultAssetMap<String, StructurePatternAsset>> {

    /** The moment id played at the anchor when a completed build activates. */
    public static final String MOMENT_ACTIVATED = "activated";

    /** The moment id played at the anchor when a standing shape is broken and reverts. */
    public static final String MOMENT_BROKEN = "broken";

    private String id;
    private AssetExtraInfo.Data data;

    @Nullable private Identity identity;
    @Nullable private Rotate rotate;
    @Nullable private Activate activate;
    @Nullable private Cell[] cells;
    @Nullable private Requires requires;
    @Nullable private Map<String, Presentation> moments;

    public static final AssetBuilderCodec<String, StructurePatternAsset> CODEC = AssetBuilderCodec.builder(
                    StructurePatternAsset.class,
                    StructurePatternAsset::new,
                    Codec.STRING,
                    // Canonicalize to lowercase at decode, matching every other Pattern A store in
                    // this mod - references (the stash tag naming which pattern stands at an
                    // anchor) are all lowercase.
                    (a, id) -> a.id = id == null ? null : id.toLowerCase(Locale.ROOT),
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - the pattern id comes from the filename */ },
                    a -> a.id)
            .documentation("Ignored - the pattern id comes from the asset filename, not this key. Kept as a schema field for editor display only.").add()
            .appendInherited(new KeyedCodec<>("Identity", Identity.CODEC, false),
                    (a, v) -> a.identity = v, a -> a.identity, (a, p) -> a.identity = p.identity)
            .documentation("Display identity: the localization keys naming this structure in player-facing messages.")
            .metadata(new UIEditorSectionStart("Identity")).add()
            .appendInherited(new KeyedCodec<>("Rotate", Rotate.CODEC, false),
                    (a, v) -> a.rotate = v, a -> a.rotate, (a, p) -> a.rotate = p.rotate)
            .documentation("Which orientations of the shape count as built: the four yaw quarter-turns (default on) and an optional X-mirror (default off).")
            .metadata(new UIEditorSectionStart("Structure")).add()
            .appendInherited(new KeyedCodec<>("Activate", Activate.CODEC, false),
                    (a, v) -> a.activate = v, a -> a.activate, (a, p) -> a.activate = p.activate)
            .documentation("What completion does to the anchor block: the station block it becomes, and the block a broken shape reverts it to.").add()
            .appendInherited(new KeyedCodec<>("Cells", new ArrayCodec<>(Cell.CODEC, Cell[]::new), false),
                    (a, v) -> a.cells = v, a -> a.cells, (a, p) -> a.cells = p.cells)
            .documentation("The cells of the shape, each an offset plus what must stand there. Exactly one cell is the anchor. Under native Parent this ARRAY is replaced wholesale, never merged per entry - a child re-authoring any cell re-authors them all.").add()
            .appendInherited(new KeyedCodec<>("Requires", Requires.CODEC, false),
                    (a, v) -> a.requires = v, a -> a.requires, (a, p) -> a.requires = p.requires)
            .documentation("The activation gate, evaluated against the player whose placement completed the shape; a failing gate leaves the blocks standing and the station unactivated.")
            .metadata(new UIEditorSectionStart("Requirements")).add()
            .appendInherited(new KeyedCodec<>("Moments",
                            new InheritMapCodec<>(Presentation.CODEC, LinkedHashMap::new), false),
                    (a, v) -> a.moments = v, a -> a.moments, (a, p) -> a.moments = p.moments)
            .documentation("Moment id -> the presentation played at the anchor. Well-known ids: 'activated' (a completed build turned into the station) and 'broken' (the standing shape was broken and reverted). Cues play at once; a DelayMs here is read as zero. Under native Parent the map merges PER MOMENT ID.")
            .metadata(new UIEditorSectionStart("Moments")).add()
            .afterDecode((StructurePatternAsset asset, com.hypixel.hytale.codec.ExtraInfo extraInfo) -> {
                if (asset.cells == null || asset.cells.length == 0) {
                    extraInfo.getValidationResults().warn(
                            "StructurePatternAsset authors no Cells - the pattern can never be built.");
                    return;
                }
                int anchors = 0;
                for (Cell cell : asset.cells) {
                    if (cell != null && cell.isAnchor()) {
                        anchors++;
                    }
                }
                if (anchors == 0) {
                    extraInfo.getValidationResults().warn(
                            "StructurePatternAsset authors no IsAnchor cell - the cell at offset (0,0,0) is used"
                                    + " (author IsAnchor: true on one cell to choose explicitly).");
                } else if (anchors > 1) {
                    extraInfo.getValidationResults().warn(
                            "StructurePatternAsset authors " + anchors
                                    + " IsAnchor cells - exactly one is expected; the first authored one is used.");
                }
                Cell anchor = asset.cells[asset.anchorCellIndex()];
                if (anchor == null || anchor.getBlock() == null || anchor.getBlock().getItemId() == null
                        || anchor.getBlock().getItemId().isBlank()) {
                    extraInfo.getValidationResults().warn(
                            "StructurePatternAsset's anchor cell authors no exact Block.ItemId - detection seeds"
                                    + " from exact ids, so this pattern will never be discovered by a placement.");
                }
                if (asset.activate == null || asset.activate.getBlock() == null
                        || asset.activate.getBlock().isBlank()) {
                    extraInfo.getValidationResults().warn(
                            "StructurePatternAsset authors no Activate.Block - a completed build has no station"
                                    + " block to become and the pattern is inert.");
                }
            })
            .build();

    public StructurePatternAsset() {
    }

    /** Java-side construction path; sets the same fields the codec fills. */
    @Nonnull
    public static StructurePatternAsset of(@Nonnull String id, @Nullable Identity identity,
            @Nullable Rotate rotate, @Nullable Activate activate, @Nullable Cell[] cells,
            @Nullable Requires requires, @Nullable Map<String, Presentation> moments) {
        StructurePatternAsset a = new StructurePatternAsset();
        a.id = id;
        a.identity = identity;
        a.rotate = rotate;
        a.activate = activate;
        a.cells = cells;
        a.requires = requires;
        a.moments = moments;
        return a;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public Identity getIdentity() {
        return identity;
    }

    @Nullable
    public Rotate getRotate() {
        return rotate;
    }

    @Nullable
    public Activate getActivate() {
        return activate;
    }

    @Nullable
    public Cell[] getCells() {
        return cells;
    }

    @Nullable
    public Requires getRequires() {
        return requires;
    }

    /** Moment id ({@value #MOMENT_ACTIVATED}/{@value #MOMENT_BROKEN}) -> presentation. */
    @Nullable
    public Map<String, Presentation> getMoments() {
        return moments;
    }

    /** The presentation for a moment id, matched case-insensitively; null when unauthored. */
    @Nullable
    public Presentation moment(@Nonnull String momentId) {
        if (moments == null) {
            return null;
        }
        for (Map.Entry<String, Presentation> e : moments.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(momentId)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Which cell is the anchor: the first cell authoring {@code IsAnchor: true}; with none, the
     * first cell whose offset is {@code (0,0,0)} (the decode-warned default); with neither, cell
     * {@code 0}. Meaningless (returns 0) for an empty/absent cell list - callers gate on
     * {@link #getCells()} first.
     */
    public int anchorCellIndex() {
        if (cells == null || cells.length == 0) {
            return 0;
        }
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] != null && cells[i].isAnchor()) {
                return i;
            }
        }
        for (int i = 0; i < cells.length; i++) {
            Cell c = cells[i];
            if (c != null && c.offsetX() == 0 && c.offsetY() == 0 && c.offsetZ() == 0) {
                return i;
            }
        }
        return 0;
    }

    /**
     * The block id a broken shape reverts its anchor to: {@code Activate.RevertBlock} when
     * authored, else the anchor cell's own exact {@code Block.ItemId}; {@code null} when neither
     * exists (the revert is then a no-op - the anchor keeps whatever block stands there).
     */
    @Nullable
    public String effectiveRevertBlock() {
        if (activate != null && activate.getRevertBlock() != null && !activate.getRevertBlock().isBlank()) {
            return activate.getRevertBlock();
        }
        if (cells == null || cells.length == 0) {
            return null;
        }
        Cell anchor = cells[anchorCellIndex()];
        if (anchor == null || anchor.getBlock() == null) {
            return null;
        }
        String itemId = anchor.getBlock().getItemId();
        return itemId != null && !itemId.isBlank() ? itemId : null;
    }

    /** Display identity: the localization keys naming this structure to players. */
    public static final class Identity {
        @Nullable protected String nameKey;
        @Nullable protected String descKey;

        public static final BuilderCodec<Identity> CODEC = BuilderCodec.builder(Identity.class, Identity::new)
                .appendInherited(new KeyedCodec<>("NameKey", Codec.STRING, false),
                        (o, v) -> o.nameKey = v, o -> o.nameKey, (o, p) -> o.nameKey = p.nameKey)
                .documentation("The localization key resolved client-side for the structure's display name; null = generic wording in player-facing messages.").add()
                .appendInherited(new KeyedCodec<>("DescKey", Codec.STRING, false),
                        (o, v) -> o.descKey = v, o -> o.descKey, (o, p) -> o.descKey = p.descKey)
                .documentation("The localization key resolved client-side for the structure's description; null = no description shown.").add()
                .build();

        @Nonnull
        public static Identity of(@Nullable String nameKey, @Nullable String descKey) {
            Identity i = new Identity();
            i.nameKey = nameKey;
            i.descKey = descKey;
            return i;
        }

        @Nullable
        public String getNameKey() {
            return nameKey;
        }

        @Nullable
        public String getDescKey() {
            return descKey;
        }
    }

    /**
     * Which orientations of the authored shape count as built. Rotation is discrete
     * (0/90/180/270 about the vertical axis) and pivots on the anchor cell.
     */
    public static final class Rotate {
        @Nullable protected Boolean yaw90;
        @Nullable protected Boolean mirror;

        public static final BuilderCodec<Rotate> CODEC = BuilderCodec.builder(Rotate.class, Rotate::new)
                .appendInherited(new KeyedCodec<>("Yaw90", Codec.BOOLEAN, false),
                        (o, v) -> o.yaw90 = v, o -> o.yaw90, (o, p) -> o.yaw90 = p.yaw90)
                .documentation("Recognize the shape in all four yaw quarter-turn orientations (default true); false = only the authored orientation counts.").add()
                .appendInherited(new KeyedCodec<>("Mirror", Codec.BOOLEAN, false),
                        (o, v) -> o.mirror = v, o -> o.mirror, (o, p) -> o.mirror = p.mirror)
                .documentation("Additionally recognize the X-mirrored form of each orientation (default false); only worth authoring for an asymmetric shape.").add()
                .build();

        @Nonnull
        public static Rotate of(@Nullable Boolean yaw90, @Nullable Boolean mirror) {
            Rotate r = new Rotate();
            r.yaw90 = yaw90;
            r.mirror = mirror;
            return r;
        }

        @Nullable
        public Boolean getYaw90() {
            return yaw90;
        }

        @Nullable
        public Boolean getMirror() {
            return mirror;
        }

        /** {@link #yaw90}, reader-defaulted true. */
        public boolean effectiveYaw90() {
            return yaw90 == null || yaw90;
        }

        /** {@link #mirror}, reader-defaulted false. */
        public boolean effectiveMirror() {
            return mirror != null && mirror;
        }
    }

    /** What completion does to the anchor block, and what a broken shape reverts it to. */
    public static final class Activate {
        @Nullable protected String block;
        @Nullable protected String revertBlock;

        public static final BuilderCodec<Activate> CODEC = BuilderCodec.builder(Activate.class, Activate::new)
                .appendInherited(new KeyedCodec<>("Block", Codec.STRING, false),
                        (o, v) -> o.block = v, o -> o.block, (o, p) -> o.block = p.block)
                .documentation("The station block item id the anchor is swapped to on completion, keeping its rotation. Author it EQUAL to the anchor cell's own block id for a custom core block that needs no swap - completion then simply arms the block that already stands there.")
                .metadata(new UIEditor(new UIEditor.Dropdown(AssetEditorDataSets.STATION_BLOCKS))).add()
                .appendInherited(new KeyedCodec<>("RevertBlock", Codec.STRING, false),
                        (o, v) -> o.revertBlock = v, o -> o.revertBlock, (o, p) -> o.revertBlock = p.revertBlock)
                .documentation("The block item id a broken shape reverts the anchor to; null = the anchor cell's own authored block id.").add()
                .build();

        @Nonnull
        public static Activate of(@Nullable String block, @Nullable String revertBlock) {
            Activate a = new Activate();
            a.block = block;
            a.revertBlock = revertBlock;
            return a;
        }

        @Nullable
        public String getBlock() {
            return block;
        }

        @Nullable
        public String getRevertBlock() {
            return revertBlock;
        }
    }

    /**
     * One cell of the shape: an anchor-relative offset plus what must stand there - EITHER a block
     * matcher ({@code Block}, an {@link ActionInput}: exact id, resource family, or tags) OR
     * {@code Empty: true} (the cell must hold air), exactly one of the two.
     */
    public static final class Cell {
        @Nullable protected Vec3i offset;
        @Nullable protected ActionInput block;
        @Nullable protected Boolean empty;
        @Nullable protected Boolean isAnchor;

        public static final BuilderCodec<Cell> CODEC = BuilderCodec.builder(Cell.class, Cell::new)
                .appendInherited(new KeyedCodec<>("Offset", Vec3i.CODEC, false),
                        (o, v) -> o.offset = v, o -> o.offset, (o, p) -> o.offset = p.offset)
                .documentation("This cell's position in whole blocks, relative to the authored frame (the anchor cell's offset is subtracted out, so any consistent frame works); unauthored axes read 0.").add()
                .appendInherited(new KeyedCodec<>("Block", ActionInput.CODEC, false),
                        (o, v) -> o.block = v, o -> o.block, (o, p) -> o.block = p.block)
                .documentation("What block must stand in this cell: an exact ItemId, a ResourceTypeId family (any rock), or Tags. A state variant (lit/unlit) matches through its base block. Exactly one of Block | Empty.").add()
                .appendInherited(new KeyedCodec<>("Empty", Codec.BOOLEAN, false),
                        (o, v) -> o.empty = v, o -> o.empty, (o, p) -> o.empty = p.empty)
                .documentation("True = this cell must hold AIR for the shape to count as built. Exactly one of Block | Empty.").add()
                .appendInherited(new KeyedCodec<>("IsAnchor", Codec.BOOLEAN, false),
                        (o, v) -> o.isAnchor = v, o -> o.isAnchor, (o, p) -> o.isAnchor = p.isAnchor)
                .documentation("True on exactly ONE cell: the block that becomes the station on completion. The anchor cell must author an exact Block.ItemId (detection seeds from it). Default false; with no anchor authored, the cell at offset (0,0,0) stands in.").add()
                .afterDecode((Cell cell, com.hypixel.hytale.codec.ExtraInfo extraInfo) -> {
                    if (!cell.hasExactlyOneRoute()) {
                        extraInfo.getValidationResults().warn(
                                "A pattern Cell should author exactly one of Block | Empty, not both or neither"
                                        + " - this cell matches nothing and the pattern can never complete.");
                    }
                })
                .build();

        @Nonnull
        public static Cell of(@Nullable Vec3i offset, @Nullable ActionInput block,
                @Nullable Boolean empty, @Nullable Boolean isAnchor) {
            Cell c = new Cell();
            c.offset = offset;
            c.block = block;
            c.empty = empty;
            c.isAnchor = isAnchor;
            return c;
        }

        @Nullable
        public Vec3i getOffset() {
            return offset;
        }

        @Nullable
        public ActionInput getBlock() {
            return block;
        }

        @Nullable
        public Boolean getEmpty() {
            return empty;
        }

        /** {@link #empty}, reader-defaulted false. */
        public boolean isEmptyCell() {
            return empty != null && empty;
        }

        @Nullable
        public Boolean getIsAnchor() {
            return isAnchor;
        }

        /** {@link #isAnchor}, reader-defaulted false. */
        public boolean isAnchor() {
            return isAnchor != null && isAnchor;
        }

        /** Exactly one of the {@code Block} | {@code Empty} routes is authored. */
        public boolean hasExactlyOneRoute() {
            boolean hasBlock = block != null;
            boolean hasEmpty = isEmptyCell();
            return hasBlock ^ hasEmpty;
        }

        /** The authored X offset, 0 when unauthored. */
        public int offsetX() {
            return offset != null ? offset.effectiveX() : 0;
        }

        /** The authored Y offset, 0 when unauthored. */
        public int offsetY() {
            return offset != null ? offset.effectiveY() : 0;
        }

        /** The authored Z offset, 0 when unauthored. */
        public int offsetZ() {
            return offset != null ? offset.effectiveZ() : 0;
        }
    }
}
