package com.vladisss.vampirismbalance;

import com.mojang.logging.LogUtils;
import com.vladisss.vampirismbalance.event.LilithFollowEventHandler;
import com.vladisss.vampirismbalance.event.LycanRitualEventHandler;
import com.vladisss.vampirismbalance.event.VampireEventHandler;
import com.vladisss.vampirismbalance.event.VampireRitualEventHandler;
import com.vladisss.vampirismbalance.event.WerewolfEventHandler;
import com.vladisss.vampirismbalance.registry.ModEntities;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(VampirismBalance.MODID)
public class VampirismBalance {

    public static final String MODID = "vampirismbalance";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VampirismBalance() {
        LOGGER.info("Vampirism Balance mod loading...");

        // MOD event bus (реестры)
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.register(modBus);

        // Forge event bus (ивенты)
        MinecraftForge.EVENT_BUS.register(new VampireEventHandler());
        MinecraftForge.EVENT_BUS.register(new WerewolfEventHandler());
        MinecraftForge.EVENT_BUS.register(new LycanRitualEventHandler());
        MinecraftForge.EVENT_BUS.register(new VampireRitualEventHandler());
        MinecraftForge.EVENT_BUS.register(new LilithFollowEventHandler());

        LOGGER.info("Vampirism Balance mod loaded!");
    }
}
