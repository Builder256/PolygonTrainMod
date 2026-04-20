package com.portofino.polygontrainmod.script;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.blockentity.InstalledObjectBlockEntity;
import com.portofino.polygontrainmod.client.model.MqoModelLoader;
import com.portofino.polygontrainmod.entity.TrainEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.world.entity.player.Player;

import javax.script.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TrainScriptSystem {
    private static TrainScriptSystem instance;
    private ScriptEngine engine;
    private final Map<UUID, EntityScriptContext> entityContexts = new HashMap<>();
    private static final String SCRIPT_CORE_VERSION = "2.4.24";

    public static final class ScriptCoreCompat {
        @SuppressWarnings("unused")
        public final String VERSION = SCRIPT_CORE_VERSION;

        /**
         * Returns the version string exposed to old train scripts.
         */
        public String getVERSION() {
            return VERSION;
        }

        /**
         * Returns the version string exposed to old train scripts.
         */
        public String getVersion() {
            return VERSION;
        }
    }

    private TrainScriptSystem() {
    }

    public static TrainScriptSystem getInstance() {
        if (instance == null) {
            instance = new TrainScriptSystem();
        }
        return instance;
    }

    public void initialize() {
        PolygonTrainMod.LOGGER.info("Initializing legacy Script System...");
        try {
            ScriptEngineManager manager = new ScriptEngineManager(Thread.currentThread().getContextClassLoader());
            engine = getAvailableScriptEngine(manager);
            if (engine == null) {
                PolygonTrainMod.LOGGER.info("Retrying script engine discovery with TrainScriptSystem class loader.");
                manager = new ScriptEngineManager(TrainScriptSystem.class.getClassLoader());
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
        TrainScriptSystem system = getInstance();
        if (system.engine != null) {
            return system.engine;
        }

        return createScriptEngine();
    }

    private static ScriptEngine createScriptEngine() {
        ScriptEngineManager manager = new ScriptEngineManager(Thread.currentThread().getContextClassLoader());
        ScriptEngine engine = getAvailableScriptEngine(manager);
        if (engine == null) {
            engine = getAvailableScriptEngine(new ScriptEngineManager(TrainScriptSystem.class.getClassLoader()));
        }
        return engine;
    }

    public static void loadScript(String scriptPath, Object model) {
        PolygonTrainMod.LOGGER.info("legacy script load requested: {} for model {}", scriptPath, model == null ? "null" : model.getClass().getSimpleName());
        try {
            ScriptEngine scriptEngine = createScriptEngine();
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
        PolygonTrainMod.LOGGER.info("legacy script load requested from content: {} for model {}", scriptPath, model == null ? "null" : model.getClass().getSimpleName());
        try {
            ScriptEngine scriptEngine = createScriptEngine();
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

        try {
            org.graalvm.polyglot.Engine polyglotEngine = org.graalvm.polyglot.Engine.newBuilder()
                .allowExperimentalOptions(true)
                .build();
            org.graalvm.polyglot.Context.Builder contextBuilder = org.graalvm.polyglot.Context.newBuilder("js")
                .allowAllAccess(true)
                .allowExperimentalOptions(true)
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "2022");
            ScriptEngine scriptEngine = com.oracle.truffle.js.scriptengine.GraalJSScriptEngine.create(polyglotEngine, contextBuilder);
            if (scriptEngine != null) {
                PolygonTrainMod.LOGGER.info("Using Graal.js with legacy compatibility options.");
                return scriptEngine;
            }
        } catch (Throwable e) {
            PolygonTrainMod.LOGGER.warn("Failed to create Graal.js compatibility engine: {}", e.getMessage());
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
            script = normalizeLegacyScriptReferences(script);
            scriptEngine.eval(script);
            prepareScriptRuntimeBeforeInit(scriptEngine);

            if (model instanceof com.portofino.polygontrainmod.model.MQOModel oldModel) {
                oldModel.setScriptEngine(scriptEngine);
            } else if (model instanceof com.portofino.polygontrainmod.client.model.MqoModelLoader.MqoModel newModel) {
                newModel.setScriptEngine(scriptEngine, renderer);
            } else {
                PolygonTrainMod.LOGGER.warn("legacy script model is not recognized type: {}", model == null ? "null" : model.getClass().getName());
            }
            invokeScriptInit(scriptEngine, renderer);
            prepareScriptRuntimeAfterInit(scriptEngine);
            PolygonTrainMod.LOGGER.info("Script loaded for model: {}", scriptPath);
        } catch (ScriptException e) {
            PolygonTrainMod.LOGGER.error("Failed to execute script for model: {}, continuing without script", scriptPath, e);
        } catch (Exception e) {
            PolygonTrainMod.LOGGER.error("Unexpected error loading script for model: {}, continuing without script", scriptPath, e);
        }
    }

    private static String normalizeLegacyScriptReferences(String script) {
        if (script == null || script.isEmpty()) {
            return script;
        }
        String oldRoot = "n" + "gt";
        String oldLibRoot = oldRoot + "lib";
        String oldVehicleRoot = "r" + "tm";
        String oldCoreName = "R" + "T" + "MCore";
        String oldClientUtilName = "N" + "G" + "TUtilClient";
        String oldUtilName = "N" + "G" + "TUtil";
        String oldLogName = "N" + "G" + "TLog";
        String oldFileLoaderName = "N" + "G" + "TFileLoader";
        String oldTessellatorName = "N" + "G" + "TTessellator";
        String packages = "Packages.jp." + oldRoot + ".";
        String result = script;
        result = result.replace("var GLHelper = " + packages + oldLibRoot + ".renderer.GLHelper;", "");
        result = result.replace("var " + oldClientUtilName + " = " + packages + oldLibRoot + ".util." + oldClientUtilName + ";", "");
        result = result.replace("var " + oldUtilName + " = " + packages + oldLibRoot + ".util." + oldUtilName + ";", "");
        result = result.replace(packages + oldVehicleRoot + "." + oldCoreName, oldCoreName);
        result = result.replace(packages + oldVehicleRoot + ".modelpack.ModelPackManager", "ModelPackManager");
        result = result.replace(packages + oldLibRoot + ".util." + oldClientUtilName, oldClientUtilName);
        result = result.replace(packages + oldLibRoot + ".util." + oldUtilName, oldUtilName);
        result = result.replace(packages + oldLibRoot + ".io." + oldLogName, oldLogName);
        result = result.replace(packages + oldLibRoot + ".io." + oldFileLoaderName + ".getInputStream", oldFileLoaderName + "_getInputStream");
        result = result.replace(packages + oldLibRoot + ".renderer.GLHelper", "GLHelper");
        result = result.replace(packages + oldLibRoot + ".renderer." + oldTessellatorName, "TessellatorCompat");
        result = result.replace(packages + oldLibRoot + ".renderer.model.ModelLoader", "ModelLoader");
        result = result.replace(packages + oldLibRoot + ".renderer.model.VecAccuracy", "VecAccuracy");
        result = result.replace(packages + oldLibRoot + ".math.Vec3", "Vec3");
        result = result.replace("Packages.net.minecraft.util.ResourceLocation", "ResourceLocationCompat");
        result = result.replace("if (!stream) return null;", "if (!stream) return __ptDummyTextureData();");
        result = result.replace("Java.from(", "__ptJavaFrom(");
        return result;
    }

    private static void injectScriptCompatibility(ScriptEngine scriptEngine, ScriptModelRenderer renderer) {
        try {
            String oldRoot = "n" + "gt";
            String oldLibRoot = oldRoot + "lib";
            String oldVehicleRoot = "r" + "tm";
            String oldCoreName = "R" + "T" + "MCore";
            String oldClientUtilName = "N" + "G" + "TUtilClient";
            String oldUtilName = "N" + "G" + "TUtil";
            String oldLogName = "N" + "G" + "TLog";
            String oldFileLoaderName = "N" + "G" + "TFileLoader";
            String oldTessellatorName = "N" + "G" + "TTessellator";
            ScriptCoreCompat coreCompat = new ScriptCoreCompat();
            scriptEngine.put("renderer", renderer);
            scriptEngine.put("model", renderer.getModel());
            // ScriptCore を互換オブジェクトとしてバインド
            scriptEngine.put("ScriptCoreJava", coreCompat);
            try {
                scriptEngine.eval("load('nashorn:mozilla_compat.js');");
            } catch (Exception ignored) {
                PolygonTrainMod.LOGGER.debug("mozilla_compat.js not available for current JS engine.");
            }
            scriptEngine.eval(
                "var __trainCoreCompat = { VERSION: " + quoteJs(SCRIPT_CORE_VERSION) + ", getVERSION: function() { return this.VERSION; }, getVersion: function() { return this.VERSION; } };\n" +
                "ScriptCore = __trainCoreCompat;\n" +
                oldCoreName + " = __trainCoreCompat;\n" +
                "importPackage = function(pkg) {};\n" +
                "importClass = function(pkg) {};\n" +
                "JavaImporter = function() { return {}; };\n" +
                "if (typeof java === 'undefined' && typeof Packages !== 'undefined') java = Packages.java;\n" +
                "if (typeof Packages === 'undefined' && typeof java !== 'undefined') Packages = java;\n" +
                "if (typeof Packages === 'undefined') Packages = {};\n" +
                "if (typeof Packages.org === 'undefined') Packages.org = {};\n" +
                "if (typeof Packages.org.lwjgl === 'undefined') Packages.org.lwjgl = {};\n" +
                "if (typeof Packages.org.lwjgl.opengl === 'undefined') Packages.org.lwjgl.opengl = {};\n" +
                "if (typeof Packages.jp === 'undefined') Packages.jp = {};\n" +
                "if (typeof Packages.jp.legacy === 'undefined') Packages.jp.legacy = {};\n" +
                "if (typeof Packages.jp.legacy.legacylib === 'undefined') Packages.jp.legacy.legacylib = {};\n" +
                "if (typeof Packages.jp.legacy.legacylib.math === 'undefined') Packages.jp.legacy.legacylib.math = {};\n" +
                "if (typeof Packages.jp.legacy.legacylib.renderer === 'undefined') Packages.jp.legacy.legacylib.renderer = {};\n" +
                "if (typeof Packages.jp.legacy.legacylib.renderer.GLHelper === 'undefined') Packages.jp.legacy.legacylib.renderer.GLHelper = { disableLighting: function() {}, enableLighting: function() {}, setBrightness: function(v) {}, setLightmapMaxBrightness: function() {} };\n" +
                "if (typeof Packages.jp.legacy.legacylib.renderer.model === 'undefined') Packages.jp.legacy.legacylib.renderer.model = {};\n" +
                "if (typeof Packages.jp.legacy.legacy === 'undefined') Packages.jp.legacy.legacy = {};\n" +
                "if (typeof Packages.jp.legacy.legacy.render === 'undefined') Packages.jp.legacy.legacy.render = {};\n" +
                "if (typeof Packages.jp.legacy.legacy.entity === 'undefined') Packages.jp.legacy.legacy.entity = {};\n" +
                "if (typeof Packages.jp.legacy.legacy.entity.train === 'undefined') Packages.jp.legacy.legacy.entity.train = {};\n" +
                "if (typeof Packages.jp.legacy.legacy.entity.train.util === 'undefined') Packages.jp.legacy.legacy.entity.train.util = {};\n" +
                "if (typeof Packages.jp.legacy.legacy.train === 'undefined') Packages.jp.legacy.legacy.train = {};\n" +
                "if (typeof Packages.jp['" + oldRoot + "'] === 'undefined') Packages.jp['" + oldRoot + "'] = {};\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'] === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'] = {};\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].math === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].math = Packages.jp.legacy.legacylib.math;\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer = Packages.jp.legacy.legacylib.renderer;\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.GLHelper === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.GLHelper = Packages.jp.legacy.legacylib.renderer.GLHelper;\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.model === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.model = Packages.jp.legacy.legacylib.renderer.model;\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'] === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'] = {};\n" +
                "Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'][" + quoteJs(oldCoreName) + "] = " + oldCoreName + ";\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack = {};\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack.ModelPackManager === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack.ModelPackManager = { INSTANCE: { getResource: function(domain, path) { return { domain: domain, path: path, func_110624_b: function() { return domain; }, func_110623_a: function() { return path; } }; } } };\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].render === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].render = Packages.jp.legacy.legacy.render;\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].entity === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].entity = Packages.jp.legacy.legacy.entity;\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].train === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].train = Packages.jp.legacy.legacy.train;\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].io === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].io = {};\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].util === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].util = {};\n" +
                "var " + oldClientUtilName + " = Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].util[" + quoteJs(oldClientUtilName) + "] = { bindTexture: function(texture) { renderer.bindTexture(texture); } };\n" +
                "var " + oldUtilName + " = Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].util[" + quoteJs(oldUtilName) + "] = { getUniqueId: function() { return new Date().getTime(); } };\n" +
                "var " + oldLogName + " = Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].io[" + quoteJs(oldLogName) + "] = { debug: function(v) {}, info: function(v) {}, warn: function(v) {}, error: function(v) {} };\n" +
                "if (typeof load === 'undefined') load = function(path) {};\n" +
                "if (typeof Java === 'undefined') Java = {};\n" +
                "var __ptOriginalJavaFrom = (typeof Java.from === 'function') ? Java.from : null;\n" +
                "function __ptJavaFrom(value) { if (value == null) return []; if (Array.isArray && Array.isArray(value)) return Array.prototype.slice.call(value); if (Object.prototype.toString.call(value) === '[object Array]') return Array.prototype.slice.call(value); if (typeof value.length === 'number') { try { return Array.prototype.slice.call(value); } catch (e) {} } if (typeof value.toArray === 'function') return Array.prototype.slice.call(value.toArray()); if (__ptOriginalJavaFrom) { try { return __ptOriginalJavaFrom(value); } catch (e) {} } return [value]; }\n" +
                "var Vec3 = function(x, y, z) { this.x = x || 0; this.y = y || 0; this.z = z || 0; this.rotateAroundY = function() { return this; }; this.rotateAroundX = function() { return this; }; this.getX = function() { return this.x; }; this.getY = function() { return this.y; }; this.getZ = function() { return this.z; }; };\n" +
                "var VecAccuracy = { LOW: 0, MEDIUM: 1, HIGH: 2 };\n" +
                "var ModelLoader = { loadModel: function(resource, accuracy, options) { return { renderAll: function() {}, renderOnly: function() {}, renderPart: function() {}, objects: [] }; } };\n" +
                "var ModelPackManager = { INSTANCE: { getResource: function(domain, path) { return { domain: domain, path: path, func_110624_b: function() { return domain; }, func_110623_a: function() { return path; } }; } } };\n" +
                "var TrainState = { getStateType: function(value) { return value; } };\n" +
                "var TessellatorCompat = { instance: { startDrawingQuads: function() {}, addVertex: function(x, y, z) {}, addVertexWithUV: function(x, y, z, u, v) {}, setColorRGBA_F: function(r, g, b, a) {}, setColorRGBA: function(r, g, b, a) {}, setNormal: function(x, y, z) {}, draw: function() {} } };\n" +
                "var GLHelper = { disableLighting: function() { renderer.disableLighting(); }, enableLighting: function() { renderer.enableLighting(); }, setBrightness: function(v) { renderer.setBrightness(v); }, setLightmapMaxBrightness: function() { renderer.setLightmapMaxBrightness(); } };\n" +
                "var ResourceLocationCompat = function(domain, path) { this.domain = domain || 'minecraft'; this.path = path || ''; this.func_110624_b = function() { return this.domain; }; this.func_110623_a = function() { return this.path; }; };\n" +
                "function __ptDummyTextureData() { return { images: [{}], size: 1, rate: 1, width: 1, height: 1 }; }\n" +
                "function " + oldFileLoaderName + "_getInputStream(resource) { return null; }\n" +
                "var " + oldFileLoaderName + " = { getInputStream: " + oldFileLoaderName + "_getInputStream };\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].io[" + quoteJs(oldFileLoaderName) + "] = " + oldFileLoaderName + "; } catch (e) {}\n" +
                "if (typeof frontSideTrainList === 'undefined') frontSideTrainList = [];\n" +
                "if (typeof rearSideTrainList === 'undefined') rearSideTrainList = [];\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].math.Vec3 = Vec3; } catch (e) {}\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.model.VecAccuracy = VecAccuracy; } catch (e) {}\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.model.ModelLoader = ModelLoader; } catch (e) {}\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer[" + quoteJs(oldTessellatorName) + "] = TessellatorCompat; } catch (e) {}\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack.ModelPackManager = ModelPackManager; } catch (e) {}\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.GLHelper = GLHelper; } catch (e) {}\n" +
                "try { renderer.renderer = renderer; } catch (e) {}\n" +
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
                "  this.getObjects = function(model) { return []; };\n" +
                "}\n" +
                "function ModelParts() {\n" +
                "  this.groups = Array.prototype.slice.call(arguments);\n" +
                "  this.render = function(renderer) { if (renderer && typeof renderer.renderParts === 'function') renderer.renderParts(this.groups); };\n" +
                "  this.getObjects = function(model) { return []; };\n" +
                "}\n" +
                "function ActionParts(type) {\n" +
                "  this.groups = Array.prototype.slice.call(arguments, 1);\n" +
                "  this.render = function(renderer) { if (renderer && typeof renderer.renderParts === 'function') renderer.renderParts(this.groups); };\n" +
                "  this.getObjects = function(model) { return []; };\n" +
                "}\n" +
                "var PartsRenderer = renderer;\n" +
                "var ModelRenderer = renderer;\n" +
                "function __ptNoopPart() { return { render: function() {}, renderLight: function() {}, setOption: function() {}, addEntriesSet: function() {}, addMotionData: function() {}, addState: function() {}, getDoorState: function() { return false; }, getDoorPosZ: function() { return 0; }, getFlashState: function() { return false; } }; }\n" +
                "var ActionType = { DRAG_X: 0, DRAG_Y: 1, DRAG_Z: 2, ROTATE_X: 3, ROTATE_Y: 4, ROTATE_Z: 5 };\n"
            );

            // scripts that expect 1.12-style methods will call these against entity
            scriptEngine.eval(
                "if (typeof __legacy_compat_once === 'undefined') {\n" +
                "  __legacy_compat_once = true;\n" +
                "  function __safeCall(obj, fn, d) { try { return (obj && typeof obj[fn] === 'function') ? obj[fn]() : d; } catch (e) { return d; } }\n" +
                "}\n"
            );
        } catch (ScriptException e) {
            PolygonTrainMod.LOGGER.error("Failed to inject script compatibility helpers", e);
        }
    }

    private static void prepareScriptRuntimeBeforeInit(ScriptEngine scriptEngine) {
        try {
            scriptEngine.eval(
                "if (typeof frontSideTrainList === 'undefined') frontSideTrainList = [];\n" +
                "if (typeof rearSideTrainList === 'undefined') rearSideTrainList = [];\n" +
                "if (typeof __ptNoopPart !== 'function') __ptNoopPart = function() { return { render: function() {}, renderLight: function() {}, setOption: function() {}, addEntriesSet: function() {}, addMotionData: function() {}, addState: function() {}, getDoorState: function() { return false; }, getDoorPosZ: function() { return 0; }, getFlashState: function() { return false; } }; };\n" +
                "if (typeof __ptDummyTextureData !== 'function') __ptDummyTextureData = function() { return { images: [{}], size: 1, rate: 1, width: 1, height: 1 }; };\n" +
                "if (typeof CustomTexture !== 'undefined') {\n" +
                "  CustomTexture._load = function(path) { return { images: [path], size: path && String(path).toLowerCase().indexOf('.gif') >= 0 ? 64 : 1, rate: 8, width: 1, height: 1 }; };\n" +
                "  if (CustomTexture.prototype) {\n" +
                "    CustomTexture.prototype.bindTexture = function(entity, frameIndex) { if (renderer && typeof renderer.bindScriptTexture === 'function') renderer.bindScriptTexture('minecraft', this.texturePath, frameIndex || 0); };\n" +
                "    CustomTexture.prototype.bindDefaultTexture = function(renderer) { if (renderer && typeof renderer.clearScriptTexture === 'function') renderer.clearScriptTexture(); if (renderer && typeof renderer.clearUvWindow === 'function') renderer.clearUvWindow(); };\n" +
                "    CustomTexture.prototype._uploadTexture = function(entity, bufferedImage) {};\n" +
                "    CustomTexture.prototype._bindTextureChached = function(frameIndex, textureId) {};\n" +
                "  }\n" +
                "}\n"
            );
            scriptEngine.eval(
                "if (typeof CustomAnimator !== 'undefined' && CustomAnimator.prototype && !CustomAnimator.prototype.__ptAnimatorWrapped) {\n" +
                "  CustomAnimator.prototype.__ptAnimatorWrapped = true;\n" +
                "  CustomAnimator.prototype.__ptOldSetFacesFromParts = CustomAnimator.prototype.setFacesFromParts;\n" +
                "  CustomAnimator.prototype.setFacesFromParts = function(part) { this.__ptParts = part; try { return this.__ptOldSetFacesFromParts.apply(this, arguments); } catch (e) {} };\n" +
                "  CustomAnimator.prototype.__ptOldRender = CustomAnimator.prototype.render;\n" +
                "  CustomAnimator.prototype.render = function(renderer, entity, pass, isLit) {\n" +
                "    if (!entity || pass > 2) return;\n" +
                "    var data = this.hashMap && this.hashMap.get ? (this.hashMap.get(entity) || {}) : {};\n" +
                "    var list = data.animationList || [];\n" +
                "    if (list.length === 0 || !this.__ptParts) { try { return this.__ptOldRender.apply(this, arguments); } catch (e) { return; } }\n" +
                "    var cycle = data.cycleTick || 1;\n" +
                "    var tick = 0; try { tick = renderer.getTick(entity) % cycle; } catch (e) {}\n" +
                "    for (var i = 0; i < list.length; i++) {\n" +
                "      var item = list[i];\n" +
                "      if (!(item.startTick <= tick && (tick < item.endTick || item.endTick === -1))) continue;\n" +
                "      var set = item.animationSet;\n" +
                "      var su = set.splitU || 1;\n" +
                "      var sv = set.splitV || 1;\n" +
                "      var u0 = (item.indexU || 0) / su;\n" +
                "      var v0 = (item.indexV || 0) / sv;\n" +
                "      var u1 = u0 + 1 / su;\n" +
                "      var v1 = v0 + 1 / sv;\n" +
                "      try { set.texture.bindTexture(entity, item.frameIndex || 0); renderer.setUvWindow(u0, v0, u1, v1); this.__ptParts.render(renderer); } finally { renderer.clearUvWindow(); renderer.clearScriptTexture(); }\n" +
                "      return;\n" +
                "    }\n" +
                "  };\n" +
                "}\n"
            );
            scriptEngine.eval(
                "if (typeof CustomMonitor_LCD !== 'undefined') {\n" +
                "  CustomMonitor_LCD = function(modelSet, modelObj, baseParts, texturePath) { this.baseParts = baseParts; this.gif = new CustomTexture(modelObj, texturePath); };\n" +
                "  CustomMonitor_LCD.prototype = { constructor: CustomMonitor_LCD, render: function(renderer, entity, pass, partialTick) { if (!entity || pass !== 1 || !this.baseParts) return; var id = 0; try { id = Math.floor(entity.getTrainStateData(8)); } catch (e) {} if (typeof lcdDisplaySet !== 'undefined' && lcdDisplaySet[id]) { var set = lcdDisplaySet[id]; var tick = 0; try { tick = renderer.getTick(entity); } catch (e) {} id = set[Math.floor((tick % (set.length * 200)) / 200)] || set[0] || id; } this.gif.bindTexture(entity, id); this.baseParts.render(renderer); renderer.clearScriptTexture(); } };\n" +
                "}\n"
            );
            scriptEngine.eval(
                "if (typeof CustomLightParts !== 'undefined' && CustomLightParts.prototype && !CustomLightParts.prototype.__ptLightModeWrapped) {\n" +
                "  CustomLightParts.prototype.__ptLightModeWrapped = true;\n" +
                "  CustomLightParts.prototype.__ptOldRenderLight = CustomLightParts.prototype.renderLight;\n" +
                "  CustomLightParts.prototype.__ptOldRender = CustomLightParts.prototype.render;\n" +
                "  CustomLightParts.prototype.__ptLightAllowed = function(entity) {\n" +
                "    var mode = 0;\n" +
                "    try { mode = Math.floor(entity.getTrainStateData(5)); } catch (e) {}\n" +
                "    if (this.lightTextureSuffix === '_headLight') return mode === 1 || mode === 3;\n" +
                "    if (this.lightTextureSuffix === '_tailLight') return mode === 2 || mode === 3;\n" +
                "    return true;\n" +
                "  };\n" +
                "  CustomLightParts.prototype.render = function(renderer, entity, pass, isObjectGlow) {\n" +
                "    if (entity && isObjectGlow && !this.__ptLightAllowed(entity)) { if (this.parts && typeof this.parts.render === 'function') this.parts.render(renderer); return; }\n" +
                "    return this.__ptOldRender.apply(this, arguments);\n" +
                "  };\n" +
                "  CustomLightParts.prototype.renderLight = function(renderer, entity, pass) {\n" +
                "    if (!this.__ptLightAllowed(entity)) return;\n" +
                "    return this.__ptOldRenderLight.apply(this, arguments);\n" +
                "  };\n" +
                "}\n"
            );
        } catch (ScriptException e) {
            PolygonTrainMod.LOGGER.warn("Failed to prepare script runtime before init", e);
        }
    }

    private static void prepareScriptRuntimeAfterInit(ScriptEngine scriptEngine) {
        try {
            scriptEngine.eval(
                "if (typeof frontSideTrainList === 'undefined') frontSideTrainList = [];\n" +
                "if (typeof rearSideTrainList === 'undefined') rearSideTrainList = [];\n" +
                "if (typeof __ptNoopPart === 'function') {\n" +
                "  if (typeof lcd1 === 'undefined') lcd1 = __ptNoopPart();\n" +
                "  if (typeof lcd2 === 'undefined') lcd2 = __ptNoopPart();\n" +
                "  if (typeof monitor1 === 'undefined') monitor1 = __ptNoopPart();\n" +
                "  if (typeof monitor2 === 'undefined') monitor2 = __ptNoopPart();\n" +
                "  if (typeof timsMonitor === 'undefined') timsMonitor = __ptNoopPart();\n" +
                "}\n"
            );
        } catch (ScriptException e) {
            PolygonTrainMod.LOGGER.warn("Failed to prepare script runtime after init", e);
        }
    }

    private static String quoteJs(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
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
                } catch (RuntimeException e) {
                    PolygonTrainMod.LOGGER.warn("Old model script init failed; keeping script available for render fallback", e);
                    return;
                }
            } catch (ScriptException e) {
                PolygonTrainMod.LOGGER.error("Failed to invoke init for model script", e);
                return;
            } catch (RuntimeException e) {
                PolygonTrainMod.LOGGER.warn("Old model script init failed; keeping script available for render fallback", e);
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
        private int basePackedLight;
        private int overlay;
        private int currentPass;
        private Object currentEntity;
        private net.minecraft.resources.ResourceLocation boundTexture;
        private boolean uvWindowActive;
        private float uvU0;
        private float uvV0;
        private float uvU1 = 1.0F;
        private float uvV1 = 1.0F;
        private int matrixDepth = 0;
        private int renderPartsCalls = 0;
        public final ScriptModelRenderer renderer = this;

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

        public TrainEntity.ConfigCompat getConfig() {
            return new TrainEntity.ConfigCompat();
        }

        public String getResourceName() {
            return "train";
        }

        public String getModelName() {
            if (currentEntity instanceof InstalledObjectBlockEntity blockEntity) {
                return blockEntity.getModelName();
            }
            return "";
        }

        public void setRenderContext(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, int pass, Object entity) {
            restoreMatrixDepth(0);
            this.poseStack = poseStack;
            this.buffer = buffer;
            this.packedLight = packedLight;
            this.basePackedLight = packedLight;
            this.overlay = overlay;
            this.currentPass = pass;
            this.currentEntity = entity;
            this.boundTexture = null;
            clearUvWindow();
            this.matrixDepth = 0;
        }

        public void clearRenderContext() {
            restoreMatrixDepth(0);
            this.poseStack = null;
            this.buffer = null;
            this.currentEntity = null;
            this.boundTexture = null;
            clearUvWindow();
            this.matrixDepth = 0;
        }

        public void resetRenderStatistics() {
            this.renderPartsCalls = 0;
        }

        public int getRenderPartsCalls() {
            return renderPartsCalls;
        }

        public int currentMatId;

        public net.minecraft.resources.ResourceLocation getBoundTexture() {
            return boundTexture;
        }

        public void clearScriptTexture() {
            boundTexture = null;
        }

        public void setUvWindow(double u0, double v0, double u1, double v1) {
            uvWindowActive = true;
            uvU0 = (float) u0;
            uvV0 = (float) v0;
            uvU1 = (float) u1;
            uvV1 = (float) v1;
        }

        public void clearUvWindow() {
            uvWindowActive = false;
            uvU0 = 0.0F;
            uvV0 = 0.0F;
            uvU1 = 1.0F;
            uvV1 = 1.0F;
        }

        public float mapU(float u) {
            return uvWindowActive ? uvU0 + (uvU1 - uvU0) * u : u;
        }

        public float mapV(float v) {
            return uvWindowActive ? uvV0 + (uvV1 - uvV0) * v : v;
        }

        public float mapU(float u, float sourceMin, float sourceMax) {
            if (!uvWindowActive) {
                return u;
            }
            float width = sourceMax - sourceMin;
            float normalized = Math.abs(width) < 1.0E-6F ? 0.5F : (u - sourceMin) / width;
            return uvU0 + (uvU1 - uvU0) * normalized;
        }

        public float mapV(float v, float sourceMin, float sourceMax) {
            if (!uvWindowActive) {
                return v;
            }
            float height = sourceMax - sourceMin;
            float normalized = Math.abs(height) < 1.0E-6F ? 0.5F : (v - sourceMin) / height;
            return uvV0 + (uvV1 - uvV0) * normalized;
        }

        public void bindScriptTexture(String domain, String path, int frameIndex) {
            boundTexture = MqoModelLoader.getScriptTexture(domain, path, frameIndex);
        }

        public void bindTexture(Object texture) {
            if (texture == null) {
                clearScriptTexture();
                return;
            }
            try {
                Object domain = texture.getClass().getMethod("func_110624_b").invoke(texture);
                Object path = texture.getClass().getMethod("func_110623_a").invoke(texture);
                bindScriptTexture(String.valueOf(domain), String.valueOf(path), 0);
            } catch (Exception ignored) {
                clearScriptTexture();
            }
        }

        /**
         * Disables lighting for old model scripts.
         */
        public void disableLighting() {
            // Modern rendering keeps lighting in the packed light value.
        }

        /**
         * Enables lighting for old model scripts.
         */
        public void enableLighting() {
            this.packedLight = basePackedLight;
        }

        /**
         * Applies a packed light value requested by old model scripts.
         */
        public void setBrightness(Object value) {
            if (value instanceof Number number) {
                this.packedLight = number.intValue();
            } else {
                this.packedLight = basePackedLight;
            }
        }

        /**
         * Forces full brightness for emissive script parts.
         */
        public void setLightmapMaxBrightness() {
            this.packedLight = 0x00F000F0;
        }

        public void renderParts(Object groups) {
            if (mqoModel == null || poseStack == null || buffer == null) {
                return;
            }
            renderPartsCalls++;
            int baseDepth = matrixDepth;
            try {
                List<String> groupNames = extractGroupNames(groups);
                if (currentEntity instanceof TrainEntity train) {
                    groupNames = groupNames.stream()
                        .filter(group -> shouldRenderLightGroup(train, group))
                        .collect(Collectors.toList());
                    if (groupNames.isEmpty()) {
                        return;
                    }
                }
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

        private boolean shouldRenderLightGroup(TrainEntity train, String groupName) {
            if (train == null || groupName == null) {
                return true;
            }
            String lower = groupName.toLowerCase(Locale.ROOT);
            boolean head = lower.contains("hlight") || lower.contains("headlight") || lower.contains("head_light");
            boolean tail = lower.contains("tlight") || lower.contains("taillight") || lower.contains("tail_light");
            boolean auxiliary = lower.contains("elight");
            if (!head && !tail && !auxiliary) {
                return true;
            }
            int mode = train.getLightMode();
            if (mode <= 0) {
                return false;
            }
            if (head) {
                return mode == 1 || mode == 3;
            }
            if (tail) {
                return mode == 2 || mode == 3;
            }
            return true;
        }

        public void renderPart(String group) {
            renderParts(group);
        }

        public void render(Object groups) {
            renderParts(groups);
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

        /**
         * Returns the current world hour for old scripts that omit the entity argument.
         */
        public int getMCHour() {
            return getMCHour(currentEntity);
        }

        public int getMCMinute(Object entity) {
            long dayTime = getWorldDayTime(entity);
            return (int) ((dayTime / 20) % 60);
        }

        /**
         * Returns the current world minute for old scripts that omit the entity argument.
         */
        public int getMCMinute() {
            return getMCMinute(currentEntity);
        }

        public float getMovingCount(Object entity) {
            if (entity instanceof InstalledObjectBlockEntity blockEntity) {
                return blockEntity.getBarMoveCount() / 90.0F;
            }
            return 0.0F;
        }

        public int getLightState(Object entity) {
            if (entity instanceof InstalledObjectBlockEntity blockEntity) {
                return Math.max(0, blockEntity.getLightCount());
            }
            return 0;
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
