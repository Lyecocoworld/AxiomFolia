package com.moulberry.axiom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Wraps NMS LevelChunk methods that may be patched by Canvas/Folia.
 * Canvas removes the 'blockEntities' field from LevelChunk, so we need
 * to use reflection or alternative APIs to access block entity storage.
 */
public class AxiomReflection {

    private static Method updateBlockEntityTickerMethod = null;
    private static MethodHandle blockEntitiesGetter = null;

    public static void init() {
        try {
            updateBlockEntityTickerMethod = LevelChunk.class.getDeclaredMethod("updateBlockEntityTicker", BlockEntity.class);
            updateBlockEntityTickerMethod.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        // Try to find the blockEntities field or getBlockEntities() getter
        try {
            // First try: getBlockEntities() method (exists in all versions)
            Method getter = LevelChunk.class.getDeclaredMethod("getBlockEntities");
            blockEntitiesGetter = MethodHandles.publicLookup().unreflect(getter);
        } catch (Exception e) {
            try {
                // Fallback: direct field access (pre-1.21.1)
                Field field = LevelChunk.class.getDeclaredField("blockEntities");
                field.setAccessible(true);
                blockEntitiesGetter = MethodHandles.lookup().unreflectGetter(field);
            } catch (Exception e2) {
                throw new RuntimeException("Cannot find blockEntities field or getBlockEntities() method", e2);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<BlockPos, BlockEntity> getBlockEntities(LevelChunk chunk) {
        try {
            return (Map<BlockPos, BlockEntity>) blockEntitiesGetter.invoke(chunk);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void updateBlockEntityTicker(LevelChunk levelChunk, BlockEntity blockEntity) {
        try {
            updateBlockEntityTickerMethod.invoke(levelChunk, blockEntity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get a block entity from a chunk, handling Canvas's modified storage.
     */
    public static BlockEntity getBlockEntity(LevelChunk chunk, BlockPos pos) {
        return getBlockEntities(chunk).get(pos);
    }

    /**
     * Remove a block entity from a chunk, handling Canvas's modified storage.
     * Uses the NMS method if available, falls back to manual removal.
     */
    public static void removeBlockEntity(LevelChunk chunk, BlockPos pos) {
        try {
            // Try NMS method first - it handles all the internal bookkeeping
            chunk.removeBlockEntity(pos);
        } catch (NoSuchFieldError | AssertionError e) {
            // Canvas removed the blockEntities field - manual removal
            manualRemoveBlockEntity(chunk, pos);
        }
    }

    /**
     * Add and register a block entity, handling Canvas's modified storage.
     */
    public static void addAndRegisterBlockEntity(LevelChunk chunk, BlockEntity blockEntity) {
        try {
            chunk.addAndRegisterBlockEntity(blockEntity);
        } catch (NoSuchFieldError | AssertionError e) {
            // Canvas removed the blockEntities field - manual add
            manualAddBlockEntity(chunk, blockEntity);
        }
    }

    private static void manualRemoveBlockEntity(LevelChunk chunk, BlockPos pos) {
        Map<BlockPos, BlockEntity> entities = getBlockEntities(chunk);
        BlockEntity existing = entities.remove(pos);
        if (existing != null) {
            existing.setRemoved();
        }
    }

    private static void manualAddBlockEntity(LevelChunk chunk, BlockEntity blockEntity) {
        Map<BlockPos, BlockEntity> entities = getBlockEntities(chunk);
        entities.put(blockEntity.getBlockPos(), blockEntity);
        // Trigger the level to recognize the block entity
        if (chunk.level instanceof ServerLevel serverLevel) {
            serverLevel.onBlockEntityAdded(blockEntity);
        }
        updateBlockEntityTicker(chunk, blockEntity);
    }
}
