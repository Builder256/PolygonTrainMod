package com.portofino.polygontrainmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.polygontrainmod.PolygonTrainMod;
//import com.portofino.polygontrainmod.client.model.CarModel;
//import com.portofino.polygontrainmod.client.model.PolygonTrainModEntityRendererLayers;
import com.portofino.polygontrainmod.entity.CarEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class CarRenderer extends EntityRenderer<CarEntity> {
    // private final CarModel model;
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(PolygonTrainMod.MODID, "textures/white-1024px.png");

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
    //  this.model = new CarModel(context.bakeLayer(PolygonTrainModEntityRendererLayers.CAR_ENTITY));
    }

    @Override
    @NotNull
    public ResourceLocation getTextureLocation(@NotNull CarEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(@NotNull CarEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // // 向き回転（EntityYawに合わせる）
        // poseStack.mulPose(Axis.YP.rotationDegrees(
        //     Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 180f
        // ));

        // model.setupAnim(entity, 0, 0, entity.tickCount + partialTick, 0, 0);
        // model.renderToBuffer(poseStack, consumer, packedLight,
        //     OverlayTexture.NO_OVERLAY, -1);

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucentCull(TEXTURE));

        Matrix4f matrix = poseStack.last().pose();

        buildQuad(buffer, matrix, packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private void buildQuad(VertexConsumer buffer, Matrix4f matrix, int light) {
        int[] vector = {1, 0, 1};
        float temp = 0;
        for (int j : vector) {
            float doubled = j * j;
            temp += doubled;
        }
        double norm = Math.sqrt(temp);
        float[] normalized = {(float) (vector[0]/norm), (float) (vector[1]/norm), (float) (vector[2]/norm)};

        // ポリゴン1
        buffer.addVertex(matrix, 0, 1, 0).setColor(255, 255, 255, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        buffer.addVertex(matrix, 0, 0, 0).setColor(255, 255, 255, 255).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        buffer.addVertex(matrix, 1, 0, 0).setColor(255, 255, 255, 255).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normalized[0], normalized[1], normalized[2]);
        buffer.addVertex(matrix, 1, 1, 0).setColor(255, 255, 255, 255).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normalized[0], normalized[1], normalized[2]);

        // 四角ポリゴン
        // ポリゴン2
        buffer.addVertex(matrix, 1, 1, 0).setColor(255, 255, 255, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normalized[0], normalized[1], normalized[2]);
        buffer.addVertex(matrix, 1, 0, 0).setColor(255, 255, 255, 255).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normalized[0], normalized[1], normalized[2]);
        buffer.addVertex(matrix, 1, 0, -1).setColor(255, 255, 255, 255).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1, 0, 0);
        buffer.addVertex(matrix, 1, 1, -1).setColor(255, 255, 255, 255).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1, 0, 0);
    }
}