package com.portofino.polygontrainmod.client;

import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.PolygonTrainModComponents;
import com.portofino.polygontrainmod.client.screen.TrainControlScreen;
import com.portofino.polygontrainmod.entity.TrainEntity;
import com.portofino.polygontrainmod.item.RailItem;
import com.portofino.polygontrainmod.network.RailPreviewAdjustPayload;
import com.portofino.polygontrainmod.network.TrainControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
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

    /**
     * Handles train controls and rail preview adjustments from keyboard input.
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }

        if (handleRailPreviewKey(mc, event.getKey())) {
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
            case GLFW.GLFW_KEY_U -> TrainHudOverlay.toggleCabHidden();
            default -> {
            }
        }
    }

    private static boolean handleRailPreviewKey(Minecraft mc, int key) {
        int dx = 0;
        int dy = 0;
        int dz = 0;
        boolean shift = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> dx = -1;
            case GLFW.GLFW_KEY_RIGHT -> dx = 1;
            case GLFW.GLFW_KEY_UP -> {
                if (shift) {
                    dy = 1;
                } else {
                    dz = -1;
                }
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (shift) {
                    dy = -1;
                } else {
                    dz = 1;
                }
            }
            default -> {
                return false;
            }
        }

        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof RailItem)) {
            stack = mc.player.getOffhandItem();
        }
        if (!(stack.getItem() instanceof RailItem)) {
            return false;
        }
        CompoundTag tag = stack.get(PolygonTrainModComponents.RAIL_PREVIEW_START.get());
        if (tag == null || !tag.contains("X") || !tag.contains("Y") || !tag.contains("Z")) {
            return false;
        }

        if (RailPreviewAdjustPayload.apply(stack, dx, dy, dz)) {
            PacketDistributor.sendToServer(new RailPreviewAdjustPayload(dx, dy, dz));
            CompoundTag updated = stack.get(PolygonTrainModComponents.RAIL_PREVIEW_START.get());
            int ox = updated == null ? 0 : updated.getInt("OffsetX");
            int oy = updated == null ? 0 : updated.getInt("OffsetY");
            int oz = updated == null ? 0 : updated.getInt("OffsetZ");
            mc.player.displayClientMessage(Component.literal(
                String.format("レール調整 X:%+.2f Y:%+.2f Z:%+.2f", ox / 16.0D, oy / 16.0D, oz / 16.0D)
            ), true);
        }
        return true;
    }
}
