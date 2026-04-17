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

            switch (payload.action()) {
                case "mascon_power" -> train.stepMascon(1);
                case "mascon_brake" -> train.stepMascon(-1);
                case "mascon_neutral" -> train.setNotch(0);
                case "toggle_headlight" -> train.setHeadlightOn(!train.isHeadlightOn());
                case "toggle_door" -> train.setDoorOpen(!train.isDoorOpen());
                case "toggle_pantograph" -> train.setPantographUp(!train.isPantographUp());
                case "toggle_reverse" -> train.setReverse(!train.isReverse());
                case "next_destination" -> train.setDestinationIndex(train.getDestinationIndex() + 1);
                case "next_sound" -> train.setSoundIndex(train.getSoundIndex() + 1);
                case "couple_nearest" -> train.coupleNearest();
                case "decouple" -> train.decouple();
                case "toggle_custom_button" -> train.toggleCustomButton(payload.value());
                default -> {
                }
            }
        });
    }
}
