package com.vladisss.vampirismbalance.registry;

import com.vladisss.vampirismbalance.VampirismBalance;
import com.vladisss.vampirismbalance.entity.LilithEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, VampirismBalance.MODID);

    public static final RegistryObject<EntityType<LilithEntity>> LILITH =
            ENTITY_TYPES.register("lilith", () ->
                    EntityType.Builder.of(LilithEntity::new, MobCategory.CREATURE)
                            .fireImmune()
                            .sized(0.6F, 1.95F)
                            .build(new ResourceLocation(VampirismBalance.MODID, "lilith").toString())
            );

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }
}
