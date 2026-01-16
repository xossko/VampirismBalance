package com.vladisss.vampirismbalance.util;

import net.minecraft.server.level.ServerPlayer;

public class WerewolvesCompat {

    /**
     * Устанавливает игроку 1 уровень оборотня через команду Vampirism
     */
    public static void applyLycanthropyLikeBite(ServerPlayer target, int durationTicks) {
        try {
            // Выполняем команду: /vampirism level werewolves:werewolf 1 <player>
            String command = String.format("vampirism level werewolves:werewolf 1 %s", target.getGameProfile().getName());

            target.getServer().getCommands().performPrefixedCommand(
                    target.getServer().createCommandSourceStack()
                            .withPermission(4) // Уровень OP для выполнения команды
                            .withSuppressedOutput(), // Не спамить в консоль
                    command
            );

            target.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§5Ваше обоняние усилилось, вы чувствуете себя немного сильнее.")
            );

        } catch (Exception e) {
            target.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§cОшибка при применении ликантропии: " + e.getMessage())
            );
            e.printStackTrace();
        }
    }
}
