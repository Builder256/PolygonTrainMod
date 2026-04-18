package com.portofino.polygontrainmod.client;

import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.entity.TrainEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = PolygonTrainMod.MODID, value = Dist.CLIENT)
public final class TrainHudOverlay {
    private static final ResourceLocation CAB_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(PolygonTrainMod.MODID, "textures/gui/cab.png");
    private static final ResourceLocation REVERSER_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(PolygonTrainMod.MODID, "textures/gui/reverser.png");
    private static final ResourceLocation MASCON_SELECTOR_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(PolygonTrainMod.MODID, "textures/gui/mascon_selector.png");
    private static final int CAB_TEX_W = 3300;
    private static final int CAB_TEX_H = 510;
    private static final int LARGE_TEX_SIZE = 4800;
    private static boolean cabHidden;

    private TrainHudOverlay() {
    }

    public static void toggleCabHidden() {
        cabHidden = !cabHidden;
    }

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        if (!(mc.player.getVehicle() instanceof TrainEntity train) || !train.isDriverPassenger(mc.player)) {
            return;
        }

        ResourceLocation layer = event.getName();
        if (VanillaGuiLayers.HOTBAR.equals(layer)
            || VanillaGuiLayers.SELECTED_ITEM_NAME.equals(layer)
            || VanillaGuiLayers.EXPERIENCE_BAR.equals(layer)
            || VanillaGuiLayers.EXPERIENCE_LEVEL.equals(layer)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mc.screen != null) {
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

        int cabW = screenW;
        int cabH = Math.max(48, Math.round(cabW * (CAB_TEX_H / (float) CAB_TEX_W)));
        int cabY = screenH - cabH;
        if (!cabHidden) {
            g.blit(CAB_TEXTURE, 0, cabY, cabW, cabH, 0.0F, 0.0F, CAB_TEX_W, CAB_TEX_H, CAB_TEX_W, CAB_TEX_H);

            int notch = train.getNotch();
            int selectorW = Math.max(14, Math.round(cabW * 0.026F));
            int selectorH = Math.max(3, Math.round(cabH * 0.018F));
            int selectorX = Math.round(cabW * 0.225F);
            int neutralY = cabY + Math.round(cabH * 0.685F);
            int stepY = Math.max(3, Math.round(cabH * 0.043F));
            int selectorY = neutralY - notch * stepY;
            g.blit(MASCON_SELECTOR_TEXTURE, selectorX, selectorY, selectorW, selectorH, 0.0F, 0.0F, LARGE_TEX_SIZE, LARGE_TEX_SIZE, LARGE_TEX_SIZE, LARGE_TEX_SIZE);

            int reverser = train.getReverser();
            int revSize = Math.max(5, Math.round(cabH * 0.075F));
            int revX = Math.round(cabW * 0.101F);
            float revRatio = reverser > 0 ? 0.29F : reverser < 0 ? 0.455F : 0.385F;
            int revY = cabY + Math.round(cabH * revRatio);
            g.blit(REVERSER_TEXTURE, revX, revY, revSize, revSize, 0.0F, 0.0F, LARGE_TEX_SIZE, LARGE_TEX_SIZE, LARGE_TEX_SIZE, LARGE_TEX_SIZE);
        }

        float speedMs = Math.abs(train.getSpeed() * 20.0F);
        int speedKmh = Math.round(speedMs * 3.6F);
        String line = speedKmh + " km/h";
        int x = screenW - font.width(line) - 8;
        int y = 8;
        g.drawString(font, line, x, y, 0xFFFFFF, true);
    }
}
