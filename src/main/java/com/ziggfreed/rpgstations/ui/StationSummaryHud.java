package com.ziggfreed.rpgstations.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.ui.hud.HudPosition;
import com.ziggfreed.common.ui.hud.KeyedCustomHud;
import com.ziggfreed.common.ui.rows.SummaryRow;
import com.ziggfreed.common.ui.rows.SummaryRowRenderer;
import com.ziggfreed.rpgstations.asset.RpgStationsSettingsAsset;
import com.ziggfreed.rpgstations.station.SettingsCatalog;
import com.ziggfreed.rpgstations.util.Log;
import com.ziggfreed.rpgstations.i18n.RpgMsg;

/**
 * The standalone-rich end-of-session summary panel (design section 4.1's summary-panel split,
 * part (b): the generic panel itself; part (a), the {@code KeyedCustomHud} base, lives in common).
 * It renders title + crest + cycles line + the item ledger, and NO channel-specific rows of its
 * own: a listening mod adds its own through a registered {@code SummaryEnricher}. Extends
 * {@link KeyedCustomHud} DIRECTLY (this mod has no HUD base of its own, per that class's javadoc).
 *
 * <p>The layout rules below are load-bearing (see {@code
 * Pages/RpgStationSummary.ui}'s header comment): outer/inner {@code Group} split (the
 * full-screen-dark fix - a HUD document's outermost element is an unanchored full-viewport
 * canvas, the real sized/backgrounded panel is a named child one level in), an EXPLICIT {@code
 * Anchor} Width on the {@code $F.@ZigDecoratedFrame} invocation itself (not just its {@code
 * #Content} children - the root-cause fix for a background that stayed full-width even after
 * every label got its own Width cap), an explicit Width on every {@code #Content} child, and
 * content-height sizing ({@link #usesContentHeight()}) so the panel hugs exactly its title/
 * crest/text/however-many ledger rows are showing. Auto-hide uses the {@code ToastController}
 * TTL pattern: a monotonic {@link #generation} counter stamped by every {@link #showSummary}
 * guards a stale scheduled hide from clearing a NEWER summary that re-armed in the meantime.
 *
 * <p><b>Neutral frame, themeable from outside.</b> The {@code .ui} uses the common {@code
 * ZigFrames.ui}'s {@code @ZigDecoratedFrame}, never another mod's frame set;
 * {@link #ROOT_SELECTOR} is the FROZEN api contract (critique m5,
 * binding) a registered {@code SummaryEnricher.decorate} (via {@code
 * api.SummaryDecorateContext#rootSelector()}) writes theming commands against cross-jar - see
 * that class's javadoc.
 */
public final class StationSummaryHud extends KeyedCustomHud {

    public static final String HUD_KEY = "rpgstations:station_summary";

    /**
     * FROZEN api contract (critique m5, binding): a registered {@code SummaryEnricher.decorate}
     * writes {@code UICommandBuilder} commands against this exact selector cross-jar (via {@code
     * api.SummaryDecorateContext#rootSelector()}) - a {@code .ui} restructure MUST keep this
     * stable. Must match the {@code .ui}'s {@code #RpgStationSummaryRoot} element id.
     */
    public static final String ROOT_SELECTOR = "#RpgStationSummaryRoot";

    /** Must match the {@code .ui}'s {@code #RpgStationSummaryRoot} static-fallback Width. */
    private static final int PANEL_WIDTH_PX = 528;

    private static final long DEFAULT_DURATION_MS = 6000L;
    private static final long UPDATE_INTERVAL_MS = 250L;

    private static final String TITLE_SEL = "#RpgStationSummaryTitle";
    private static final String TEXT_SEL = "#RpgStationSummaryText";
    private static final String ITEM_MORE_SEL = "#RpgStationSummaryItemMore";
    private static final String STATION_ICON_SEL = "#RpgStationSummaryStationIcon";

    /** Row slots the {@code .ui} pre-declares ({@code #RpgStationSummaryItem0..5}); the rest overflow. */
    private static final int MAX_LEDGER_ROWS = 6;

    /**
     * The smaller SECOND Label every row slot declares, painted from a row's optional {@code
     * SummaryRow.subText} and hidden on a row that has none. Naming it to the renderer is what
     * opts these slots into two-line rows; the {@code .ui} must keep declaring it in EVERY slot,
     * since a command against a selector the document does not declare crashes the client.
     */
    private static final String SUB_LABEL_ID = "#Sub";

    private static final Color LUCKY_ROW_COLOR = Color.decode("#ffd24a");
    private static final Color CONSUMED_ROW_COLOR = Color.decode("#e08a8a");
    private static final Color PRODUCED_ROW_COLOR = Color.decode("#8fd18a");

    /** Generation token guarding a stale scheduled hide (the {@code ToastController} TTL pattern). */
    private final AtomicLong generation = new AtomicLong(0L);

    public StationSummaryHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, HUD_KEY);
    }

    /**
     * One item-ledger row: the already-composed client-resolved text line, its semantic
     * {@link SummaryRow.Kind}, and an OPTIONAL smaller second line ({@link #subLine}) rendered
     * beneath it at {@code @LedgerSubStyle}. A null {@link #subLine} collapses the row back to one
     * line, so a row only grows when it has something extra to say.
     */
    public static final class LedgerRow {
        @Nonnull final String itemId;
        final int quantity;
        @Nonnull final Message line;
        @Nonnull final SummaryRow.Kind kind;
        @Nullable final Message subLine;

        public LedgerRow(@Nonnull String itemId, int quantity, @Nonnull Message line, @Nonnull SummaryRow.Kind kind) {
            this(itemId, quantity, line, kind, null);
        }

        public LedgerRow(@Nonnull String itemId, int quantity, @Nonnull Message line,
                @Nonnull SummaryRow.Kind kind, @Nullable Message subLine) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.line = line;
            this.kind = kind;
            this.subLine = subLine;
        }

        @Nonnull
        public String itemId() {
            return itemId;
        }

        public int quantity() {
            return quantity;
        }

        @Nonnull
        public Message line() {
            return line;
        }

        @Nonnull
        public SummaryRow.Kind kind() {
            return kind;
        }

        /** The optional smaller second line; null = a single-line row. */
        @Nullable
        public Message subLine() {
            return subLine;
        }
    }

    /** Fallback default: top-center, offset down to clear the native top-bar cluster. */
    @Nonnull
    public static HudPosition defaultPosition() {
        return new HudPosition(HudPosition.AnchorEdge.TOP, HudPosition.HorizontalEdge.CENTER, 0, 72);
    }

    @Nonnull
    @Override
    protected String rootSelector() {
        return ROOT_SELECTOR;
    }

    @Override
    protected int panelWidth() {
        return PANEL_WIDTH_PX;
    }

    /** Unused: this HUD is content-sized ({@link #usesContentHeight()}). */
    @Override
    protected int panelHeight() {
        return 0;
    }

    @Override
    protected boolean usesContentHeight() {
        return true;
    }

    @Override
    protected long updateIntervalMs() {
        return UPDATE_INTERVAL_MS;
    }

    /**
     * Reads {@code RpgStationsSettingsAsset.SummaryHud.Position}/{@code OffsetX}/{@code OffsetY};
     * falls back to {@link #defaultPosition()}.
     */
    @Nonnull
    @Override
    protected HudPosition configuredPosition() {
        RpgStationsSettingsAsset.SummaryHud hud = SettingsCatalog.getInstance().current().getSummaryHud();
        if (hud == null || hud.getPosition() == null) {
            return defaultPosition();
        }
        int offsetX = hud.getOffsetX() != null ? hud.getOffsetX() : defaultPosition().getOffsetX();
        int offsetY = hud.getOffsetY() != null ? hud.getOffsetY() : defaultPosition().getOffsetY();
        HudPosition parsed = HudPosition.parse(hud.getPosition(), offsetX, offsetY);
        return parsed != null ? parsed : defaultPosition();
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Pages/RpgStationSummary.ui");
        applyConfiguredPosition(commandBuilder);
        // The .ui ships hidden (Visible: false); nothing else to push until the first showSummary.
    }

    /**
     * Show {@code title}/{@code body}/{@code extraRows}+{@code ledgerRows} now (a partial
     * update), run {@code decorateHook} (if any) against the SAME command builder before it
     * pushes (a registered {@code SummaryEnricher.decorate}, design section 3.2), then schedule
     * the auto-hide at {@code durationMs}. A second call before the first hide fires bumps
     * {@link #generation}, so the STALE scheduled hide from the first call becomes a no-op.
     */
    public void showSummary(@Nonnull Message title, @Nonnull Message body, @Nullable String stationIconItemId,
            @Nonnull List<SummaryRow> extraRows, @Nonnull List<LedgerRow> ledgerRows, long durationMs,
            @Nullable Consumer<UICommandBuilder> decorateHook) {
        long gen = generation.incrementAndGet();

        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set(rootSelector() + ".Visible", true);
        cmd.set(TITLE_SEL + ".TextSpans", title);
        cmd.set(TEXT_SEL + ".TextSpans", body);
        renderStationIcon(cmd, stationIconItemId);
        renderLedger(cmd, extraRows, ledgerRows);
        if (decorateHook != null) {
            try {
                decorateHook.accept(cmd);
            } catch (Throwable t) {
                Log.fine(HUD_KEY + ": summary decorate hook failed: " + t.getMessage());
            }
        }
        update(false, cmd);

        long ttl = durationMs > 0 ? durationMs : DEFAULT_DURATION_MS;
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> hideIfCurrent(gen), ttl, TimeUnit.MILLISECONDS);
    }

    /**
     * Populate the fixed ledger row slots via the shared, mod-agnostic {@link SummaryRowRenderer}
     * (hiding any unused slot), plus the keyed overflow row when the row count exceeds {@link
     * #MAX_LEDGER_ROWS}. {@code extraRows} (a registered {@code SummaryEnricher}'s own rows,
     * design section 3.2) are PREPENDED before the engine's own item rows, in registration
     * order. Each row's color is baked into its {@link Message} via {@link Message#color} (never
     * a separate style command) - the rich-text convention every other ledger surface in this
     * codebase follows.
     *
     * <p>Rows are rendered in the TWO-LINE form ({@link #SUB_LABEL_ID}): a row carrying a {@code
     * SummaryRow.subText} shows it beneath its headline at the smaller {@code @LedgerSubStyle},
     * and a row without one collapses back to a single line. This mod's own PRODUCED rows use it for
     * the per-cycle yield decomposition; an enricher's rows may use it too (a contribution channel's
     * breakdown belongs to whichever mod owns that channel's vocabulary, so what THEIR second line
     * says is never decided here).
     */
    private static void renderLedger(@Nonnull UICommandBuilder cmd, @Nonnull List<SummaryRow> extraRows,
            @Nonnull List<LedgerRow> ledgerRows) {
        List<SummaryRow> summaryRows = new ArrayList<>(extraRows.size() + ledgerRows.size());
        summaryRows.addAll(extraRows);
        for (LedgerRow row : ledgerRows) {
            summaryRows.add(buildItemRow(row));
        }
        int overflow = SummaryRowRenderer.render(cmd, "#RpgStationSummaryItem", MAX_LEDGER_ROWS, summaryRows,
                SUB_LABEL_ID);
        cmd.set(ITEM_MORE_SEL + ".Visible", overflow > 0);
        if (overflow > 0) {
            cmd.set(ITEM_MORE_SEL + ".TextSpans",
                    RpgMsg.tr("ui.station.summary.items_more", overflow));
        }
    }

    private static void renderStationIcon(@Nonnull UICommandBuilder cmd, @Nullable String stationIconItemId) {
        if (stationIconItemId == null || stationIconItemId.isEmpty()) {
            cmd.set(STATION_ICON_SEL + ".Visible", false);
            return;
        }
        cmd.set(STATION_ICON_SEL + ".Visible", true);
        cmd.set(STATION_ICON_SEL + " #Icon.Slots", List.of(new ItemGridSlot(new ItemStack(stationIconItemId, 1))));
    }

    @Nonnull
    private static SummaryRow buildItemRow(@Nonnull LedgerRow row) {
        // An ENHANCE row (design section 9.5, round-7 D-6 / critique m11) is NEVER recolored here:
        // a per-stat enhance line arrives pre-styled by its provider (per-stat school colors) and
        // the engine's own durability row bakes its accent at composition - so its Message renders
        // verbatim. Every other kind bakes a per-kind color onto its line.
        if (row.kind == SummaryRow.Kind.ENHANCE) {
            return new SummaryRow(row.itemId, row.line, row.subLine, row.kind);
        }
        Color color = switch (row.kind) {
            case CONSUMED -> CONSUMED_ROW_COLOR;
            case LUCKY -> LUCKY_ROW_COLOR;
            default -> PRODUCED_ROW_COLOR;
        };
        Message sub = row.subLine != null ? row.subLine.color(color) : null;
        return new SummaryRow(row.itemId, row.line.color(color), sub, row.kind);
    }

    /** The scheduled hide: a no-op when a newer {@link #showSummary} already re-armed. */
    private void hideIfCurrent(long gen) {
        if (generation.get() != gen) {
            return;
        }
        try {
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set(rootSelector() + ".Visible", false);
            update(false, cmd);
        } catch (Throwable t) {
            Log.fine(HUD_KEY + ": scheduled auto-hide failed: " + t.getMessage());
        }
    }

    /**
     * Resolve {@code playerRef}'s registered instance and push a summary, returning {@code
     * false} (never throwing) when the surface is settings-disabled, unregistered, or the push
     * fails. MUST run on the WORLD THREAD (mirrors {@code MmoHud}'s contract - the native
     * {@code HudManager} map is not concurrent). {@code extraRows} and {@code decorateHook} are
     * the {@code SummaryEnricher} plumbing (design section 3.2) - pass {@code List.of()}/{@code
     * null} when nothing is registered.
     */
    public static boolean tryShow(@Nonnull PlayerRef playerRef, @Nonnull Message title, @Nonnull Message body,
            @Nullable String stationIconItemId, @Nonnull List<SummaryRow> extraRows,
            @Nonnull List<LedgerRow> ledgerRows, @Nullable Consumer<UICommandBuilder> decorateHook) {
        RpgStationsSettingsAsset.SummaryHud settings = SettingsCatalog.getInstance().current().getSummaryHud();
        if (settings != null && !settings.isEnabled()) {
            return false;
        }
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return false;
            }
            Player player = ref.getStore().getComponent(ref, Player.getComponentType());
            if (player == null) {
                return false;
            }
            StationSummaryHud hud = KeyedCustomHud.get(player, HUD_KEY, StationSummaryHud.class);
            if (hud == null) {
                return false;
            }
            long durationMs = settings != null && settings.getTtlMs() != null && settings.getTtlMs() > 0
                    ? settings.getTtlMs() : DEFAULT_DURATION_MS;
            hud.showSummary(title, body, stationIconItemId, extraRows, ledgerRows, durationMs, decorateHook);
            return true;
        } catch (Throwable t) {
            Log.fine(HUD_KEY + ": tryShow failed: " + t.getMessage());
            return false;
        }
    }
}
