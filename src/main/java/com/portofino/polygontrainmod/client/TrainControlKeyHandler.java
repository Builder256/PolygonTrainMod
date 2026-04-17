package com.portofino.polygontrainmod.client;

import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.client.screen.TrainControlScreen;
import com.portofino.polygontrainmod.entity.TrainEntity;
import com.portofino.polygontrainmod.network.TrainControlPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PolygonTrainMod.MODID, value = Dist.CLIENT)
public final class TrainControlKeyHandler {

    private TrainControlKeyHandler() {
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }

        if (!(mc.player.getVehicle() instanceof TrainEntity train)) {
            return;
        }

        if (!train.isDriverPassenger(mc.player)) {
            return;
        }

        switch (event.getKey()) {
            case GLFW.GLFW_KEY_E -> mc.setScreen(new TrainControlScreen(train));
            case GLFW.GLFW_KEY_S -> PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_power", 0));
            case GLFW.GLFW_KEY_W -> PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_brake", 0));
            case GLFW.GLFW_KEY_X -> PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_neutral", 0));
            default -> {
            }
        }
    }
}
