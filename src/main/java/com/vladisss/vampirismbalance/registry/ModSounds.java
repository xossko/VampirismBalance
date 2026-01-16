package com.vladisss.vampirismbalance.registry;

import com.vladisss.vampirismbalance.VampirismBalance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, VampirismBalance.MODID);

    // ВАЖНО: имя должно совпадать с ключом в sounds.json
    public static final RegistryObject<SoundEvent> RITUAL_VAMPIRE =
            SOUND_EVENTS.register("ritual.vampire",  // <- ТОЧКА, а не подчёркивание!
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(VampirismBalance.MODID, "ritual.vampire")  // <- И здесь тоже!
                    ));

    public static void register(IEventBus bus) {
        SOUND_EVENTS.register(bus);
    }
}
