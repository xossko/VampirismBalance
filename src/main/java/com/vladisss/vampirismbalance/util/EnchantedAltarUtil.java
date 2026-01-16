package com.vladisss.vampirismbalance.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;

public class EnchantedAltarUtil {

    private static final String ALTAR_CLASS = "net.favouriteless.enchanted.common.blocks.entity.AltarBlockEntity";

    public static int findNearbyAltarPower(ServerLevel level, BlockPos center, int radius) {
        BlockEntity altar = findNearestAltar(level, center, radius);
        if (altar == null) {
            return -1;
        }

        Integer power = readPower(altar);
        return power != null ? power : -1;
    }

    public static BlockEntity findNearestAltar(ServerLevel level, BlockPos center, int radius) {
        double minDist = Double.MAX_VALUE;
        BlockEntity best = null;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {

            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) continue;
            if (!be.getClass().getName().equals(ALTAR_CLASS)) continue;

            double dist = pos.distSqr(center);
            if (dist < minDist) {
                minDist = dist;
                best = be;
            }
        }
        return best;
    }

    private static Integer readPower(BlockEntity altar) {
        // 1. Пробуем поле currentPower (DOUBLE!)
        try {
            Field f = altar.getClass().getDeclaredField("currentPower");
            f.setAccessible(true);
            Object val = f.get(altar);
            if (val instanceof Number) {
                return ((Number) val).intValue(); // Приводим к int
            }
        } catch (Exception ignored) {}

        // 2. Пробуем NBT (тип 6 = TAG_DOUBLE)
        try {
            CompoundTag tag = altar.saveWithFullMetadata();
            if (tag.contains("currentPower", 6)) { // 6 = TAG_DOUBLE
                return (int) tag.getDouble("currentPower");
            }
            if (tag.contains("currentPower", 3)) { // 3 = TAG_INT (fallback)
                return tag.getInt("currentPower");
            }
        } catch (Exception ignored) {}

        return null;
    }
    public static boolean consumePower(BlockEntity altar, int amount) {
        try {
            Field f = altar.getClass().getDeclaredField("currentPower");
            f.setAccessible(true);
            double current = ((Number) f.get(altar)).doubleValue();
            f.set(altar, current - amount);
            altar.setChanged(); // Помечаем для сохранения
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
