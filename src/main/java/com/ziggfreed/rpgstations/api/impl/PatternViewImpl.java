package com.ziggfreed.rpgstations.api.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.api.PatternView;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.StructurePatternAsset;

/**
 * Read-only {@link PatternView} adapter over a folded {@link StructurePatternAsset}: everything is
 * snapshotted at construction into plain immutable values, so a consumer may retain the view.
 * Cell offsets are normalized ANCHOR-relative (the anchor cell's authored offset subtracted out),
 * matching the frame the runtime walk actually matches in, so the anchor cell always reads
 * {@code (0,0,0)} whatever authoring frame the file used.
 */
final class PatternViewImpl implements PatternView {

    @Nonnull private final String id;
    @Nullable private final String activateBlock;
    @Nullable private final String revertBlock;
    private final boolean rotateYaw90;
    private final boolean rotateMirror;
    @Nonnull private final List<CellView> cells;

    PatternViewImpl(@Nonnull StructurePatternAsset asset) {
        this.id = asset.getId();
        StructurePatternAsset.Activate activate = asset.getActivate();
        this.activateBlock = activate != null ? blankToNull(activate.getBlock()) : null;
        this.revertBlock = asset.effectiveRevertBlock();
        StructurePatternAsset.Rotate rotate = asset.getRotate();
        this.rotateYaw90 = rotate == null || rotate.effectiveYaw90();
        this.rotateMirror = rotate != null && rotate.effectiveMirror();

        StructurePatternAsset.Cell[] authored = asset.getCells();
        if (authored == null || authored.length == 0) {
            this.cells = List.of();
            return;
        }
        int anchorIndex = asset.anchorCellIndex();
        StructurePatternAsset.Cell anchor = authored[anchorIndex];
        int ax = anchor != null ? anchor.offsetX() : 0;
        int ay = anchor != null ? anchor.offsetY() : 0;
        int az = anchor != null ? anchor.offsetZ() : 0;
        List<CellView> out = new ArrayList<>(authored.length);
        for (int i = 0; i < authored.length; i++) {
            StructurePatternAsset.Cell cell = authored[i];
            if (cell == null) {
                continue;
            }
            out.add(cellViewOf(cell, i == anchorIndex, ax, ay, az));
        }
        this.cells = List.copyOf(out);
    }

    /** One cell's summary: anchor-relative offset plus the dominant matcher route and its value. */
    @Nonnull
    private static CellView cellViewOf(@Nonnull StructurePatternAsset.Cell cell, boolean anchor,
            int ax, int ay, int az) {
        int x = cell.offsetX() - ax;
        int y = cell.offsetY() - ay;
        int z = cell.offsetZ() - az;
        if (cell.isEmptyCell()) {
            return new CellView(x, y, z, anchor, ROUTE_EMPTY, null);
        }
        ActionInput block = cell.getBlock();
        if (block != null) {
            String itemId = blankToNull(block.getItemId());
            if (itemId != null) {
                return new CellView(x, y, z, anchor, ROUTE_ITEM_ID, itemId);
            }
            String family = blankToNull(block.getResourceTypeId());
            if (family != null) {
                return new CellView(x, y, z, anchor, ROUTE_RESOURCE_TYPE, family);
            }
            Map<String, String[]> tags = block.getTags();
            if (tags != null && !tags.isEmpty()) {
                return new CellView(x, y, z, anchor, ROUTE_TAGS, tagsSummary(tags));
            }
        }
        return new CellView(x, y, z, anchor, ROUTE_NONE, null);
    }

    /** The readable tag summary: one {@code key} or {@code key=v1|v2} term per entry, comma-joined in authored order. */
    @Nonnull
    private static String tagsSummary(@Nonnull Map<String, String[]> tags) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String[]> e : tags.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(e.getKey());
            String[] values = e.getValue();
            if (values != null && values.length > 0) {
                sb.append('=').append(String.join("|", values));
            }
        }
        return sb.toString();
    }

    @Nullable
    private static String blankToNull(@Nullable String s) {
        return s != null && !s.isBlank() ? s : null;
    }

    @Override
    @Nonnull
    public String id() {
        return id;
    }

    @Override
    @Nullable
    public String activateBlock() {
        return activateBlock;
    }

    @Override
    @Nullable
    public String revertBlock() {
        return revertBlock;
    }

    @Override
    public boolean rotateYaw90() {
        return rotateYaw90;
    }

    @Override
    public boolean rotateMirror() {
        return rotateMirror;
    }

    @Override
    public int cellCount() {
        return cells.size();
    }

    @Override
    @Nonnull
    public List<CellView> cells() {
        return cells;
    }
}
