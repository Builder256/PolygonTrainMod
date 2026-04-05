package com.portofino.polygontrainmod.client.model.mqo.object;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record MQOFace(
    int vertices,
    int[] vertexIndices,
    int material,
    float[][] uvCoordinates,
    MQOVector[] normalVectors
) {
    @Override
    public String toString() {
        return "MQOFace{" +
            "vertices=" + vertices +
            ", vertexIndices=" + java.util.Arrays.toString(vertexIndices) +
            ", material=" + material +
            ", uvCoordinates=" + java.util.Arrays.deepToString(uvCoordinates) +
            ", normalVectors=" + java.util.Arrays.toString(normalVectors) +
            '}';
    }
}
