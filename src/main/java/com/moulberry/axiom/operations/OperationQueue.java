package com.moulberry.axiom.operations;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.FoliaCompat;
import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OperationQueue {

    private final Lock queueLock = new ReentrantLock();
    private final Map<ServerLevel, List<PendingOperation>> newPendingOperations = new HashMap<>();
    private final Lock executionLock = new ReentrantLock();
    private final Map<ServerLevel, List<PendingOperation>> pendingOperations = new HashMap<>();

    public void tick() {
        // Merge new operations into pending (safe on any thread)
        this.queueLock.lock();
        try {
            for (Map.Entry<ServerLevel, List<PendingOperation>> entry : this.newPendingOperations.entrySet()) {
                List<PendingOperation> currentOperations = this.pendingOperations.get(entry.getKey());
                if (currentOperations != null) {
                    currentOperations.addAll(entry.getValue());
                } else {
                    this.pendingOperations.put(entry.getKey(), entry.getValue());
                }
            }
            this.newPendingOperations.clear();
        } finally {
            this.queueLock.unlock();
        }

        if (FoliaCompat.isFolia()) {
            tickFolia();
        } else {
            tickPaper();
        }
    }

    private void tickPaper() {
        if (!Bukkit.isPrimaryThread()) {
            throw new WrongThreadException();
        }

        this.executionLock.lock();
        try {
            processAllPending();
        } finally {
            this.executionLock.unlock();
        }
    }

    private void tickFolia() {
        // On Folia, we MUST dispatch per-world operations to region threads.
        // getCurrentWorldData() returns null on the global thread.
        var worldIterator = this.pendingOperations.entrySet().iterator();
        while (worldIterator.hasNext()) {
            var perWorld = worldIterator.next();
            ServerLevel level = perWorld.getKey();
            List<PendingOperation> operations = perWorld.getValue();

            if (operations.isEmpty()) {
                worldIterator.remove();
                continue;
            }

            // Use the first operation's executor position for region targeting
            PendingOperation firstOp = operations.get(0);
            int chunkX = firstOp.executor().getBlockX() >> 4;
            int chunkZ = firstOp.executor().getBlockZ() >> 4;

            // Schedule on the region thread that owns this chunk
            Bukkit.getRegionScheduler().execute(AxiomPaper.PLUGIN, level.getWorld(), chunkX, chunkZ,
                () -> processOperationsForLevel(level, operations));
        }
    }

    private void processOperationsForLevel(ServerLevel level, List<PendingOperation> operations) {
        var iterator = operations.iterator();
        while (iterator.hasNext()) {
            PendingOperation op = iterator.next();
            try {
                op.tick(level);
                if (op.isFinished()) {
                    iterator.remove();
                } else {
                    break;
                }
            } catch (Throwable t) {
                FoliaCompat.kickPlayer(op.executor().getBukkitEntity(),
                    Component.text("An error occurred while processing operation: " + t.getMessage()));
                iterator.remove();
            }
        }
    }

    private void processAllPending() {
        var worldIterator = this.pendingOperations.entrySet().iterator();
        while (worldIterator.hasNext()) {
            var perWorldOperations = worldIterator.next();
            var perWorldIterator = perWorldOperations.getValue().iterator();

            while (perWorldIterator.hasNext()) {
                PendingOperation operation = perWorldIterator.next();
                try {
                    operation.tick(perWorldOperations.getKey());
                    if (operation.isFinished()) {
                        perWorldIterator.remove();
                    } else {
                        break;
                    }
                } catch (Throwable t) {
                    ServerPlayer executor = operation.executor();
                    FoliaCompat.kickPlayer(executor.getBukkitEntity(),
                        Component.text("An error occurred while processing operation: " + t.getMessage()));
                    perWorldIterator.remove();
                }
            }

            if (perWorldOperations.getValue().isEmpty()) {
                worldIterator.remove();
            }
        }
    }

    public void add(ServerLevel level, PendingOperation operation) {
        this.queueLock.lock();
        try {
            List<PendingOperation> operations = this.newPendingOperations.computeIfAbsent(level, k -> new ArrayList<>());

            if (operations.isEmpty() && !FoliaCompat.isFolia() && Bukkit.isPrimaryThread() && this.executionLock.tryLock()) {
                try {
                    var currentOperations = this.pendingOperations.get(level);
                    if (currentOperations == null || currentOperations.isEmpty()) {
                        operation.tick(level);
                        if (operation.isFinished()) {
                            return;
                        }
                    }
                } finally {
                    this.executionLock.unlock();
                }
            }

            operations.add(operation);
        } finally {
            this.queueLock.unlock();
        }
    }

}
