package com.portofino.polygontrainmod.client.model.mqo;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static com.portofino.polygontrainmod.PolygonTrainMod.LOGGER;
import static com.portofino.polygontrainmod.PolygonTrainMod.MODID;

@OnlyIn(Dist.CLIENT)
public class MQOLoader {
    public static @Nullable MQOModel load(@NotNull String modelFilePath) {
        final var modelLocation = ResourceLocation.fromNamespaceAndPath(MODID, modelFilePath);
        final var resourceManager = Minecraft.getInstance().getResourceManager();

        MQOModel model = null;

        try (
            final InputStream modelInputStream = resourceManager.open(modelLocation);
            final var modelReader = new BufferedReader(new InputStreamReader(modelInputStream, StandardCharsets.UTF_8))
        ) {
            // TODO: 文字エンコーディングの判別に対応
            final var parseResult = MQOParser.parse(modelReader);
            if (parseResult.status() == MQOParseResultStatus.SUCCESS) model = parseResult.model();

        } catch (IOException e) {
            LOGGER.error("MQOファイルの読み込みに失敗しました。", e);
        }
        return model;
    }
}
