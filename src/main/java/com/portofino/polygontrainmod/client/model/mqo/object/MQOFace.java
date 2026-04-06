package com.portofino.polygontrainmod.client.model.mqo.object;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public record MQOFace(
    int vertices,
    int[] vertexIndices,
    int material,
    float[][] uvs,
    MQOVector[] vectors
) {
    @Override
    public @NotNull String toString() {
        return "MQOFace{" +
            "vertices=" + vertices +
            ", vertexIndices=" + java.util.Arrays.toString(vertexIndices) +
            ", material=" + material +
            ", uvs=" + java.util.Arrays.deepToString(uvs) +
            ", vectors=" + java.util.Arrays.toString(vectors) +
            '}';
    }
}
