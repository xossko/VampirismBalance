package com.vladisss.vampirismbalance.client;

import com.vladisss.vampirismbalance.VampirismBalance;
import com.vladisss.vampirismbalance.entity.LilithEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class LilithRenderer extends MobRenderer<LilithEntity, HumanoidModel<LilithEntity>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(VampirismBalance.MODID, "textures/entity/lilith.png");

    public LilithRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(LilithEntity entity) {
        return TEXTURE;
    }
}
