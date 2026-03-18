//package com.portofino.polygontrainmod.client.model;
//
//import net.minecraft.client.model.geom.ModelLayerLocation;
//import net.minecraft.resources.ResourceLocation;
//import net.neoforged.bus.api.SubscribeEvent;
//import net.neoforged.neoforge.client.event.EntityRenderersEvent;
//
//import static com.portofino.polygontrainmod.PolygonTrainMod.MODID;
//
//public class PolygonTrainModEntityRendererLayers {
//    public static final ModelLayerLocation CAR_ENTITY = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MODID, "car"), "main");
//
//    @SubscribeEvent
//    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
//        event.registerLayerDefinition(CAR_ENTITY, CarModel::createBodyLayer);
//    }
//}