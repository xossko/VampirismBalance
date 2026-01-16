package com.vladisss.vampirismbalance.event;

import com.vladisss.vampirismbalance.entity.LilithEntity;
import com.vladisss.vampirismbalance.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public class LilithFollowEventHandler {
    public static final String TAG_LILITH_UUID = "vbl_lilith_uuid";

    @SubscribeEvent
    public void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String id = player.getPersistentData().getString(TAG_LILITH_UUID);
        if (id.isEmpty()) return;

        UUID uuid;
        try { uuid = UUID.fromString(id); } catch (Exception e) { return; }

        ServerLevel from = player.server.getLevel(event.getFrom());
        ServerLevel to = player.server.getLevel(event.getTo());
        if (from == null || to == null) return;

        Entity e = from.getEntity(uuid);
        if (!(e instanceof LilithEntity lilith)) return;

        // переносим сущность в новое измерение и телепортируем рядом с игроком
        Entity moved = lilith.changeDimension(to);
        if (moved != null) {
            moved.teleportTo(player.getX(), player.getY(), player.getZ());
        }
    }
}
