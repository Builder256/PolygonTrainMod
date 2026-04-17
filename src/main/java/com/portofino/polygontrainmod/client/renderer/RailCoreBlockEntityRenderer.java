package com.portofino.polygontrainmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.polygontrainmod.blockentity.LargeRailCoreBlockEntity;
import com.portofino.polygontrainmod.client.model.MqoModelLoader;
import com.portofino.polygontrainmod.rail.RailDefinition;
import com.portofino.polygontrainmod.rail.RailRegistry;
import com.portofino.polygontrainmod.rail.util.RailMap;
import com.portofino.polygontrainmod.rail.util.RailPosition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

public class RailCoreBlockEntityRenderer implements BlockEntityRenderer<LargeRailCoreBlockEntity> {
    /** RTM RailPartsRendererBase#createRailPos と同じ。 */
    private static int computeRtmMax(double length) {
        return Math.max(2, (int) (length * 2.0));
    }

    /**
     * 直線のみ: Baru 等は全サンプルでレールを描くため Z-fighting しやすい。極小の Y オフセットで深度を分散。
     * 曲線ではベジェと整合したサンプル間隔のためオフセットしない。
     */
    private static float depthJitter(int pos) {
        return ((pos & 15) - 7.5f) * 4.0e-6f;
    }

    public RailCoreBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(LargeRailCoreBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!be.isLoaded()) return;
        RailDefinition def = RailRegistry.getById(be.getRailDefinitionId());
        if (def == null) def = RailRegistry.getSelected();
        if (def == null) return;
        MqoModelLoader.MqoModel model = MqoModelLoader.loadModelForRail(def);
        if (model == null) return;
        RailMap[] maps = be.getAllRailMaps();
        if (maps.length == 0) return;

        BlockPos origin = be.getBlockPos();
        double ox = origin.getX();
        double oy = origin.getY();
        double oz = origin.getZ();
        net.minecraft.world.phys.Vec3 mo = def.getModelOffset();
        float scale = def.getModelScale();
        final RailDefinition definition = def;

        for (RailMap map : maps) {
            if (map == null) continue;
            double length = map.getLength();
            if (length < 1.0e-4) continue;
            int max = computeRtmMax(length);
            int capacity = max + 1;
            for (int i = 0; i <= max; i++) {
                double[] point = map.getRailPos(max, i);
                double wx = point[1];
                double wy = map.getRailHeight(max, i);
                double wz = point[0];
                float yaw = map.getRailYaw(max, i);
                float pitch = map.getRailPitch(max, i);
                float roll = map.getCant(max, i);
                poseStack.pushPose();
                float yBump = depthJitter(i);
                poseStack.translate(wx - ox, wy - oy - 0.0625 + yBump, wz - oz);
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
                poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
                poseStack.translate(mo.x, mo.y, mo.z);
                poseStack.scale(scale, scale, scale);
                final int pos = i;
                MqoModelLoader.renderModel(model, poseStack, buffer, packedLight,
                    groupName -> {
                        if (groupName.startsWith("side") && pos != 0 && pos != max) {
                            return false;
                        }
                        return true;
                    });
                poseStack.popPose();
            }
        }
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(LargeRailCoreBlockEntity be) {
        if (!be.isLoaded()) {
            BlockPos p = be.getBlockPos();
            return new AABB(p).inflate(1.0);
        }
        RailMap[] maps = be.getAllRailMaps();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (RailMap map : maps) {
            if (map == null) continue;
            double length = map.getLength();
            if (length < 1.0e-4) continue;
            int max = computeRtmMax(length);
            for (int i = 0; i <= max; i++) {
                double[] point = map.getRailPos(max, i);
                double x = point[1];
                double y = map.getRailHeight(max, i);
                double z = point[0];
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
        }
        if (minX == Double.POSITIVE_INFINITY) {
            BlockPos p = be.getBlockPos();
            return new AABB(p).inflate(1.0);
        }
        return new AABB(minX - 8.0, minY - 4.0, minZ - 8.0, maxX + 8.0, maxY + 8.0, maxZ + 8.0);
    }

    @Override
    public boolean shouldRenderOffScreen(LargeRailCoreBlockEntity blockEntity) {
        return true;
    }
}
