package com.portofino.polygontrainmod.rail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portofino.polygontrainmod.PolygonTrainMod;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RailPackLoader {
    private static final List<RailDefinition> LOADED = new ArrayList<>();
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        LOADED.clear();
        loadFromModJar();
        loadFromExternalDirectories();
        loadFromGameDirectories();
        RailRegistry.setDefinitions(LOADED);
        PolygonTrainMod.LOGGER.info("Loaded {} rail definition(s)", LOADED.size());
    }

    private static void loadFromModJar() {
        try {
            Path packsDir = ModList.get().getModFileById(PolygonTrainMod.MODID).getFile()
                .findResource("assets", "polygontrainmod", "rail_packs");
            if (packsDir != null && Files.isDirectory(packsDir)) {
                loadZipDirectory(packsDir);
            }
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.warn("Could not load rail packs from mod jar", e);
        }
    }

    private static void loadFromExternalDirectories() {
        for (String dirName : new String[]{"rail_packs", "packs", ""}) {
            try {
                Path externalDir = FMLPaths.GAMEDIR.get().resolve("config").resolve("polygontrainmod");
                if (!dirName.isEmpty()) externalDir = externalDir.resolve(dirName);
                if (Files.isDirectory(externalDir)) loadZipDirectory(externalDir);
            } catch (Exception e) {
                PolygonTrainMod.LOGGER.warn("Could not scan external rail packs {}", dirName, e);
            }
        }
    }

    private static void loadFromGameDirectories() {
        try {
            Path gameDir = FMLPaths.GAMEDIR.get();
            if (Files.isDirectory(gameDir)) {
                loadZipDirectory(gameDir);
                Path modsDir = gameDir.resolve("mods");
                if (Files.isDirectory(modsDir)) loadZipDirectory(modsDir);
            }
            Path contentDir = gameDir.resolve("content");
            if (Files.isDirectory(contentDir)) loadZipDirectory(contentDir);
            Path vp = gameDir.resolve("vehicle_packs");
            if (Files.isDirectory(vp)) loadZipDirectory(vp);
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.warn("Could not scan game directory for rail packs", e);
        }
    }

    private static void loadZipDirectory(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".zip"))
                .forEach(zipPath -> {
                    try (InputStream is = Files.newInputStream(zipPath)) {
                        loadRailPack(is, zipPath.getFileName().toString());
                    } catch (Exception e) {
                        PolygonTrainMod.LOGGER.warn("Failed to load rail pack {}", zipPath.getFileName(), e);
                    }
                });
        }
    }

    public static synchronized void reload() {
        loaded = false;
        load();
    }

    private static void loadRailPack(InputStream zipInput, String packName) throws IOException {
        List<byte[]> jsonBytes = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(zipInput)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String name = normalize(entry.getName());
                    if (isRailJson(name)) {
                        jsonBytes.add(zip.readAllBytes());
                    }
                }
                zip.closeEntry();
            }
        }
        for (byte[] bytes : jsonBytes) {
            parseRailJson(bytes, packName);
        }
    }

    private static void parseRailJson(byte[] bytes, String packName) {
        try {
            JsonElement el = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!el.isJsonObject()) return;
            JsonObject obj = el.getAsJsonObject();
            String railName = getString(obj, "railName");
            String id = railName != null ? railName : "rail";
            String displayName = railName != null ? railName : id;
            JsonObject model = getObject(obj, "model");
            if (model == null) model = getObject(obj, "railModel");
            if (model == null) model = getObject(obj, "railModel2");
            if (model == null) return;
            String modelFile = getString(model, "modelFile");
            if (modelFile == null || modelFile.isBlank()) return;
            String scriptPath = getString(model, "rendererPath");
            if (scriptPath == null || scriptPath.isBlank()) scriptPath = getString(obj, "scriptPath");
            Map<String, String> tex = parseTextures(model);
            Vec3 offset = parseVec3(model, "offset", 1.0 / 16.0);
            float scale = parseFloat(model, "scale", 1.0F);
            int ballast = obj.has("ballastWidth") ? obj.get("ballastWidth").getAsInt() : 0;
            LOADED.add(new RailDefinition(id, displayName, packName, packName, modelFile, scriptPath, tex, offset, scale, ballast));
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.warn("Failed to parse rail json in {}: {}", packName, e.getMessage());
        }
    }

    private static boolean isRailJson(String path) {
        if (!path.endsWith(".json")) return false;
        String n = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
        return n.startsWith("modelrail_");
    }

    private static String normalize(String raw) {
        return raw.replace('\\', '/');
    }

    private static JsonObject getObject(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null;
    }

    private static String getString(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
    }

    private static Map<String, String> parseTextures(JsonObject modelObj) {
        if (modelObj == null || !modelObj.has("textures") || !modelObj.get("textures").isJsonArray()) {
            return Map.of();
        }
        Map<String, String> overrides = new HashMap<>();
        JsonArray array = modelObj.getAsJsonArray("textures");
        for (JsonElement entry : array) {
            if (!entry.isJsonArray()) continue;
            JsonArray pair = entry.getAsJsonArray();
            if (pair.size() < 2) continue;
            String mat = pair.get(0).getAsString();
            String tex = pair.get(1).getAsString();
            if (!mat.isBlank() && !tex.isBlank()) overrides.put(mat, tex);
        }
        return overrides;
    }

    private static Vec3 parseVec3(JsonObject obj, String key, double scale) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) return Vec3.ZERO;
        JsonArray arr = obj.getAsJsonArray(key);
        if (arr.size() < 3) return Vec3.ZERO;
        try {
            return new Vec3(arr.get(0).getAsDouble() * scale, arr.get(1).getAsDouble() * scale, arr.get(2).getAsDouble() * scale);
        } catch (Exception e) {
            return Vec3.ZERO;
        }
    }

    private static float parseFloat(JsonObject obj, String key, float def) {
        if (obj == null || !obj.has(key)) return def;
        try {
            return obj.get(key).getAsFloat();
        } catch (Exception e) {
            return def;
        }
    }

    public static Path resolvePackPath(String packName) {
        if (packName == null || packName.isBlank()) return null;
        try {
            Path packsDir = ModList.get().getModFileById(PolygonTrainMod.MODID).getFile()
                .findResource("assets", "polygontrainmod", "rail_packs");
            if (packsDir != null) {
                Path p = packsDir.resolve(packName);
                if (Files.exists(p)) return p;
            }
        } catch (Exception ignored) {
        }
        try {
            Path vehiclePacks = ModList.get().getModFileById(PolygonTrainMod.MODID).getFile()
                .findResource("assets", "polygontrainmod", "vehicle_packs");
            if (vehiclePacks != null) {
                Path p = vehiclePacks.resolve(packName);
                if (Files.exists(p)) return p;
            }
        } catch (Exception ignored) {
        }
        for (String dir : new String[]{"rail_packs", "packs", "vehicle_packs", ""}) {
            try {
                Path ext = FMLPaths.GAMEDIR.get().resolve("config").resolve("polygontrainmod");
                if (!dir.isEmpty()) ext = ext.resolve(dir);
                ext = ext.resolve(packName);
                if (Files.exists(ext)) return ext;
            } catch (Exception ignored) {
            }
        }
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path candidate = gameDir.resolve(packName);
        if (Files.exists(candidate)) return candidate;
        Path modsDir = gameDir.resolve("mods").resolve(packName);
        if (Files.exists(modsDir)) return modsDir;
        Path contentDir = gameDir.resolve("content").resolve(packName);
        if (Files.exists(contentDir)) return contentDir;
        return null;
    }

    public static InputStream openPackStream(RailDefinition definition) throws IOException {
        if (definition == null) return null;
        Path p = resolvePackPath(definition.getPackName());
        return p == null ? null : Files.newInputStream(p);
    }

    public static InputStream openPackStreamByName(String packName) throws IOException {
        Path p = resolvePackPath(packName);
        return p == null ? null : Files.newInputStream(p);
    }

    /** スクリプトファイルの内容をパックZIPから読み込む。見つからない場合はnullを返す。 */
    public static String readScriptContent(RailDefinition definition) {
        if (definition == null || definition.getScriptPath() == null || definition.getScriptPath().isBlank()) return null;
        Path packPath = resolvePackPath(definition.getPackName());
        if (packPath == null) return null;
        String scriptPath = normalize(definition.getScriptPath());
        String scriptFileName = scriptPath.contains("/")
            ? scriptPath.substring(scriptPath.lastIndexOf('/') + 1).toLowerCase()
            : scriptPath.toLowerCase();
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(Files.newInputStream(packPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = normalize(entry.getName());
                if (name.equalsIgnoreCase(scriptPath) || name.toLowerCase().endsWith("/" + scriptFileName)
                        || name.toLowerCase().equals(scriptFileName)) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
                zip.closeEntry();
            }
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.warn("Failed to read script {} from pack {}", definition.getScriptPath(), definition.getPackName(), e);
        }
        return null;
    }
}
