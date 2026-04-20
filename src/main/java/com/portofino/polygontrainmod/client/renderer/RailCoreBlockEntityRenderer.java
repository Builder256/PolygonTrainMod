package com.portofino.polygontrainmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.polygontrainmod.blockentity.LargeRailCoreBlockEntity;
import com.portofino.polygontrainmod.client.model.MqoModelLoader;
import com.portofino.polygontrainmod.rail.RailDefinition;
import com.portofino.polygontrainmod.rail.RailRegistry;
import com.portofino.polygontrainmod.rail.util.RailMap;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

public class RailCoreBlockEntityRenderer implements BlockEntityRenderer<LargeRailCoreBlockEntity> {
    private static int computeRailSampleMax(double length) {
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
        try {
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

            int activeIndex = Mth.clamp(be.getActiveSegmentIndex(), 0, maps.length - 1);
            int previousIndex = Mth.clamp(be.getPreviousSegmentIndex(), 0, maps.length - 1);
            float switchProgress = be.getSwitchProgress(partialTick);
            boolean animateSwitch = maps.length > 1 && previousIndex != activeIndex && switchProgress < 0.999F;

            if (maps.length > 1) {
                for (int i = 0; i < maps.length; i++) {
                    RailMap map = maps[i];
                    if (map == null) {
                        continue;
                    }
                    renderRailMap(map, poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model);
                }
                if (animateSwitch && maps[previousIndex] != null && maps[activeIndex] != null) {
                    renderInterpolatedMap(maps[previousIndex], maps[activeIndex], switchProgress, poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model);
                }
                return;
            }

            RailMap activeMap = maps[activeIndex];
            if (activeMap != null) {
                renderRailMap(activeMap, poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model);
            }
        } catch (Throwable t) {
            com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.warn("Skipping rail render at {} after renderer failure", be.getBlockPos(), t);
        }
    }

    private void renderRailMap(
        RailMap map,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        double ox,
        double oy,
        double oz,
        net.minecraft.world.phys.Vec3 mo,
        float scale,
        MqoModelLoader.MqoModel model
    ) {
        double length = map.getLength();
        if (length < 1.0e-4) {
            return;
        }

        int max = computeRailSampleMax(length);
        for (int i = 0; i <= max; i++) {
            double[] point = map.getRailPos(max, i);
            renderRailSample(
                point[1],
                map.getRailHeight(max, i),
                point[0],
                map.getRailYaw(max, i),
                map.getRailPitch(max, i),
                map.getCant(max, i),
                i,
                max,
                poseStack,
                buffer,
                packedLight,
                ox,
                oy,
                oz,
                mo,
                scale,
                model
            );
        }
    }

    private void renderInterpolatedMap(
        RailMap previousMap,
        RailMap activeMap,
        float progress,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        double ox,
        double oy,
        double oz,
        net.minecraft.world.phys.Vec3 mo,
        float scale,
        MqoModelLoader.MqoModel model
    ) {
        int previousMax = computeRailSampleMax(previousMap.getLength());
        int activeMax = computeRailSampleMax(activeMap.getLength());
        int max = Math.max(previousMax, activeMax);
        for (int i = 0; i <= max; i++) {
            float t = max <= 0 ? 0.0F : i / (float) max;
            int previousIndex = Mth.clamp(Math.round(t * previousMax), 0, previousMax);
            int activeIndex = Mth.clamp(Math.round(t * activeMax), 0, activeMax);
            double[] previousPoint = previousMap.getRailPos(previousMax, previousIndex);
            double[] activePoint = activeMap.getRailPos(activeMax, activeIndex);
            double wx = Mth.lerp(progress, previousPoint[1], activePoint[1]);
            double wy = Mth.lerp(progress, previousMap.getRailHeight(previousMax, previousIndex), activeMap.getRailHeight(activeMax, activeIndex));
            double wz = Mth.lerp(progress, previousPoint[0], activePoint[0]);
            float yaw = Mth.rotLerp(progress, previousMap.getRailYaw(previousMax, previousIndex), activeMap.getRailYaw(activeMax, activeIndex));
            float pitch = Mth.rotLerp(progress, previousMap.getRailPitch(previousMax, previousIndex), activeMap.getRailPitch(activeMax, activeIndex));
            float roll = Mth.rotLerp(progress, previousMap.getCant(previousMax, previousIndex), activeMap.getCant(activeMax, activeIndex));
            renderRailSample(wx, wy, wz, yaw, pitch, roll, i, max, poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model);
        }
    }

    private void renderRailSample(
        double wx,
        double wy,
        double wz,
        float yaw,
        float pitch,
        float roll,
        int pos,
        int max,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        double ox,
        double oy,
        double oz,
        net.minecraft.world.phys.Vec3 mo,
        float scale,
        MqoModelLoader.MqoModel model
    ) {
        poseStack.pushPose();
        float yBump = depthJitter(pos);
        poseStack.translate(wx - ox, wy - oy - 0.0625 + yBump, wz - oz);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        poseStack.translate(mo.x, mo.y, mo.z);
        poseStack.scale(scale, scale, scale);
        MqoModelLoader.renderModel(model, poseStack, buffer, packedLight,
            groupName -> {
                if (groupName.startsWith("side") && pos != 0 && pos != max) {
                    return false;
                }
                return true;
            });
        poseStack.popPose();
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
            int max = computeRailSampleMax(length);
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

    @Override
    public int getViewDistance() {
        return 512;
    }
}
