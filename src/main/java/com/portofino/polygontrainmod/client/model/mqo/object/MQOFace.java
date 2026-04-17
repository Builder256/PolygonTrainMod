package com.portofino.polygontrainmod.client.model.mqo.object;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// MQOモデルのフェース
///
/// @param vertices      頂点数
/// @param vertexIndices 頂点がそれぞれオブジェクトの何番目の頂点を指すかのインデックス
/// @param material      使用するマテリアルのインデックス
/// @param uvs           それぞれの頂点のUV座標
/// @param normals       それぞれの頂点の法線ベクトル カスタムされていないときはnull
@OnlyIn(Dist.CLIENT)
public record MQOFace(
    int vertices,
    int[] vertexIndices,
    int material,
    float[][] uvs,
    @Nullable MQOVector[] normals
) {
    @Override
    public @NotNull String toString() {
        return "MQOFace{" +
            "vertices=" + vertices +
            ", vertexIndices=" + java.util.Arrays.toString(vertexIndices) +
            ", material=" + material +
            ", uvs=" + java.util.Arrays.deepToString(uvs) +
            ", normals=" + java.util.Arrays.toString(normals) +
            '}';
    }
}
