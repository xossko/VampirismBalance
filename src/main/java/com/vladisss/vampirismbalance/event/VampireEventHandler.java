package com.vladisss.vampirismbalance.event;

import com.vladisss.vampirismbalance.VampirismBalance;
import de.teamlapen.vampirism.api.entity.player.vampire.IVampirePlayer;
import de.teamlapen.vampirism.entity.player.vampire.VampirePlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public class VampireEventHandler {

    private static final UUID VAMPIRE_DAMAGE_UUID = UUID.fromString("a1b2c3d4-e5f6-4a5b-9c8d-7e6f5a4b3c2d");
    private static final UUID VAMPIRE_SPEED_UUID = UUID.fromString("b2c3d4e5-f6a7-5b6c-9d8e-8f7a6b5c4d3e");

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.tickCount % 20 != 0) return;

        player.getCapability(VampirePlayer.CAP).ifPresent(vampire -> {
            // Используй getFactionLevel() вместо isVampire()
            int level = vampire.getLevel();
            if (level > 0) {
                applyVampireBuffs(player, level);
            } else {
                removeVampireBuffs(player);
            }
        });
    }

    private void applyVampireBuffs(Player player, int level) {
        int bonusSteps = level / 3;
        double damageMultiplier = Math.min(bonusSteps * 0.2, 1.0);

        AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.removeModifier(VAMPIRE_DAMAGE_UUID);
            if (damageMultiplier > 0) {
                attackDamage.addPermanentModifier(new AttributeModifier(
                        VAMPIRE_DAMAGE_UUID,
                        "Vampire Damage Boost",
                        damageMultiplier,
                        AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }
        }

        int speedSteps = level / 2;
        double speedMultiplier = speedSteps * 0.05;

        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(VAMPIRE_SPEED_UUID);
            if (speedMultiplier > 0) {
                movementSpeed.addPermanentModifier(new AttributeModifier(
                        VAMPIRE_SPEED_UUID,
                        "Vampire Speed Boost",
                        speedMultiplier,
                        AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }
        }

        if (level >= 10) {
            player.heal(0.5f);
        } else if (level >= 5) {
            player.heal(0.25f);
        }
    }

    private void removeVampireBuffs(Player player) {
        AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.removeModifier(VAMPIRE_DAMAGE_UUID);
        }

        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(VAMPIRE_SPEED_UUID);
        }
    }
}
