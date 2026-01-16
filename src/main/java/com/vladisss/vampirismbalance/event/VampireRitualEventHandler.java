package com.vladisss.vampirismbalance.event;

import com.vladisss.vampirismbalance.entity.LilithEntity;
import com.vladisss.vampirismbalance.registry.ModEntities;
import com.vladisss.vampirismbalance.registry.ModSounds;
import com.vladisss.vampirismbalance.util.EnchantedAltarUtil;
import de.teamlapen.vampirism.api.VReference;
import de.teamlapen.vampirism.entity.factions.FactionPlayerHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class VampireRitualEventHandler {

    // Хранилище активных ритуалов: позиция черепа -> данные о ритуале
    private static final Map<BlockPos, RitualData> activeRituals = new HashMap<>();

    private static class RitualData {
        final ServerLevel level;
        final ServerPlayer player;
        final long startTick;

        RitualData(ServerLevel level, ServerPlayer player, long startTick) {
            this.level = level;
            this.player = player;
            this.startTick = startTick;
        }
    }

    // items
    private static final ResourceLocation ITEM_ARTHANA = new ResourceLocation("enchanted", "arthana");
    private static final ResourceLocation ITEM_CHALICE_EMPTY = new ResourceLocation("enchanted", "chalice");
    private static final ResourceLocation ITEM_CHALICE_FILLED = new ResourceLocation("enchanted", "chalice_filled");

    // skull NBT tags
    private static final String TAG_RITUAL_READY = "vbl_vampire_ritual_ready";
    private static final String TAG_RITUAL_DONE = "vbl_vampire_ritual_done";

    // altar config
    private static final int REQUIRED_POWER = 5000;
    private static final int SEARCH_RADIUS = 50;

    // 7x7 square => radius = 3
    private static final int OUTER_RADIUS = 3;

    @SubscribeEvent
    public void onKill(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;

        Entity src = event.getSource().getEntity();
        if (!(src instanceof ServerPlayer player)) return;

        if (!(event.getEntity() instanceof Chicken chicken)) return;
        if (!chicken.isBaby()) return;

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        if (!isItem(main, ITEM_ARTHANA)) return;
        if (!isItem(off, ITEM_CHALICE_EMPTY)) return;

        ServerLevel level = (ServerLevel) player.level();
        BlockPos skullPos = findSkullPosNear(level, chicken.blockPosition());
        if (skullPos == null) return;

        if (!isValidVampireStructure(level, skullPos)) return;

        // заменить пустой кубок на наполненный
        Item filled = ForgeRegistries.ITEMS.getValue(ITEM_CHALICE_FILLED);
        if (filled == null) return;

        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(filled, 1));

        level.playSound(
                null,
                chicken.blockPosition(),
                SoundEvents.PLAYER_HURT,
                SoundSource.PLAYERS,
                5.0F,
                0.6F
        );

        // отметить череп как "готов к клику"
        BlockEntity be = level.getBlockEntity(skullPos);
        if (be instanceof SkullBlockEntity skullBe) {
            CompoundTag tag = skullBe.getPersistentData();
            tag.putBoolean(TAG_RITUAL_READY, true);
            tag.remove(TAG_RITUAL_DONE);
            skullBe.setChanged();
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Проверяем все активные ритуалы
        Iterator<Map.Entry<BlockPos, RitualData>> iterator = activeRituals.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, RitualData> entry = iterator.next();
            BlockPos pos = entry.getKey();
            RitualData data = entry.getValue();

            long currentTick = data.level.getServer().getTickCount();
            long elapsed = currentTick - data.startTick;

            // Пока ритуал идёт (меньше 370 тиков)
            if (elapsed < 370) {
                // В самом начале активируем редстоун
                if (elapsed == 1) {
                    activateRedstone(data.level, pos, true);
                }

                // Каждые 2 тика создаём столбы огня
                if (elapsed % 2 == 0) {
                    spawnFirePillars(data.level, pos);
                }

                // Каждые 20 тиков (1 секунда) применяем эффекты
                if (elapsed % 20 == 0) {
                    applyDarknessEffect(data.level, pos, 50);
                }
            }

            // Прошло 370 тиков (18.5 секунд)?
            if (elapsed >= 370) {
                // Деактивируем редстоун перед финалом
                activateRedstone(data.level, pos, false);

                // 1) Молния
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(data.level);
                if (bolt != null) {
                    bolt.moveTo(pos.getX() + 0.5, pos.getY() + 0.0, pos.getZ() + 0.5);
                    bolt.setVisualOnly(true);
                    data.level.addFreshEntity(bolt);
                }

                // 2) Спавн Лилит
                LilithEntity lilith = new LilithEntity(ModEntities.LILITH.get(), data.level);
                if (lilith != null) {
                    lilith.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, data.player.getYRot(), 0);
                    lilith.tame(data.player);
                    lilith.setOwnerUUID(data.player.getUUID());
                    data.level.addFreshEntity(lilith);
                    data.player.getPersistentData().putString("vbl_lilith_uuid", lilith.getUUID().toString());
                }

                // Удаляем завершённый ритуал
                iterator.remove();
            }
        }
    }




    @SubscribeEvent
    public void onRightClickSkull(PlayerInteractEvent.RightClickBlock event) {
        Player p = event.getEntity();
        Level lvl = p.level();
        if (lvl.isClientSide) return;

        if (!(p instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        BlockPos pos = event.getPos();
        BlockState state = lvl.getBlockState(pos);

        if (!(state.getBlock() instanceof SkullBlock)) return;

        ItemStack held = event.getItemStack();
        if (!isItem(held, ITEM_CHALICE_FILLED)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setUseItem(Event.Result.DENY);
        event.setUseBlock(Event.Result.DENY);

        ServerLevel level = (ServerLevel) lvl;

        if (!isValidVampireStructure(level, pos)) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SkullBlockEntity skullBe)) return;

        CompoundTag tag = skullBe.getPersistentData();

        // 0) Ритуал только ночью
        long dayTime = level.getDayTime() % 24000L;
        boolean isNight = dayTime >= 13000L && dayTime <= 23000L;

        if (!isNight) {
            player.displayClientMessage(
                    Component.literal("Ритуал можно провести только ночью.")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        if (!tag.getBoolean(TAG_RITUAL_READY)) return;
        if (tag.getBoolean(TAG_RITUAL_DONE)) return;

        // ПРОВЕРКА АЛТАРЯ
        int power = EnchantedAltarUtil.findNearbyAltarPower(level, pos, SEARCH_RADIUS);
        if (power < REQUIRED_POWER) {
            player.displayClientMessage(
                    Component.literal("Недостаточно силы в алтаре: " + power + "/" + REQUIRED_POWER)
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        // Списываем силу с алтаря
        BlockEntity altar = EnchantedAltarUtil.findNearestAltar(level, pos, SEARCH_RADIUS);
        if (altar != null) {
            EnchantedAltarUtil.consumePower(altar, REQUIRED_POWER);
        }

        // ВАЖНО: сразу помечаем как "использовано"
        tag.putBoolean(TAG_RITUAL_DONE, true);
        skullBe.setChanged();

        // Опустошаем кубок в руке
        Item empty = ForgeRegistries.ITEMS.getValue(ITEM_CHALICE_EMPTY);
        if (empty != null) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(empty, 1));
        }

        // 1) Проигрываем ритуальную музыку
        level.playSound(
                null,
                pos,
                ModSounds.RITUAL_VAMPIRE.get(),
                SoundSource.MASTER,
                9.0F,
                1.0F
        );

        // 2) Сохраняем данные о начале ритуала
        long currentTick = level.getServer().getTickCount();
        activeRituals.put(pos.immutable(), new RitualData(level, player, currentTick));
    }

    private static void applyDarknessEffect(ServerLevel level, BlockPos center, int radius) {
        // Находим всех игроков в радиусе
        java.util.List<ServerPlayer> nearbyPlayers = level.getPlayers(
                player -> player.blockPosition().distSqr(center) <= radius * radius
        );

        // Применяем эффекты
        for (ServerPlayer player : nearbyPlayers) {
            // Эффект тьмы
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DARKNESS,
                    40,
                    0,
                    false,
                    false,
                    true
            ));

            // Эффект тряски экрана
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.CONFUSION,
                    40,
                    0,
                    false,
                    false,
                    false
            ));

            // Звук землетрясения каждые 20 тиков (1 раз в секунду)
            player.playNotifySound(
                    net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT,  // Глубокий грохочущий звук
                    net.minecraft.sounds.SoundSource.AMBIENT,
                    0.5F,   // Громкость
                    0.5F    // Низкий тон
            );
        }
    }
    private static void activateRedstone(ServerLevel level, BlockPos skullPos, boolean activate) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;

                BlockPos pos = skullPos.offset(dx, 0, dz);

                if (level.getBlockState(pos).getBlock() == Blocks.REDSTONE_WIRE) {
                    // Просто обновляем соседние блоки и сам редстоун
                    level.updateNeighborsAt(pos, Blocks.REDSTONE_WIRE);
                    level.scheduleTick(pos, Blocks.REDSTONE_WIRE, 0);

                    // Ставим максимальную мощность напрямую
                    BlockState currentState = level.getBlockState(pos);
                    level.setBlock(pos,
                            currentState.setValue(
                                    net.minecraft.world.level.block.RedStoneWireBlock.POWER,
                                    activate ? 15 : 0
                            ),
                            3
                    );
                }
            }
        }
    }




    private static void spawnFirePillars(ServerLevel level, BlockPos skullPos) {
        // Координаты 4 угловых факелов (±3, ±3 от черепа)
        BlockPos[] torchPositions = {
                skullPos.offset(-OUTER_RADIUS, 0, -OUTER_RADIUS),  // Северо-запад
                skullPos.offset(OUTER_RADIUS, 0, -OUTER_RADIUS),   // Северо-восток
                skullPos.offset(-OUTER_RADIUS, 0, OUTER_RADIUS),   // Юго-запад
                skullPos.offset(OUTER_RADIUS, 0, OUTER_RADIUS)     // Юго-восток
        };

        // Для каждого факела создаём столб огня высотой 10 блоков
        for (BlockPos torchPos : torchPositions) {
            for (int y = 1; y <= 10; y++) {
                double x = torchPos.getX() + 0.5;
                double yPos = torchPos.getY() + y;
                double z = torchPos.getZ() + 0.5;

                // Добавляем частицы пламени
                level.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.FLAME,  // Обычное пламя
                        x, yPos, z,      // Позиция
                        3,               // Количество частиц
                        0.1, 0.1, 0.1,  // Разброс по X, Y, Z
                        0.02             // Скорость
                );

                // Добавляем фиолетовые частицы душ для зловещего эффекта
                if (y % 2 == 0) {  // Каждый второй блок
                    level.sendParticles(
                            net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                            x, yPos, z,
                            2,
                            0.15, 0.15, 0.15,
                            0.01
                    );
                }
            }
        }
    }


    private static BlockPos findSkullPosNear(ServerLevel level, BlockPos base) {
        if (isSkull(level, base)) return base;
        if (isSkull(level, base.below())) return base.below();
        return null;
    }

    private static boolean isSkull(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof SkullBlock;
    }

    private static boolean isValidVampireStructure(ServerLevel level, BlockPos skullPos) {
        if (level.getBlockState(skullPos).getBlock() != Blocks.SKELETON_SKULL) return false;

        // 1) редстоун вокруг черепа (кольцо 3x3)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos p = skullPos.offset(dx, 0, dz);
                if (level.getBlockState(p).getBlock() != Blocks.REDSTONE_WIRE) return false;
            }
        }

        // 2) внешний периметр 7x7: радиус 3
        for (int dx = -OUTER_RADIUS; dx <= OUTER_RADIUS; dx++) {
            for (int dz = -OUTER_RADIUS; dz <= OUTER_RADIUS; dz++) {
                boolean border = Math.max(Math.abs(dx), Math.abs(dz)) == OUTER_RADIUS;
                if (!border) continue;

                BlockPos p = skullPos.offset(dx, 0, dz);
                boolean corner = Math.abs(dx) == OUTER_RADIUS && Math.abs(dz) == OUTER_RADIUS;

                if (corner) {
                    if (level.getBlockState(p).getBlock() != Blocks.TORCH) return false;
                } else {
                    if (level.getBlockState(p).getBlock() != Blocks.TRIPWIRE) return false;
                }
            }
        }

        // 3) внутренние "уголки" нити: (±2,±2)
        int inner = OUTER_RADIUS - 1; // 2
        int[] s = new int[]{-inner, inner};
        for (int dx : s) {
            for (int dz : s) {
                BlockPos p = skullPos.offset(dx, 0, dz);
                if (level.getBlockState(p).getBlock() != Blocks.TRIPWIRE) return false;
            }
        }

        return true;
    }

    private static boolean isItem(ItemStack stack, ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item != null && stack.is(item);
    }
}
