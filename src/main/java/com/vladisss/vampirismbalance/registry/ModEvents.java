package com.vladisss.vampirismbalance.registry;

import com.vladisss.vampirismbalance.VampirismBalance;
import com.vladisss.vampirismbalance.entity.LilithEntity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VampirismBalance.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvents {

    @SubscribeEvent
    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.LILITH.get(), LilithEntity.createAttributes().build());
    }
}
