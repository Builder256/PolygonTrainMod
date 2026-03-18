package com.portofino.polygontrainmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.polygontrainmod.PolygonTrainMod;
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
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class CarRenderer extends EntityRenderer<CarEntity> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(PolygonTrainMod.MODID, "textures/uv-checker-1024px.png");

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    @NotNull
    public ResourceLocation getTextureLocation(@NotNull CarEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(@NotNull CarEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucentCull(TEXTURE));

        Matrix4f matrix = poseStack.last().pose();

        buildQuad(buffer, matrix, packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private void buildQuad(VertexConsumer buffer, Matrix4f matrix, int light) {
        Vector3f testNormalVector = new Vector3f(1, 0, 1).normalize();

        float x = testNormalVector.x;
        float y = testNormalVector.y;
        float z = testNormalVector.z;

        // ポリゴン1
        buffer.addVertex(matrix, 0, 1, 0).setColor(255, 255, 255, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        buffer.addVertex(matrix, 0, 0, 0).setColor(255, 255, 255, 255).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        buffer.addVertex(matrix, 1, 0, 0).setColor(255, 255, 255, 255).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(x, y, z);
        buffer.addVertex(matrix, 1, 1, 0).setColor(255, 255, 255, 255).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(x, y, z);

        // 両ポリゴンの重なる2頂点について、ナナメ45度の方向の法線ベクトルを追加して、簡易的なスムースシェーディング処理を行う

        // ポリゴン2
        buffer.addVertex(matrix, 1, 1, 0).setColor(255, 255, 255, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(x, y, z);
        buffer.addVertex(matrix, 1, 0, 0).setColor(255, 255, 255, 255).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(x, y, z);
        buffer.addVertex(matrix, 1, 0, -1).setColor(255, 255, 255, 255).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1, 0, 0);
        buffer.addVertex(matrix, 1, 1, -1).setColor(255, 255, 255, 255).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1, 0, 0);
    }
}