package com.portofino.polygontrainmod.installedobject;

import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class InstalledObjectDefinition {
    private final String id;
    private final String displayName;
    private final String packName;
    private final InstalledObjectCategory category;
    private final String modelFile;
    private final String scriptPath;
    private final Map<String, String> textureOverrides;
    private final Vec3 modelOffset;
    private final float modelScale;
    private final boolean smoothing;
    private final float width;
    private final float height;
    private final float depth;
    private final String signTexture;

    public InstalledObjectDefinition(String id, String displayName, String packName, InstalledObjectCategory category,
                                     String modelFile, String scriptPath, Map<String, String> textureOverrides,
                                     Vec3 modelOffset, float modelScale, boolean smoothing,
                                     float width, float height, float depth, String signTexture) {
        this.id = id;
        this.displayName = displayName;
        this.packName = packName;
        this.category = category;
        this.modelFile = modelFile;
        this.scriptPath = scriptPath;
        this.textureOverrides = textureOverrides == null ? Map.of() : Map.copyOf(textureOverrides);
        this.modelOffset = modelOffset == null ? Vec3.ZERO : modelOffset;
        this.modelScale = modelScale <= 0.0F ? 1.0F : modelScale;
        this.smoothing = smoothing;
        this.width = width <= 0.0F ? 1.0F : width;
        this.height = height <= 0.0F ? 1.0F : height;
        this.depth = depth <= 0.0F ? 0.125F : depth;
        this.signTexture = signTexture == null ? "" : signTexture;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPackName() {
        return packName;
    }

    public InstalledObjectCategory getCategory() {
        return category;
    }

    public String getModelFile() {
        return modelFile;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public Map<String, String> getTextureOverrides() {
        return textureOverrides;
    }

    public Vec3 getModelOffset() {
        return modelOffset;
    }

    public float getModelScale() {
        return modelScale;
    }

    public boolean isSmoothing() {
        return smoothing;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public float getDepth() {
        return depth;
    }

    public String getSignTexture() {
        return signTexture;
    }
}
