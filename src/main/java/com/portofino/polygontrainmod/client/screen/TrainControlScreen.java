package com.portofino.polygontrainmod.client.screen;

import com.portofino.polygontrainmod.entity.TrainEntity;
import com.portofino.polygontrainmod.network.TrainControlPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class TrainControlScreen extends Screen {

    private final TrainEntity train;

    public TrainControlScreen(TrainEntity train) {
        super(Component.literal("Train Control"));
        this.train = train;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 80;
        int y = this.height / 2 - 56;
        int w = 160;
        int h = 20;
        int gap = 22;

        addRenderableWidget(Button.builder(Component.literal("ライト ON/OFF"), b -> send("toggle_headlight"))
            .bounds(x, y, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("ドア 開閉"), b -> send("toggle_door"))
            .bounds(x, y + gap, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("進行方向 切替"), b -> send("toggle_reverse"))
            .bounds(x, y + gap * 2, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("パンタ 上げ下げ"), b -> send("toggle_pantograph"))
            .bounds(x, y + gap * 3, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("方向幕 次へ"), b -> send("next_destination"))
            .bounds(x, y + gap * 4, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("音 次へ"), b -> send("next_sound"))
            .bounds(x, y + gap * 5, w, h).build());
    }

    private void send(String action) {
        PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), action, 0));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, Component.literal("RTM 運転台"), width / 2, height / 2 - 72, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
