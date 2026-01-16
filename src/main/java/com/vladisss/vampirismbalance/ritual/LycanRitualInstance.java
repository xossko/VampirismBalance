package com.vladisss.vampirismbalance.ritual;

import com.vladisss.vampirismbalance.util.WerewolvesCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class LycanRitualInstance {
    private final ServerLevel level;
    private final BlockPos center;
    private final Player player;
    private final ServerPlayer target;
    private final RitualItems items;
    private final int[][] circle;

    private int currentStep = 0;
    private int ticksSinceLastStep = 0;
    private final int TICKS_PER_STEP = 20; // 1 секунда между шагами

    public LycanRitualInstance(ServerLevel level, BlockPos center, Player player,
                               ServerPlayer target, RitualItems items, int[][] circle) {
        this.level = level;
        this.center = center;
        this.player = player;
        this.target = target;
        this.items = items;
        this.circle = circle;
    }

    // Возвращает true, если ритуал завершен
    public boolean tick() {
        ticksSinceLastStep++;

        if (ticksSinceLastStep < TICKS_PER_STEP) {
            return false;
        }

        ticksSinceLastStep = 0;
        currentStep++;

        switch (currentStep) {
            case 1: step0_Start(); return false;
            case 2: step1_Stone(); return false;           // ← НОВЫЙ ШАГ
            case 3: step2_TongueOfDog(); return false;     // ← переименован
            case 4: step3_Exhale(); return false;          // ← переименован
            case 5: step4_Wolfsbane(); return false;       // ← переименован
            case 6: step5_DemonicBlood(); return false;    // ← переименован
            case 7: step6_WerewolfTooth(); return false;   // ← переименован
            case 8: step7_Final(); return true;            // ← переименован
            default: return true;
        }
    }

    // НОВЫЙ метод для камня
    private void step1_Stone() {
        if (items.stoneEntity != null && !items.stoneEntity.isRemoved()) {
            BlockPos pos = items.stoneEntity.blockPosition();
            level.sendParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                    25, 0.2, 0.3, 0.2, 0.05);
            items.stoneEntity.discard();
        }
        level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.BLOCKS, 1.0f, 1.5f);
    }

    private void step0_Start() {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5,
                20, 0.3, 0.3, 0.3, 0.03);
        level.playSound(null, center, SoundEvents.PORTAL_AMBIENT,
                SoundSource.BLOCKS, 1.0f, 0.8f);
    }

    private void step2_TongueOfDog() {
        int remaining = 10;
        for (ItemEntity entity : items.tongueOfDogEntities) {
            if (remaining <= 0) break;
            int toRemove = Math.min(remaining, entity.getItem().getCount());
            if (toRemove >= entity.getItem().getCount()) {
                BlockPos pos = entity.blockPosition();
                level.sendParticles(ParticleTypes.SMOKE,
                        pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                        15, 0.2, 0.3, 0.2, 0.02);
                entity.discard();
            } else {
                entity.getItem().shrink(toRemove);
            }
            remaining -= toRemove;
        }
        level.playSound(null, center, SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    private void step3_Exhale() {
        if (items.exhaleEntity != null && !items.exhaleEntity.isRemoved()) {
            BlockPos pos = items.exhaleEntity.blockPosition();
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                    20, 0.3, 0.4, 0.3, 0.01);
            items.exhaleEntity.discard();
        }
        level.playSound(null, center, SoundEvents.SOUL_ESCAPE,
                SoundSource.BLOCKS, 1.0f, 0.7f);
    }

    private void step4_Wolfsbane() {
        if (items.wolfsbaneEntity != null && !items.wolfsbaneEntity.isRemoved()) {
            BlockPos pos = items.wolfsbaneEntity.blockPosition();
            level.sendParticles(ParticleTypes.WITCH,
                    pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                    25, 0.4, 0.4, 0.4, 0.05);
            items.wolfsbaneEntity.discard();
        }
        level.playSound(null, center, SoundEvents.BREWING_STAND_BREW,
                SoundSource.BLOCKS, 1.0f, 1.5f);
    }

    private void step5_DemonicBlood() {
        if (items.demonicBloodEntity != null && !items.demonicBloodEntity.isRemoved()) {
            BlockPos pos = items.demonicBloodEntity.blockPosition();
            level.sendParticles(ParticleTypes.CRIMSON_SPORE,
                    pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                    30, 0.3, 0.5, 0.3, 0.04);
            items.demonicBloodEntity.discard();
        }
        level.playSound(null, center, SoundEvents.WITHER_HURT,
                SoundSource.BLOCKS, 0.7f, 0.9f);
    }

    private void step6_WerewolfTooth() {
        if (items.werewolfToothEntity != null && !items.werewolfToothEntity.isRemoved()) {
            BlockPos pos = items.werewolfToothEntity.blockPosition();
            level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                    35, 0.5, 0.6, 0.5, 0.03);
            items.werewolfToothEntity.discard();
        }
        level.playSound(null, center, SoundEvents.WOLF_GROWL,
                SoundSource.BLOCKS, 1.2f, 0.8f);
    }

    private void step7_Final() {
        for (int[] p : circle) {
            double x = center.getX() + 0.5 + p[0];
            double y = center.getY() + 0.05;
            double z = center.getZ() + 0.5 + p[1];
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z,
                    3, 0.1, 0.05, 0.1, 0.01);
        }
        level.sendParticles(ParticleTypes.FLASH,
                center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5,
                1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5,
                1, 0, 0, 0, 0);
        level.playSound(null, center, SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.BLOCKS, 1.0f, 1.2f);

        WerewolvesCompat.applyLycanthropyLikeBite(target, 20 * 60 * 10);

    }

    public static class RitualItems {
        public ItemEntity stoneEntity = null;  // ← ДОБАВЬТЕ ЭТО
        public List<ItemEntity> tongueOfDogEntities = new ArrayList<>();
        public ItemEntity exhaleEntity = null;
        public ItemEntity wolfsbaneEntity = null;
        public ItemEntity demonicBloodEntity = null;
        public ItemEntity werewolfToothEntity = null;
    }

}
