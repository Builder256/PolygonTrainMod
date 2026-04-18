package com.portofino.polygontrainmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.polygontrainmod.client.model.MqoModelLoader;
import com.portofino.polygontrainmod.entity.TrainEntity;
import com.portofino.polygontrainmod.script.TrainScriptSystem;
import com.portofino.polygontrainmod.vehicle.VehicleDefinition;
import com.portofino.polygontrainmod.vehicle.VehicleRegistry;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class TrainEntityRenderer extends EntityRenderer<TrainEntity> {
    public TrainEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(TrainEntity entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public void render(TrainEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        VehicleDefinition def = VehicleRegistry.getById(entity.getVehicleId());
        if (def == null) {
            com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.error("Vehicle definition not found for ID: {}", entity.getVehicleId());
            return;
        }

        com.portofino.polygontrainmod.client.model.MqoModelLoader.MqoModel model = MqoModelLoader.loadModelForVehicle(def);
        if (model == null) {
            com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.warn("Model is null, falling back to wireframe box");
            renderWireframeBox(entity, poseStack, buffer, packedLight);
            return;
        }

        if (model.getScriptEngine() != null) {
            entity.setScriptEngine(model.getScriptEngine());
        }

        boolean failed = false;
        try {
            poseStack.pushPose();
            float renderYaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
            poseStack.mulPose(Axis.YP.rotationDegrees(renderYaw));

            // Apply model offset and scale
            poseStack.translate(def.getModelOffset().x, def.getModelOffset().y, def.getModelOffset().z);
            poseStack.scale(def.getModelScale(), def.getModelScale(), def.getModelScale());

            MqoModelLoader.renderModel(model, poseStack, buffer, packedLight, (stack, groupName) -> {
                applyDoorTransform(stack, def.getLeftDoors(), groupName, entity.doorMoveL);
                applyDoorTransform(stack, def.getRightDoors(), groupName, entity.doorMoveR);
            }, entity);

            // Render bogies
            for (VehicleDefinition.BogieDefinition bogieDef : def.getBogies()) {
                BogieRenderer.renderBogie(poseStack, bogieDef, def, entity, buffer, packedLight, renderYaw);
            }
        } catch (Exception e) {
            com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.error("Failed to render model", e);
            failed = true;
        } finally {
            poseStack.popPose();
        }
        if (failed) {
            renderWireframeBox(entity, poseStack, buffer, packedLight);
        }
    }

    private static void applyDoorTransform(PoseStack poseStack, java.util.List<VehicleDefinition.DoorAnimationDefinition> doors, String groupName, float progressTicks) {
        if (doors == null || doors.isEmpty() || groupName == null) {
            return;
        }
        float progress = smoothstep(Mth.clamp(progressTicks / 60.0F, 0.0F, 1.0F));
        for (VehicleDefinition.DoorAnimationDefinition door : doors) {
            if (!door.objects().contains(groupName)) {
                continue;
            }
            poseStack.translate(
                door.openTranslation().x * progress,
                door.openTranslation().y * progress,
                door.openTranslation().z * progress
            );
            return;
        }
    }

    private static float smoothstep(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    private void renderWireframeBox(TrainEntity entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        net.minecraft.world.phys.AABB box = entity.getBoundingBox();
        poseStack.pushPose();

        float minX = (float) (box.minX - entity.getX());
        float minY = (float) (box.minY - entity.getY());
        float minZ = (float) (box.minZ - entity.getZ());
        float maxX = (float) (box.maxX - entity.getX());
        float maxY = (float) (box.maxY - entity.getY());
        float maxZ = (float) (box.maxZ - entity.getZ());

        var consumer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.lines());
        net.minecraft.client.renderer.LevelRenderer.renderLineBox(poseStack, consumer, minX, minY, minZ, maxX, maxY, maxZ, 0.0f, 1.0f, 0.0f, 1.0f);

        poseStack.popPose();
    }
}
