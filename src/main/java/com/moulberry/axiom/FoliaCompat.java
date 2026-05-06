package com.moulberry.axiom;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Compatibility layer for Paper / Folia.
 * Detects Folia at runtime and delegates scheduling / player actions accordingly.
 * All Folia-specific calls go through reflection so the code compiles against Paper only.
 */
public final class FoliaCompat {

    private FoliaCompat() {}

    private static final boolean FOLIA;
    private static final Method GLOBAL_TICK_THREAD_METHOD; // RegionizedServer.isGlobalTickThread()

    static {
        boolean folia = false;
        Method isGlobalTickThread = null;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
            isGlobalTickThread = Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
                .getMethod("isGlobalTickThread");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // Not Folia
        }
        FOLIA = folia;
        GLOBAL_TICK_THREAD_METHOD = isGlobalTickThread;
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    // ── Scheduling ──────────────────────────────────────────────────────

    /**
     * Schedule a repeating task that runs on the global region thread (Folia)
     * or the main thread (Paper). Equivalent to scheduleSyncRepeatingTask.
     */
    public static Object runGlobalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), delayTicks, periodTicks);
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    /**
     * Execute a task on the global region thread / main thread.
     */
    public static void executeGlobal(Plugin plugin, Runnable task) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Execute a task on the region that owns the given location.
     */
    public static void executeAtLocation(Plugin plugin, Location location, Runnable task) {
        if (FOLIA) {
            Bukkit.getRegionScheduler().execute(plugin, location, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Execute a task on the entity's region thread.
     */
    public static void executeForEntity(Entity entity, Plugin plugin, Runnable task, long delayTicks) {
        if (FOLIA) {
            if (delayTicks > 0) {
                entity.getScheduler().execute(plugin, task, null, delayTicks);
            } else {
                entity.getScheduler().execute(plugin, task, null, 1);
            }
        } else {
            if (delayTicks > 0) {
                Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }
    }

    // ── Player actions ──────────────────────────────────────────────────

    /**
     * Kick a player safely on their own region thread.
     */
    public static void kickPlayer(Player player, Component reason) {
        if (FOLIA) {
            player.getScheduler().execute(AxiomPaper.PLUGIN, () -> player.kick(reason), null, 1);
        } else {
            player.kick(reason);
        }
    }

    /**
     * Teleport a player safely on their own region thread.
     */
    public static void teleportPlayer(Player player, Location location) {
        if (FOLIA) {
            player.getScheduler().execute(AxiomPaper.PLUGIN, () -> player.teleport(location), null, 1);
        } else {
            player.teleport(location);
        }
    }

    // ── Utility ─────────────────────────────────────────────────────────

    /**
     * Pack chunk coordinates into a long key.
     * Replaces ChunkPos.pack(int, int) which was removed in MC 1.21.1+.
     */
    public static long chunkPosPack(int x, int z) {
        return (long) x & 4294967295L | ((long) z & 4294967295L) << 32;
    }

    /**
     * Unpack X from a packed chunk position long.
     * Replaces ChunkPos.unpackX(long) / ChunkPos.getX(long).
     */
    public static int chunkPosUnpackX(long packed) {
        return (int) packed;
    }

    /**
     * Unpack Z from a packed chunk position long.
     * Replaces ChunkPos.unpackZ(long) / ChunkPos.getZ(long).
     */
    public static int chunkPosUnpackZ(long packed) {
        return (int) (packed >> 32);
    }

    /**
     * Check if the current thread is the main thread (Paper) or the
     * global region thread (Folia).
     */
    public static boolean isGlobalThread() {
        if (FOLIA) {
            try {
                return (boolean) GLOBAL_TICK_THREAD_METHOD.invoke(null);
            } catch (Exception e) {
                return false;
            }
        }
        return Bukkit.isPrimaryThread();
    }
}
