package com.portofino.polygontrainmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.entity.CarEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class CarRenderer extends EntityRenderer<CarEntity> {

    public static final ResourceLocation TEXTURE
        = ResourceLocation.fromNamespaceAndPath(PolygonTrainMod.MODID, "textures/block/test_block");

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    @NotNull
    public ResourceLocation getTextureLocation(@NotNull CarEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(
        CarEntity entity,
        float entityYaw,
        float partialTick,
        PoseStack posestack,
        MultiBufferSource buffer,
        int packedLight
    ) {
        super.render(entity, entityYaw, partialTick, posestack, buffer, packedLight);
    }
}
