package com.portofino.polygontrainmod.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PolygonTrainModNetwork {
    private PolygonTrainModNetwork() {
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SelectModelPayload.TYPE, SelectModelPayload.STREAM_CODEC, SelectModelPayload::handleOnServer);
        registrar.playToServer(TrainControlPayload.TYPE, TrainControlPayload.STREAM_CODEC, TrainControlPayload::handleOnServer);
    }
}
