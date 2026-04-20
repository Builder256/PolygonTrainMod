package com.portofino.polygontrainmod.network;

import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.PolygonTrainModComponents;
import com.portofino.polygontrainmod.item.ModelSelectableItem;
import com.portofino.polygontrainmod.item.RailItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SelectModelPayload(String modelId) implements CustomPacketPayload {
    public static final Type<SelectModelPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(PolygonTrainMod.MODID, "select_model")
    );
    public static final StreamCodec<ByteBuf, SelectModelPayload> STREAM_CODEC =
        ByteBufCodecs.STRING_UTF8.map(SelectModelPayload::new, SelectModelPayload::modelId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SelectModelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof com.portofino.polygontrainmod.item.TrainVehicleItem) {
                    stack.set(PolygonTrainModComponents.SELECTED_MODEL_ID.get(), payload.modelId());
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Selected model: " + payload.modelId() + ". Now right-click on rail to spawn."));
                    break;
                }
            }

            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof RailItem
                    || stack.getItem() instanceof com.portofino.polygontrainmod.item.TrainItem
                    || stack.getItem() instanceof ModelSelectableItem) {
                    stack.set(PolygonTrainModComponents.SELECTED_MODEL_ID.get(), payload.modelId());
                    break;
                }
            }
        });
    }
}
