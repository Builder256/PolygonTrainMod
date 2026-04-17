package com.portofino.polygontrainmod.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MQOModel {
    private final List<Vertex> vertices = new ArrayList<>();
    private final List<Face> faces = new ArrayList<>();
    private final Map<String, Material> materials = new HashMap<>();
    private final List<ObjectGroup> groups = new ArrayList<>();
    private Object scriptEngine; // Script engine for animations

    public void addVertex(float x, float y, float z) {
        vertices.add(new Vertex(x, y, z));
    }

    public void addFace(int[] vertexIndices, float[][] uvs, String materialName) {
        faces.add(new Face(vertexIndices, uvs, materialName));
    }

    public void addMaterial(String name, ResourceLocation texture) {
        materials.put(name, new Material(name, texture));
    }

    public void addGroup(String name) {
        groups.add(new ObjectGroup(name));
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, int pass) {
        // Execute script before rendering if available
        if (scriptEngine != null) {
            executeScript(poseStack, pass);
        }

        com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("Rendering MQO model: {} vertices, {} faces, {} materials", vertices.size(), faces.size(), materials.size());

        for (Face face : faces) {
            Material mat = materials.get(face.materialName);
            if (mat != null) {
                // Render face with texture
                for (int i = 0; i < face.vertexIndices.length; i++) {
                    int vi = face.vertexIndices[i];
                    if (vi >= 0 && vi < vertices.size()) {
                        Vertex v = vertices.get(vi);
                        float u = face.uvs != null && i < face.uvs.length ? face.uvs[i][0] : 0.0f;
                        float vCoord = face.uvs != null && i < face.uvs.length ? face.uvs[i][1] : 0.0f;
                        consumer.addVertex(poseStack.last().pose(), v.x, v.y, v.z)
                            .setColor(1.0f, 1.0f, 1.0f, 1.0f)
                            .setUv(u, vCoord)
                            .setUv2((packedLight & 0xFFFF), (packedLight >> 16))
                            .setNormal(0.0f, 1.0f, 0.0f);
                    }
                }
            }
        }
    }

    private void executeScript(PoseStack poseStack, int pass) {
        // Execute script to apply transformations
        // This is a simplified implementation
        try {
            if (scriptEngine instanceof javax.script.ScriptEngine) {
                javax.script.ScriptEngine engine = (javax.script.ScriptEngine) scriptEngine;
                engine.put("poseStack", poseStack);
                engine.put("pass", pass);
                engine.eval("if (typeof render === 'function') render(poseStack, pass);");
            }
        } catch (Exception e) {
            // Script execution failed, continue without script
        }
    }

    public void setScriptEngine(Object engine) {
        this.scriptEngine = engine;
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight) {
        render(poseStack, consumer, packedLight, 0);
    }

    public static class Vertex {
        public final float x, y, z;
        public Vertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class Face {
        public final int[] vertexIndices;
        public final float[][] uvs; // UV coordinates for each vertex
        public final String materialName;
        public Face(int[] vertexIndices, float[][] uvs, String materialName) {
            this.vertexIndices = vertexIndices;
            this.uvs = uvs;
            this.materialName = materialName;
        }
    }

    public static class Material {
        public final String name;
        public final ResourceLocation texture;
        public Material(String name, ResourceLocation texture) {
            this.name = name;
            this.texture = texture;
        }
    }

    public static class ObjectGroup {
        public final String name;
        public ObjectGroup(String name) {
            this.name = name;
        }
    }
}
