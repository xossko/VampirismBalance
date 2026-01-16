package com.vladisss.vampirismbalance.event;

import com.vladisss.vampirismbalance.util.EnchantedAltarUtil;
import com.vladisss.vampirismbalance.util.TaglockUtil;
import com.vladisss.vampirismbalance.util.WerewolvesCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import com.vladisss.vampirismbalance.ritual.LycanRitualInstance;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LycanRitualEventHandler {
    private static final List<LycanRitualInstance> ACTIVE_RITUALS = new ArrayList<>();
    private static final ResourceLocation BLOCK_GOLDEN = new ResourceLocation("enchanted", "golden_chalk");
    private static final ResourceLocation BLOCK_NETHER = new ResourceLocation("enchanted", "nether_chalk");
    private static final ResourceLocation ITEM_STONE = new ResourceLocation("enchanted", "attuned_stone_charged");
    private static final ResourceLocation ITEM_TAGLOCK = new ResourceLocation("enchanted", "taglock");

    // ============ ПРЕДМЕТЫ ДЛЯ РИТУАЛА ============
    private static final ResourceLocation ITEM_TONGUE_OF_DOG = new ResourceLocation("enchanted", "tongue_of_dog");
    private static final ResourceLocation ITEM_EXHALE = new ResourceLocation("enchanted", "exhale_of_the_horned_one");
    private static final ResourceLocation ITEM_WOLFSBANE = new ResourceLocation("enchanted", "wolfsbane_flower");
    private static final ResourceLocation ITEM_DEMONIC_BLOOD = new ResourceLocation("enchanted", "demonic_blood");
    private static final ResourceLocation ITEM_WEREWOLF_TOOTH = new ResourceLocation("werewolves", "werewolf_tooth");

    private static final int REQUIRED_POWER = 10_000;
    private static final int SEARCH_RADIUS = 50;
    private static final int STONE_MAX_AGE = 120;
    private static final double ITEM_SEARCH_RADIUS = 2.0; // радиус поиска предметов на земле

    private static final int[][] CIRCLE = {
            {-2,-6},{-1,-6},{0,-6},{1,-6},{2,-6},{-4,-5},{-3,-5},{3,-5},{4,-5},
            {-5,-4},{-4,-4},{4,-4},{5,-4},{-5,-3},{5,-3},{-6,-2},{6,-2},
            {-6,-1},{6,-1},{-6,0},{6,0},{-6,1},{6,1},{-6,2},{6,2},
            {-5,3},{5,3},{-5,4},{-4,4},{4,4},{5,4},{-4,5},{-3,5},
            {3,5},{4,5},{-2,6},{-1,6},{0,6},{1,6},{2,6}
    };

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide) return;

        ItemEntity ent = event.getEntity();
        ItemStack stack = ent.getItem();

        if (!isItem(stack, ITEM_STONE)) return;

        ent.getPersistentData().putString("vbl_thrower", player.getUUID().toString());
        ent.getPersistentData().putLong("vbl_tick", player.level().getGameTime());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = event.getPos();
        BlockState state = level.getBlockState(center);

        // 1. Проверка golden_chalk
        Block goldenBlock = ForgeRegistries.BLOCKS.getValue(BLOCK_GOLDEN);
        if (goldenBlock == null || state.getBlock() != goldenBlock) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        // 2. Время (полнолуние + полночь)
        if (!isCorrectTime(level)) return;

        // 3. Круг
        if (!checkCircle(level, center)) return;

        // 4. Камень на земле
        ItemEntity stone = findStone(level, center, player.getUUID());
        if (stone == null) return;

        // 5. Taglock в инвентаре
        ItemStack taglock = findTaglock(player);
        if (taglock.isEmpty()) return;

        UUID targetUuid = TaglockUtil.readBoundUuid(taglock);
        if (targetUuid == null) {
            player.displayClientMessage(Component.literal("Taglock пустой!").withStyle(ChatFormatting.RED), true);
            return;
        }

        ServerPlayer target = level.getServer().getPlayerList().getPlayer(targetUuid);
        if (target == null) {
            player.displayClientMessage(Component.literal("Цель не в сети!").withStyle(ChatFormatting.RED), true);
            return;
        }

        // 6. Алтарь
        int power = EnchantedAltarUtil.findNearbyAltarPower(level, center, SEARCH_RADIUS);
        if (power < REQUIRED_POWER) {
            player.displayClientMessage(
                    Component.literal("Алтарь: " + power + "/" + REQUIRED_POWER)
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        // 7. ПОИСК ПРЕДМЕТОВ НА ЗЕМЛЕ
        RitualItems items = findRitualItemsOnGround(level, center);
        if (!items.hasAll()) {
            player.displayClientMessage(
                    Component.literal("Найдено: " + items.getDebugInfo())
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );
            player.displayClientMessage(
                    Component.literal("Недостаточно ингредиентов на земле! Выбросьте их на golden_chalk.")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        // Списываем power с алтаря
        BlockEntity altar = EnchantedAltarUtil.findNearestAltar(level, center, SEARCH_RADIUS);
        if (altar != null) {
            EnchantedAltarUtil.consumePower(altar, REQUIRED_POWER);
        }

        // Удаляем камень и taglock сразу
        taglock.shrink(1);

        // ✅ Запускаем АНИМИРОВАННЫЙ РИТУАЛ: предметы испаряются по очереди
        startAnimatedRitual(level, center, player, target, items, stone);

        event.setCanceled(true);
    }

    // ============ ПОИСК ПРЕДМЕТОВ НА ЗЕМЛЕ (ItemEntity) ============
    // ============ ПОИСК ПРЕДМЕТОВ НА ЗЕМЛЕ (ItemEntity) ============
    private static RitualItems findRitualItemsOnGround(ServerLevel level, BlockPos center) {
        RitualItems result = new RitualItems();

        // Увеличен радиус до 3 блоков
        AABB box = new AABB(center).inflate(3.0, 2.0, 3.0);
        List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, box);

        for (ItemEntity entity : entities) {
            ItemStack stack = entity.getItem();

            if (isItem(stack, ITEM_TONGUE_OF_DOG)) {
                result.tongueOfDogEntities.add(entity);
            } else if (isItem(stack, ITEM_EXHALE)) {
                result.exhaleEntity = entity;
            } else if (isItem(stack, ITEM_WOLFSBANE)) {
                result.wolfsbaneEntity = entity;
            } else if (isItem(stack, ITEM_DEMONIC_BLOOD)) {
                result.demonicBloodEntity = entity;
            } else if (isItem(stack, ITEM_WEREWOLF_TOOTH)) {
                result.werewolfToothEntity = entity;
            }
        }

        return result;
    }

    private static class RitualItems {
        List<ItemEntity> tongueOfDogEntities = new ArrayList<>();
        ItemEntity exhaleEntity = null;
        ItemEntity wolfsbaneEntity = null;
        ItemEntity demonicBloodEntity = null;
        ItemEntity werewolfToothEntity = null;

        boolean hasAll() {
            int tongueCount = 0;
            for (ItemEntity e : tongueOfDogEntities) {
                if (e != null && !e.isRemoved()) {
                    tongueCount += e.getItem().getCount();
                }
            }

            return tongueCount >= 10
                    && exhaleEntity != null && !exhaleEntity.isRemoved()
                    && wolfsbaneEntity != null && !wolfsbaneEntity.isRemoved()
                    && demonicBloodEntity != null && !demonicBloodEntity.isRemoved()
                    && werewolfToothEntity != null && !werewolfToothEntity.isRemoved();
        }

        // DEBUG метод
        String getDebugInfo() {
            int tongueCount = 0;
            for (ItemEntity e : tongueOfDogEntities) {
                if (e != null && !e.isRemoved()) {
                    tongueCount += e.getItem().getCount();
                }
            }

            return String.format(
                    "Tongue: %d/10, Exhale: %s, Wolfsbane: %s, Blood: %s, Tooth: %s",
                    tongueCount,
                    exhaleEntity != null ? "✓" : "✗",
                    wolfsbaneEntity != null ? "✓" : "✗",
                    demonicBloodEntity != null ? "✓" : "✗",
                    werewolfToothEntity != null ? "✓" : "✗"
            );
        }
    }


    // ============ АНИМАЦИЯ РИТУАЛА: ИСПАРЕНИЕ ПРЕДМЕТОВ ПО ОЧЕРЕДИ ============
    private static void startAnimatedRitual(ServerLevel level, BlockPos center, Player player,
                                            ServerPlayer target, RitualItems items, ItemEntity stone) {  // ← добавили stone
        LycanRitualInstance.RitualItems ritualItems = new LycanRitualInstance.RitualItems();
        ritualItems.stoneEntity = stone;  // ← ДОБАВЬТЕ ЭТО
        ritualItems.tongueOfDogEntities = items.tongueOfDogEntities;
        ritualItems.exhaleEntity = items.exhaleEntity;
        ritualItems.wolfsbaneEntity = items.wolfsbaneEntity;
        ritualItems.demonicBloodEntity = items.demonicBloodEntity;
        ritualItems.werewolfToothEntity = items.werewolfToothEntity;

        LycanRitualInstance ritual = new LycanRitualInstance(level, center, player, target, ritualItems, CIRCLE);
        ACTIVE_RITUALS.add(ritual);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        ACTIVE_RITUALS.removeIf(LycanRitualInstance::tick);
    }




    private static boolean isCorrectTime(ServerLevel level) {
        long time = level.getDayTime() % 24000L;
        return level.getMoonPhase() == 0 && time >= 18000L && time <= 18100L;
    }

    private static boolean checkCircle(ServerLevel level, BlockPos center) {
        Block nether = ForgeRegistries.BLOCKS.getValue(BLOCK_NETHER);
        Block golden = ForgeRegistries.BLOCKS.getValue(BLOCK_GOLDEN);
        if (nether == null || golden == null) return false;

        int y = center.getY();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                Block block = level.getBlockState(pos).getBlock();

                boolean isCenter = (dx == 0 && dz == 0);
                boolean isRing = isInCircle(dx, dz);

                if (isCenter) {
                    if (block != golden) return false;
                } else if (isRing) {
                    if (block != nether) return false;
                } else {
                    if (block == nether || block == golden) return false;
                }
            }
        }
        return true;
    }

    private static boolean isInCircle(int dx, int dz) {
        for (int[] p : CIRCLE) {
            if (p[0] == dx && p[1] == dz) return true;
        }
        return false;
    }

    private static ItemEntity findStone(ServerLevel level, BlockPos center, UUID thrower) {
        long now = level.getGameTime();
        AABB box = new AABB(center).inflate(0.5, 1, 0.5);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box);

        for (ItemEntity e : items) {
            if (!isItem(e.getItem(), ITEM_STONE)) continue;

            String who = e.getPersistentData().getString("vbl_thrower");
            long tick = e.getPersistentData().getLong("vbl_tick");

            if (thrower.toString().equals(who) && now - tick <= STONE_MAX_AGE) {
                return e;
            }
        }
        return null;
    }

    private static ItemStack findTaglock(Player player) {
        ItemStack off = player.getOffhandItem();
        if (isItem(off, ITEM_TAGLOCK) && TaglockUtil.readBoundUuid(off) != null) {
            return off;
        }

        for (ItemStack s : player.getInventory().items) {
            if (isItem(s, ITEM_TAGLOCK) && TaglockUtil.readBoundUuid(s) != null) {
                return s;
            }
        }

        return ItemStack.EMPTY;
    }

    private static boolean isItem(ItemStack stack, ResourceLocation id) {
        var item = ForgeRegistries.ITEMS.getValue(id);
        return item != null && stack.is(item);
    }
}
