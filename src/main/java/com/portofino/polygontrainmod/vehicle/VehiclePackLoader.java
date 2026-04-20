package com.portofino.polygontrainmod.vehicle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.rail.RailPackLoader;
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
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class VehiclePackLoader {
    private static final List<VehicleDefinition> LOADED = new ArrayList<>();
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        LOADED.clear();
        loadFromModJar();
        loadFromExternalDirectories();
        loadFromGameDirectories();
        VehicleRegistry.setDefinitions(LOADED);
        PolygonTrainMod.LOGGER.info("Loaded {} vehicle definition(s)", LOADED.size());
    }

    private static void loadFromModJar() {
        try {
            Path packsDir = ModList.get().getModFileById(PolygonTrainMod.MODID).getFile()
                .findResource("assets", "polygontrainmod", "vehicle_packs");
            if (packsDir != null && Files.exists(packsDir)) {
                scanPackRoot(packsDir);
            }
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.warn("Could not load vehicle packs from mod jar", e);
        }
    }

    private static void loadFromExternalDirectories() {
        for (String dirName : new String[]{"vehicle_packs", "packs", ""}) {
            try {
                Path externalDir = FMLPaths.GAMEDIR.get().resolve("config").resolve("polygontrainmod");
                if (!dirName.isEmpty()) externalDir = externalDir.resolve(dirName);
                if (Files.exists(externalDir)) scanPackRoot(externalDir);
            } catch (Exception e) {
                PolygonTrainMod.LOGGER.warn("Could not scan external vehicle packs {}", dirName, e);
            }
        }
    }

    private static void loadFromGameDirectories() {
        try {
            Path gameDir = FMLPaths.GAMEDIR.get();
            if (Files.exists(gameDir)) {
                scanPackRoot(gameDir);
                Path modsDir = gameDir.resolve("mods");
                if (Files.exists(modsDir)) scanPackRoot(modsDir);
            }
            Path contentDir = gameDir.resolve("content");
            if (Files.exists(contentDir)) scanPackRoot(contentDir);
            Path vehiclePacksDir = gameDir.resolve("vehicle_packs");
            if (Files.exists(vehiclePacksDir)) scanPackRoot(vehiclePacksDir);
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.warn("Could not scan game directory for vehicle packs", e);
        }
    }

    private static void scanPackRoot(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        PolygonTrainMod.LOGGER.info("Scanning vehicle pack root: {}", dir);
        try (var stream = Files.list(dir)) {
            stream.forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        if (looksLikeVehiclePackDirectory(path)) {
                            PolygonTrainMod.LOGGER.info("Scanning vehicle pack directory: {}", path);
                            loadVehiclePackDirectory(path);
                        }
                    } else if (isSupportedArchive(path)) {
                        // 配布形式に依らず同じ入口で処理できるよう、archive としてまとめて扱う。
                        PolygonTrainMod.LOGGER.info("Scanning vehicle pack archive: {}", path.getFileName());
                        loadVehicleZip(path);
                    }
                } catch (Exception e) {
                    PolygonTrainMod.LOGGER.warn("Failed to scan vehicle pack {}", path.getFileName(), e);
                }
            });
        }
    }

    private static boolean looksLikeVehiclePackDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        if (Files.exists(dir.resolve("assets")) || Files.exists(dir.resolve("scripts")) || Files.exists(dir.resolve("textures"))) {
            return true;
        }
        try (var stream = Files.walk(dir, 4)) {
            return stream
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                .anyMatch(name -> name.endsWith(".json") && (name.startsWith("modeltrain_") || name.startsWith("train_")));
        } catch (IOException e) {
            PolygonTrainMod.LOGGER.warn("Could not inspect vehicle pack directory {}", dir.getFileName(), e);
            return false;
        }
    }

    private static void loadVehicleZip(Path zipPath) throws IOException {
        try (InputStream is = Files.newInputStream(zipPath)) {
            loadVehiclePack(is, zipPath.getFileName().toString());
        }
    }

    private static boolean isSupportedArchive(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".zip") || fileName.endsWith(".jar");
    }

    private static void loadVehiclePackDirectory(Path packDir) throws IOException {
        String packName = packDir.getFileName().toString();
        try (var stream = Files.walk(packDir)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".json"))
                .forEach(path -> {
                    try {
                        parseTrainJson(Files.readAllBytes(path), packName);
                    } catch (Exception e) {
                        PolygonTrainMod.LOGGER.warn("Failed to load vehicle pack json {} in {}", path, packName, e);
                    }
                });
        }
    }

    public static synchronized void reload() {
        loaded = false;
        load();
    }

    private static void loadVehiclePack(InputStream zipInput, String packName) throws IOException {
        List<byte[]> jsonBytes = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(zipInput)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String name = normalize(entry.getName());
                    if (isTrainJson(name)) {
                        jsonBytes.add(zip.readAllBytes());
                    }
                }
                zip.closeEntry();
            }
        }
        for (byte[] bytes : jsonBytes) {
            parseTrainJson(bytes, packName);
        }
    }

    private static boolean isTrainJson(String path) {
        return path.toLowerCase().endsWith(".json");
    }

    private static String normalize(String raw) {
        return raw.replace('\\', '/');
    }

    private static void parseTrainJson(byte[] bytes, String packName) {
        try {
            JsonElement el = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!el.isJsonObject()) return;
            JsonObject obj = el.getAsJsonObject();
            String id = getString(obj, "trainName");
            if (id == null || id.isBlank()) id = "train";
            String displayName = id;
            JsonObject trainModel = getObject(obj, "trainModel2");
            if (trainModel == null) trainModel = getObject(obj, "trainModel");
            if (trainModel == null) return;
            String modelFile = getString(trainModel, "modelFile");
            if (modelFile == null || modelFile.isBlank()) return;
            Map<String, String> tex = parseTextures(trainModel);
            Vec3 offset = parseVec3(trainModel, "offset", 1.0 / 16.0);
            float scale = parseFloat(trainModel, "scale", 1.0F);
            String scriptPath = getString(trainModel, "rendererPath");
            if (scriptPath == null || scriptPath.isBlank()) {
                scriptPath = getString(trainModel, "renderScriptPath");
            }
            if (scriptPath == null || scriptPath.isBlank()) {
                scriptPath = getString(obj, "rendererPath");
            }
            if (scriptPath == null || scriptPath.isBlank()) {
                scriptPath = getString(obj, "renderScriptPath");
            }
            if (scriptPath == null || scriptPath.isBlank()) {
                scriptPath = getString(obj, "scriptPath");
            }

            String doorType = getString(trainModel, "doorType");
            if (doorType == null || doorType.isBlank()) {
                doorType = getString(obj, "doorType");
            }

            List<Vec3> bogiePositions = new ArrayList<>();
            appendRawArray(trainModel, "bogiePos", bogiePositions);
            appendRawArray(obj, "bogiePos", bogiePositions);
            List<VehicleDefinition.BogieDefinition> bogies = new ArrayList<>();
            if (obj.has("bogieModel3") && obj.get("bogieModel3").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("bogieModel3");
                for (int i = 0; i < arr.size(); i++) {
                    JsonElement element = arr.get(i);
                    if (!element.isJsonObject()) continue;
                    JsonObject bogieModel = element.getAsJsonObject();
                    String bogieFile = getString(bogieModel, "modelFile");
                    if (bogieFile == null || bogieFile.isBlank()) continue;
                    Map<String, String> bogieTex = parseTextures(bogieModel);
                    Vec3 position = i < bogiePositions.size() ? bogiePositions.get(i) : Vec3.ZERO;
                    bogies.add(new VehicleDefinition.BogieDefinition(bogieFile, bogieTex, position));
                }
            } else {
                JsonObject bogieModel = getObject(obj, "bogieModel2");
                if (bogieModel == null) bogieModel = getObject(obj, "bogieModel");
                Map<String, String> bogieTex = bogieModel != null ? parseTextures(bogieModel) : Map.of();
                String bogieFile = bogieModel != null ? getString(bogieModel, "modelFile") : null;
                if (bogieFile != null && !bogieFile.isBlank()) {
                    if (bogiePositions.isEmpty()) {
                        bogiePositions.add(new Vec3(0.0, 0.0, 0.0));
                    }
                    for (Vec3 p : bogiePositions) {
                        bogies.add(new VehicleDefinition.BogieDefinition(bogieFile, bogieTex, p));
                    }
                }
            }
            // seatPos: integer values in 1/16-block units → divide by 16
            List<Vec3> seats = new ArrayList<>();
            appendSeatArray(trainModel, "seatPos", seats);
            appendSeatArray(obj, "seatPos", seats);
            // seatPosF: float values already in block units → no division
            appendRawArray(trainModel, "seatPosF", seats);
            appendRawArray(obj, "seatPosF", seats);

            // playerPos / playerPosF: float values already in block units → no division
            List<Vec3> playerPositions = new ArrayList<>();
            appendRawArray(trainModel, "playerPos", playerPositions);
            appendRawArray(obj, "playerPos", playerPositions);
            appendRawArray(trainModel, "playerPosF", playerPositions);
            appendRawArray(obj, "playerPosF", playerPositions);

            Vec3 seatOffset = !playerPositions.isEmpty() ? playerPositions.get(0) : (!seats.isEmpty() ? seats.get(0) : null);
            float trainDistance = parseFloat(trainModel, "trainDistance", parseFloat(obj, "trainDistance", 4.5F));
            int driverSeatIndex = parseInt(trainModel, "driverSeatIndex", parseInt(obj, "driverSeatIndex", 0));
            List<Vec3> selectableSeats = new ArrayList<>();
            selectableSeats.addAll(playerPositions);
            selectableSeats.addAll(seats);
            int frontDriverSeatIndex = resolveFrontDriverSeatIndex(obj, trainModel, selectableSeats, driverSeatIndex);
            int rearDriverSeatIndex = resolveRearDriverSeatIndex(obj, trainModel, selectableSeats, frontDriverSeatIndex);
            List<VehicleDefinition.DoorAnimationDefinition> leftDoors = parseDoorAnimations(obj, trainModel, "door_left");
            List<VehicleDefinition.DoorAnimationDefinition> rightDoors = parseDoorAnimations(obj, trainModel, "door_right");
            List<Float> notchMaxSpeeds = parseFloatList(obj, trainModel, "maxSpeed");
            float acceleration = parseFloat(trainModel, "acceleration",
                parseFloat(trainModel, "accelerateion", parseFloat(obj, "acceleration", parseFloat(obj, "accelerateion", 0.00243F))));
            boolean smoothing = parseBoolean(trainModel, "smoothing", parseBoolean(obj, "smoothing", false));

            LOADED.add(new VehicleDefinition(
                id,
                displayName,
                packName,
                modelFile,
                tex,
                offset,
                scale,
                bogies,
                seats,
                playerPositions,
                seatOffset,
                scriptPath,
                doorType,
                trainDistance,
                driverSeatIndex,
                frontDriverSeatIndex,
                rearDriverSeatIndex,
                leftDoors,
                rightDoors,
                notchMaxSpeeds,
                acceleration,
                smoothing
            ));
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.warn("Failed to parse train json in {}: {}", packName, e.getMessage());
        }
    }

    private static int resolveFrontDriverSeatIndex(JsonObject root, JsonObject trainModel, List<Vec3> seats, int fallback) {
        int configured = parseInt(trainModel, "frontDriverSeatIndex", parseInt(root, "frontDriverSeatIndex", Integer.MIN_VALUE));
        if (configured != Integer.MIN_VALUE) {
            return configured;
        }

        int configuredDriverSeat = parseInt(trainModel, "driverSeatIndex", parseInt(root, "driverSeatIndex", Integer.MIN_VALUE));
        if (configuredDriverSeat != Integer.MIN_VALUE) {
            return configuredDriverSeat;
        }

        if (seats.isEmpty()) {
            return fallback;
        }

        return findExtremeSeatIndexByZ(seats, true);
    }

    private static int resolveRearDriverSeatIndex(JsonObject root, JsonObject trainModel, List<Vec3> seats, int fallbackFrontIndex) {
        int configured = parseInt(trainModel, "rearDriverSeatIndex", parseInt(root, "rearDriverSeatIndex", Integer.MIN_VALUE));
        if (configured != Integer.MIN_VALUE) {
            return configured;
        }

        if (seats.isEmpty()) {
            return fallbackFrontIndex;
        }

        return findExtremeSeatIndexByZ(seats, false);
    }

    private static int findExtremeSeatIndexByZ(List<Vec3> seats, boolean front) {
        if (seats.isEmpty()) {
            return 0;
        }

        int bestIndex = 0;
        double bestZ = seats.get(0).z;
        for (int i = 1; i < seats.size(); i++) {
            double z = seats.get(i).z;
            if (front ? z > bestZ : z < bestZ) {
                bestZ = z;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static List<VehicleDefinition.DoorAnimationDefinition> parseDoorAnimations(JsonObject root, JsonObject trainModel, String key) {
        List<VehicleDefinition.DoorAnimationDefinition> doors = new ArrayList<>();
        appendDoorAnimations(trainModel, key, doors);
        appendDoorAnimations(root, key, doors);
        return doors;
    }

    private static void appendDoorAnimations(JsonObject obj, String key, List<VehicleDefinition.DoorAnimationDefinition> out) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) return;
        for (JsonElement element : obj.getAsJsonArray(key)) {
            if (!element.isJsonObject()) continue;
            JsonObject door = element.getAsJsonObject();
            List<String> objects = new ArrayList<>();
            if (door.has("objects") && door.get("objects").isJsonArray()) {
                for (JsonElement objectElement : door.getAsJsonArray("objects")) {
                    if (objectElement.isJsonPrimitive()) {
                        String name = objectElement.getAsString();
                        if (!name.isBlank()) objects.add(name);
                    }
                }
            }
            if (objects.isEmpty()) continue;
            Vec3 pos = parseVec3(door, "pos", 1.0D);
            Vec3 translation = parseDoorTranslation(door);
            out.add(new VehicleDefinition.DoorAnimationDefinition(objects, pos, translation));
        }
    }

    private static Vec3 parseDoorTranslation(JsonObject door) {
        if (door == null || !door.has("transform") || !door.get("transform").isJsonArray()) {
            return Vec3.ZERO;
        }
        JsonArray transforms = door.getAsJsonArray("transform");
        if (transforms.isEmpty() || !transforms.get(0).isJsonArray()) {
            return Vec3.ZERO;
        }
        JsonArray first = transforms.get(0).getAsJsonArray();
        if (first.size() < 3) {
            return Vec3.ZERO;
        }
        try {
            return new Vec3(first.get(0).getAsDouble(), first.get(1).getAsDouble(), first.get(2).getAsDouble());
        } catch (Exception e) {
            return Vec3.ZERO;
        }
    }

    private static List<Float> parseFloatList(JsonObject root, JsonObject trainModel, String key) {
        JsonArray array = null;
        if (trainModel != null && trainModel.has(key) && trainModel.get(key).isJsonArray()) {
            array = trainModel.getAsJsonArray(key);
        } else if (root != null && root.has(key) && root.get(key).isJsonArray()) {
            array = root.getAsJsonArray(key);
        }
        if (array == null) {
            return List.of();
        }
        List<Float> values = new ArrayList<>();
        for (JsonElement element : array) {
            try {
                values.add(element.getAsFloat());
            } catch (Exception ignored) {
            }
        }
        return values;
    }

    /** seatPos: integer values in 1/16-block units, divide by 16 */
    private static void appendSeatArray(JsonObject obj, String key, List<Vec3> out) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return;
        for (JsonElement e : obj.getAsJsonArray(key)) {
            if (!e.isJsonArray()) continue;
            JsonArray a = e.getAsJsonArray();
            if (a.size() < 3) continue;
            out.add(new Vec3(a.get(0).getAsDouble() / 16.0, a.get(1).getAsDouble() / 16.0, a.get(2).getAsDouble() / 16.0));
        }
    }

    /** playerPos / seatPosF / bogiePos: float values already in block units, no division */
    private static void appendRawArray(JsonObject obj, String key, List<Vec3> out) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) return;
        for (JsonElement e : obj.getAsJsonArray(key)) {
            if (!e.isJsonArray()) continue;
            JsonArray a = e.getAsJsonArray();
            if (a.size() < 3) continue;
            out.add(new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()));
        }
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

    private static int parseInt(JsonObject obj, String key, int def) {
        if (obj == null || !obj.has(key)) return def;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean parseBoolean(JsonObject obj, String key, boolean def) {
        if (obj == null || !obj.has(key)) return def;
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception e) {
            return def;
        }
    }

    public static VehicleDefinition getVehicleDefinitionById(String id) {
        return VehicleRegistry.getById(id);
    }

    public static InputStream openPackStream(VehicleDefinition definition) throws IOException {
        if (definition == null) return null;
        Path p = RailPackLoader.resolvePackPath(definition.getPackName());
        return p == null ? null : Files.newInputStream(p);
    }

    public static String readScriptContent(VehicleDefinition definition) {
        if (definition == null || definition.getScriptPath() == null || definition.getScriptPath().isBlank()) {
            return null;
        }
        Path packPath = RailPackLoader.resolvePackPath(definition.getPackName());
        if (packPath == null) {
            return null;
        }
        String scriptPath = normalize(definition.getScriptPath());
        String scriptFileName = scriptPath.contains("/") ? scriptPath.substring(scriptPath.lastIndexOf('/') + 1).toLowerCase() : scriptPath.toLowerCase();

        try {
            if (Files.isDirectory(packPath)) {
                Path resolved = resolveFilePath(packPath, scriptPath);
                if (resolved != null) {
                    return Files.readString(resolved, StandardCharsets.UTF_8);
                }
                // fallback by file name only within pack
                try (var stream = Files.walk(packPath)) {
                    for (Path file : (Iterable<Path>) stream::iterator) {
                        if (!Files.isRegularFile(file)) continue;
                        String name = file.getFileName().toString().toLowerCase();
                        if (name.equals(scriptFileName)) {
                            return Files.readString(file, StandardCharsets.UTF_8);
                        }
                    }
                }
                return null;
            }
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(Files.newInputStream(packPath))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = normalize(entry.getName());
                    if (name.equalsIgnoreCase(scriptPath) || name.toLowerCase().endsWith("/" + scriptFileName) || name.toLowerCase().equals(scriptFileName)) {
                        return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    zip.closeEntry();
                }
            }
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.warn("Failed to read vehicle script {} from pack {}", definition.getScriptPath(), definition.getPackName(), e);
        }
        return null;
    }

    private static Path resolveFilePath(Path root, String relative) throws IOException {
        if (relative == null) return null;
        String norm = relative.replace('\\', '/');
        Path candidate = root.resolve(norm);
        if (Files.exists(candidate) && Files.isRegularFile(candidate)) return candidate;
        candidate = root.resolve("assets/minecraft").resolve(norm);
        if (Files.exists(candidate) && Files.isRegularFile(candidate)) return candidate;
        String leaf = norm.contains("/") ? norm.substring(norm.lastIndexOf('/') + 1) : norm;
        try (var stream = Files.walk(root)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(file)) continue;
                String name = file.getFileName().toString();
                if (name.equalsIgnoreCase(norm) || name.equalsIgnoreCase(leaf)) return file;
            }
        }
        return null;
    }
}
