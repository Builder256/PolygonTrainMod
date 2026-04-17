package com.portofino.polygontrainmod.client;

import com.portofino.polygontrainmod.PolygonTrainModComponents;
import com.portofino.polygontrainmod.client.screen.ModelSelectScreen;
import com.portofino.polygontrainmod.client.screen.TrainFormationScreen;
import com.portofino.polygontrainmod.network.SelectModelPayload;
import com.portofino.polygontrainmod.rail.RailRegistry;
import com.portofino.polygontrainmod.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class ClientItemHelper {
    private ClientItemHelper() {}

    public static void openRailSelectScreen(Player player, ItemStack stack) {
        List<ModelSelectScreen.ModelInfo> infos = RailRegistry.getAll().stream()
            .map(d -> new ModelSelectScreen.ModelInfo(d.getId(), d.getDisplayName()))
            .toList();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable("screen.polygontrainmod.select_rail"),
            infos,
            modelId -> PacketDistributor.sendToServer(new SelectModelPayload(modelId))
        ));
    }

    public static void openTrainSelectScreen(Player player, ItemStack stack) {
        List<ModelSelectScreen.ModelInfo> infos = VehicleRegistry.getAll().stream()
            .map(d -> new ModelSelectScreen.ModelInfo(d.getId(), d.getDisplayName()))
            .toList();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable("screen.polygontrainmod.select_train"),
            infos,
            modelId -> PacketDistributor.sendToServer(new SelectModelPayload(modelId))
        ));
    }

    public static void openTrainSelectScreen(TrainFormationScreen formationScreen) {
        List<ModelSelectScreen.ModelInfo> infos = VehicleRegistry.getAll().stream()
            .map(d -> new ModelSelectScreen.ModelInfo(d.getId(), d.getDisplayName()))
            .toList();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable("screen.polygontrainmod.select_train"),
            infos,
            modelId -> {
                formationScreen.updateFormationWithVehicle(modelId);
            }
        ));
    }

    public static void openTrainSelectScreen() {
        List<ModelSelectScreen.ModelInfo> infos = VehicleRegistry.getAll().stream()
            .map(d -> new ModelSelectScreen.ModelInfo(d.getId(), d.getDisplayName()))
            .toList();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable("screen.polygontrainmod.select_train"),
            infos,
            modelId -> PacketDistributor.sendToServer(new SelectModelPayload(modelId))
        ));
    }

    public static void openVehicleFormationScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new TrainFormationScreen(stack));
    }
}
