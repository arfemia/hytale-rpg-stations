package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * The puppet presentation route (round-4 design, "mount the player, hide their player model, and
 * spawn/display a visual of their character model performing the steps" - the maintainer's
 * verbatim proposal): a top-level {@link StationAsset#getPuppet()} group, sibling to {@code Hold}/
 * {@code Camera}/{@code Animation}/{@code Custody}, whole-GROUP overridable per {@link ActionDef}
 * (design doc's section 2 rationale - the puppet is ORTHOGONAL to which mount holds the real
 * player, so it is never nested under {@code Hold}). See
 * {@code .claude/research/raw/rpg-stations-puppet-presentation-design-2026-07-22.md} sections 3
 * (this schema) and the round-4 maintainer decisions this leg locks in:
 *
 * <ul>
 *   <li><b>{@link Hide#getRoute()} is CROWNED {@code "Scale"}</b> as the default (in-game spike
 *   proven: fully hides the puppeteer's own rendered body, including the held item, in both
 *   first- and third-person - see {@code ziggfreed-common}'s {@code entity.PlayerPuppetService
 *   #hideByScale}/{@code #revealByScale}). The design doc's {@code "ModelSwap"} and
 *   {@code "HiddenManager"} routes are RETIRED this round (buggy / unproven) - {@link Hide} is a
 *   THREE-arm union discriminator now: {@code "Scale"} (default), {@code "Effect"} (FUTURE,
 *   schema-reserved - see {@link Hide#getEffectId()}), {@code "None"} (the degraded fallback,
 *   puppet spawns but the real player stays visible).
 *   <li><b>{@link Look#getSource()} defaults {@code "PlayerClone"}</b> (the puppet clones the
 *   live player skin - the maintainer's literal "their character model"), with the knob left as
 *   an OPEN performer seam: {@code "Model"} (a fixed authored look) already composes cleanly, and
 *   a FUTURE provider/minion route (e.g. a summoned helper performing the work instead of the
 *   player) extends {@link Look#getSource()}'s union without a schema break - see that field's
 *   own javadoc.
 * </ul>
 *
 * <p>Per-station default -&gt; per-action override (design 3.1): the station-level {@code Puppet}
 * group is the default for the implicit {@code "work"} action; an authored {@link ActionDef#getPuppet()}
 * REPLACES the whole group wholesale (design 9.1's whole-group rule, resolved through
 * {@code station.ActionResolver#resolve}), never a per-leaf merge. A {@code StationStep} carries
 * its OWN small per-step override (see {@code StationStep.PuppetOverride}) for the moment-to-
 * moment {@code Clip}/{@code Prop} only - the hide/look/spawn knobs stay session-scoped, set once
 * at engage.
 *
 * <pre>{@code
 * "Puppet": {
 *   "Enabled": true,
 *   "Hide":   { "Route": "Scale" },
 *   "Look":   { "Source": "PlayerClone" },
 *   "Offset": { "X": 0.0, "Y": 0.0, "Z": 0.0 },
 *   "Yaw":    0.0,
 *   "Prop":   { "Source": "MirrorHeld", "Slot": "Hotbar" }
 * }
 * }</pre>
 */
public final class Puppet {

    // ---- Hide.Route union arms ----
    public static final String HIDE_ROUTE_SCALE = "Scale";
    public static final String HIDE_ROUTE_EFFECT = "Effect";
    public static final String HIDE_ROUTE_NONE = "None";

    // ---- Look.Source union arms ----
    public static final String LOOK_SOURCE_PLAYER_CLONE = "PlayerClone";
    public static final String LOOK_SOURCE_MODEL = "Model";
    /** The NpcRole performer arm (seam wave, decision 47): a Role-driven NPCEntity backend. */
    public static final String LOOK_SOURCE_NPC_ROLE = "NpcRole";

    // ---- Look.Role.SkinSource union arms ----
    public static final String SKIN_SOURCE_PLAYER_CLONE = "PlayerClone";
    public static final String SKIN_SOURCE_ROLE_DEFAULT = "RoleDefault";

    // ---- Prop.Source union arms ----
    public static final String PROP_SOURCE_MIRROR_HELD = "MirrorHeld";
    public static final String PROP_SOURCE_ITEM_ID = "ItemId";
    public static final String PROP_SOURCE_NONE = "None";

    // ---- Prop.Slot arms ----
    public static final String PROP_SLOT_HOTBAR = "Hotbar";
    public static final String PROP_SLOT_UTILITY = "Utility";

    @Nullable protected Boolean enabled;
    @Nullable protected Hide hide;
    @Nullable protected Look look;
    @Nullable protected Offset offset;
    @Nullable protected Double yaw;
    @Nullable protected Prop prop;

    public static final BuilderCodec<Puppet> CODEC = BuilderCodec.builder(Puppet.class, Puppet::new)
            .appendInherited(new KeyedCodec<>("Enabled", Codec.BOOLEAN, false),
                    (o, v) -> o.enabled = v, o -> o.enabled, (o, p) -> o.enabled = p.enabled).add()
            .appendInherited(new KeyedCodec<>("Hide", Hide.CODEC, false),
                    (o, v) -> o.hide = v, o -> o.hide, (o, p) -> o.hide = p.hide).add()
            .appendInherited(new KeyedCodec<>("Look", Look.CODEC, false),
                    (o, v) -> o.look = v, o -> o.look, (o, p) -> o.look = p.look).add()
            .appendInherited(new KeyedCodec<>("Offset", Offset.CODEC, false),
                    (o, v) -> o.offset = v, o -> o.offset, (o, p) -> o.offset = p.offset).add()
            .appendInherited(new KeyedCodec<>("Yaw", Codec.DOUBLE, false),
                    (o, v) -> o.yaw = v, o -> o.yaw, (o, p) -> o.yaw = p.yaw).add()
            .appendInherited(new KeyedCodec<>("Prop", Prop.CODEC, false),
                    (o, v) -> o.prop = v, o -> o.prop, (o, p) -> o.prop = p.prop).add()
            .build();

    public Puppet() {
    }

    /** Java-side factory; sets the same fields the codec fills. */
    @Nonnull
    public static Puppet of(@Nullable Boolean enabled, @Nullable Hide hide, @Nullable Look look,
            @Nullable Offset offset, @Nullable Double yaw, @Nullable Prop prop) {
        Puppet p = new Puppet();
        p.enabled = enabled;
        p.hide = hide;
        p.look = look;
        p.offset = offset;
        p.yaw = yaw;
        p.prop = prop;
        return p;
    }

    @Nullable
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * {@link #enabled}, reader-defaulted to {@code true} when null (design 3.2): presence of the
     * {@code Puppet} group plus a non-{@code false} {@link #enabled} activates the whole route -
     * spawn AND hide together. {@code false} means the classic in-body worker, regardless of what
     * {@link #hide}/{@link #look} author (native {@code Parent} inheritance can flip a puppet OFF
     * on a child station while inheriting the rest of the group unchanged).
     */
    public boolean effectiveEnabled() {
        return enabled == null || enabled;
    }

    @Nullable
    public Hide getHide() {
        return hide;
    }

    @Nullable
    public Look getLook() {
        return look;
    }

    @Nullable
    public Offset getOffset() {
        return offset;
    }

    @Nullable
    public Double getYaw() {
        return yaw;
    }

    /** {@link #yaw} (degrees, world-space), reader-defaulted to {@code 0.0} when null. */
    public double effectiveYawDegrees() {
        return yaw != null ? yaw : 0.0;
    }

    @Nullable
    public Prop getProp() {
        return prop;
    }

    /**
     * The hide-route knob (design 3.3): {@link #route} is a UNION DISCRIMINATOR (not a mode - the
     * three arms route to structurally different mechanisms, the same shape as {@code
     * StationAsset.Hold.Mount.Surface}), round-4-narrowed to the two IN-GAME-PROVEN/reserved arms
     * plus the degraded fallback. {@link #effectId} is read ONLY by the future {@code "Effect"}
     * route (schema-reserved, unimplemented this leg - the shadowstep pointer: the MMO's dash
     * ability applies the native {@code Portal_Teleport} {@code EntityEffect} (1000ms
     * {@code TargetCaster}), which visually hides a player; a future station could author an
     * effect id shaped like it here instead of the {@code "Scale"} mechanism).
     */
    public static final class Hide {
        @Nullable protected String route;
        @Nullable protected String effectId;

        public static final BuilderCodec<Hide> CODEC = BuilderCodec.builder(Hide.class, Hide::new)
                .appendInherited(new KeyedCodec<>("Route", Codec.STRING, false),
                        (o, v) -> o.route = v, o -> o.route, (o, p) -> o.route = p.route).add()
                .appendInherited(new KeyedCodec<>("EffectId", Codec.STRING, false),
                        (o, v) -> o.effectId = v, o -> o.effectId, (o, p) -> o.effectId = p.effectId).add()
                .build();

        @Nonnull
        public static Hide of(@Nullable String route, @Nullable String effectId) {
            Hide h = new Hide();
            h.route = route;
            h.effectId = effectId;
            return h;
        }

        @Nullable
        public String getRoute() {
            return route;
        }

        @Nullable
        public String getEffectId() {
            return effectId;
        }

        /**
         * {@link #route}, reader-defaulted (and typo-tolerant, case-insensitive) to
         * {@link #HIDE_ROUTE_SCALE} - the in-game-crowned default - for anything other than an
         * exact {@link #HIDE_ROUTE_EFFECT}/{@link #HIDE_ROUTE_NONE} match.
         */
        @Nonnull
        public String effectiveRoute() {
            if (HIDE_ROUTE_EFFECT.equalsIgnoreCase(route)) {
                return HIDE_ROUTE_EFFECT;
            }
            if (HIDE_ROUTE_NONE.equalsIgnoreCase(route)) {
                return HIDE_ROUTE_NONE;
            }
            return HIDE_ROUTE_SCALE;
        }
    }

    /**
     * The puppet-appearance knob (design 3.4, seam wave decision 47's FULL nesting symmetry):
     * {@link #source} is a UNION DISCRIMINATOR defaulting to {@link #LOOK_SOURCE_PLAYER_CLONE} (the
     * puppet clones the live player skin) with THREE arms - {@link #LOOK_SOURCE_PLAYER_CLONE},
     * {@link #LOOK_SOURCE_MODEL} (a fixed authored model), and {@link #LOOK_SOURCE_NPC_ROLE} (the
     * Role-driven NPCEntity performer backend). The seam stays open: a FUTURE arm (a provider-
     * registered look) extends this union without a schema break - a new consumer only teaches the
     * engine-side resolver a new {@link #source} string.
     *
     * <p><b>Nesting symmetry (decision 47):</b> the flat {@code ModelId}/{@code FallbackModelId}
     * leaves retro-nest into the cohesive {@link Model} group (read only when {@link #source} is
     * {@link #LOOK_SOURCE_MODEL}); the NpcRole arm's own configuration is the parallel {@link Role}
     * group (read only when {@link #source} is {@link #LOOK_SOURCE_NPC_ROLE}). Each arm's group is
     * ignored by the other arms, orthogonal knobs never a mode. {@link Model#getFallbackModelId()}
     * doubles as the resolution-ladder fallback for the whole Look (any-source: an unresolvable
     * primary look -&gt; {@code FallbackModelId} -&gt; the engine's default rig, never a red-X).
     */
    public static final class Look {
        @Nullable protected String source;
        @Nullable protected Model model;
        @Nullable protected Role role;

        public static final BuilderCodec<Look> CODEC = BuilderCodec.builder(Look.class, Look::new)
                .appendInherited(new KeyedCodec<>("Source", Codec.STRING, false),
                        (o, v) -> o.source = v, o -> o.source, (o, p) -> o.source = p.source)
                .documentation("The puppet appearance discriminator: 'PlayerClone' (default), 'Model' (Look.Model), or 'NpcRole' (Look.Role).").add()
                .appendInherited(new KeyedCodec<>("Model", Model.CODEC, false),
                        (o, v) -> o.model = v, o -> o.model, (o, p) -> o.model = p.model)
                .documentation("The fixed-model appearance group (read only when Source is 'Model'); also carries the any-source FallbackModelId.").add()
                .appendInherited(new KeyedCodec<>("Role", Role.CODEC, false),
                        (o, v) -> o.role = v, o -> o.role, (o, p) -> o.role = p.role)
                .documentation("The NpcRole performer configuration group (read only when Source is 'NpcRole').").add()
                .build();

        @Nonnull
        public static Look of(@Nullable String source, @Nullable Model model, @Nullable Role role) {
            Look l = new Look();
            l.source = source;
            l.model = model;
            l.role = role;
            return l;
        }

        /** Convenience: a fixed-model look ({@code Source: "Model"} + a {@link Model} group). */
        @Nonnull
        public static Look model(@Nullable String modelId, @Nullable String fallbackModelId) {
            return of(LOOK_SOURCE_MODEL, Model.of(modelId, fallbackModelId), null);
        }

        @Nullable
        public String getSource() {
            return source;
        }

        /** The fixed-model appearance group; null = none authored (read only when {@link #effectiveSource()} is {@link #LOOK_SOURCE_MODEL}). */
        @Nullable
        public Model getModel() {
            return model;
        }

        /** The NpcRole performer group; null = none authored (read only when {@link #effectiveSource()} is {@link #LOOK_SOURCE_NPC_ROLE}). */
        @Nullable
        public Role getRole() {
            return role;
        }

        /**
         * {@link #source}, reader-defaulted (case-insensitive) to {@link #LOOK_SOURCE_PLAYER_CLONE}
         * for anything other than an exact {@link #LOOK_SOURCE_MODEL}/{@link #LOOK_SOURCE_NPC_ROLE}
         * match.
         */
        @Nonnull
        public String effectiveSource() {
            if (LOOK_SOURCE_MODEL.equalsIgnoreCase(source)) {
                return LOOK_SOURCE_MODEL;
            }
            if (LOOK_SOURCE_NPC_ROLE.equalsIgnoreCase(source)) {
                return LOOK_SOURCE_NPC_ROLE;
            }
            return LOOK_SOURCE_PLAYER_CLONE;
        }
    }

    /**
     * The fixed-model appearance group (decision 47's retro-nest of the old flat {@code ModelId}/
     * {@code FallbackModelId} leaves). {@link #modelId} backs {@link #LOOK_SOURCE_MODEL} (a fixed
     * authored look, e.g. an apprentice/golem regardless of who works); {@link #fallbackModelId} is
     * the resolution-ladder fallback for the WHOLE Look (any source) when the primary look is
     * unreadable/unresolvable ({@code PlayerClone} clone fails, a dangling {@code ModelId} or role,
     * ...) - the ladder ends at the engine's default rig, never a red-X or a crash.
     */
    public static final class Model {
        @Nullable protected String modelId;
        @Nullable protected String fallbackModelId;

        public static final BuilderCodec<Model> CODEC = BuilderCodec.builder(Model.class, Model::new)
                .appendInherited(new KeyedCodec<>("ModelId", Codec.STRING, false),
                        (o, v) -> o.modelId = v, o -> o.modelId, (o, p) -> o.modelId = p.modelId)
                .documentation("The fixed model asset id used when Look.Source is 'Model'.").add()
                .appendInherited(new KeyedCodec<>("FallbackModelId", Codec.STRING, false),
                        (o, v) -> o.fallbackModelId = v, o -> o.fallbackModelId,
                        (o, p) -> o.fallbackModelId = p.fallbackModelId)
                .documentation("The any-source resolution-ladder fallback model id when the primary look is unresolvable.").add()
                .build();

        @Nonnull
        public static Model of(@Nullable String modelId, @Nullable String fallbackModelId) {
            Model m = new Model();
            m.modelId = modelId;
            m.fallbackModelId = fallbackModelId;
            return m;
        }

        @Nullable
        public String getModelId() {
            return modelId;
        }

        @Nullable
        public String getFallbackModelId() {
            return fallbackModelId;
        }
    }

    /**
     * The NpcRole performer configuration group (seam wave, decision 47/48; design
     * {@code raw/rpg-stations-look-source-performer-seam-2026-07-24.md} section 1.2), read ONLY when
     * {@link Look#effectiveSource()} is {@link #LOOK_SOURCE_NPC_ROLE}. Every leaf is an independently
     * nullable orthogonal knob (no leaf bundles another's behavior):
     *
     * <ul>
     *   <li>{@link #roleId} - the native Role asset id backing this performer
     *   ({@code Server/NPC/Roles/<RoleId>.json}, id-ref-only; required when {@code Source:"NpcRole"};
     *   a dangling id is a validator finding and engage falls back to the bare-Holder performer).
     *   <li>{@link #skinSource} - a sub-union ({@link #SKIN_SOURCE_PLAYER_CLONE} default |
     *   {@link #SKIN_SOURCE_ROLE_DEFAULT}) picking whose appearance the Role NPC wears: a cloned live
     *   player skin, or the role asset's own default model.
     *   <li>{@link #persist} - {@code false} (default) marks the NPC {@code NonSerialized} at spawn
     *   (the transient posture, matching the bare Holder); {@code true} reserves the future
     *   persistent-hireling posture (the identity component reconcile seam).
     *   <li>{@link #speedMps} - the performer walk-speed override; null defers to the role asset's own
     *   {@code MotionControllerWalk.MaxWalkSpeed} (bare-Holder parity default 2.5 m/s).
     * </ul>
     */
    public static final class Role {
        /** The bare-Holder-parity walk speed (m/s) documented when neither this leaf nor the role asset overrides it. */
        public static final double DEFAULT_SPEED_MPS = 2.5;

        @Nullable protected String roleId;
        @Nullable protected String skinSource;
        @Nullable protected Boolean persist;
        @Nullable protected Double speedMps;

        public static final BuilderCodec<Role> CODEC = BuilderCodec.builder(Role.class, Role::new)
                .appendInherited(new KeyedCodec<>("RoleId", Codec.STRING, false),
                        (o, v) -> o.roleId = v, o -> o.roleId, (o, p) -> o.roleId = p.roleId)
                .documentation("The native Role asset id backing this performer (id-ref-only; required when Source is 'NpcRole').").add()
                .appendInherited(new KeyedCodec<>("SkinSource", Codec.STRING, false),
                        (o, v) -> o.skinSource = v, o -> o.skinSource, (o, p) -> o.skinSource = p.skinSource)
                .documentation("Whose appearance the Role NPC wears: 'PlayerClone' (default, cloned live player skin) or 'RoleDefault' (the role's own model).").add()
                .appendInherited(new KeyedCodec<>("Persist", Codec.BOOLEAN, false),
                        (o, v) -> o.persist = v, o -> o.persist, (o, p) -> o.persist = p.persist)
                .documentation("false (default) spawns the performer NonSerialized (transient); true reserves the persistent-hireling posture.").add()
                .appendInherited(new KeyedCodec<>("SpeedMps", Codec.DOUBLE, false),
                        (o, v) -> o.speedMps = v, o -> o.speedMps, (o, p) -> o.speedMps = p.speedMps)
                .documentation("Performer walk-speed override (m/s); null defers to the role asset's own MotionControllerWalk speed.").add()
                .build();

        @Nonnull
        public static Role of(@Nullable String roleId, @Nullable String skinSource, @Nullable Boolean persist,
                @Nullable Double speedMps) {
            Role r = new Role();
            r.roleId = roleId;
            r.skinSource = skinSource;
            r.persist = persist;
            r.speedMps = speedMps;
            return r;
        }

        @Nullable
        public String getRoleId() {
            return roleId;
        }

        @Nullable
        public String getSkinSource() {
            return skinSource;
        }

        @Nullable
        public Boolean getPersist() {
            return persist;
        }

        @Nullable
        public Double getSpeedMps() {
            return speedMps;
        }

        /** True when {@link #roleId} is authored (a non-blank native Role id). */
        public boolean hasRoleId() {
            return roleId != null && !roleId.isBlank();
        }

        /** {@link #skinSource}, reader-defaulted (case-insensitive) to {@link #SKIN_SOURCE_PLAYER_CLONE}. */
        @Nonnull
        public String effectiveSkinSource() {
            return SKIN_SOURCE_ROLE_DEFAULT.equalsIgnoreCase(skinSource)
                    ? SKIN_SOURCE_ROLE_DEFAULT : SKIN_SOURCE_PLAYER_CLONE;
        }

        /** {@link #persist}, reader-defaulted to {@code false} (the transient NonSerialized posture) when null. */
        public boolean effectivePersist() {
            return persist != null && persist;
        }
    }

    /** A relative {@code X}/{@code Y}/{@code Z} shift off the station's block-top anchor, each leaf independently nullable (default 0). */
    public static final class Offset {
        @Nullable protected Double x;
        @Nullable protected Double y;
        @Nullable protected Double z;

        public static final BuilderCodec<Offset> CODEC = BuilderCodec.builder(Offset.class, Offset::new)
                .appendInherited(new KeyedCodec<>("X", Codec.DOUBLE, false),
                        (o, v) -> o.x = v, o -> o.x, (o, p) -> o.x = p.x).add()
                .appendInherited(new KeyedCodec<>("Y", Codec.DOUBLE, false),
                        (o, v) -> o.y = v, o -> o.y, (o, p) -> o.y = p.y).add()
                .appendInherited(new KeyedCodec<>("Z", Codec.DOUBLE, false),
                        (o, v) -> o.z = v, o -> o.z, (o, p) -> o.z = p.z).add()
                .build();

        @Nonnull
        public static Offset of(@Nullable Double x, @Nullable Double y, @Nullable Double z) {
            Offset o = new Offset();
            o.x = x;
            o.y = y;
            o.z = z;
            return o;
        }

        @Nullable
        public Double getX() {
            return x;
        }

        @Nullable
        public Double getY() {
            return y;
        }

        @Nullable
        public Double getZ() {
            return z;
        }
    }

    /**
     * The held-prop knob (design 3.6, DRY over {@code Animation} - the work CLIP is NOT
     * re-declared here, only the prop): {@link #source} is a UNION DISCRIMINATOR defaulting to
     * {@link #PROP_SOURCE_MIRROR_HELD} (the puppet holds a copy of the player's live hotbar item).
     * {@link #itemId} backs {@link #PROP_SOURCE_ITEM_ID} (a forced prop, e.g. a hammer a ritual
     * hands the puppet regardless of what the player holds); {@link #slot} picks main
     * ({@link #PROP_SLOT_HOTBAR}, default) vs off-hand ({@link #PROP_SLOT_UTILITY}). This EXACT
     * shape is reused verbatim by {@code StationStep.PuppetOverride#getProp()} for a per-step prop
     * swap (e.g. a glowing bar for the quench step) - never duplicated.
     */
    public static final class Prop {
        @Nullable protected String source;
        @Nullable protected String itemId;
        @Nullable protected String slot;

        public static final BuilderCodec<Prop> CODEC = BuilderCodec.builder(Prop.class, Prop::new)
                .appendInherited(new KeyedCodec<>("Source", Codec.STRING, false),
                        (o, v) -> o.source = v, o -> o.source, (o, p) -> o.source = p.source).add()
                .appendInherited(new KeyedCodec<>("ItemId", Codec.STRING, false),
                        (o, v) -> o.itemId = v, o -> o.itemId, (o, p) -> o.itemId = p.itemId).add()
                .appendInherited(new KeyedCodec<>("Slot", Codec.STRING, false),
                        (o, v) -> o.slot = v, o -> o.slot, (o, p) -> o.slot = p.slot).add()
                .build();

        @Nonnull
        public static Prop of(@Nullable String source, @Nullable String itemId, @Nullable String slot) {
            Prop p = new Prop();
            p.source = source;
            p.itemId = itemId;
            p.slot = slot;
            return p;
        }

        @Nullable
        public String getSource() {
            return source;
        }

        @Nullable
        public String getItemId() {
            return itemId;
        }

        @Nullable
        public String getSlot() {
            return slot;
        }

        /** {@link #source}, reader-defaulted (case-insensitive) to {@link #PROP_SOURCE_MIRROR_HELD}. */
        @Nonnull
        public String effectiveSource() {
            if (PROP_SOURCE_ITEM_ID.equalsIgnoreCase(source)) {
                return PROP_SOURCE_ITEM_ID;
            }
            if (PROP_SOURCE_NONE.equalsIgnoreCase(source)) {
                return PROP_SOURCE_NONE;
            }
            return PROP_SOURCE_MIRROR_HELD;
        }

        /** {@link #slot}, reader-defaulted (case-insensitive) to {@link #PROP_SLOT_HOTBAR}. */
        @Nonnull
        public String effectiveSlot() {
            return PROP_SLOT_UTILITY.equalsIgnoreCase(slot) ? PROP_SLOT_UTILITY : PROP_SLOT_HOTBAR;
        }
    }
}
