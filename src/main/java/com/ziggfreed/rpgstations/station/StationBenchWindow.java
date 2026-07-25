package com.ziggfreed.rpgstations.station;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.builtin.crafting.window.BenchWindow;
import com.hypixel.hytale.builtin.crafting.window.ProcessingBenchWindow;
import com.hypixel.hytale.builtin.crafting.window.SimpleCraftingWindow;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Opens the NATIVE crafting/processing bench WINDOW at a station block, from our own Java
 * (selection wave, decision 51a - the maintainer's "reuse the native crafting UI" amendment). A
 * station whose block authors a native {@code BlockType.Bench} identity (open {@code Bench.Id};
 * recipes scope to it by {@code BenchRequirement} string match) opens the vanilla window on
 * SNEAK+F, while plain F keeps the diegetic work loop - the two coexist on one block, per the
 * ledger's bench entry (a custom {@code rpg_station_use} on Use forgoes only the automatic
 * bench-open convenience; our own Java opens the SAME native window at any point).
 *
 * <p><b>Faithful mirror of the first-party openers</b> ({@code builtin.crafting.interaction
 * .OpenBenchPageInteraction} for Crafting, {@code OpenProcessingBenchInteraction} for Processing):
 * fetch the per-block chunk-store {@code BenchBlock} (and, for Processing, {@code
 * ProcessingBenchBlock} + {@code BlockStateInfo}), build the matching {@code BenchWindow}, register
 * it into {@code BenchBlock.getWindows()} keyed by the player uuid with a close-event removal, and
 * open it via {@code PageManager.setPageWithWindows(ref, store, Page.Bench, true, window)}.
 *
 * <p><b>Defensive on every degradation path, returns {@code false} (never throws) so the caller
 * falls through to the plain diegetic toggle:</b>
 * <ul>
 *   <li>no {@code BlockType.Bench} on the block (the common case - the Sawmill/CuttingBoard/MountSpike
 *   blocks are plain diegetic stations, no {@code Bench} identity);</li>
 *   <li>{@code BlockType.getItem() == null} - the {@code BenchWindow} ctor THROWS on a bench block
 *   with no associated item ({@code BenchWindow.java}'s explicit guard), pre-checked here;</li>
 *   <li>the block carries no {@code BenchBlock} chunk-store component (or, for Processing, no
 *   {@code ProcessingBenchBlock}) - the block declares a {@code Bench} identity but not the
 *   per-block state components the window needs;</li>
 *   <li>a {@code DiagramCrafting}/{@code StructuralCrafting} bench type - out of scope for a
 *   diegetic station (only {@code Crafting} + {@code Processing} route to a window here).</li>
 * </ul>
 *
 * <p>LIVE: the CookingFire block authors a native {@code BlockType.Bench{Type:Processing,
 * Id:Campfire}} (plus the {@code BenchBlock}/{@code ProcessingBenchBlock} components), so a sneak+F
 * on it opens this native fuel-less Processing window (the classic non-bench stations still fall
 * through the first bullet to the diegetic toggle). All ECS access here is READ-ONLY component
 * fetches plus the native window registration the first-party openers themselves run inside an
 * interaction context, so it is safe from {@code StationUseInteraction#firstRun}'s own
 * command-buffer context.
 */
final class StationBenchWindow {

    private StationBenchWindow() {
    }

    /**
     * Attempt to open the native bench window at {@code (x,y,z)}. Returns {@code true} iff a window
     * was opened (the caller must NOT fall through to the diegetic toggle then); {@code false} on
     * every not-a-bench / degradation path (the caller falls through). Never throws.
     */
    static boolean open(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
            @Nonnull Player player, @Nonnull UUID playerUuid, int x, int y, int z) {
        try {
            BlockType blockType = world.getBlockType(x, y, z);
            if (blockType == null) {
                return false;
            }
            Bench bench = blockType.getBench();
            if (bench == null) {
                return false; // not a bench-backed station block (the common case, dormant)
            }
            // Pre-check the BenchWindow ctor's own throw: a bench BlockType with no associated Item
            // hard-fails in the ctor - degrade to the diegetic loop rather than crash the press.
            if (blockType.getItem() == null) {
                Log.warn("STATION bench block '" + blockType.getId()
                        + "' has no associated item; cannot open native window, falling through to work loop");
                return false;
            }
            BenchType type = bench.getType();
            if (type != BenchType.Crafting && type != BenchType.Processing) {
                Log.fine("STATION bench type " + type + " not supported for a station window; falling through");
                return false;
            }

            // Per-block chunk-store components (the exact first-party lookup chain).
            var chunkStore = world.getChunkStore();
            var chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(x, z));
            if (chunkRef == null || !chunkRef.isValid()) {
                return false;
            }
            var chunkStoreStore = chunkStore.getStore();
            BlockComponentChunk blockComponentChunk =
                    chunkStoreStore.getComponent(chunkRef, BlockComponentChunk.getComponentType());
            if (blockComponentChunk == null) {
                return false;
            }
            var blockEntityRef = blockComponentChunk.getEntityReference(ChunkUtil.indexBlockInColumn(x, y, z));
            if (blockEntityRef == null || !blockEntityRef.isValid()) {
                return false;
            }
            BenchBlock benchBlock = chunkStoreStore.getComponent(blockEntityRef, BenchBlock.getComponentType());
            if (benchBlock == null) {
                Log.fine("STATION bench block at " + x + "," + y + "," + z
                        + " has no BenchBlock component; falling through to work loop");
                return false;
            }
            // Non-deprecated rotation read (WorldChunk#getRotationIndex is @Deprecated(forRemoval);
            // World#getBlockRotationIndex is its replacement, the same read StationCustodyDisplay uses).
            int rotationIndex = world.getBlockRotationIndex(x, y, z);

            BenchWindow window;
            ProcessingBenchBlock benchState = null;
            if (type == BenchType.Processing) {
                benchState = chunkStoreStore.getComponent(blockEntityRef, ProcessingBenchBlock.getComponentType());
                if (benchState == null) {
                    Log.fine("STATION processing bench at " + x + "," + y + "," + z
                            + " has no ProcessingBenchBlock component; falling through");
                    return false;
                }
                BlockModule.BlockStateInfo blockStateInfo =
                        chunkStoreStore.getComponent(blockEntityRef, BlockModule.BlockStateInfo.getComponentType());
                window = new ProcessingBenchWindow(benchState, benchBlock, blockStateInfo, x, y, z,
                        rotationIndex, blockType);
            } else {
                window = new SimpleCraftingWindow(x, y, z, rotationIndex, blockType, benchBlock);
            }

            Map<UUID, BenchWindow> windows = benchBlock.getWindows();
            BenchWindow made = window;
            if (windows.putIfAbsent(playerUuid, made) != null) {
                // A window for this player is already registered at this block (double-press) - the
                // native openers no-op the same way; nothing to open, but do not fall through to a
                // work-loop engage on top of an open window.
                return true;
            }
            window.registerCloseEvent(event -> windows.remove(playerUuid, made));
            if (benchState != null) {
                // Mirror the first-party OpenProcessingBenchInteraction: refresh fuel state against
                // the now-registered window set before the page opens.
                benchState.updateFuelValues(windows);
            }
            if (!player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window)) {
                windows.remove(playerUuid, made);
                return false;
            }
            return true;
        } catch (Throwable t) {
            Log.warn("STATION native bench-window open failed: " + t.getMessage());
            return false;
        }
    }
}
