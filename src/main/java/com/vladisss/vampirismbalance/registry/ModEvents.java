package com.vladisss.vampirismbalance.registry;

import com.vladisss.vampirismbalance.entity.LilithEntity;
import com.vladisss.vampirismbalance.event.*;
import de.teamlapen.vampirism.api.VReference;
import de.teamlapen.vampirism.entity.factions.FactionPlayerHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

// Импорты Epic Fight
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

public class ModEvents {

    // HIGHEST приоритет - выполняется раньше Epic Fight
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeathHighest(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        FactionPlayerHandler factionHandler = FactionPlayerHandler.get(player);

        // ДЛЯ ВСЕХ ВАМПИРОВ: Останавливаем Epic Fight ПЕРЕД его обработкой смерти
        if (factionHandler.getCurrentFaction() == VReference.VAMPIRE_FACTION) {
            if (ModList.get().isLoaded("epicfight")) {
                player.getCapability(EpicFightCapabilities.CAPABILITY_ENTITY).ifPresent(entityPatch -> {
                    if (entityPatch instanceof ServerPlayerPatch playerPatch) {
                        playerPatch.toVanillaMode(true);
                        player.getPersistentData().putBoolean("vbl_restore_epicfight_mode", true);

                    }
                });
            }

        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // РИТУАЛ: Обрабатываем превращение в вампира через ритуал
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean("vbl_can_become_vampire")) return;

        event.setCanceled(true);
        data.remove("vbl_can_become_vampire");
        data.remove("vbl_vampire_unlock_time");

        FactionPlayerHandler factionHandler = FactionPlayerHandler.get(player);
        factionHandler.setFactionAndLevel(VReference.VAMPIRE_FACTION, 1);
        player.setHealth(0.5F);

        player.displayClientMessage(
                Component.literal("Кажется вы чувствуете голод, необычный")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                false
        );
    }
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        CompoundTag tag = player.getPersistentData();
        if (!tag.getBoolean("vbl_restore_epicfight_mode")) return;

        // Ждём именно момента, когда игрок "встал"
        if (player.getPose() != Pose.STANDING) return;

        // (опционально) ограничить только вампирами, чтобы не трогать остальных
        FactionPlayerHandler factionHandler = FactionPlayerHandler.get(player);
        if (factionHandler.getCurrentFaction() != VReference.VAMPIRE_FACTION) return;

        player.getCapability(EpicFightCapabilities.CAPABILITY_ENTITY).ifPresent(entityPatch -> {
            if (entityPatch instanceof ServerPlayerPatch playerPatch) {
                playerPatch.toEpicFightMode(true);
                tag.remove("vbl_restore_epicfight_mode");
            }
        });
    }


    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModEvents::onEntityAttributeCreation);

        MinecraftForge.EVENT_BUS.register(new VampireRitualEventHandler());
        MinecraftForge.EVENT_BUS.register(new VampireEventHandler());
        MinecraftForge.EVENT_BUS.register(new WerewolfEventHandler());
        MinecraftForge.EVENT_BUS.register(new LycanRitualEventHandler());
        MinecraftForge.EVENT_BUS.register(new LilithFollowEventHandler());
        MinecraftForge.EVENT_BUS.register(LilithEntity.BossDeathHandler.class);
        MinecraftForge.EVENT_BUS.register(ModEvents.class);
    }

    private static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.LILITH.get(), LilithEntity.createAttributes().build());
    }
}
