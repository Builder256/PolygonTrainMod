package com.portofino.polygontrainmod.client.screen;

import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.entity.TrainEntity;
import com.portofino.polygontrainmod.network.TrainControlPayload;
import com.portofino.polygontrainmod.vehicle.VehicleDefinition;
import com.portofino.polygontrainmod.vehicle.VehicleRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public class TrainControlScreen extends Screen {

    private static final ResourceLocation CAB_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(PolygonTrainMod.MODID, "textures/gui/cab.png");
    private static final ResourceLocation REVERSER_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(PolygonTrainMod.MODID, "textures/gui/reverser.png");
    private static final ResourceLocation MASCON_SELECTOR_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(PolygonTrainMod.MODID, "textures/gui/mascon_selector.png");
    private static final int CAB_TEX_W = 3300;
    private static final int CAB_TEX_H = 510;
    private static final int LARGE_TEX_SIZE = 4800;

    private final TrainEntity train;

    public TrainControlScreen(TrainEntity train) {
        super(Component.literal("Train Control"));
        this.train = train;
    }

    @Override
    protected void init() {
        int available = Math.max(240, this.width - 24);
        int colGap = 8;
        int w = Math.max(72, Math.min(118, (available - colGap * 2) / 3));
        int h = 20;
        int gap = 26;
        int x = Math.max(12, (this.width - (w * 3 + colGap * 2)) / 2);
        int maxRows = 7;
        int y = Math.max(12, this.height - scaledCabHeight() - gap * maxRows - 8);
        VehicleDefinition def = VehicleRegistry.getById(train.getVehicleId());

        addRenderableWidget(Button.builder(Component.literal("前照灯"), b -> send("set_light_mode", 1))
            .bounds(x, y, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("尾灯"), b -> send("set_light_mode", 2))
            .bounds(x, y + gap, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("前照灯・尾灯"), b -> send("set_light_mode", 3))
            .bounds(x, y + gap * 2, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("消灯"), b -> send("set_light_mode", 0))
            .bounds(x, y + gap * 3, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("前"), b -> send("set_reverser", 1))
            .bounds(x, y + gap * 4, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("中"), b -> send("set_reverser", 0))
            .bounds(x, y + gap * 5, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("後"), b -> send("set_reverser", -1))
            .bounds(x, y + gap * 6, w, h).build());

        int x2 = x + w + colGap;
        addRenderableWidget(Button.builder(Component.literal("両ドア"), b -> send("toggle_door"))
            .bounds(x2, y, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("左ドア"), b -> send("toggle_door_left"))
            .bounds(x2, y + gap, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("右ドア"), b -> send("toggle_door_right"))
            .bounds(x2, y + gap * 2, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("パンタ"), b -> send("toggle_pantograph"))
            .bounds(x2, y + gap * 3, w, h).build());

        int x3 = x2 + w + colGap;
        String[] rollsignNames = train.getResourceState().getResourceSet().getConfig().rollsignNames;
        int destinationCount = Math.max(1, rollsignNames.length);
        String destinationName = rollsignNames[Math.floorMod(train.getDestinationIndex(), destinationCount)];
        addRenderableWidget(Button.builder(Component.literal("方向幕 " + destinationName), b -> send("next_destination"))
            .bounds(x3, y, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("音 " + (train.getSoundIndex() + 1)), b -> send("next_sound"))
            .bounds(x3, y + gap, w, h).build());
        if (def != null && def.getScriptPath() != null && !def.getScriptPath().isBlank()) {
            for (int i = 0; i < 4; i++) {
                final int index = i;
                addRenderableWidget(Button.builder(Component.literal("ボタン " + (i + 1)), b -> send("toggle_custom_button", index))
                    .bounds(x3, y + gap * (i + 2), w, h).build());
            }
        }
    }

    private void send(String action) {
        send(action, 0);
    }

    private void send(String action, int value) {
        applyLocal(action, value);
        PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), action, value));
        rebuildWidgets();
    }

    private void applyLocal(String action, int value) {
        switch (action) {
            case "set_light_mode" -> train.setLightMode(value);
            case "toggle_door" -> train.setDoorOpen(!train.isDoorOpen());
            case "toggle_door_left" -> train.setDoorLeftOpen(!train.isDoorLeftOpen());
            case "toggle_door_right" -> train.setDoorRightOpen(!train.isDoorRightOpen());
            case "toggle_pantograph" -> train.setPantographUp(!train.isPantographUp());
            case "set_reverser" -> train.setReverser(value);
            case "next_destination" -> {
                int count = Math.max(1, train.getResourceState().getResourceSet().getConfig().rollsignNames.length);
                train.setDestinationIndex((train.getDestinationIndex() + 1) % count);
            }
            case "next_sound" -> train.setSoundIndex(train.getSoundIndex() + 1);
            case "toggle_custom_button" -> train.toggleCustomButton(value);
            default -> {
            }
        }
    }

    private int scaledCabHeight() {
        return Math.max(48, Math.round(this.width * (CAB_TEX_H / (float) CAB_TEX_W)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
