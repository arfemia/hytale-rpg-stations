package com.ziggfreed.rpgstations.pages;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.ziggfreed.common.ui.ZigRichButton;
import com.ziggfreed.rpgstations.i18n.RpgMsg;
import com.ziggfreed.rpgstations.pages.PickerCategories.Category;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The multi-output PICKER page (decision 50, demoted by decision 51's native-composition
 * amendment to the fallback route: a station whose input matches recipes across multiple native
 * categories that the native bench window cannot express - e.g. a diegetic work loop rather than
 * instant bench crafting - offers this picker instead). A compact icon TAB STRIP, one tab per
 * available output category; the player picks which output the station should produce.
 *
 * <p><b>Caller contract (decision 50, binding on whoever opens this page, NOT enforced here):</b>
 * <ul>
 *   <li>Open this page ONLY when 2+ categories are available for the station - a single-category
 *   station never shows a picker at all.</li>
 *   <li>NEVER auto-open it - the player must explicitly request the picker (sneak+F on the
 *   station; plain F engages work as today per the round-3 selector-entity fallback pattern).</li>
 *   <li>{@code showLocked} is the authored {@code Picker.ShowLocked} knob (default true): true
 *   keeps every category visible with the locked ones greyed + labeled with the required tool;
 *   false drops locked categories from the strip entirely.</li>
 * </ul>
 *
 * <p>This page owns rendering + selection ONLY - it has no opinion on how a category id maps to
 * a recipe, a bench window, or a station action; the caller supplies the ordered {@link Category}
 * list and receives the chosen id back through {@link PickerCallback#onSelect}, called AFTER the
 * page has already closed (so the callback is free to open a follow-on page, e.g. the native
 * bench window, without fighting this one for the client's active page slot).
 *
 * <p>Every {@link #handleDataEvent} exit path closes the page before returning (the
 * infinite-loading trap this mod's own {@code investigate-before-deferring-ui-elements} memory
 * note warns about) - there is no partial-update path here, the page is a single-shot chooser.
 */
public final class RpgStationPickerPage extends InteractiveCustomUIPage<RpgStationPickerPage.PickerEventData> {

    private static final String PAGE_TEMPLATE = "Pages/RpgStationPicker.ui";
    private static final String TAB_TEMPLATE = "Pages/RpgStationPickerTab.ui";
    private static final String TABS_CONTAINER = "#RpgStationPickerTabs";

    /** Called once, AFTER this page has already closed, with the chosen category's id. */
    @FunctionalInterface
    public interface PickerCallback {
        void onSelect(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String categoryId);

        /** Fired when the player closes the picker without choosing anything. No-op by default. */
        default void onCancel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        }
    }

    private final List<Category> categories;
    private final boolean showLocked;
    private final PickerCallback callback;

    public RpgStationPickerPage(@Nonnull PlayerRef playerRef, @Nonnull List<Category> categories,
            boolean showLocked, @Nonnull PickerCallback callback) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PickerEventData.CODEC);
        this.categories = List.copyOf(categories);
        this.showLocked = showLocked;
        this.callback = callback;
    }

    /**
     * Resolve the caller (a registered player). Never throws; returns {@code false} without
     * opening anything on any missing/invalid state.
     */
    public static boolean open(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull PlayerRef playerRef, @Nonnull List<Category> categories, boolean showLocked,
            @Nonnull PickerCallback callback) {
        try {
            Player player = (Player) store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return false;
            }
            player.getPageManager().openCustomPage(ref, store,
                    new RpgStationPickerPage(playerRef, categories, showLocked, callback));
            return true;
        } catch (Throwable t) {
            Log.fine("RpgStationPickerPage: open failed: " + t.getMessage());
            return false;
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(PAGE_TEMPLATE);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "close"));

        cmd.set("#RpgStationPickerTitle.TextSpans", RpgMsg.tr("ui.station.picker.title"));
        cmd.set("#RpgStationPickerHint.TextSpans", RpgMsg.tr("ui.station.picker.hint"));

        List<Category> visible = PickerCategories.visibleCategories(categories, showLocked);
        for (int i = 0; i < visible.size(); i++) {
            Category c = visible.get(i);
            cmd.append(TABS_CONTAINER, TAB_TEMPLATE);
            String sel = TABS_CONTAINER + "[" + i + "]";
            String btn = sel + " #TabBtn";

            cmd.set(sel + " #Icon.Slots", List.of(new ItemGridSlot(new ItemStack(c.iconItemId(), 1))));

            if (c.label() != null) {
                ZigRichButton.text(cmd, btn, c.label());
                cmd.set(btn + " #Label.Visible", true);
            } else {
                cmd.set(btn + " #Label.Visible", false);
            }

            if (c.costLine() != null) {
                cmd.set(sel + " #Cost.TextSpans", c.costLine());
                cmd.set(sel + " #Cost.Visible", true);
            } else {
                cmd.set(sel + " #Cost.Visible", false);
            }

            if (c.locked()) {
                cmd.set(btn + ".Enabled", false);
                Message lockText = c.requiredToolLabel() != null
                        ? RpgMsg.tr("ui.station.picker.locked", c.requiredToolLabel())
                        : RpgMsg.tr("ui.station.picker.locked_generic");
                cmd.set(sel + " #LockReason.TextSpans", lockText);
                cmd.set(sel + " #LockReason.Visible", true);
                // No click binding: a disabled Button is also the defense-in-depth guard against a
                // stale client packet re-firing a click on a category the player cannot use.
            } else {
                cmd.set(sel + " #LockReason.Visible", false);
                events.addEventBinding(CustomUIEventBindingType.Activating, btn,
                        EventData.of("Action", "select").append("Category", c.id()));
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull PickerEventData data) {
        // Close the page whenever a live Player exists (single-shot chooser, no partial updates,
        // so nothing is left open for a stale binding to double-fire against). A missing Player is
        // near-impossible on a click the player themselves sent and carries no open page to close
        // anyway - but the callback below STILL fires so the caller (the station engine) is never
        // left waiting on a picker result that never arrives.
        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }

        if ("select".equals(data.action) && data.category != null && !data.category.isEmpty()) {
            callback.onSelect(ref, store, data.category);
            return;
        }
        callback.onCancel(ref, store);
    }

    /** The state event this page round-trips: {@code select} (value in {@code category}) or {@code close}. */
    public static final class PickerEventData {
        public String action;
        public String category;

        public static final BuilderCodec<PickerEventData> CODEC =
                BuilderCodec.builder(PickerEventData.class, PickerEventData::new)
                        .append(new KeyedCodec<>("Action", Codec.STRING),
                                (d, v, i) -> d.action = v,
                                (d, i) -> d.action)
                        .add()
                        .append(new KeyedCodec<>("Category", Codec.STRING),
                                (d, v, i) -> d.category = v,
                                (d, i) -> d.category)
                        .add()
                        .build();
    }
}
