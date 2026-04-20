package com.portofino.polygontrainmod.network;

import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.entity.TrainEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrainControlPayload(int trainEntityId, String action, int value) implements CustomPacketPayload {

    public static final Type<TrainControlPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(PolygonTrainMod.MODID, "train_control")
    );

    public static final StreamCodec<ByteBuf, TrainControlPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT,
        TrainControlPayload::trainEntityId,
        ByteBufCodecs.STRING_UTF8,
        TrainControlPayload::action,
        ByteBufCodecs.INT,
        TrainControlPayload::value,
        TrainControlPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(TrainControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level().getEntity(payload.trainEntityId()) instanceof TrainEntity train)) {
                return;
            }
            if (!train.isDriverPassenger(player)) {
                return;
            }
            TrainEntity controlTrain = train.getFormationHead();

            switch (payload.action()) {
                case "mascon_power" -> controlTrain.stepMascon(1);
                case "mascon_brake" -> controlTrain.stepMascon(-1);
                case "mascon_neutral" -> controlTrain.setNotch(0);
                case "toggle_headlight" -> controlTrain.setHeadlightOn(!controlTrain.isHeadlightOn());
                case "set_light_mode" -> controlTrain.setLightModeForFormation(payload.value());
                case "toggle_door" -> controlTrain.toggleDoorForFormation();
                case "toggle_door_left" -> controlTrain.toggleDoorSideForFormation(true);
                case "toggle_door_right" -> controlTrain.toggleDoorSideForFormation(false);
                case "toggle_pantograph" -> controlTrain.setPantographUpForFormation(!controlTrain.isPantographUp());
                case "toggle_reverse" -> controlTrain.setReverse(!controlTrain.isReverse());
                case "set_reverser" -> controlTrain.setReverser(payload.value());
                case "next_destination" -> {
                    int count = Math.max(1, controlTrain.getResourceState().getResourceSet().getConfig().rollsignNames.length);
                    controlTrain.setDestinationIndexForFormation((controlTrain.getDestinationIndex() + 1) % count);
                }
                case "next_sound" -> controlTrain.setSoundIndex(controlTrain.getSoundIndex() + 1);
                case "couple_nearest" -> train.coupleNearest();
                case "decouple" -> train.decouple();
                case "toggle_custom_button" -> controlTrain.toggleCustomButton(payload.value());
                default -> {
                }
            }
        });
    }
}
