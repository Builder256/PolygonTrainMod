package com.portofino.polygontrainmod.client;

import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.entity.TrainEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = PolygonTrainMod.MODID, value = Dist.CLIENT)
public final class TrainHudOverlay {

    private TrainHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        if (!(mc.player.getVehicle() instanceof TrainEntity train)) {
            return;
        }

        if (!train.isDriverPassenger(mc.player)) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        float speedMs = Math.abs(train.getSpeed() * 20.0F);
        int speedKmh = Math.round(speedMs * 3.6F);
        int notch = train.getNotch();
        String notchText = notch > 0 ? "P" + notch : notch < 0 ? "B" + (-notch) : "N";

        String line1 = "RTM HUD";
        String line2 = "Speed: " + speedKmh + " km/h";
        String line3 = "Notch: " + notchText;
        String line4 = "S: Power  W: Brake  X: N";

        int x = screenW - 140;
        int y = screenH - 52;
        g.fill(x - 6, y - 6, x + 132, y + 46, 0x88000000);
        g.drawString(font, line1, x, y, 0xFFFFFF, false);
        g.drawString(font, line2, x, y + 12, 0x00FFAA, false);
        g.drawString(font, line3, x, y + 24, 0xFFD966, false);
        g.drawString(font, line4, x, y + 36, 0x99CCFF, false);
    }
}
