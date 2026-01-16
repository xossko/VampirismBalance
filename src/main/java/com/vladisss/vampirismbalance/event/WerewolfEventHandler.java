package com.vladisss.vampirismbalance.event;

import com.vladisss.vampirismbalance.VampirismBalance;
import de.teamlapen.werewolves.api.entities.player.IWerewolfPlayer;
import de.teamlapen.werewolves.entities.player.werewolf.WerewolfPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = VampirismBalance.MODID)
public class WerewolfEventHandler {

    private static final UUID WEREWOLF_DAMAGE_UUID = UUID.fromString("c3d4e5f6-a7b8-6c7d-8e9f-9a8b7c6d5e4f");
    private static final UUID WEREWOLF_ARMOR_UUID = UUID.fromString("d4e5f6a7-b8c9-7d8e-9f0a-0b1c2d3e4f5a");
    private static final UUID WEREWOLF_SPEED_UUID = UUID.fromString("e5f6a7b8-c9d0-8e9f-0a1b-1c2d3e4f5a6b");
    private static final boolean WEREWOLVES_LOADED = ModList.get().isLoaded("werewolves");
    private static final UUID WEREWOLF_ATTACK_SPEED_UUID = UUID.fromString("f6a7b8c9-d0e1-9f0a-1b2c-2d3e4f5a6b7c");

    // Храним последний уровень каждого игрока
    private static final Map<UUID, Integer> lastLevelMap = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!WEREWOLVES_LOADED) {
            VampirismBalance.LOGGER.warn("Werewolves mod not loaded!");
            return;
        }
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.tickCount % 20 != 0) return;


        player.getCapability(WerewolfPlayer.CAP).ifPresent(werewolf -> {
            int level = werewolf.getLevel();


            if (level > 0) {
                String form = werewolf.getForm().getName();

                // Проверяем достижение новых уровней
                checkLevelUpNotifications(player, level);

                applyWerewolfBuffs(player, level, form);
            } else {

                removeWerewolfBuffs(player);
                lastLevelMap.remove(player.getUUID());
            }
        });

    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!WEREWOLVES_LOADED) return;

        Player player = event.getEntity();

        player.getCapability(WerewolfPlayer.CAP).ifPresent(werewolf -> {
            int currentLevel = werewolf.getLevel();
            int lastSeenLevel = lastLevelMap.getOrDefault(player.getUUID(), 0);


            // Проверяем пропущенные уведомления
            checkLevelUpNotifications(player, currentLevel);
        });
    }

    // Метод вынесен на уровень класса
    private static void checkLevelUpNotifications(Player player, int currentLevel) {
        UUID playerUUID = player.getUUID();
        int lastLevel = lastLevelMap.getOrDefault(playerUUID, 0);

        // Обновляем сохранённый уровень
        lastLevelMap.put(playerUUID, currentLevel);

        // Проверяем переход на ключевые уровни
        if (currentLevel > lastLevel) {
            VampirismBalance.LOGGER.info("Level changed from {} to {}, checking milestone notifications", lastLevel, currentLevel);

            // Проверяем каждый пропущенный уровень
            for (int level = lastLevel + 1; level <= currentLevel; level++) {
                if (level == 5) {
                    sendLevelUpMessage(player, "§4Звериная ярость пробуждается в твоей крови...", SoundEvents.WOLF_GROWL);
                } else if (level == 9) {
                    sendLevelUpMessage(player, "§4Луна шепчет... Ты становишься мощнее...", SoundEvents.WOLF_HOWL);
                } else if (level == 13) {
                    sendLevelUpMessage(player, "§4Древняя сила течёт по твоим венам!", SoundEvents.ENDER_DRAGON_GROWL);
                }
            }
        }
    }

    private static void sendLevelUpMessage(Player player, String message, net.minecraft.sounds.SoundEvent sound) {
        // Отправляем сообщение в центр экрана (action bar) на 2 секунды
        // Для этого отправляем его несколько раз с задержкой
        player.displayClientMessage(Component.literal(message), true);

        // Планируем повторную отправку через 1 секунду (20 тиков)
        player.level().getServer().tell(new net.minecraft.server.TickTask(20, () -> {
            player.displayClientMessage(Component.literal(message), true);
        }));

        // Воспроизводим звук
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                sound,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );

        VampirismBalance.LOGGER.info("Werewolf {} reached milestone level", player.getName().getString());
    }


    private static void applyWerewolfBuffs(Player player, int level, String form) {
        boolean isTransformed = !form.equals("none");

        // === 1. УРОН: +10% за уровень (без формы -50%, днём -20%) ===
        double damageBoost = level * 0.1;

// Проверка времени суток и измерения
        boolean isNight = true; // По умолчанию считаем ночью (для Нижнего мира и Энда)

// Только в обычном мире проверяем время суток
        if (player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            long timeOfDay = player.level().getDayTime() % 24000;
            isNight = timeOfDay >= 13000 && timeOfDay < 23000;
        }

        if (!isTransformed) {
            damageBoost *= 0.5; // Без формы: -50%
        }

        if (!isNight) {
            damageBoost *= 0.8; // Не ночью: -20% (только в обычном мире)
        }

        AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.removeModifier(WEREWOLF_DAMAGE_UUID);
            if (damageBoost > 0) {
                attackDamage.addPermanentModifier(new AttributeModifier(
                        WEREWOLF_DAMAGE_UUID,
                        "Werewolf Damage Boost",
                        damageBoost,
                        AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }
        }



        // === 2. БРОНЯ: +1 единица за уровень (работает ВСЕГДА) ===
        double armorBonus = level * 1.0;

        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(WEREWOLF_ARMOR_UUID);
            if (armorBonus > 0) {
                armor.addPermanentModifier(new AttributeModifier(
                        WEREWOLF_ARMOR_UUID,
                        "Werewolf Armor Boost",
                        armorBonus,
                        AttributeModifier.Operation.ADDITION
                ));
            }
        }

        // === 3. СКОРОСТЬ: прогрессивная (+7% за диапазон), без формы -50% ===
        double speedBonus = 0.0;

        if (level < 4) {
            speedBonus = 0.07;
        } else if (level < 8) {
            speedBonus = 0.14;
        } else if (level < 12) {
            speedBonus = 0.21;
        } else {
            speedBonus = 0.28;
        }

        if (!isTransformed) {
            speedBonus *= 0.5;
        }

        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(WEREWOLF_SPEED_UUID);
            if (speedBonus > 0) {
                movementSpeed.addPermanentModifier(new AttributeModifier(
                        WEREWOLF_SPEED_UUID,
                        "Werewolf Speed Boost",
                        speedBonus,
                        AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }
        }
        // === 3.5. СКОРОСТЬ АТАКИ: прогрессивная (+7% за диапазон), без формы -50%, днём -20% ===
        double attackSpeedBonus = 0.0;

        if (level < 4) {
            attackSpeedBonus = 0.07;
        } else if (level < 8) {
            attackSpeedBonus = 0.14;
        } else if (level < 12) {
            attackSpeedBonus = 0.21;
        } else {
            attackSpeedBonus = 0.28;
        }

        if (!isTransformed) {
            attackSpeedBonus *= 0.5;
        }

        if (!isNight) {
            attackSpeedBonus *= 0.8; // Днём -20%
        }

        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            attackSpeed.removeModifier(WEREWOLF_ATTACK_SPEED_UUID);
            if (attackSpeedBonus > 0) {
                attackSpeed.addPermanentModifier(new AttributeModifier(
                        WEREWOLF_ATTACK_SPEED_UUID,
                        "Werewolf Attack Speed Boost",
                        attackSpeedBonus,
                        AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }
        }


        // === 4. ПРЫЖОК: эффект Jump Boost (только в формах) ===
        if (isTransformed) {
            int jumpLevel = -1; // -1 означает "не применять эффект"

            if (level >= 5 && level < 8) {
                jumpLevel = 0; // Jump Boost I (+1 блок)
            } else if (level >= 9 && level < 12) {
                jumpLevel = 1; // Jump Boost II (+2 блока)
            } else if (level >= 13) {
                jumpLevel = 2; // Jump Boost III (+3 блока)
            }

            // Применяем эффект только если jumpLevel >= 0
            if (jumpLevel >= 0) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.JUMP,
                        40,
                        jumpLevel,
                        false,
                        false
                ));
            }
        }



        // === 5. РЕГЕНЕРАЦИЯ: прогрессивная (только в формах) ===
        if (isTransformed) {
            float healAmount = 0.0f;

            if (level < 4) {
                healAmount = 0.5f;
            } else if (level < 8) {
                healAmount = 1.0f;
            } else if (level < 12) {
                healAmount = 1.5f;
            } else {
                healAmount = 2.0f;
            }

            player.heal(healAmount);
        }
    }

    private static void removeWerewolfBuffs(Player player) {
        AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.removeModifier(WEREWOLF_DAMAGE_UUID);
        }

        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(WEREWOLF_ARMOR_UUID);
        }

        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(WEREWOLF_SPEED_UUID);
        }

        player.removeEffect(MobEffects.JUMP);
    }
}
