package com.portofino.polygontrainmod.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.rail.RailDefinition;
import com.portofino.polygontrainmod.rail.RailPackLoader;
import com.portofino.polygontrainmod.modelpack.VehicleModelPackManager;
import com.portofino.polygontrainmod.script.TrainScriptSystem;
import com.portofino.polygontrainmod.vehicle.VehicleDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;

/**
 * Metasequoia (.mqo) loader aligned with legacy model library {@code MqoModel}: 0.01 vertex scale, triangulation and quad handling.
 */
public final class MqoModelLoader {
    private static final Pattern V_PATTERN = Pattern.compile("V\\((.+?)\\)");
    private static final Pattern UV_PATTERN = Pattern.compile("UV\\((.+?)\\)");
    private static final Pattern M_PATTERN = Pattern.compile("M\\((.+?)\\)");
    private static final Map<String, MqoModel> MODEL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, TextureInfo> TEXTURE_INFO_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<ResourceLocation>> SCRIPT_TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> MISSING_SCRIPT_WARNINGS = ConcurrentHashMap.newKeySet();
    private static ResourceLocation fallbackWhite;

    private MqoModelLoader() {
    }

    public static MqoModel loadModelForRail(RailDefinition def) {
        if (def == null) return null;
        String key = "r|" + def.getPackName() + "|" + def.getModelFile() + "|" + def.getTextureOverrides().hashCode();
        if (MODEL_CACHE.containsKey(key)) {
            return MODEL_CACHE.get(key);
        }
        Path packPath = RailPackLoader.resolvePackPath(def.getPackName());
        if (packPath == null) {
            return null;
        }
        MqoModel model = loadInternal(packPath, def.getModelFile(), def.getTextureOverrides(), false);
        if (model != null) {
            loadScriptForModel(model, packPath, def.getScriptPath());
            MODEL_CACHE.put(key, model);
        }
        return model;
    }

    public static MqoModel loadModelForVehicle(VehicleDefinition def) {
        if (def == null) {
            PolygonTrainMod.LOGGER.warn("loadModelForVehicle: def is null");
            return null;
        }
        String scriptPath = def.getScriptPath() != null ? def.getScriptPath() : "";
        // legacy script は init() で trainName/modelName ごとの差分を固定するため、車両ID単位で分離する
        String key = "v|" + def.getId() + "|" + def.getPackName() + "|" + def.getModelFile() + "|" + def.getTextureOverrides().hashCode() + "|" + scriptPath.hashCode() + "|" + def.isSmoothing();
        if (MODEL_CACHE.containsKey(key)) {
            return MODEL_CACHE.get(key);
        }
        PolygonTrainMod.LOGGER.debug("loadModelForVehicle: vehicleId={}, scriptPath='{}'", def.getId(), def.getScriptPath());
        
        Path packPath = RailPackLoader.resolvePackPath(def.getPackName());
        if (packPath == null) {
            PolygonTrainMod.LOGGER.warn("loadModelForVehicle: packPath is null for pack {}", def.getPackName());
            return null;
        }
        MqoModel model = loadInternal(packPath, def.getModelFile(), def.getTextureOverrides(), def.isSmoothing());
        if (model != null) {
            PolygonTrainMod.LOGGER.info("loadModelForVehicle: model loaded, loading script");
            loadScriptForModel(model, packPath, def.getScriptPath(), def.getId());
            MODEL_CACHE.put(key, model);
        } else {
            PolygonTrainMod.LOGGER.warn("loadModelForVehicle: model is null");
        }
        return model;
    }

    public static MqoModel loadModelForVehiclePart(VehicleDefinition def, String modelFile, Map<String, String> textureOverrides) {
        if (def == null || modelFile == null || modelFile.isBlank()) return null;
        Map<String, String> tex = textureOverrides == null ? Map.of() : textureOverrides;
        String key = "vp|" + def.getPackName() + "|" + modelFile + "|" + tex.hashCode() + "|" + def.isSmoothing();
        return MODEL_CACHE.computeIfAbsent(key, k -> loadInternal(RailPackLoader.resolvePackPath(def.getPackName()), modelFile, tex, def.isSmoothing()));
    }

    public static MqoModel loadModelFromPack(String packName, String modelFile, Map<String, String> textureOverrides,
                                             String scriptPath, boolean smoothing) {
        if (packName == null || modelFile == null || modelFile.isBlank()) {
            return null;
        }
        Map<String, String> tex = textureOverrides == null ? Map.of() : textureOverrides;
        String key = "p|" + packName + "|" + modelFile + "|" + tex.hashCode() + "|" + smoothing + "|" + (scriptPath == null ? 0 : scriptPath.hashCode());
        if (MODEL_CACHE.containsKey(key)) {
            return MODEL_CACHE.get(key);
        }
        Path packPath = RailPackLoader.resolvePackPath(packName);
        if (packPath == null) {
            return null;
        }
        MqoModel model = loadInternal(packPath, modelFile, tex, smoothing);
        if (model != null) {
            loadScriptForModel(model, packPath, scriptPath);
            MODEL_CACHE.put(key, model);
        }
        return model;
    }

    private static MqoModel loadInternal(Path packPath, String modelFile, Map<String, String> textureOverrides, boolean smoothing) {
        if (packPath == null || !Files.exists(packPath)) return null;
        try {
            if (Files.isDirectory(packPath)) {
                Path mqoPath = resolveFilePath(packPath, modelFile);
                if (mqoPath == null) {
                    PolygonTrainMod.LOGGER.warn("MQO not found in pack {}: {}", packPath.getFileName(), modelFile);
                    return null;
                }
                String text = modelFile.toLowerCase(Locale.ROOT).endsWith(".mqoz") ? readCompressedMqo(mqoPath) : Files.readString(mqoPath, StandardCharsets.UTF_8);
                return bake(text, new TextureOpener() {
                    @Override
                    public InputStream open(String rel) throws Exception {
                        return openDirectoryTexture(packPath, rel);
                    }
                    @Override
                    public String getPackKey() {
                        return packPath.toString();
                    }
                }, textureOverrides, smoothing);
            }
            try (ZipFile zf = new ZipFile(packPath.toFile())) {
                ZipEntry mqoEntry = findEntry(zf, modelFile);
                if (mqoEntry == null) {
                    PolygonTrainMod.LOGGER.warn("MQO not found in pack {}: {}", packPath.getFileName(), modelFile);
                    return null;
                }
                String text;
                try (InputStream in = zf.getInputStream(mqoEntry)) {
                    text = modelFile.toLowerCase(Locale.ROOT).endsWith(".mqoz") ? readCompressedMqo(in) : new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                return bake(text, new TextureOpener() {
                    @Override
                    public InputStream open(String rel) throws Exception {
                        return openZipTexture(zf, rel);
                    }
                    @Override
                    public String getPackKey() {
                        return packPath.toString();
                    }
                }, textureOverrides, smoothing);
            }
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.warn("Failed to load MQO {} from {}", modelFile, packPath, e);
            return null;
        }
    }

    private static InputStream openZipTexture(ZipFile zf, String relative) throws java.io.IOException {
        ZipEntry e = findEntry(zf, relative);
        return e == null ? null : zf.getInputStream(e);
    }

    private static InputStream openDirectoryTexture(Path root, String relative) throws java.io.IOException {
        Path file = resolveFilePath(root, relative);
        return file == null ? null : Files.newInputStream(file);
    }

    private static String readCompressedMqo(Path path) throws java.io.IOException {
        try (ZipFile zf = new ZipFile(path.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(zf.entries())) {
                if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".mqo")) {
                    try (InputStream in = zf.getInputStream(entry)) {
                        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
        }
        throw new java.io.IOException("No .mqo entry found inside compressed MQO: " + path);
    }

    private static String readCompressedMqo(InputStream input) throws java.io.IOException {
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            input.transferTo(baos);
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(baos.toByteArray()))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".mqo")) {
                        try (java.io.ByteArrayOutputStream inner = new java.io.ByteArrayOutputStream()) {
                            zis.transferTo(inner);
                            return new String(inner.toByteArray(), StandardCharsets.UTF_8);
                        }
                    }
                }
            }
        }
        throw new java.io.IOException("No .mqo entry found inside compressed MQO stream");
    }

    private static Path resolveFilePath(Path root, String relative) throws java.io.IOException {
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

    private static String normalizeScriptPath(String scriptPath) {
        if (scriptPath == null || scriptPath.isBlank()) {
            return "";
        }
        return scriptPath.replace('\\', '/').replaceFirst("^/+", "");
    }

    private static ZipEntry findEntry(ZipFile zf, String relative) {
        if (relative == null) return null;
        String norm = relative.replace('\\', '/');
        ZipEntry direct = zf.getEntry(norm);
        if (direct != null && !direct.isDirectory()) return direct;
        direct = zf.getEntry("assets/minecraft/" + norm);
        if (direct != null && !direct.isDirectory()) return direct;
        String leaf = norm.contains("/") ? norm.substring(norm.lastIndexOf('/') + 1) : norm;
        String leafLower = leaf.toLowerCase(Locale.ROOT);
        java.util.Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry ze = en.nextElement();
            if (ze.isDirectory()) continue;
            String name = ze.getName().replace('\\', '/');
            if (name.equalsIgnoreCase(norm)) return ze;
            int slash = name.lastIndexOf('/');
            String shortName = slash >= 0 ? name.substring(slash + 1) : name;
            if (shortName.equalsIgnoreCase(leaf) || shortName.equalsIgnoreCase(leafLower)) return ze;
        }
        return null;
    }

    private static MqoModel bake(String mqoText, TextureOpener opener, Map<String, String> textureOverrides, boolean smoothing) throws Exception {
        List<String> materialOrder = new ArrayList<>();
        List<Vec3> currentVerts = new ArrayList<>();
        // key = groupName + "|" + matKey so each object×material pair is a separate batch
        Map<String, BatchBuilder> byGroup = new HashMap<>();
        int mirrorType = -1;
        int braceType = -1;
        String currentGroup = "default";
        Pattern OBJ_NAME = Pattern.compile("Object\\s+\"([^\"]*)\"");

        String[] lines = mqoText.split("\\R");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            if (line.equals("{")) continue;
            if (line.startsWith("}")) {
                braceType = -1;
                continue;
            }
            if (braceType >= 0) {
                if (braceType == 1) {
                    Vec3 v = parseVertexLine(line);
                    if (v != null) currentVerts.add(v);
                } else if (braceType == 2) {
                    addFaceLine(line, currentVerts, materialOrder, textureOverrides, opener, mirrorType, currentGroup, byGroup);
                } else if (braceType == 3) {
                    String[] tok = line.split("\\s+");
                    if (tok.length > 0) {
                        String name = tok[0].replace("\"", "");
                        if (!name.isBlank()) materialOrder.add(name);
                    }
                }
                continue;
            }
            if (line.startsWith("Material ")) { braceType = 3; continue; }
            if (line.startsWith("vertex ")) { currentVerts.clear(); braceType = 1; continue; }
            if (line.startsWith("face ")) { braceType = 2; continue; }
            if (line.startsWith("Object ")) {
                mirrorType = -1;
                Matcher m = OBJ_NAME.matcher(line);
                currentGroup = m.find() ? m.group(1) : "default";
                continue;
            }
            if (line.startsWith("mirror_axis ")) {
                String[] p = line.split("\\s+");
                if (p.length > 1) {
                    int axis = Integer.parseInt(p[1]);
                    mirrorType = axis == 1 ? 0 : axis == 2 ? 1 : axis == 3 ? 2 : -1;
                }
            }
        }

        List<Batch> out = new ArrayList<>();
        for (BatchBuilder bb : byGroup.values()) {
            if (!bb.positions.isEmpty()) out.add(bb.bake(smoothing));
        }
        List<ResourceLocation> materialTextures = new ArrayList<>(materialOrder.size());
        for (int i = 0; i < materialOrder.size(); i++) {
            materialTextures.add(resolveTexture((byte) i, materialOrder, textureOverrides, opener).location);
        }
        return new MqoModel(out, materialTextures);
    }

    private static Vec3 parseVertexLine(String line) {
        String[] t = line.split("\\s+");
        try {
            if (t.length == 2) {
                float x = Float.parseFloat(t[0]) * 0.01f;
                float y = Float.parseFloat(t[1]) * 0.01f;
                return new Vec3(x, y, 0);
            }
            if (t.length >= 3) {
                float x = Float.parseFloat(t[0]) * 0.01f;
                float y = Float.parseFloat(t[1]) * 0.01f;
                float z = Float.parseFloat(t[2]) * 0.01f;
                return new Vec3(x, y, z);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static void addFaceLine(
        String line,
        List<Vec3> verts,
        List<String> materialOrder,
        Map<String, String> textureOverrides,
        TextureOpener opener,
        int mirrorType,
        String groupName,
        Map<String, BatchBuilder> byGroup
    ) throws Exception {
        String[] tokens = line.split("\\s+");
        if (tokens.length == 0) return;
        int vertexCount = Integer.parseInt(tokens[0]);
        if (vertexCount < 3) return;
        byte matId = (byte) parseMaterialId(line);
        TextureInfo textureInfo = resolveTexture(matId, materialOrder, textureOverrides, opener);
        int matKey = matId & 0xFF;
        String vi = matchGroup(V_PATTERN, line);
        String uv = matchGroup(UV_PATTERN, line);
        if (vi == null) return;
        String[] vidx = vi.trim().split("\\s+");
        float[] uvs = parseUv(uv, vertexCount);
        // 材質単位ではなく、この面が触っている UV 範囲だけで半透明判定する。
        boolean translucent = textureInfo.isTranslucent(uvs, vertexCount);
        String batchKey = groupName + "|" + matKey + "|" + translucent;
        BatchBuilder bb = byGroup.computeIfAbsent(batchKey, k -> new BatchBuilder(groupName, textureInfo.location, matKey, translucent));

        if (vertexCount == 4) {
            addQuad(verts, vidx, uvs, matId, bb, mirrorType);
        } else {
            addPolygonFan(verts, vidx, uvs, vertexCount, bb, mirrorType);
        }
    }

    private static void addQuad(List<Vec3> verts, String[] vidx, float[] uvs, byte matId, BatchBuilder bb, int mirrorType) {
        int[] ix = new int[4];
        for (int i = 0; i < 4; i++) ix[i] = Integer.parseInt(vidx[i]);
        Vec3[] p = new Vec3[4];
        float[] u = new float[4];
        float[] v = new float[4];
        for (int i = 0; i < 4; i++) {
            int si = 3 - i;
            p[si] = verts.get(ix[i]);
            if (uvs != null) {
                u[si] = uvs[i * 2];
                v[si] = uvs[i * 2 + 1];
            }
        }
        emitQuad(p[0], p[1], p[2], p[3], u[0], v[0], u[1], v[1], u[2], v[2], u[3], v[3], bb, mirrorType);
    }

    private static void emitQuad(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                                  float u0, float v0, float u1, float v1,
                                  float u2, float v2, float u3, float v3,
                                  BatchBuilder bb, int mirrorType) {
        Vector3f e1 = new Vector3f((float) (p1.x - p0.x), (float) (p1.y - p0.y), (float) (p1.z - p0.z));
        Vector3f e2 = new Vector3f((float) (p2.x - p0.x), (float) (p2.y - p0.y), (float) (p2.z - p0.z));
        Vector3f n = e1.cross(e2);
        if (n.lengthSquared() > 1.0e-8f) n.normalize();
        else n.set(0, 1, 0);
        bb.put(p0, n, u0, v0);
        bb.put(p1, n, u1, v1);
        bb.put(p2, n, u2, v2);
        bb.put(p3, n, u3, v3);
        if (mirrorType >= 0 && mirrorType <= 2 && !isFaceOnMirrorPlane(new Vec3[]{p0, p1, p2, p3}, mirrorType)) {
            Vector3f mn = mirrorN(n, mirrorType);
            bb.put(mirror(p0, mirrorType), mn, u0, v0);
            bb.put(mirror(p3, mirrorType), mn, u3, v3);
            bb.put(mirror(p2, mirrorType), mn, u2, v2);
            bb.put(mirror(p1, mirrorType), mn, u1, v1);
        }
    }

    private static void addPolygonFan(List<Vec3> verts, String[] vidx, float[] uvs, int vertexCount, BatchBuilder bb, int mirrorType) {
        int n = (vertexCount - 2) * 3;
        List<Integer> order = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            int index = i % 3 == 0 ? 0 : i / 3 + i % 3;
            index = (vertexCount - index) % vertexCount;
            order.add(index);
        }
        for (int i = 0; i < order.size(); i += 3) {
            int ia = order.get(i);
            int ib = order.get(i + 1);
            int ic = order.get(i + 2);
            Vec3 pa = verts.get(Integer.parseInt(vidx[ia]));
            Vec3 pb = verts.get(Integer.parseInt(vidx[ib]));
            Vec3 pc = verts.get(Integer.parseInt(vidx[ic]));
            float ua = uvs == null ? 0 : uvs[ia * 2];
            float va = uvs == null ? 0 : uvs[ia * 2 + 1];
            float ub = uvs == null ? 0 : uvs[ib * 2];
            float vb = uvs == null ? 0 : uvs[ib * 2 + 1];
            float uc = uvs == null ? 0 : uvs[ic * 2];
            float vc = uvs == null ? 0 : uvs[ic * 2 + 1];
            emitTri(pa, pb, pc, ua, va, ub, vb, uc, vc, bb, mirrorType);
        }
    }

    private static void emitTri(Vec3 p0, Vec3 p1, Vec3 p2, float u0, float v0, float u1, float v1, float u2, float v2, BatchBuilder bb, int mirrorType) {
        Vector3f e1 = new Vector3f((float) (p1.x - p0.x), (float) (p1.y - p0.y), (float) (p1.z - p0.z));
        Vector3f e2 = new Vector3f((float) (p2.x - p0.x), (float) (p2.y - p0.y), (float) (p2.z - p0.z));
        Vector3f n = e1.cross(e2);
        if (n.lengthSquared() > 1.0e-8f) n.normalize();
        else n.set(0, 1, 0);
        // QUADSモードは4頂点/面が必要 → 3頂点の三角形は縮退クワッドとして扱う (v0,v1,v2,v2)
        bb.put(p0, n, u0, v0);
        bb.put(p1, n, u1, v1);
        bb.put(p2, n, u2, v2);
        bb.put(p2, n, u2, v2);
        if (mirrorType >= 0 && mirrorType <= 2 && !isFaceOnMirrorPlane(new Vec3[]{p0, p1, p2}, mirrorType)) {
            Vector3f mn = mirrorN(n, mirrorType);
            bb.put(mirror(p0, mirrorType), mn, u0, v0);
            bb.put(mirror(p2, mirrorType), mn, u2, v2);
            bb.put(mirror(p1, mirrorType), mn, u1, v1);
            bb.put(mirror(p1, mirrorType), mn, u1, v1);
        }
    }

    private static Vec3 mirror(Vec3 p, int type) {
        float x = (float) p.x;
        float y = (float) p.y;
        float z = (float) p.z;
        float[] m = switch (type) {
            case 0 -> new float[]{-1, 1, 1};
            case 1 -> new float[]{1, -1, 1};
            default -> new float[]{1, 1, -1};
        };
        return new Vec3(x * m[0], y * m[1], z * m[2]);
    }

    private static Vector3f mirrorN(Vector3f n, int type) {
        float[] m = switch (type) {
            case 0 -> new float[]{-1, 1, 1};
            case 1 -> new float[]{1, -1, 1};
            default -> new float[]{1, 1, -1};
        };
        Vector3f o = new Vector3f(n.x * m[0], n.y * m[1], n.z * m[2]);
        if (o.lengthSquared() > 1.0e-8f) o.normalize();
        return o;
    }

    private static boolean isFaceOnMirrorPlane(Vec3[] points, int mirrorType) {
        if (mirrorType < 0 || mirrorType > 2) return false;
        double epsilon = 1.0e-5;
        for (Vec3 p : points) {
            double value = mirrorType == 0 ? p.x : mirrorType == 1 ? p.y : p.z;
            if (Math.abs(value) > epsilon) {
                return false;
            }
        }
        return true;
    }

    private static float[] parseUv(String uv, int vertexCount) {
        if (uv == null || uv.isBlank()) return null;
        String[] parts = uv.trim().split("\\s+");
        if (parts.length < vertexCount * 2) return null;
        float[] out = new float[vertexCount * 2];
        for (int i = 0; i < vertexCount * 2; i++) {
            out[i] = Float.parseFloat(parts[i]);
        }
        return out;
    }

    private static int parseMaterialId(String line) {
        String m = matchGroup(M_PATTERN, line);
        if (m == null || m.isBlank()) return 0;
        try {
            return Integer.parseInt(m.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String matchGroup(Pattern pat, String line) {
        Matcher mm = pat.matcher(line);
        return mm.find() ? mm.group(1) : null;
    }

    private static TextureInfo resolveTexture(byte matId, List<String> materialOrder, Map<String, String> overrides, TextureOpener opener) throws Exception {
        String matName = null;
        int idx = matId & 0xFF;
        if (idx >= 0 && idx < materialOrder.size()) {
            matName = materialOrder.get(idx);
        }
        if (matName == null && !materialOrder.isEmpty()) matName = materialOrder.get(0);
        String path = null;
        if (matName != null && overrides.containsKey(matName)) {
            path = overrides.get(matName);
        }
        if (path == null && overrides.containsKey("default")) {
            path = overrides.get("default");
        }
        if (path == null && !overrides.isEmpty()) {
            path = overrides.values().iterator().next();
        }
        if (path == null) path = "textures/misc/white.png";
        String selectedPath = path;
        String cacheKey = opener.getPackKey() + "|" + selectedPath;
        return TEXTURE_INFO_CACHE.computeIfAbsent(cacheKey, k -> registerTextureFromZip(selectedPath, opener));
    }

    private static void loadScriptForModel(MqoModel model, Path packPath, String scriptPath) {
        loadScriptForModel(model, packPath, scriptPath, null);
    }

    private static void loadScriptForModel(MqoModel model, Path packPath, String scriptPath, String modelName) {
        if (model == null || packPath == null) {
            PolygonTrainMod.LOGGER.warn("loadScriptForModel: model or packPath is null");
            return;
        }
        String normalized = normalizeScriptPath(scriptPath);
        String leaf = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
        boolean hasExplicitPath = !normalized.isBlank();

        PolygonTrainMod.LOGGER.info("loadScriptForModel: scriptPath='{}', normalized='{}', leaf='{}', hasExplicitPath={}", scriptPath, normalized, leaf, hasExplicitPath);

        try {
            if (hasExplicitPath) {
                String legacyScript = VehicleModelPackManager.INSTANCE.getScript(normalized);
                if (legacyScript == null || legacyScript.isBlank()) {
                    legacyScript = VehicleModelPackManager.INSTANCE.getScript(leaf);
                }
                if (legacyScript != null && !legacyScript.isBlank()) {
                    PolygonTrainMod.LOGGER.info("Loaded legacy script from resource manager: {}, length={}", normalized, legacyScript.length());
                    TrainScriptSystem.loadScript(normalized, legacyScript, model, modelName);
                    return;
                }
            }
        } catch (Exception ignored) {
            if (hasExplicitPath) {
                String warnKey = packPath + "|" + normalized;
                if (MISSING_SCRIPT_WARNINGS.add(warnKey)) {
                    PolygonTrainMod.LOGGER.warn("Legacy script lookup failed for {}; falling back to pack search", normalized);
                }
            }
            // legacy resource manager may not be initialized or the script may not be available
        }

        PolygonTrainMod.LOGGER.info("Attempting to load legacy model script '{}' from pack {}", hasExplicitPath ? normalized : "(fallback search)", packPath);
        try {
            if (Files.isDirectory(packPath)) {
                Path scriptFile = null;
                if (hasExplicitPath) {
                    scriptFile = resolveFilePath(packPath, normalized);
                    if (scriptFile == null) {
                        scriptFile = resolveFilePath(packPath, leaf);
                    }
                }
                if (scriptFile != null && Files.exists(scriptFile)) {
                    PolygonTrainMod.LOGGER.info("Found model script at {}", scriptFile);
                    String script = Files.readString(scriptFile, StandardCharsets.UTF_8);
                    script = preprocessScriptIncludesForDirectory(scriptFile, rootDirectory(packPath));
                    PolygonTrainMod.LOGGER.info("Script file loaded, length={}", script.length());
                    TrainScriptSystem.loadScript(normalized, script, model, modelName);
                } else {
                    Path fallback = findFallbackScriptFile(packPath);
                    if (fallback != null) {
                        PolygonTrainMod.LOGGER.warn("Model script {} not found in pack directory {}; using fallback {}", normalized, packPath, fallback);
                        String script = Files.readString(fallback, StandardCharsets.UTF_8);
                        script = preprocessScriptIncludesForDirectory(fallback, rootDirectory(packPath));
                        TrainScriptSystem.loadScript(fallback.toString(), script, model, modelName);
                    } else {
                        if (hasExplicitPath) {
                            PolygonTrainMod.LOGGER.warn("Model script not found in pack directory: {} (normalized={})", packPath, normalized);
                        } else {
                            PolygonTrainMod.LOGGER.warn("No fallback model script found in pack directory: {}", packPath);
                        }
                    }
                }
            } else {
                try (ZipFile zf = new ZipFile(packPath.toFile())) {
                    ZipEntry entry = null;
                    if (hasExplicitPath) {
                        entry = findEntry(zf, normalized);
                        if (entry == null && !leaf.isBlank()) {
                            entry = findEntry(zf, leaf);
                        }
                    }
                    if (entry != null) {
                        PolygonTrainMod.LOGGER.info("Found model script in pack zip: {}", entry.getName());
                        try (InputStream in = zf.getInputStream(entry)) {
                            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                            script = preprocessScriptIncludesForZip(zf, entry.getName(), script);
                            TrainScriptSystem.loadScript(normalized, script, model, modelName);
                        }
                    } else {
                        ZipEntry fallback = findFallbackScriptEntry(zf);
                        if (fallback != null) {
                            PolygonTrainMod.LOGGER.warn("Model script {} not found in pack zip {}; using fallback {}", normalized, packPath, fallback.getName());
                            try (InputStream in = zf.getInputStream(fallback)) {
                                String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                                script = preprocessScriptIncludesForZip(zf, fallback.getName(), script);
                                TrainScriptSystem.loadScript(fallback.getName(), script, model, modelName);
                            }
                        } else {
                            if (hasExplicitPath) {
                                PolygonTrainMod.LOGGER.warn("Model script not found in pack zip: {} (normalized={})", packPath, normalized);
                            } else {
                                PolygonTrainMod.LOGGER.warn("No fallback model script found in pack zip: {}", packPath);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.warn("Failed to load script {} from pack {}", scriptPath, packPath, e);
        }
    }

    private static Path rootDirectory(Path packPath) {
        if (packPath == null) {
            return null;
        }
        return Files.isDirectory(packPath) ? packPath : packPath.getParent();
    }

    private static String preprocessScriptIncludesForDirectory(Path scriptFile, Path root) {
        try {
            return preprocessScriptIncludes(
                Files.readString(scriptFile, StandardCharsets.UTF_8),
                normalize(scriptFile.toString()),
                includePath -> resolveIncludeFromDirectory(scriptFile, root, includePath)
            );
        } catch (Exception e) {
            return safeRead(scriptFile);
        }
    }

    private static String safeRead(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String preprocessScriptIncludesForZip(ZipFile zipFile, String entryName, String content) {
        return preprocessScriptIncludes(content, normalize(entryName), includePath -> resolveIncludeFromZip(zipFile, entryName, includePath));
    }

    private static String preprocessScriptIncludes(String content, String scriptIdentifier, IncludeResolver resolver) {
        return preprocessScriptIncludes(content, scriptIdentifier, resolver, new HashSet<>());
    }

    private static String preprocessScriptIncludes(String content, String scriptIdentifier, IncludeResolver resolver, Set<String> visiting) {
        if (content == null || content.isBlank()) {
            return content;
        }
        if (!visiting.add(scriptIdentifier)) {
            PolygonTrainMod.LOGGER.warn("Detected cyclic script include for {}", scriptIdentifier);
            return content;
        }

        String processed = content;
        Matcher matcher = Pattern.compile("(?m)^\\s*//\\s*include\\s*<([^>]+)>\\s*$").matcher(processed);
        while (matcher.find()) {
            String includeTarget = matcher.group(1).trim();
            String replacement = "";
            try {
                IncludeSource includeSource = resolver.resolve(includeTarget);
                if (includeSource != null && includeSource.content() != null) {
                    replacement = preprocessScriptIncludes(includeSource.content(), includeSource.identifier(), resolver, visiting);
                }
            } catch (Exception e) {
                PolygonTrainMod.LOGGER.warn("Failed to resolve include '{}' in {}", includeTarget, scriptIdentifier, e);
            }
            processed = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
            matcher = Pattern.compile("(?m)^\\s*//\\s*include\\s*<([^>]+)>\\s*$").matcher(processed);
        }

        visiting.remove(scriptIdentifier);
        return processed;
    }

    private static IncludeSource resolveIncludeFromDirectory(Path scriptFile, Path root, String includePath) throws IOException {
        String normalizedInclude = normalize(includePath);
        Path parent = scriptFile.getParent();

        if (parent != null) {
            Path relative = parent.resolve(normalizedInclude).normalize();
            if (Files.exists(relative) && Files.isRegularFile(relative)) {
                return new IncludeSource(normalize(relative.toString()), Files.readString(relative, StandardCharsets.UTF_8));
            }
        }

        if (root != null) {
            Path rootResolved = root.resolve(normalizedInclude).normalize();
            if (Files.exists(rootResolved) && Files.isRegularFile(rootResolved)) {
                return new IncludeSource(normalize(rootResolved.toString()), Files.readString(rootResolved, StandardCharsets.UTF_8));
            }
            Path found = resolveFilePath(root, normalizedInclude);
            if (found != null) {
                return new IncludeSource(normalize(found.toString()), Files.readString(found, StandardCharsets.UTF_8));
            }
        }

        return null;
    }

    private static IncludeSource resolveIncludeFromZip(ZipFile zipFile, String currentEntryName, String includePath) throws IOException {
        String normalizedInclude = normalize(includePath);
        String current = normalize(currentEntryName);
        String parent = "";
        int slash = current.lastIndexOf('/');
        if (slash >= 0) {
            parent = current.substring(0, slash + 1);
        }

        ZipEntry relative = findEntry(zipFile, parent + normalizedInclude);
        if (relative == null) {
            relative = findEntry(zipFile, normalizedInclude);
        }
        if (relative == null) {
            return null;
        }

        try (InputStream in = zipFile.getInputStream(relative)) {
            return new IncludeSource(normalize(relative.getName()), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String normalize(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    @FunctionalInterface
    private interface IncludeResolver {
        IncludeSource resolve(String includePath) throws Exception;
    }

    private record IncludeSource(String identifier, String content) {}

    private static Path findFallbackScriptFile(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return null;
        Path found = null;
        try (var stream = Files.walk(root)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(file)) continue;
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (!relative.toLowerCase(Locale.ROOT).contains("/scripts/")) continue;
                if (!relative.toLowerCase(Locale.ROOT).endsWith(".js")) continue;
                if (found != null) {
                    return null;
                }
                found = file;
            }
        }
        return found;
    }

    private static ZipEntry findFallbackScriptEntry(ZipFile zf) {
        if (zf == null) return null;
        ZipEntry fallback = null;
        java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            String name = entry.getName().replace('\\', '/');
            if (!(name.toLowerCase(Locale.ROOT).contains("/scripts/") && name.toLowerCase(Locale.ROOT).endsWith(".js"))) continue;
            if (fallback != null) {
                return null;
            }
            fallback = entry;
        }
        return fallback;
    }

    private static TextureInfo registerTextureFromZip(String path, TextureOpener opener) {
        try (InputStream in = opener.open(path)) {
            if (in != null) {
                byte[] data = in.readAllBytes();
                com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(new ByteArrayInputStream(data));
                AlphaMask alphaMask = readAlphaMask(ImageIO.read(new ByteArrayInputStream(data)));
                DynamicTexture tex = new DynamicTexture(img);
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(PolygonTrainMod.MODID,
                    "dynamic/mqo/" + Integer.toHexString(path.hashCode()));
                Minecraft.getInstance().getTextureManager().register(loc, tex);
                return new TextureInfo(loc, alphaMask);
            }
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.debug("Could not load texture {}: {}", path, e.getMessage());
        }
        ResourceLocation fallback = fallbackTexture();
        return new TextureInfo(fallback, AlphaMask.EMPTY);
    }

    private static AlphaMask readAlphaMask(BufferedImage image) {
        if (image == null || !image.getColorModel().hasAlpha()) {
            return AlphaMask.EMPTY;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        BitSet transparentPixels = new BitSet(width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) < 255) {
                    transparentPixels.set(y * width + x);
                }
            }
        }
        return transparentPixels.isEmpty() ? AlphaMask.EMPTY : new AlphaMask(width, height, transparentPixels);
    }

    private static ResourceLocation fallbackTexture() {
        if (fallbackWhite != null) return fallbackWhite;
        try {
            com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(4, 4, false);
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    img.setPixelRGBA(x, y, 0xFFFFFFFF);
                }
            }
            DynamicTexture tex = new DynamicTexture(img);
            fallbackWhite = ResourceLocation.fromNamespaceAndPath(PolygonTrainMod.MODID, "dynamic/mqo/_white");
            Minecraft.getInstance().getTextureManager().register(fallbackWhite, tex);
        } catch (Exception e) {
            fallbackWhite = TextureManager.INTENTIONAL_MISSING_TEXTURE;
        }
        return fallbackWhite;
    }

    public static ResourceLocation getScriptTexture(String domain, String path, int frameIndex) {
        if (path == null || path.isBlank()) {
            return fallbackTexture();
        }
        String namespace = domain == null || domain.isBlank() ? "minecraft" : domain;
        String normalizedPath = path.replace('\\', '/');
        String cacheKey = namespace + ":" + normalizedPath;
        List<ResourceLocation> frames = SCRIPT_TEXTURE_CACHE.computeIfAbsent(cacheKey, key -> loadScriptTextureFrames(namespace, normalizedPath));
        if (frames.isEmpty()) {
            return fallbackTexture();
        }
        int index = Math.floorMod(frameIndex, frames.size());
        return frames.get(index);
    }

    private static List<ResourceLocation> loadScriptTextureFrames(String domain, String path) {
        try (InputStream in = openScriptTextureStream(domain, path)) {
            if (in == null) {
                return List.of(fallbackTexture());
            }
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".gif")) {
                return registerGifFrames(domain, path, in);
            }
            return List.of(registerBufferedImage(domain, path, 0, ImageIO.read(in)));
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.warn("Could not load script texture {}:{}: {}", domain, path, e.getMessage());
            return List.of(fallbackTexture());
        }
    }

    private static InputStream openScriptTextureStream(String domain, String path) throws IOException {
        String entryName = "assets/" + domain + "/" + path;
        Path modsDir = Minecraft.getInstance().gameDirectory.toPath().resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            return null;
        }
        try (var files = Files.list(modsDir)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".zip") && !name.endsWith(".jar")) {
                    continue;
                }
                ZipFile zip = new ZipFile(file.toFile());
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) {
                    zip.close();
                    continue;
                }
                InputStream raw = zip.getInputStream(entry);
                return new java.io.FilterInputStream(raw) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        zip.close();
                    }
                };
            }
        }
        return null;
    }

    private static List<ResourceLocation> registerGifFrames(String domain, String path, InputStream in) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            return List.of(fallbackTexture());
        }
        ImageReader reader = readers.next();
        List<ResourceLocation> frames = new ArrayList<>();
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(in)) {
            reader.setInput(imageInput);
            int count = reader.getNumImages(true);
            BufferedImage composed = null;
            java.awt.Graphics2D graphics = null;
            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                if (composed == null) {
                    composed = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    graphics = composed.createGraphics();
                }
                int left = 0;
                int top = 0;
                try {
                    Node root = reader.getImageMetadata(i).getAsTree(reader.getImageMetadata(i).getNativeMetadataFormatName());
                    Node desc = findGifMetadataNode(root, "ImageDescriptor");
                    if (desc != null && desc.getAttributes() != null) {
                        Node leftNode = desc.getAttributes().getNamedItem("imageLeftPosition");
                        Node topNode = desc.getAttributes().getNamedItem("imageTopPosition");
                        if (leftNode != null) left = Integer.parseInt(leftNode.getNodeValue());
                        if (topNode != null) top = Integer.parseInt(topNode.getNodeValue());
                    }
                } catch (Exception ignored) {
                }
                graphics.drawImage(frame, left, top, null);
                BufferedImage snapshot = new BufferedImage(composed.getWidth(), composed.getHeight(), BufferedImage.TYPE_INT_ARGB);
                snapshot.setData(composed.getData());
                frames.add(registerBufferedImage(domain, path, i, snapshot));
            }
            if (graphics != null) {
                graphics.dispose();
            }
        } finally {
            reader.dispose();
        }
        return frames;
    }

    private static Node findGifMetadataNode(Node root, String nodeName) {
        if (root == null) {
            return null;
        }
        if (nodeName.equalsIgnoreCase(root.getNodeName())) {
            return root;
        }
        Node child = root.getFirstChild();
        while (child != null) {
            Node found = findGifMetadataNode(child, nodeName);
            if (found != null) {
                return found;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static ResourceLocation registerBufferedImage(String domain, String path, int frame, BufferedImage image) {
        if (image == null) {
            return fallbackTexture();
        }
        com.mojang.blaze3d.platform.NativeImage nativeImage = new com.mojang.blaze3d.platform.NativeImage(image.getWidth(), image.getHeight(), true);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                nativeImage.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        String safe = Integer.toHexString((domain + ":" + path + "#" + frame).hashCode());
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(PolygonTrainMod.MODID, "dynamic/script/" + safe);
        Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(nativeImage));
        return loc;
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        renderModel(model, poseStack, buffer, packedLight, null);
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, Object entity) {
        if (model == null) return;
        model.render(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, null, null, entity);
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, GroupTransform groupTransform, Object entity) {
        if (model == null) return;
        model.render(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, null, groupTransform, entity);
    }

    @FunctionalInterface
    private interface TextureOpener {
        InputStream open(String path) throws Exception;
        default String getPackKey() {
            return "";
        }
    }

    private static final class TextureInfo {
        final ResourceLocation location;
        final AlphaMask alphaMask;

        TextureInfo(ResourceLocation location, AlphaMask alphaMask) {
            this.location = location;
            this.alphaMask = alphaMask == null ? AlphaMask.EMPTY : alphaMask;
        }

        boolean isTranslucent(float[] uvs, int vertexCount) {
            if (uvs == null || uvs.length < vertexCount * 2) {
                return alphaMask.hasAnyTransparency();
            }
            return alphaMask.intersectsUvBounds(uvs, vertexCount);
        }
    }

    private static final class AlphaMask {
        static final AlphaMask EMPTY = new AlphaMask(0, 0, new BitSet());

        final int width;
        final int height;
        final BitSet transparentPixels;

        AlphaMask(int width, int height, BitSet transparentPixels) {
            this.width = width;
            this.height = height;
            this.transparentPixels = transparentPixels;
        }

        boolean hasAnyTransparency() {
            return !transparentPixels.isEmpty();
        }

        boolean intersectsUvBounds(float[] uvs, int vertexCount) {
            if (width <= 0 || height <= 0 || transparentPixels.isEmpty()) {
                return false;
            }
            float minU = Float.POSITIVE_INFINITY;
            float maxU = Float.NEGATIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY;
            float maxV = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < vertexCount; i++) {
                float u = wrapUv(uvs[i * 2]);
                float v = wrapUv(uvs[i * 2 + 1]);
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }
            int startX = uvToPixel(minU, width);
            int endX = uvToPixel(maxU, width);
            int startY = uvToPixel(minV, height);
            int endY = uvToPixel(maxV, height);
            for (int y = startY; y <= endY; y++) {
                // BitSet を行単位で見ると、画像全体を毎回走査せずに透明画素を探せる。
                int rowStart = y * width + startX;
                int hit = transparentPixels.nextSetBit(rowStart);
                if (hit >= 0 && hit <= y * width + endX) {
                    return true;
                }
            }
            return false;
        }

        private static float wrapUv(float value) {
            if (!Float.isFinite(value)) {
                return 0.0F;
            }
            float wrapped = value % 1.0F;
            if (wrapped < 0.0F) {
                wrapped += 1.0F;
            }
            if (value == 1.0F) {
                return 1.0F;
            }
            return wrapped;
        }

        private static int uvToPixel(float uv, int size) {
            if (size <= 1) {
                return 0;
            }
            float clamped = Math.max(0.0F, Math.min(1.0F, uv));
            return Math.min(size - 1, Math.max(0, Math.round(clamped * (size - 1))));
        }
    }

    /** グループ名を受け取り、そのグループをレンダリングするかどうかを返す述語。 */
    @FunctionalInterface
    public interface GroupPredicate {
        boolean shouldRender(String groupName);
    }

    /** グループ名を受け取り、そのグループに対して追加の変換を行う関数。 */
    @FunctionalInterface
    public interface GroupTransform {
        void apply(PoseStack poseStack, String groupName);
    }

    private static final class BatchBuilder {
        final String groupName;
        final ResourceLocation texture;
        final boolean translucent;
        final int materialId;
        final List<Float> positions = new ArrayList<>();
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;

        BatchBuilder(String groupName, ResourceLocation texture, int materialId, boolean translucent) {
            this.groupName = groupName;
            this.texture = texture;
            this.materialId = materialId;
            this.translucent = translucent;
        }

        void put(Vec3 p, Vector3f n, float u, float v) {
            positions.add((float) p.x);
            positions.add((float) p.y);
            positions.add((float) p.z);
            positions.add(n.x);
            positions.add(n.y);
            positions.add(n.z);
            positions.add(u);
            positions.add(v);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }

        Batch bake(boolean smoothing) {
            if (smoothing) {
                applySmoothNormals();
            }
            float[] data = new float[positions.size()];
            for (int i = 0; i < positions.size(); i++) data[i] = positions.get(i);
            float safeMinU = Float.isFinite(minU) ? minU : 0.0F;
            float safeMaxU = Float.isFinite(maxU) ? maxU : 1.0F;
            float safeMinV = Float.isFinite(minV) ? minV : 0.0F;
            float safeMaxV = Float.isFinite(maxV) ? maxV : 1.0F;
            return new Batch(groupName, texture, data, data.length / 8, materialId, translucent, safeMinU, safeMaxU, safeMinV, safeMaxV);
        }

        private void applySmoothNormals() {
            int vertexCount = positions.size() / 8;
            if (vertexCount <= 0) {
                return;
            }

            Map<String, List<Integer>> byPosition = new HashMap<>();
            Vector3f[] originalNormals = new Vector3f[vertexCount];
            for (int i = 0; i < vertexCount; i++) {
                int o = i * 8;
                byPosition.computeIfAbsent(positionKey(o), k -> new ArrayList<>()).add(i);
                originalNormals[i] = new Vector3f(positions.get(o + 3), positions.get(o + 4), positions.get(o + 5));
                if (originalNormals[i].lengthSquared() > 1.0E-8F) {
                    originalNormals[i].normalize();
                } else {
                    originalNormals[i].set(0.0F, 1.0F, 0.0F);
                }
            }

            float cosThreshold = (float) Math.cos(Math.toRadians(60.0D));
            for (int i = 0; i < vertexCount; i++) {
                int o = i * 8;
                List<Integer> shared = byPosition.get(positionKey(o));
                if (shared == null || shared.isEmpty()) {
                    continue;
                }

                Vector3f current = originalNormals[i];
                Vector3f sum = new Vector3f();
                for (int other : shared) {
                    Vector3f normal = originalNormals[other];
                    if (current.dot(normal) >= cosThreshold) {
                        sum.add(normal);
                    }
                }
                if (sum.lengthSquared() > 1.0E-8F) {
                    sum.normalize();
                    positions.set(o + 3, sum.x);
                    positions.set(o + 4, sum.y);
                    positions.set(o + 5, sum.z);
                }
            }
        }

        private String positionKey(int offset) {
            return Math.round(positions.get(offset) * 100000.0F) + ","
                + Math.round(positions.get(offset + 1) * 100000.0F) + ","
                + Math.round(positions.get(offset + 2) * 100000.0F);
        }
    }

    public static final class MqoModel {
        private final List<Batch> batches;

        private final ScriptModel scriptModel;

        public MqoModel(List<Batch> batches, List<ResourceLocation> materialTextures) {
            this.batches = batches;
            this.scriptModel = new ScriptModel(materialTextures);
        }

        private ScriptEngine scriptEngine;
        private TrainScriptSystem.ScriptModelRenderer scriptRenderer;

        public void setScriptEngine(ScriptEngine engine, TrainScriptSystem.ScriptModelRenderer renderer) {
            this.scriptEngine = engine;
            this.scriptRenderer = renderer;
        }

        public void setScriptEngine(Object engine) {
            if (engine instanceof ScriptEngine scriptEngine) {
                setScriptEngine(scriptEngine, null);
            }
        }

        public ScriptEngine getScriptEngine() {
            return scriptEngine;
        }

        public ScriptModel getScriptModel() {
            return scriptModel;
        }

        private boolean executeScript(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, int pass, Object entity) {
            if (scriptEngine == null) {
                return false;
            }
            try {
                if (scriptRenderer != null) {
                    scriptRenderer.setRenderContext(poseStack, buffer, packedLight, overlay, pass, entity);
                }
                if (scriptEngine instanceof ScriptEngine engine) {
                    int renderPartsBefore = scriptRenderer != null ? scriptRenderer.getRenderPartsCalls() : 0;
                    // 直接 PoseStack を公開すると script 側の push/pop 不整合を追跡できず
                    // "Pose stack not empty" を誘発しやすいため、互換 renderer 経由に限定する
                    engine.put("poseStack", null);
                    engine.put("pass", pass);
                    engine.put("entity", entity);
                    Object renderType = engine.eval("typeof render");
                    if ("function".equals(renderType)) {
                        PolygonTrainMod.LOGGER.debug("Invoking legacy model script render() for pass {}", pass);
                        if (engine instanceof Invocable invocable) {
                            try {
                                invocable.invokeFunction("render", entity, pass, null);
                                return scriptRenderer == null || scriptRenderer.getRenderPartsCalls() > renderPartsBefore;
                            } catch (NoSuchMethodException ignored) {
                                PolygonTrainMod.LOGGER.debug("render(entity, pass, null) not callable via Invocable, falling back to eval");
                            }
                        }
                        engine.eval("render(entity, pass, null);");
                        return scriptRenderer == null || scriptRenderer.getRenderPartsCalls() > renderPartsBefore;
                    }
                    PolygonTrainMod.LOGGER.debug("legacy model script has no render() function for pass {}", pass);
                }
            } catch (Exception e) {
                PolygonTrainMod.LOGGER.error("legacy model script execution failed on pass {}", pass, e);
            } finally {
                if (scriptRenderer != null) {
                    scriptRenderer.clearRenderContext();
                }
            }
            return false;
        }

        private void renderInternal(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                                    boolean translucent, GroupPredicate groupFilter, GroupTransform groupTransform, TrainScriptSystem.ScriptModelRenderer scriptRenderer) {
            for (Batch batch : batches) {
                if (groupFilter != null && !groupFilter.shouldRender(batch.groupName)) continue;
                if (translucent != batch.translucent) continue;
                if (scriptRenderer != null) {
                    scriptRenderer.currentMatId = batch.materialId;
                }
                if (groupTransform != null) {
                    poseStack.pushPose();
                }
                try {
                    if (groupTransform != null) {
                        groupTransform.apply(poseStack, batch.groupName);
                    }
                    PoseStack.Pose pose = poseStack.last();
                    Matrix4f mat = pose.pose();
                    Matrix3f norm = pose.normal();
                    boolean scriptTexture = scriptRenderer != null && scriptRenderer.getBoundTexture() != null;
                    ResourceLocation texture = scriptTexture ? scriptRenderer.getBoundTexture() : batch.texture;
                    boolean needsBlend = translucent && (scriptTexture || batch.translucent);
                    RenderType renderType = needsBlend
                        ? RenderType.entityTranslucent(texture)
                        : RenderType.entityCutoutNoCull(texture);
                    VertexConsumer vc = buffer.getBuffer(renderType);
                    for (int i = 0; i < batch.vertexCount; i++) {
                        int o = i * 8;
                        float x = batch.data[o];
                        float y = batch.data[o + 1];
                        float z = batch.data[o + 2];
                        float nx = batch.data[o + 3];
                        float ny = batch.data[o + 4];
                        float nz = batch.data[o + 5];
                        float u = batch.data[o + 6];
                        float v = batch.data[o + 7];
                        if (scriptRenderer != null) {
                            u = scriptRenderer.mapU(u, batch.minU, batch.maxU);
                            v = scriptRenderer.mapV(v, batch.minV, batch.maxV);
                        }
                        Vector3f tn = new Vector3f(nx, ny, nz);
                        norm.transform(tn);
                        vc.addVertex(mat, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(overlay).setLight(packedLight)
                            .setNormal(tn.x, tn.y, tn.z);
                    }
                } finally {
                    if (groupTransform != null) {
                        poseStack.popPose();
                    }
                }
            }
        }

        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay) {
            render(poseStack, buffer, packedLight, overlay, null, null, null);
        }

        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, GroupPredicate groupFilter) {
            render(poseStack, buffer, packedLight, overlay, groupFilter, null, null);
        }

        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, GroupPredicate groupFilter, GroupTransform groupTransform) {
            render(poseStack, buffer, packedLight, overlay, groupFilter, groupTransform, null);
        }

        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                           GroupPredicate groupFilter, GroupTransform groupTransform, Object entity) {
            boolean scriptRendered = false;
            try {
                if (scriptRenderer != null) {
                    scriptRenderer.resetRenderStatistics();
                }
                scriptRendered = scriptEngine != null && (
                    executeScript(poseStack, buffer, packedLight, overlay, 0, entity) |
                    executeScript(poseStack, buffer, packedLight, overlay, 1, entity) |
                    executeScript(poseStack, buffer, packedLight, overlay, 2, entity)
                );
            } finally {
                if (scriptRenderer != null) {
                    scriptRenderer.clearRenderContext();
                }
            }
            if (!scriptRendered) {
                renderInternal(poseStack, buffer, packedLight, overlay, false, groupFilter, groupTransform, scriptRenderer);
                renderInternal(poseStack, buffer, packedLight, overlay, true, groupFilter, groupTransform, scriptRenderer);
            }
        }
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, GroupPredicate groupFilter) {
        if (model == null) return;
        model.render(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, groupFilter);
    }

    public static void renderModelWithoutScript(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, boolean translucent, GroupPredicate groupFilter, TrainScriptSystem.ScriptModelRenderer renderer) {
        if (model == null) return;
        model.renderInternal(poseStack, buffer, packedLight, overlay, translucent, groupFilter, null, renderer);
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, GroupPredicate groupFilter, GroupTransform groupTransform) {
        if (model == null) return;
        model.render(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, groupFilter, groupTransform);
    }

    private static final class Batch {
        final String groupName;
        final ResourceLocation texture;
        final boolean translucent;
        final int materialId;
        final float[] data;
        final int vertexCount;
        final float minU;
        final float maxU;
        final float minV;
        final float maxV;

        Batch(String groupName, ResourceLocation texture, float[] data, int vertexCount, int materialId, boolean translucent,
              float minU, float maxU, float minV, float maxV) {
            this.groupName = groupName;
            this.texture = texture;
            this.translucent = translucent;
            this.materialId = materialId;
            this.data = data;
            this.vertexCount = vertexCount;
            this.minU = minU;
            this.maxU = maxU;
            this.minV = minV;
            this.maxV = maxV;
        }
    }

    public static final class ScriptModel {
        public final ScriptMaterialTexture[] textures;

        ScriptModel(List<ResourceLocation> materialTextures) {
            this.textures = new ScriptMaterialTexture[materialTextures.size()];
            for (int i = 0; i < materialTextures.size(); i++) {
                this.textures[i] = new ScriptMaterialTexture(new ScriptMaterial(materialTextures.get(i)));
            }
        }
    }

    public static final class ScriptMaterialTexture {
        public ScriptMaterial material;

        ScriptMaterialTexture(ScriptMaterial material) {
            this.material = material;
        }
    }

    public static final class ScriptMaterial {
        public Object texture;

        ScriptMaterial(ResourceLocation texture) {
            this.texture = new ScriptTexture(texture);
        }
    }

    public static final class ScriptTexture {
        public String namespace;
        public String domain;
        public String path;
        public String resourcePath;

        ScriptTexture(ResourceLocation resource) {
            this.namespace = resource.getNamespace();
            this.domain = this.namespace;
            this.path = resource.getPath();
            this.resourcePath = this.path;
        }

        public String func_110624_b() {
            return namespace;
        }

        public String func_110623_a() {
            return path;
        }
    }
}
