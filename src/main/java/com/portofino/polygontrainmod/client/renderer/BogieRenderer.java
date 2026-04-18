package com.portofino.polygontrainmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.polygontrainmod.client.model.MqoModelLoader;
import com.portofino.polygontrainmod.client.model.MqoModelLoader.MqoModel;
import com.portofino.polygontrainmod.entity.TrainEntity;
import com.portofino.polygontrainmod.vehicle.VehicleDefinition;
import net.minecraft.client.renderer.MultiBufferSource;

public class BogieRenderer {
    public static void renderBogie(PoseStack poseStack, VehicleDefinition.BogieDefinition bogieDef, 
                                   VehicleDefinition parentDef, TrainEntity entity, MultiBufferSource buffer, int packedLight) {
        renderBogie(poseStack, bogieDef, parentDef, entity, buffer, packedLight, entity != null ? entity.getYRot() : 0.0F);
    }

    public static void renderBogie(PoseStack poseStack, VehicleDefinition.BogieDefinition bogieDef,
                                   VehicleDefinition parentDef, TrainEntity entity, MultiBufferSource buffer, int packedLight,
                                   float baseYaw) {
        if (bogieDef == null || bogieDef.modelFile() == null || bogieDef.modelFile().isBlank()) {
            return;
        }

        MqoModel bogieModel = MqoModelLoader.loadModelForVehiclePart(parentDef, bogieDef.modelFile(), bogieDef.textureOverrides());
        if (bogieModel == null) {
            return;
        }

        poseStack.pushPose();
        try {
            // Apply bogie position relative to train
            poseStack.translate(bogieDef.position().x, bogieDef.position().y, bogieDef.position().z);
            if (entity != null) {
                poseStack.mulPose(Axis.YP.rotationDegrees(entity.getBogieYawOffset(bogieDef, baseYaw)));
            }

            // Render the bogie model
            MqoModelLoader.renderModel(bogieModel, poseStack, buffer, packedLight);
        } finally {
            poseStack.popPose();
        }
    }
}
