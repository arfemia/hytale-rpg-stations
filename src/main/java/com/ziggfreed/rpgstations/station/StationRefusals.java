package com.ziggfreed.rpgstations.station;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.cast.WorldEvictors;
import com.ziggfreed.common.i18n.Msg;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.i18n.RpgMsg;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The ONE seam every refused press answers through: a station that turns a player away (no
 * materials, the wrong tool, someone else's pile, a busy anchor, a locked gate, a structure that
 * cannot be raised, ...) does not merely toast - it ANSWERS, with a notice, a cue, and a native
 * event, and it stops stacking the same notice on a mashed key. This class is the vanilla
 * processing bench's own failure cue ({@code Bench.FailedSoundEventId}, which the crafting systems
 * play whenever a bench cannot proceed) expressed through this engine's moment vocabulary, so a
 * refusal feels native beside one.
 *
 * <p><b>Every refusal is a moment.</b> A denial's reason is its own wording key's tail - the part
 * of {@code ui.station.<reason>} after the dot, written in id casing ({@link #reasonOf}:
 * {@code no_materials} is the reason {@code No_Materials}) - and it plays the moment id
 * {@code Refused:<Reason>} ({@link StationFlairs#refusedMomentId}); a {@code _named} wording
 * variant (a line that names the socket or structure it is about) collapses to its BASE reason,
 * because the moment is about WHY, never about which line was picked.
 *
 * <p><b>Resolution order, nearest wins, per leaf</b> ({@link #resolveCue}): the action's
 * {@code Moments["Refused:<Reason>"]}, then its {@code Moments["Refused"]}, then the settings'
 * {@code Moments["Refused:<Reason>"]}, then the settings' {@code Moments["Refused"]} - each inner
 * layer's authored leaves over the outer's, an omitted leaf falling through (the inherit-on-omit
 * rule the whole schema follows), so an action authoring only {@code Particles} keeps the settings'
 * sound underneath. An AUTHORED empty {@code Sounds} array is a leaf and reads as "none", which
 * is how one station is silenced; only an omitted key falls through. The flair overlay then
 * applies to the resolved cue exactly as to every other moment, the {@code Refused:<Reason>}
 * flair entry over the bare {@code Refused} one. A structure pattern's refusal reads the
 * pattern's own {@code Moments} where an action's would be read, and takes no flair overlay,
 * exactly like its {@code Activated}/{@code Broken} moments. Every id is matched
 * case-insensitively, so a pack authoring {@code refused:no_materials} resolves too.
 *
 * <p><b>The repeat throttle, and the one asymmetry in it.</b> A repeat of the SAME reason by the
 * same player at the SAME block inside {@code Settings.Refusals.RepeatWindowMs} is a mashed key:
 * it sends no second notice, plays none of the cue's non-sound leaves ({@code Particles},
 * {@code Shake}, {@code Interaction}, {@code Effect}) and fires no event - a second camera shake
 * on a held key would be worse than the stacked notices this exists to fix. <b>The {@code Sounds}
 * leaf ESCAPES the throttle and plays on EVERY refused press</b>, so the station always answers
 * audibly and never reads as broken. That split is deliberate, not a bug: read
 * {@link Press#refuse(String, Message)} with it in mind. A different reason, or the same reason at
 * another block, is new information and always gets the full answer.
 *
 * <p><b>A refusal cue plays at once.</b> It has no session to queue against, so a
 * {@code DelayMs} on it - the moment's own or one of its {@code Sounds} entries' - reads as zero
 * (the same degrade a structure moment gets); the validator notes one. The answer to a press
 * belongs on the press.
 *
 * <p>Live playback rides {@link StationService#playPresentationAt}, the sessionless route every
 * other sessionless moment uses; the notice rides {@link StationService#toast}; the event is
 * {@link StationEvents#fireRefused}. The pure cores ({@link #reasonOf}, {@link #resolveCue},
 * {@link #admit}, {@link #throttleKey}) carry no engine handle and are what the unit tests pin.
 */
public final class StationRefusals {

    /** Every refusal wording key lives under this prefix; the reason is what follows it. */
    static final String REASON_KEY_PREFIX = "ui.station.";

    /** The wording-variant suffix a reason collapses away: {@code socket_missing_named} is the reason {@code socket_missing}. */
    static final String NAMED_SUFFIX = "_named";

    /** How many throttle entries may accumulate before a decision prunes the expired ones. */
    static final int THROTTLE_PRUNE_SIZE = 256;

    private static final StationRefusals INSTANCE = new StationRefusals();

    /** (player | block | reason) -> the last FULL answer's wall-clock ms; in-memory only, self-pruning. */
    private final Map<String, Long> lastAnsweredAt = new ConcurrentHashMap<>();

    private StationRefusals() {
    }

    @Nonnull
    public static StationRefusals getInstance() {
        return INSTANCE;
    }

    // ==================== Pure cores ====================

    /**
     * The reason a refusal wording key names, in id casing: {@code ui.station.no_materials} is
     * {@code No_Materials}, {@code ui.station.socket_missing_named} is {@code Socket_Missing},
     * {@code ui.station.retrieve.busy} is {@code Retrieve_Busy}. The tail's dot and underscore
     * segments each become one {@code Word} of an underscore-separated PascalCase id, the shape
     * every id this engine mints takes ({@code Rare_Find}, {@code Step:Mill:Chop}). A key outside
     * the family is folded whole, so a refusal authored under some other prefix still gets a
     * stable moment id rather than an exception.
     */
    @Nonnull
    static String reasonOf(@Nonnull String langKey) {
        String tail = langKey.startsWith(REASON_KEY_PREFIX) ? langKey.substring(REASON_KEY_PREFIX.length()) : langKey;
        if (tail.endsWith(NAMED_SUFFIX)) {
            tail = tail.substring(0, tail.length() - NAMED_SUFFIX.length());
        }
        StringBuilder id = new StringBuilder(tail.length());
        for (String word : tail.split("[._]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (id.length() > 0) {
                id.append('_');
            }
            id.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return id.toString();
    }

    /**
     * The refusal cue for {@code reason}: the four layers folded nearest-first per leaf (see the
     * class javadoc), or null when no layer authors anything. Both maps are case-insensitive by
     * construction ({@link StationFlairs#caseInsensitiveMomentKeys} - an action's map arrives that
     * way from {@link ActionResolver}, the settings' from {@link SettingsCatalog}), so a plain
     * lookup answers under any authored casing; either may be null.
     */
    @Nullable
    static Presentation resolveCue(@Nonnull String reason, @Nullable Map<String, Presentation> nearest,
            @Nullable Map<String, Presentation> defaults) {
        String perReason = StationFlairs.refusedMomentId(reason);
        Presentation cue = layer(defaults, StationFlairs.MOMENT_REFUSED);
        cue = Presentation.overlaid(cue, layer(defaults, perReason));
        cue = Presentation.overlaid(cue, layer(nearest, StationFlairs.MOMENT_REFUSED));
        cue = Presentation.overlaid(cue, layer(nearest, perReason));
        return cue;
    }

    @Nullable
    private static Presentation layer(@Nullable Map<String, Presentation> moments, @Nonnull String momentId) {
        return moments == null ? null : moments.get(momentId);
    }

    /** The throttle key: one FULL answer per (player, block, reason) inside the window. */
    @Nonnull
    static String throttleKey(@Nonnull UUID playerId, @Nonnull String blockKey, @Nonnull String reason) {
        return playerId + "|" + blockKey + "|" + reason;
    }

    /**
     * PURE throttle decision: {@code true} (and the answered-at record updates) when {@code key}
     * was not answered in full within {@code windowMs} of {@code nowMs}; a window of zero or less
     * admits everything. Self-pruning: once the map outgrows {@link #THROTTLE_PRUNE_SIZE}, expired
     * entries are dropped before recording, so the map stays bounded by the refusals genuinely
     * live inside one window.
     */
    static boolean admit(@Nonnull Map<String, Long> lastAt, @Nonnull String key, long nowMs, long windowMs) {
        if (windowMs <= 0) {
            return true;
        }
        Long last = lastAt.get(key);
        if (last != null && nowMs - last < windowMs) {
            return false;
        }
        if (lastAt.size() > THROTTLE_PRUNE_SIZE) {
            lastAt.values().removeIf(at -> at == null || nowMs - at >= windowMs);
        }
        lastAt.put(key, nowMs);
        return true;
    }

    // ==================== The live entry ====================

    /**
     * Opens the refusal context for ONE press at {@code (x, y, z)}. The caller enriches it as the
     * press resolves ({@link Press#world}, {@link Press#station}, {@link Press#action}) and calls
     * {@link Press#refuse} at whichever denial it reaches; a press that engages never calls it and
     * the context costs nothing. {@code ref} may be null where no player entity is in hand (an
     * environment-driven structure walk), in which case the cue's entity-bound leaves are skipped.
     */
    @Nonnull
    public static Press press(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef, @Nonnull String stationId, int x, int y, int z) {
        return INSTANCE.new Press(store, ref, playerRef, stationId, x, y, z);
    }

    /**
     * One press's refusal context - what the engine knows about the press at the moment it turns
     * it away. Mutable on purpose: a press learns its world, station and action in that order, and
     * a denial can land between any two of those.
     */
    public final class Press {

        private final Store<EntityStore> store;
        @Nullable private final Ref<EntityStore> ref;
        private final PlayerRef playerRef;
        private final int x;
        private final int y;
        private final int z;
        private String stationId;
        @Nullable private String actionId;
        @Nullable private World world;
        @Nullable private StationAsset asset;
        @Nullable private Map<String, Presentation> nearestMoments;
        private boolean flairsApply = true;

        private Press(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef, @Nonnull String stationId, int x, int y, int z) {
            this.store = store;
            this.ref = ref;
            this.playerRef = playerRef;
            this.stationId = stationId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** The block's world, once resolved; resolved from {@code ref} at refusal time when not supplied. */
        @Nonnull
        public Press world(@Nullable World world) {
            this.world = world;
            return this;
        }

        /** The station the press resolved to: its id names the refusal and its flairs overlay the cue. */
        @Nonnull
        public Press station(@Nonnull StationAsset asset) {
            this.asset = asset;
            this.stationId = asset.getId();
            return this;
        }

        /** The action the press resolved to: its id rides the event and its {@code Moments} are the nearest layer. */
        @Nonnull
        public Press action(@Nullable ActionResolver.ResolvedAction action) {
            this.actionId = action != null ? action.getActionId() : null;
            this.nearestMoments = action != null ? action.getMoments() : null;
            return this;
        }

        /**
         * A nearest layer that is not an action's - a structure pattern's own {@code Moments}
         * (made case-insensitive here). A pattern refusal takes no flair overlay, like its other
         * moments.
         */
        @Nonnull
        public Press patternMoments(@Nullable Map<String, Presentation> moments) {
            this.nearestMoments = moments != null ? StationFlairs.caseInsensitiveMomentKeys(moments) : null;
            this.flairsApply = false;
            return this;
        }

        /** Refuses with the plain wording {@code langKey} (no arguments). */
        public void refuse(@Nonnull String langKey) {
            refuse(langKey, RpgMsg.tr(langKey));
        }

        /**
         * Refuses with a wording that NAMES what it is about when {@code labelKey} is authored
         * ({@code <baseKey>_named} with the label as its one argument), else the plain
         * {@code baseKey}. The reason is {@code baseKey}'s either way.
         */
        public void refuseNamed(@Nonnull String baseKey, @Nullable String labelKey) {
            Message toast = labelKey != null && !labelKey.isBlank()
                    ? RpgMsg.tr(baseKey + NAMED_SUFFIX, Msg.key(labelKey))
                    : RpgMsg.tr(baseKey);
            refuse(baseKey, toast);
        }

        /**
         * Refuses the press: the notice, the cue and the event, throttled per the class javadoc.
         * {@code langKey} names the reason ({@link #reasonOf}); {@code toast} is the already-built
         * wording (with whatever arguments it carries). Never throws.
         */
        public void refuse(@Nonnull String langKey, @Nonnull Message toast) {
            try {
                String reason = reasonOf(langKey);
                UUID playerId = playerRef.getUuid();
                UUID worldUuid = playerRef.getWorldUuid();
                String blockKey = StationAnchors.worldPrefix(String.valueOf(worldUuid)) + x + ":" + y + ":" + z;
                long windowMs = SettingsCatalog.getInstance().current().effectiveRefusalRepeatWindowMs();
                // No player id means nothing to key a repeat on: answer in full rather than not at all.
                boolean fresh = playerId == null
                        || admit(lastAnsweredAt, throttleKey(playerId, blockKey, reason),
                                System.currentTimeMillis(), windowMs);
                if (fresh) {
                    StationService.toast(playerRef, toast);
                }
                playCue(reason, fresh);
                if (fresh && playerId != null && worldUuid != null) {
                    StationEvents.fireRefused(store, playerRef, playerId, worldUuid, x, y, z, stationId, actionId,
                            reason);
                }
            } catch (Throwable t) {
                Log.fine("STATION refusal '" + langKey + "' failed to answer: " + t.getMessage());
            }
        }

        /**
         * The cue, split on the throttle's one asymmetry: {@code Sounds} play on EVERY refused
         * press, everything else only on a fresh one. Both halves ride the sessionless route, at
         * once (no delay is honored on a refusal).
         */
        private void playCue(@Nonnull String reason, boolean fresh) {
            Presentation cue = resolveCue(reason, nearestMoments, SettingsCatalog.getInstance().defaultMoments());
            if (flairsApply && asset != null) {
                UUID playerId = playerRef.getUuid();
                if (playerId != null) {
                    Map<String, Map<String, Presentation>> flairs =
                            FlairCatalog.getInstance().effectiveFlairsFor(stationId, asset);
                    cue = StationFlairs.effective(cue, flairs, StationFlairs.MOMENT_REFUSED, playerId, stationId);
                    cue = StationFlairs.effective(cue, flairs, StationFlairs.refusedMomentId(reason), playerId,
                            stationId);
                }
            }
            if (cue == null) {
                return;
            }
            World w = world != null ? world : worldOf();
            if (w == null) {
                return;
            }
            Presentation sounds = cue.soundsOnly();
            if (sounds != null) {
                StationService.playPresentationAt(w, playerRef, ref, sounds, x, y, z);
            }
            Presentation rest = fresh ? cue.withoutSounds() : null;
            if (rest != null) {
                StationService.playPresentationAt(w, playerRef, ref, rest, x, y, z);
            }
        }

        @Nullable
        private World worldOf() {
            if (ref == null) {
                return null;
            }
            try {
                return WorldEvictors.worldOf(ref);
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
