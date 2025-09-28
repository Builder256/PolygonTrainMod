package com.portofino.polygondtrainmod.client.renderer;

import com.portofino.polygondtrainmod.registry.PolygonTrainModEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class PolygonTrainModRenderers {
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PolygonTrainModEntities.CAR.get(), CarRenderer::new);
    }
}
