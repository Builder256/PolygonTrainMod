package com.portofino.polygontrainmod.script;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.client.model.MqoModelLoader;
import com.portofino.polygontrainmod.entity.TrainEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.world.entity.player.Player;

import javax.script.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RTMScriptSystem {
    private static RTMScriptSystem instance;
    private ScriptEngine engine;
    private final Map<UUID, EntityScriptContext> entityContexts = new HashMap<>();
    private static final String RTM_CORE_VERSION = "2.4.24";

    private static final class RTMCoreCompat {
        @SuppressWarnings("unused")
        public final String VERSION = RTM_CORE_VERSION;
    }

    private RTMScriptSystem() {
    }

    public static RTMScriptSystem getInstance() {
        if (instance == null) {
            instance = new RTMScriptSystem();
        }
        return instance;
    }

    public void initialize() {
        PolygonTrainMod.LOGGER.info("Initializing RTM Script System...");
        try {
            ScriptEngineManager manager = new ScriptEngineManager(Thread.currentThread().getContextClassLoader());
            engine = getAvailableScriptEngine(manager);
            if (engine == null) {
                PolygonTrainMod.LOGGER.info("Retrying script engine discovery with RTMScriptSystem class loader.");
                manager = new ScriptEngineManager(RTMScriptSystem.class.getClassLoader());
                engine = getAvailableScriptEngine(manager);
            }
            if (engine == null) {
                PolygonTrainMod.LOGGER.warn("JavaScript engine not available. Java 21 requires an external JS engine dependency such as Graal.js.");
            } else {
                PolygonTrainMod.LOGGER.info("JavaScript engine initialized successfully.");
            }
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.error("Failed to initialize JavaScript engine: {}", e.getMessage(), e);
        }
    }

    public void setScriptEngine(ScriptEngine engine) {
        this.engine = engine;
    }

    private static ScriptEngine getScriptEngine() {
        RTMScriptSystem system = getInstance();
        if (system.engine != null) {
            return system.engine;
        }

        ScriptEngineManager manager = new ScriptEngineManager(Thread.currentThread().getContextClassLoader());
        ScriptEngine engine = getAvailableScriptEngine(manager);
        if (engine == null) {
            engine = getAvailableScriptEngine(new ScriptEngineManager(RTMScriptSystem.class.getClassLoader()));
        }
        return engine;
    }

    public static void loadScript(String scriptPath, Object model) {
        PolygonTrainMod.LOGGER.info("RTM script load requested: {} for model {}", scriptPath, model == null ? "null" : model.getClass().getSimpleName());
        try {
            ScriptEngine scriptEngine = getScriptEngine();
            if (scriptEngine == null) {
                PolygonTrainMod.LOGGER.warn("JavaScript engine not available for model script: {}", scriptPath);
                return;
            }

            Path path = Path.of(scriptPath);
            if (Files.exists(path)) {
                PolygonTrainMod.LOGGER.info("Loading script from filesystem path: {}", path);
                String script = Files.readString(path);
                loadScript(scriptPath, script, model, scriptEngine);
            } else {
                PolygonTrainMod.LOGGER.info("Script path not found on filesystem, skipping direct load: {}", scriptPath);
            }
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.error("Failed to load script for model: {}", scriptPath, e);
        }
    }

    public static void loadScript(String scriptPath, String script, Object model) {
        PolygonTrainMod.LOGGER.info("RTM script load requested from content: {} for model {}", scriptPath, model == null ? "null" : model.getClass().getSimpleName());
        try {
            ScriptEngine scriptEngine = getScriptEngine();
            if (scriptEngine == null) {
                PolygonTrainMod.LOGGER.warn("JavaScript engine not available for model script: {}", scriptPath);
                return;
            }
            loadScript(scriptPath, script, model, scriptEngine);
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.error("Failed to load script for model: {}", scriptPath, e);
        }
    }

    private static ScriptEngine getAvailableScriptEngine(ScriptEngineManager manager) {
        if (manager == null) {
            return null;
        }

        String[] engineNames = {"javascript", "js", "Graal.js", "graal.js", "nashorn"};
        for (String name : engineNames) {
            ScriptEngine scriptEngine = manager.getEngineByName(name);
            if (scriptEngine != null) {
                PolygonTrainMod.LOGGER.info("Using JavaScript engine '{}'.", name);
                return scriptEngine;
            }
        }

        if (!manager.getEngineFactories().isEmpty()) {
            PolygonTrainMod.LOGGER.warn(
                "Available script engines: {}",
                manager.getEngineFactories().stream()
                    .map(ScriptEngineFactory::getEngineName)
                    .collect(Collectors.joining(", "))
            );
        } else {
            PolygonTrainMod.LOGGER.warn("No script engine providers found on the classpath.");
        }

        try {
            Class<?> factoryClass = Class.forName("org.graalvm.polyglot.js.jsr223.GraalJSScriptEngineFactory");
            ScriptEngineFactory factory = (ScriptEngineFactory) factoryClass.getDeclaredConstructor().newInstance();
            ScriptEngine scriptEngine = factory.getScriptEngine();
            if (scriptEngine != null) {
                PolygonTrainMod.LOGGER.info("Using Graal.js ScriptEngineFactory directly.");
                return scriptEngine;
            }
        } catch (ClassNotFoundException ignored) {
            // Graal.js is not available on the classpath.
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.warn("Failed to instantiate Graal.js ScriptEngineFactory: {}", e.getMessage());
        }

        return null;
    }

    private static void loadScript(String scriptPath, String script, Object model, ScriptEngine scriptEngine) {
        PolygonTrainMod.LOGGER.info("Executing model script: {} (model={})", scriptPath, model == null ? "null" : model.getClass().getSimpleName());
        try {
            ScriptModelRenderer renderer = new ScriptModelRenderer(model);
            injectScriptCompatibility(scriptEngine, renderer);
            scriptEngine.eval(script);

            if (model instanceof com.portofino.polygontrainmod.model.MQOModel oldModel) {
                oldModel.setScriptEngine(scriptEngine);
            } else if (model instanceof com.portofino.polygontrainmod.client.model.MqoModelLoader.MqoModel newModel) {
                newModel.setScriptEngine(scriptEngine, renderer);
            } else {
                PolygonTrainMod.LOGGER.warn("RTM script model is not recognized type: {}", model == null ? "null" : model.getClass().getName());
            }
            invokeScriptInit(scriptEngine, renderer);
            PolygonTrainMod.LOGGER.info("Script loaded for model: {}", scriptPath);
        } catch (ScriptException e) {
            PolygonTrainMod.LOGGER.error("Failed to execute script for model: {}, continuing without script", scriptPath, e);
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.error("Unexpected error loading script for model: {}, continuing without script", scriptPath, e);
        }
    }

    private static void injectScriptCompatibility(ScriptEngine scriptEngine, ScriptModelRenderer renderer) {
        try {
            scriptEngine.put("renderer", renderer);
            scriptEngine.put("model", renderer.getModel());
            // RTMCore を互換オブジェクトとしてバインド
            scriptEngine.put("RTMCore", new RTMCoreCompat());
            try {
                scriptEngine.eval("load('nashorn:mozilla_compat.js');");
            } catch (Exception ignored) {
                PolygonTrainMod.LOGGER.debug("mozilla_compat.js not available for current JS engine.");
            }
            scriptEngine.eval(
                "if (typeof java === 'undefined' && typeof Packages !== 'undefined') java = Packages.java;\n" +
                "if (typeof Packages === 'undefined' && typeof java !== 'undefined') Packages = java;\n" +
                "if (typeof Packages === 'undefined') Packages = {};\n" +
                "if (typeof Packages.org === 'undefined') Packages.org = {};\n" +
                "if (typeof Packages.org.lwjgl === 'undefined') Packages.org.lwjgl = {};\n" +
                "if (typeof Packages.org.lwjgl.opengl === 'undefined') Packages.org.lwjgl.opengl = {};\n" +
                "if (typeof Packages.jp === 'undefined') Packages.jp = {};\n" +
                "if (typeof Packages.jp.ngt === 'undefined') Packages.jp.ngt = {};\n" +
                "if (typeof Packages.jp.ngt.ngtlib === 'undefined') Packages.jp.ngt.ngtlib = {};\n" +
                "if (typeof Packages.jp.ngt.ngtlib.math === 'undefined') Packages.jp.ngt.ngtlib.math = {};\n" +
                "if (typeof Packages.jp.ngt.ngtlib.renderer === 'undefined') Packages.jp.ngt.ngtlib.renderer = {};\n" +
                "if (typeof Packages.jp.ngt.ngtlib.renderer.GLHelper === 'undefined') Packages.jp.ngt.ngtlib.renderer.GLHelper = { setBrightness: function(v) {} };\n" +
                "if (typeof Packages.jp.ngt.rtm === 'undefined') Packages.jp.ngt.rtm = {};\n" +
                "if (typeof Packages.jp.ngt.rtm.render === 'undefined') Packages.jp.ngt.rtm.render = {};\n" +
                "if (typeof Packages.jp.ngt.rtm.entity === 'undefined') Packages.jp.ngt.rtm.entity = {};\n" +
                "if (typeof Packages.jp.ngt.rtm.entity.train === 'undefined') Packages.jp.ngt.rtm.entity.train = {};\n" +
                "if (typeof Packages.jp.ngt.rtm.entity.train.util === 'undefined') Packages.jp.ngt.rtm.entity.train.util = {};\n" +
                "if (typeof Packages.jp.ngt.rtm.train === 'undefined') Packages.jp.ngt.rtm.train = {};\n" +
                "if (typeof importPackage === 'undefined') importPackage = function(pkg) {};\n" +
                "if (typeof importClass === 'undefined') importClass = function(pkg) {};\n" +
                "if (typeof JavaImporter === 'undefined') JavaImporter = function() {};\n" +
                "if (typeof load === 'undefined') load = function(path) {};\n" +
                "if (typeof RTMCore === 'undefined') RTMCore = {};\n" +
                "var GL11 = {\n" +
                "  glPushMatrix: function() { renderer.pushMatrix(); },\n" +
                "  glPopMatrix: function() { renderer.popMatrix(); },\n" +
                "  glTranslatef: function(x, y, z) { renderer.translate(x, y, z); },\n" +
                "  glRotatef: function(angle, x, y, z) { renderer.rotate(angle, x, y, z); },\n" +
                "  glScalef: function(x, y, z) { renderer.scale(x, y, z); }\n" +
                "};\n" +
                "function Parts() {\n" +
                "  this.groups = Array.prototype.slice.call(arguments);\n" +
                "  this.render = function(renderer) { if (renderer && typeof renderer.renderParts === 'function') renderer.renderParts(this.groups); };\n" +
                "}\n" +
                "function ActionParts(type) {\n" +
                "  this.groups = Array.prototype.slice.call(arguments, 1);\n" +
                "  this.render = function(renderer) { if (renderer && typeof renderer.renderParts === 'function') renderer.renderParts(this.groups); };\n" +
                "}\n" +
                "var ActionType = { DRAG_X: 0, DRAG_Y: 1, DRAG_Z: 2, ROTATE_X: 3, ROTATE_Y: 4, ROTATE_Z: 5 };\n"
            );

            // scripts that expect 1.12-style methods will call these against entity
            scriptEngine.eval(
                "if (typeof __rtm_compat_once === 'undefined') {\n" +
                "  __rtm_compat_once = true;\n" +
                "  function __safeCall(obj, fn, d) { try { return (obj && typeof obj[fn] === 'function') ? obj[fn]() : d; } catch (e) { return d; } }\n" +
                "}\n"
            );
        } catch (ScriptException e) {
            PolygonTrainMod.LOGGER.error("Failed to inject script compatibility helpers", e);
        }
    }

    private static void invokeScriptInit(ScriptEngine scriptEngine, ScriptModelRenderer renderer) {
        Object initModel = renderer.getModel();
        if (scriptEngine instanceof Invocable invocable) {
            try {
                invocable.invokeFunction("init", renderer, initModel);
                return;
            } catch (NoSuchMethodException ignored) {
                try {
                    invocable.invokeFunction("init");
                    return;
                } catch (NoSuchMethodException ignored2) {
                    // no init function with either signature
                } catch (ScriptException e) {
                    PolygonTrainMod.LOGGER.error("Failed to invoke init(renderer, model) for model script", e);
                    return;
                }
            } catch (ScriptException e) {
                PolygonTrainMod.LOGGER.error("Failed to invoke init for model script", e);
                return;
            }
        }

        try {
            scriptEngine.eval("if (typeof init === 'function') init();");
        } catch (ScriptException e) {
            PolygonTrainMod.LOGGER.error("Failed to invoke init() fallback for model script", e);
        }
    }

    public static void invokeScriptTick(ScriptEngine scriptEngine, Object entity) {
        if (scriptEngine == null) return;
        if (scriptEngine instanceof Invocable invocable) {
            try {
                invocable.invokeFunction("tick", entity);
                return;
            } catch (NoSuchMethodException ignored) {
                // no tick function
            } catch (ScriptException e) {
                PolygonTrainMod.LOGGER.error("Failed to invoke tick(entity) for script", e);
            }
        }
        try {
            scriptEngine.eval("if (typeof tick === 'function') tick();");
        } catch (ScriptException e) {
            PolygonTrainMod.LOGGER.error("Failed to invoke tick() fallback for script", e);
        }
    }

    public static void invokeScriptUpdate(ScriptEngine scriptEngine, Object entity, float partialTicks) {
        if (scriptEngine == null) return;
        if (scriptEngine instanceof Invocable invocable) {
            try {
                invocable.invokeFunction("update", entity, partialTicks);
                return;
            } catch (NoSuchMethodException ignored) {
                // no update function
            } catch (ScriptException e) {
                PolygonTrainMod.LOGGER.error("Failed to invoke update(entity, partialTicks) for script", e);
            }
        }
        try {
            scriptEngine.eval("if (typeof update === 'function') update();");
        } catch (ScriptException e) {
            PolygonTrainMod.LOGGER.error("Failed to invoke update() fallback for script", e);
        }
    }

    public static void invokeScriptRender(ScriptEngine scriptEngine, Object entity, float partialTicks) {
        if (scriptEngine == null) return;
        if (scriptEngine instanceof Invocable invocable) {
            try {
                invocable.invokeFunction("render", entity, partialTicks);
                return;
            } catch (NoSuchMethodException ignored) {
                // no render function
            } catch (ScriptException e) {
                PolygonTrainMod.LOGGER.error("Failed to invoke render(entity, partialTicks) for script", e);
            }
        }
        try {
            scriptEngine.eval("if (typeof render === 'function') render();");
        } catch (ScriptException e) {
            PolygonTrainMod.LOGGER.error("Failed to invoke render() fallback for script", e);
        }
    }

    public void executeTrainScript(TrainEntity train, String script) {
        if (engine == null || script == null || script.isEmpty()) {
            return;
        }

        EntityScriptContext context = getOrCreateContext(train);
        setupScriptContext(context, train, null);

        try {
            Bindings bindings = engine.createBindings();
            bindings.putAll(context.variables);
            engine.eval(script, bindings);
        } catch (ScriptException e) {
            PolygonTrainMod.LOGGER.error("Script execution error for vehicle '{}'", train.getVehicleId(), e);
        }
    }

    public void executeEventScript(Entity entity, String eventType, Object... parameters) {
        EntityScriptContext context = getOrCreateContext(entity);
        context.variables.put("eventType", eventType);
        context.variables.put("eventParams", parameters);

        // Event scripts would be loaded from model definition
        // For now, this is a placeholder for future implementation
    }

    private EntityScriptContext getOrCreateContext(Entity entity) {
        return entityContexts.computeIfAbsent(entity.getUUID(), k -> new EntityScriptContext());
    }

    private void setupScriptContext(EntityScriptContext context, TrainEntity train, Player player) {
        context.variables.put("currentTrain", train);
        context.variables.put("train", train);
        context.variables.put("player", player);
        context.variables.put("currentPlayer", player);
        context.variables.put("world", train.level());
        context.variables.put("level", train.level());
        context.variables.put("x", train.getX());
        context.variables.put("y", train.getY());
        context.variables.put("z", train.getZ());
        context.variables.put("yaw", train.getYRot());
        context.variables.put("pitch", train.getXRot());
        context.variables.put("trainDistance", train.getTrainDistance());
        context.variables.put("vehicleId", train.getVehicleId());
    }

    public void removeContext(Entity entity) {
        entityContexts.remove(entity.getUUID());
    }

    private static class EntityScriptContext {
        final Map<String, Object> variables = new HashMap<>();
    }

    public static final class ScriptModelRenderer {
        private final Object model;
        private final MqoModelLoader.MqoModel mqoModel;
        private PoseStack poseStack;
        private MultiBufferSource buffer;
        private int packedLight;
        private int overlay;
        private int currentPass;
        private int matrixDepth = 0;
        private int renderPartsCalls = 0;

        public ScriptModelRenderer(Object model) {
            this.model = model;
            this.mqoModel = model instanceof MqoModelLoader.MqoModel m ? m : null;
        }

        public Object getModel() {
            if (mqoModel != null) {
                return mqoModel.getScriptModel();
            }
            return model;
        }

        public Object registerParts(Object parts) {
            return parts;
        }

        public void setRenderContext(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, int pass, Object entity) {
            restoreMatrixDepth(0);
            this.poseStack = poseStack;
            this.buffer = buffer;
            this.packedLight = packedLight;
            this.overlay = overlay;
            this.currentPass = pass;
            this.matrixDepth = 0;
        }

        public void clearRenderContext() {
            restoreMatrixDepth(0);
            this.poseStack = null;
            this.buffer = null;
            this.matrixDepth = 0;
        }

        public void resetRenderStatistics() {
            this.renderPartsCalls = 0;
        }

        public int getRenderPartsCalls() {
            return renderPartsCalls;
        }

        public int currentMatId;

        public void renderParts(Object groups) {
            if (mqoModel == null || poseStack == null || buffer == null) {
                return;
            }
            renderPartsCalls++;
            int baseDepth = matrixDepth;
            try {
                List<String> groupNames = extractGroupNames(groups);
                boolean translucent = currentPass > 0;
                currentMatId = 0;
                MqoModelLoader.renderModelWithoutScript(mqoModel, poseStack, buffer, packedLight, overlay, translucent, groupNames::contains, this);
            } finally {
                // このrenderParts呼び出し内で増えた分だけ戻す
                while (matrixDepth > baseDepth) {
                    poseStack.popPose();
                    matrixDepth--;
                }
            }
        }

        public int getMatrixDepth() {
            return matrixDepth;
        }

        public void restoreMatrixDepth(int targetDepth) {
            if (poseStack == null) {
                matrixDepth = Math.max(0, targetDepth);
                return;
            }
            int safeTarget = Math.max(0, targetDepth);
            while (matrixDepth > safeTarget) {
                poseStack.popPose();
                matrixDepth--;
            }
        }

        public int getTick(Object entity) {
            if (!(entity instanceof Entity)) {
                return 0;
            }
            Entity e = (Entity) entity;
            try {
                java.lang.reflect.Field field = Entity.class.getDeclaredField("tickCount");
                field.setAccessible(true);
                return field.getInt(e);
            } catch (Exception ignored) {
            }
            return 0;
        }

        public int getMCHour(Object entity) {
            long dayTime = getWorldDayTime(entity);
            return (int) ((dayTime / 20 / 60) % 24);
        }

        public int getMCMinute(Object entity) {
            long dayTime = getWorldDayTime(entity);
            return (int) ((dayTime / 20) % 60);
        }

        private long getWorldDayTime(Object entity) {
            if (!(entity instanceof Entity)) {
                return 0;
            }
            Entity e = (Entity) entity;
            if (e.level() == null) {
                return 0;
            }
            try {
                return e.level().dayTime();
            } catch (Exception ignored) {
                return 0;
            }
        }

        public float sigmoid(double x) {
            float clamped = (float) Math.max(0.0D, Math.min(1.0D, x));
            return clamped * clamped * (3.0F - 2.0F * clamped);
        }

        public void pushMatrix() {
            if (poseStack != null) {
                poseStack.pushPose();
                matrixDepth++;
            }
        }

        public void popMatrix() {
            if (poseStack != null && matrixDepth > 0) {
                poseStack.popPose();
                matrixDepth--;
            }
        }

        public void translate(float x, float y, float z) {
            if (poseStack != null) {
                poseStack.translate(x, y, z);
            }
        }

        public void rotate(float angle, float x, float y, float z) {
            if (poseStack == null) {
                return;
            }
            if (x == 1.0f && y == 0.0f && z == 0.0f) {
                poseStack.mulPose(Axis.XP.rotationDegrees(angle));
            } else if (x == 0.0f && y == 1.0f && z == 0.0f) {
                poseStack.mulPose(Axis.YP.rotationDegrees(angle));
            } else if (x == 0.0f && y == 0.0f && z == 1.0f) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
            } else {
                // Arbitrary axis rotation is not supported in this compatibility helper.
            }
        }

        public void rotate(double angle, String axis, double originX, double originY, double originZ) {
            if (poseStack == null || axis == null || axis.isBlank()) {
                return;
            }

            float a = (float) angle;
            float x = (float) originX;
            float y = (float) originY;
            float z = (float) originZ;

            translate(x, y, z);
            switch (axis.trim().toUpperCase()) {
                case "X" -> rotate(a, 1.0f, 0.0f, 0.0f);
                case "Y" -> rotate(a, 0.0f, 1.0f, 0.0f);
                case "Z" -> rotate(a, 0.0f, 0.0f, 1.0f);
                default -> {
                    PolygonTrainMod.LOGGER.warn("Unsupported rotate axis in script: {}", axis);
                }
            }
            translate(-x, -y, -z);
        }

        public void scale(float x, float y, float z) {
            if (poseStack != null) {
                poseStack.scale(x, y, z);
            }
        }

        private static List<String> extractGroupNames(Object groups) {
            if (groups == null) {
                return Collections.emptyList();
            }
            if (groups instanceof String s) {
                return List.of(s);
            }
            if (groups instanceof java.util.Collection<?> collection) {
                return collection.stream().map(Object::toString).collect(Collectors.toList());
            }
            if (groups.getClass().isArray()) {
                Object[] arr = (Object[]) groups;
                return java.util.Arrays.stream(arr).map(Object::toString).collect(Collectors.toList());
            }
            if (groups instanceof Map<?, ?> map) {
                Object lengthValue = map.get("length");
                if (lengthValue instanceof Number lengthNumber) {
                    int length = lengthNumber.intValue();
                    List<String> result = new ArrayList<>();
                    for (int i = 0; i < length; i++) {
                        Object value = map.get(String.valueOf(i));
                        if (value != null) {
                            result.add(value.toString());
                        }
                    }
                    return result;
                }
                return map.values().stream().map(Object::toString).collect(Collectors.toList());
            }
            List<String> scriptObjectGroups = extractScriptObjectGroupNames(groups);
            if (!scriptObjectGroups.isEmpty()) {
                return scriptObjectGroups;
            }
            return List.of(groups.toString());
        }

        private static List<String> extractScriptObjectGroupNames(Object groups) {
            try {
                Class<?> type = groups.getClass();
                java.lang.reflect.Method isArrayMethod = null;
                try {
                    isArrayMethod = type.getMethod("isArray");
                } catch (NoSuchMethodException ignored) {
                }
                if (isArrayMethod != null && Boolean.TRUE.equals(isArrayMethod.invoke(groups))) {
                    Integer length = getScriptObjectLength(groups);
                    if (length != null) {
                        return getScriptObjectElements(groups, length);
                    }
                }

                Integer length = getScriptObjectLength(groups);
                if (length != null) {
                    return getScriptObjectElements(groups, length);
                }
            } catch (Exception ignored) {
            }
            return Collections.emptyList();
        }

        private static Integer getScriptObjectLength(Object groups) {
            try {
                Class<?> type = groups.getClass();
                java.lang.reflect.Method hasMember = type.getMethod("hasMember", String.class);
                java.lang.reflect.Method getMember = type.getMethod("getMember", String.class);
                if (Boolean.TRUE.equals(hasMember.invoke(groups, "length"))) {
                    Object lengthValue = getMember.invoke(groups, "length");
                    if (lengthValue instanceof Number number) {
                        return number.intValue();
                    }
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
            }

            try {
                Class<?> type = groups.getClass();
                java.lang.reflect.Method get = type.getMethod("get", Object.class);
                java.lang.reflect.Method lengthMethod = type.getMethod("length");
                Object lengthValue = lengthMethod.invoke(groups);
                if (lengthValue instanceof Number number) {
                    return number.intValue();
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
            }

            return null;
        }

        private static List<String> getScriptObjectElements(Object groups, int length) {
            List<String> result = new ArrayList<>();
            try {
                Class<?> type = groups.getClass();
                java.lang.reflect.Method getMember = type.getMethod("getMember", String.class);
                for (int i = 0; i < length; i++) {
                    Object value = getMember.invoke(groups, String.valueOf(i));
                    if (value != null) {
                        result.add(value.toString());
                    }
                }
                return result;
            } catch (NoSuchMethodException e) {
                try {
                    Class<?> type = groups.getClass();
                    java.lang.reflect.Method get = type.getMethod("get", Object.class);
                    for (int i = 0; i < length; i++) {
                        Object value = get.invoke(groups, i);
                        if (value != null) {
                            result.add(value.toString());
                        }
                    }
                    return result;
                } catch (Exception ignored) {
                }
            } catch (Exception ignored) {
            }
            return Collections.emptyList();
        }
    }
}
