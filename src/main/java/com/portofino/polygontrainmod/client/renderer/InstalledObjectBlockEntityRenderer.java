package com.portofino.polygontrainmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.portofino.polygontrainmod.blockentity.InstalledObjectBlockEntity;
import com.portofino.polygontrainmod.client.model.MqoModelLoader;
import com.portofino.polygontrainmod.installedobject.InstalledObjectCategory;
import com.portofino.polygontrainmod.installedobject.InstalledObjectDefinition;
import com.portofino.polygontrainmod.installedobject.InstalledObjectRegistry;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class InstalledObjectBlockEntityRenderer implements BlockEntityRenderer<InstalledObjectBlockEntity> {
    public InstalledObjectBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(InstalledObjectBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        InstalledObjectDefinition definition = InstalledObjectRegistry.getById(blockEntity.getDefinitionId());
        if (definition == null) {
            return;
        }
        if (blockEntity.getCategory() == InstalledObjectCategory.WIRE && blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
            renderWire(blockEntity, poseStack, buffer);
        }
        if (definition.getModelFile() != null && !definition.getModelFile().isBlank()) {
            MqoModelLoader.MqoModel model = MqoModelLoader.loadModelFromPack(
                definition.getPackName(),
                definition.getModelFile(),
                definition.getTextureOverrides(),
                definition.getScriptPath(),
                definition.isSmoothing()
            );
            if (model != null) {
                poseStack.pushPose();
                poseStack.translate(0.5D, 0.0D, 0.5D);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - blockEntity.getYaw()));
                poseStack.translate(definition.getModelOffset().x, definition.getModelOffset().y, definition.getModelOffset().z);
                poseStack.scale(definition.getModelScale(), definition.getModelScale(), definition.getModelScale());
                MqoModelLoader.renderModel(model, poseStack, buffer, packedLight, blockEntity);
                poseStack.popPose();
                return;
            }
        }
        if (blockEntity.getCategory() == InstalledObjectCategory.SIGNBOARD) {
            renderSignboardOutline(definition, poseStack, buffer);
        }
    }

    private void renderWire(InstalledObjectBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource buffer) {
        BlockPos start = blockEntity.getWireStart();
        BlockPos end = blockEntity.getWireEnd();
        if (start == null || end == null) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(blockEntity.getBlockPos());
        Vec3 from = Vec3.atCenterOf(start).add(-center.x, -center.y, -center.z);
        Vec3 to = Vec3.atCenterOf(end).add(-center.x, -center.y, -center.z);
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
        int samples = 16;
        for (int i = 0; i < samples; i++) {
            double t0 = i / (double) samples;
            double t1 = (i + 1.0D) / samples;
            Vec3 p0 = from.lerp(to, t0).add(0.0D, sag(t0), 0.0D);
            Vec3 p1 = from.lerp(to, t1).add(0.0D, sag(t1), 0.0D);
            LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                Math.min(p0.x, p1.x) - 0.01D,
                Math.min(p0.y, p1.y) - 0.01D,
                Math.min(p0.z, p1.z) - 0.01D,
                Math.max(p0.x, p1.x) + 0.01D,
                Math.max(p0.y, p1.y) + 0.01D,
                Math.max(p0.z, p1.z) + 0.01D,
                0.75F, 0.9F, 1.0F, 0.7F
            );
        }
    }

    private static double sag(double t) {
        return -0.35D * Math.sin(Math.PI * t);
    }

    private void renderSignboardOutline(InstalledObjectDefinition definition, PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
        double halfWidth = definition.getWidth() * 0.5D;
        double height = definition.getHeight();
        double halfDepth = Math.max(0.02D, definition.getDepth() * 0.5D);
        LevelRenderer.renderLineBox(
            poseStack,
            consumer,
            0.5D - halfWidth, 0.0D, 0.5D - halfDepth,
            0.5D + halfWidth, height, 0.5D + halfDepth,
            1.0F, 0.95F, 0.6F, 0.9F
        );
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(InstalledObjectBlockEntity blockEntity) {
        if (blockEntity.getCategory() == InstalledObjectCategory.WIRE && blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
            Vec3 a = Vec3.atCenterOf(blockEntity.getWireStart());
            Vec3 b = Vec3.atCenterOf(blockEntity.getWireEnd());
            return new AABB(a, b).inflate(2.0D);
        }
        return new AABB(blockEntity.getBlockPos()).inflate(4.0D);
    }

    @Override
    public boolean shouldRenderOffScreen(InstalledObjectBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }
}
