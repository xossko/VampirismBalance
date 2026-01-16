package com.vladisss.vampirismbalance.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class TaglockUtil {

    /**
     * Читает UUID из taglock (формат Enchanted: {entity:[I;a,b,c,d]})
     */
    public static UUID readBoundUuid(ItemStack stack) {
        if (stack.isEmpty()) return null;

        CompoundTag tag = stack.getTag();
        if (tag == null) return null;

        // Enchanted формат: {entity:[I;...]}
        if (tag.contains("entity", 11)) { // 11 = TAG_INT_ARRAY
            int[] arr = tag.getIntArray("entity");
            if (arr.length == 4) {
                long msb = ((long) arr[0] << 32) | (arr[1] & 0xffffffffL);
                long lsb = ((long) arr[2] << 32) | (arr[3] & 0xffffffffL);
                return new UUID(msb, lsb);
            }
        }

        return null;
    }
}
