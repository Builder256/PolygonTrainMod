package com.portofino.polygontrainmod.rail;

import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class RailDefinition {
    private final String id;
    private final String displayName;
    private final String packName;
    private final String packResourcePath;
    private final String modelFile;
    private final String scriptPath;
    private final Map<String, String> textureOverrides;
    private final Vec3 modelOffset;
    private final float modelScale;
    private final int ballastWidth;

    public RailDefinition(String id, String displayName, String packName, String packResourcePath,
                          String modelFile, String scriptPath, Map<String, String> textureOverrides,
                          Vec3 modelOffset, float modelScale, int ballastWidth) {
        this.id = id;
        this.displayName = displayName;
        this.packName = packName;
        this.packResourcePath = packResourcePath;
        this.modelFile = modelFile;
        this.scriptPath = scriptPath;
        this.textureOverrides = textureOverrides == null ? Map.of() : Map.copyOf(textureOverrides);
        this.modelOffset = modelOffset == null ? Vec3.ZERO : modelOffset;
        this.modelScale = modelScale <= 0 ? 1.0F : modelScale;
        this.ballastWidth = Math.max(0, ballastWidth);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getPackName() { return packName; }
    public String getPackResourcePath() { return packResourcePath; }
    public String getModelFile() { return modelFile; }
    public String getScriptPath() { return scriptPath; }
    public Map<String, String> getTextureOverrides() { return textureOverrides; }
    public Vec3 getModelOffset() { return modelOffset; }
    public float getModelScale() { return modelScale; }
    public int getBallastWidth() { return ballastWidth; }
}
