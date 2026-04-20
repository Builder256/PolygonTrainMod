package com.portofino.polygontrainmod.installedobject;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.rail.RailPackLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class InstalledObjectPackLoader {
    private static final List<InstalledObjectDefinition> LOADED = new ArrayList<>();
    private static boolean loaded;

    private InstalledObjectPackLoader() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        LOADED.clear();
        try {
            loadArchiveDirectory(FMLPaths.GAMEDIR.get());
            Path modsDir = FMLPaths.GAMEDIR.get().resolve("mods");
            if (Files.isDirectory(modsDir)) {
                loadArchiveDirectory(modsDir);
            }
            Path contentDir = FMLPaths.GAMEDIR.get().resolve("content");
            if (Files.isDirectory(contentDir)) {
                loadArchiveDirectory(contentDir);
            }
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.warn("Could not scan installed object packs", e);
        }
        InstalledObjectRegistry.setDefinitions(LOADED);
        PolygonTrainMod.LOGGER.info("Loaded {} installed object definition(s)", LOADED.size());
    }

    public static synchronized void reload() {
        loaded = false;
        load();
    }

    private static void loadArchiveDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(InstalledObjectPackLoader::isSupportedArchive)
                .forEach(path -> {
                    try (InputStream input = Files.newInputStream(path)) {
                        loadPack(input, path.getFileName().toString());
                    } catch (Exception e) {
                        PolygonTrainMod.LOGGER.warn("Failed to load installed object pack {}", path.getFileName(), e);
                    }
                });
        }
    }

    private static boolean isSupportedArchive(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".zip") || fileName.endsWith(".jar");
    }

    private static void loadPack(InputStream zipInput, String packName) throws IOException {
        List<EntryData> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(zipInput)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String normalized = normalize(entry.getName());
                    if (isSupportedJson(normalized)) {
                        entries.add(new EntryData(normalized, zip.readAllBytes()));
                    }
                }
                zip.closeEntry();
            }
        }
        for (EntryData entry : entries) {
            parse(entry.path(), entry.bytes(), packName);
        }
    }

    private static boolean isSupportedJson(String path) {
        String file = leaf(path).toLowerCase(Locale.ROOT);
        return file.startsWith("modelmachine_")
            || file.startsWith("modelsignal_")
            || file.startsWith("modelconnector_")
            || file.startsWith("modelwire_")
            || file.startsWith("modelcrossing_")
            || file.startsWith("signboard_");
    }

    private static void parse(String path, byte[] bytes, String packName) {
        try {
            JsonElement element = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
                return;
            }
            JsonObject obj = element.getAsJsonObject();
            String file = leaf(path);
            String lower = file.toLowerCase(Locale.ROOT);
            if (lower.startsWith("signboard_")) {
                parseSignboard(obj, packName, file);
                return;
            }

            InstalledObjectCategory category = categoryFor(obj, lower);
            JsonObject model = getObject(obj, "model");
            String modelFile = model == null ? null : getString(model, "modelFile");
            if (modelFile == null || modelFile.isBlank()) {
                return;
            }
            String name = firstNonBlank(getString(obj, "name"), getString(obj, "signalName"), file.replace(".json", ""));
            String id = category.name().toLowerCase(Locale.ROOT) + ":" + packName + ":" + name;
            String scriptPath = model == null ? null : getString(model, "rendererPath");
            Vec3 offset = parseVec3(model, "offset", 1.0 / 16.0);
            float scale = parseFloat(model, "scale", 1.0F);
            boolean smoothing = getBoolean(obj, "smoothing", true);
            LOADED.add(new InstalledObjectDefinition(
                id,
                name,
                packName,
                category,
                modelFile,
                scriptPath,
                parseTextures(model),
                offset,
                scale,
                smoothing,
                1.0F,
                1.0F,
                0.125F,
                ""
            ));
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.warn("Failed to parse installed object json {} in {}: {}", path, packName, e.getMessage());
        }
    }

    private static void parseSignboard(JsonObject obj, String packName, String file) {
        String texture = getString(obj, "texture");
        if (texture == null || texture.isBlank()) {
            return;
        }
        String name = file.replace(".json", "");
        String id = InstalledObjectCategory.SIGNBOARD.name().toLowerCase(Locale.ROOT) + ":" + packName + ":" + name;
        LOADED.add(new InstalledObjectDefinition(
            id,
            name,
            packName,
            InstalledObjectCategory.SIGNBOARD,
            "",
            "",
            Map.of(),
            Vec3.ZERO,
            1.0F,
            false,
            (float) getDouble(obj, "width", 1.0),
            (float) getDouble(obj, "height", 1.0),
            (float) getDouble(obj, "depth", 0.125),
            texture
        ));
    }

    private static InstalledObjectCategory categoryFor(JsonObject obj, String lowerFile) {
        String machineType = firstNonBlank(getString(obj, "machineType"), getString(obj, "MachineType")).toLowerCase(Locale.ROOT);
        String name = firstNonBlank(getString(obj, "name"), getString(obj, "signalName")).toLowerCase(Locale.ROOT);
        if (lowerFile.startsWith("modelmachine_")) {
            if (machineType.equals("gate")
                || lowerFile.contains("crossing")
                || lowerFile.contains("fumikiri")
                || lowerFile.contains("cross")
                || name.contains("cross")) {
                return InstalledObjectCategory.CROSSING;
            }
            return InstalledObjectCategory.LIGHT;
        }
        if (lowerFile.startsWith("modelsignal_")) {
            return InstalledObjectCategory.SIGNAL;
        }
        if (lowerFile.startsWith("modelcrossing_") || lowerFile.contains("crossing") || lowerFile.contains("fumikiri")) {
            return InstalledObjectCategory.CROSSING;
        }
        if (lowerFile.startsWith("modelwire_")) {
            return InstalledObjectCategory.WIRE;
        }
        return InstalledObjectCategory.INSULATOR;
    }

    private static String normalize(String value) {
        return value.replace('\\', '/');
    }

    private static String leaf(String value) {
        int idx = value.lastIndexOf('/');
        return idx >= 0 ? value.substring(idx + 1) : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static JsonObject getObject(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null;
    }

    private static String getString(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double getDouble(JsonObject object, String key, double fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsDouble();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float parseFloat(JsonObject object, String key, float fallback) {
        return (float) getDouble(object, key, fallback);
    }

    private static Vec3 parseVec3(JsonObject object, String key, double scale) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return Vec3.ZERO;
        }
        JsonArray array = object.getAsJsonArray(key);
        if (array.size() < 3) {
            return Vec3.ZERO;
        }
        try {
            return new Vec3(array.get(0).getAsDouble() * scale, array.get(1).getAsDouble() * scale, array.get(2).getAsDouble() * scale);
        } catch (Exception e) {
            return Vec3.ZERO;
        }
    }

    private static Map<String, String> parseTextures(JsonObject modelObj) {
        if (modelObj == null || !modelObj.has("textures") || !modelObj.get("textures").isJsonArray()) {
            return Map.of();
        }
        Map<String, String> textures = new HashMap<>();
        JsonArray array = modelObj.getAsJsonArray("textures");
        for (JsonElement element : array) {
            if (!element.isJsonArray()) {
                continue;
            }
            JsonArray pair = element.getAsJsonArray();
            if (pair.size() < 2) {
                continue;
            }
            String material = pair.get(0).getAsString();
            String texture = pair.get(1).getAsString();
            if (!material.isBlank() && !texture.isBlank()) {
                textures.put(material, texture);
            }
        }
        return textures;
    }

    public static Path resolvePackPath(String packName) {
        return RailPackLoader.resolvePackPath(packName);
    }

    private record EntryData(String path, byte[] bytes) {
    }
}
