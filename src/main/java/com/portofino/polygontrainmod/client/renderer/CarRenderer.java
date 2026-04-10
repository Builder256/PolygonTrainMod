package com.portofino.polygontrainmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.portofino.polygontrainmod.client.model.mqo.MQOLoader;
import com.portofino.polygontrainmod.client.model.mqo.MQOModel;
import com.portofino.polygontrainmod.client.model.mqo.object.MQOVector;
import com.portofino.polygontrainmod.entity.CarEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.*;

import static com.portofino.polygontrainmod.PolygonTrainMod.MODID;

@OnlyIn(Dist.CLIENT)
public final class CarRenderer extends EntityRenderer<CarEntity> {
    // モデル・テクスチャ
    private static final String[] TEXTURE_PATHS = {"textures/car/toyota_prius-phv.png", "textures/car/wheel.png"};
    private static final ResourceLocation[] TEXTURES = Arrays
        .stream(TEXTURE_PATHS)
        .map(path -> ResourceLocation.fromNamespaceAndPath(MODID, path))
        .toArray(ResourceLocation[]::new);
    private static MQOModel MODEL = Objects.requireNonNull(MQOLoader.load("models/car/toyota_prius-phv.mqo"), "モデルをロードできませんでした。");
    // オブジェクト
    private static final RenderGroup[] RENDER_GROUPS;

    private static final String PART_NAME_BODY = "body";
    private static final String PART_NAME_STEERING_WHEEL = "steering";
    private static final String PART_NAME_STEERED_WHEEL_F_L = "wheelF_L";
    private static final String PART_NAME_STEERED_WHEEL_F_R = "wheelF_R";
    private static final String PART_NAME_WHEEL_R = "wheelR";

    static {
        // TODO: 頂点法線の計算アルゴリズムを、一般的なものからMetasequoia特有の特殊アルゴリズムで再実装、選択可能にする。
        // TODO: AIの書いたアルゴリズムを理解する
        final var objects = MODEL.objects();
        final var materials = MODEL.materials();

        if (materials.length != TEXTURES.length)
            throw new RuntimeException("テクスチャとモデルのマテリアルの数量が一致しません。");

        final var root = Arrays
            .stream(TEXTURES)
            .map(texture -> new RenderGroup(texture, new ArrayList<Part>()))
            .toArray(RenderGroup[]::new);

        // オブジェクトごとの処理
        for (var obj : objects) {
            final var name = obj.name();
            final var objVertices = obj.vertices();
            final var faces = obj.faces();
            final var isSmooth = obj.isSmoothShadingEnabled();
            final var cosThreshold = (float) Math.cos(Math.toRadians(obj.autoSmoothAngle()));

            // 各フェースの面法線を事前に計算する
            MQOVector[] faceNormalsCalculated = new MQOVector[faces.length];
            for (int i = 0; i < faces.length; i++) {
                var face = faces[i];
                var vIndices = face.vertexIndices();
                if (vIndices != null && vIndices.length >= 3) {
                    // MQO(CW)の順序: 0, 1, 2
                    // Minecraft(CCW)では逆順になるため、法線計算もそれに合わせる
                    var v0 = objVertices[vIndices[vIndices.length - 1]];
                    var v1 = objVertices[vIndices[vIndices.length - 2]];
                    var v2 = objVertices[vIndices[vIndices.length - 3]];
                    float ax = v1.x() - v0.x();
                    float ay = v1.y() - v0.y();
                    float az = v1.z() - v0.z();
                    float bx = v2.x() - v0.x();
                    float by = v2.y() - v0.y();
                    float bz = v2.z() - v0.z();
                    float nx = ay * bz - az * by;
                    float ny = az * bx - ax * bz;
                    float nz = ax * by - ay * bx;
                    float r = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (r > 0) {
                        faceNormalsCalculated[i] = new MQOVector(nx / r, ny / r, nz / r);
                    } else {
                        faceNormalsCalculated[i] = new MQOVector(0, 1, 0); // フォールバック
                    }
                } else {
                    faceNormalsCalculated[i] = new MQOVector(0, 1, 0);
                }
            }

            // 頂点インデックスから、その頂点を含むフェースのインデックスリストへのマップを作成
            Map<Integer, List<Integer>> vertexToFaces = new HashMap<>();
            for (int i = 0; i < faces.length; i++) {
                for (int vIdx : faces[i].vertexIndices()) {
                    vertexToFaces.computeIfAbsent(vIdx, k -> new ArrayList<>()).add(i);
                }
            }

            // マテリアルごとにこのオブジェクトのポリゴンを一時的に分類
            Map<Integer, List<Polygon>> polygonsByMaterial = new HashMap<>();

            // フェースごとの処理
            for (int faceIdx = 0; faceIdx < faces.length; faceIdx++) {
                var face = faces[faceIdx];
                final var vertexQuantity = face.vertices();
                final var vertexIndices = face.vertexIndices();
                final var matIndex = face.material();
                final var uvs = face.uvs();
                final var customNormals = face.normals();
                final var currentFaceNormal = faceNormalsCalculated[faceIdx];

                if (matIndex < 0 || matIndex >= materials.length) continue;

                List<Vertex> faceVertices = new ArrayList<>();

                // 頂点ごとの処理
                for (var i = vertexQuantity - 1; i >= 0; i--) { // MQOは時計回り、Minecraftは反時計回り
                    final var vertexIndex = vertexIndices[i];
                    final var vertexCoord = objVertices[vertexIndex];
                    final var vertexUV = (uvs != null && i < uvs.length) ? uvs[i] : new float[]{0, 0};

                    float nx, ny, nz;
                    // 1. カスタム法線がある場合はそれを使用
                    if (customNormals != null && i < customNormals.length && customNormals[i] != null) {
                        nx = customNormals[i].x();
                        ny = customNormals[i].y();
                        nz = customNormals[i].z();
                    }
                    // 2. スムースシェーディングが有効な場合、周辺フェースの法線を平均化
                    else if (isSmooth && (currentFaceNormal.x() != 0 || currentFaceNormal.y() != 0 || currentFaceNormal.z() != 0)) {
                        float snx = 0, sny = 0, snz = 0;
                        List<Integer> sharedFaces = vertexToFaces.get(vertexIndex);
                        if (sharedFaces != null) {
                            for (int otherFaceIdx : sharedFaces) {
                                var otherNormal = faceNormalsCalculated[otherFaceIdx];
                                // dot積で角度を判定
                                float dot = currentFaceNormal.x() * otherNormal.x() +
                                    currentFaceNormal.y() * otherNormal.y() +
                                    currentFaceNormal.z() * otherNormal.z();
                                if (dot >= cosThreshold) {
                                    snx += otherNormal.x();
                                    sny += otherNormal.y();
                                    snz += otherNormal.z();
                                }
                            }
                        }
                        float r = (float) Math.sqrt(snx * snx + sny * sny + snz * snz);
                        if (r > 0) {
                            nx = snx / r;
                            ny = sny / r;
                            nz = snz / r;
                        } else {
                            nx = currentFaceNormal.x();
                            ny = currentFaceNormal.y();
                            nz = currentFaceNormal.z();
                        }
                    }
                    // 3. それ以外は面法線を使用
                    else {
                        nx = currentFaceNormal.x();
                        ny = currentFaceNormal.y();
                        nz = currentFaceNormal.z();
                    }

                    faceVertices.add(new Vertex(
                        cm2m(vertexCoord.x()),
                        cm2m(vertexCoord.y()),
                        cm2m(vertexCoord.z()),
                        vertexUV[0],
                        vertexUV[1],
                        nx, ny, nz
                    ));
                }

                // 多角形ポリゴンを3点または4点の集まりに分割する
                List<Polygon> polygons = polygonsByMaterial.computeIfAbsent(matIndex, k -> new ArrayList<>());
                if (vertexQuantity == 3 || vertexQuantity == 4) {
                    polygons.add(new Polygon(faceVertices.toArray(Vertex[]::new)));
                } else if (vertexQuantity > 4) {
                    // 三角形ファン方式で分割
                    for (int j = 1; j < faceVertices.size() - 1; j++) {
                        polygons.add(new Polygon(new Vertex[]{
                            faceVertices.get(0),
                            faceVertices.get(j),
                            faceVertices.get(j + 1)
                        }));
                    }
                }
            }

            // このオブジェクトに含まれるポリゴンをマテリアルごとにパーツとして登録
            for (var entry : polygonsByMaterial.entrySet()) {
                root[entry.getKey()].parts().add(new Part(name, entry.getValue()));
            }
        }

        RENDER_GROUPS = root;
        MODEL = null; // 解析が終わったので参照を消してメモリを解放
    }

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    @NotNull
    public ResourceLocation getTextureLocation(@NotNull CarEntity entity) {
        return TEXTURES[0];
    }

    @Override
    public void render(
        @NotNull CarEntity entity,
        float entityYaw,
        float partialTick,
        PoseStack poseStack,
        @NotNull MultiBufferSource bufferSource,
        int packedLight
    ) {
        poseStack.pushPose();

        // 車両の回転を描画
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw)); // 車両を中心で回転させる 符号を逆転させる必要がある
        // EntityのYawは正の方向から見て時計回りだが、OpenGLのglRotateは正の方向から見て反時計回りだからということだと思う。

        final Matrix4f matrix = poseStack.last().pose();
        // テクスチャごとの描画
        for (var renderGroup : RENDER_GROUPS) {
            final VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucentCull(renderGroup.texture)); // 半透明で裏面にカリングをする
            // パーツごとの描画
            for (var part : renderGroup.parts) {
                // 座標変換が必要な場合は、ここでやったりするのかな
                // ポリゴンごとの描画
                for (var polygon : part.polygons) {
                    final var len = polygon.vertices.length;
                    if (len < 3 || len > 4) continue; // 頂点が3個未満、4超過のポリゴンは描画できない
                    var currentBuffer = buffer;
                    for (var vertex : polygon.vertices) {
                        currentBuffer = addVertexToVertexConsumerAndGetVertexConsumerBack(currentBuffer, matrix, packedLight, vertex);
                    }
                    if (len == 3) { // 三角ポリゴンの場合は最後の頂点をもう一回追加して四角ポリゴン化
                        addVertexToVertexConsumerAndGetVertexConsumerBack(currentBuffer, matrix, packedLight, polygon.vertices[2]);
                    }
                }
            }
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /// VertexConsumerに頂点を追加する
    private static VertexConsumer addVertexToVertexConsumerAndGetVertexConsumerBack(VertexConsumer buffer, Matrix4f matrix, int packedLight, Vertex v) {
        return buffer
            .addVertex(matrix, v.x, v.y, v.z)
            .setColor(255, 255, 255, 255)
            .setUv(v.u, v.v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(v.nx, v.ny, v.nz);
    }

    /// センチメートル単位の値をメートルに変換する
    private static float cm2m(float cm) {
        return cm / 100; // 100cm=1m
    }

    private record Material(String name, ResourceLocation texture) {
    }

    /// テクスチャごとのパーツの集合
    private record RenderGroup(ResourceLocation texture, List<Part> parts) {
    }

    /// パーツ 座標変換をする際の最低単位、オブジェクトごと、あるいはボブジェクトのグループごとに存在する
    private record Part(String name, List<Polygon> polygons) {
    }

    /// ポリゴン 3または4個の頂点からなる
    private record Polygon(Vertex[] vertices) {
    }

    /// 頂点 頂点座標、UV座標、頂点法線ベクトルからなる
    private record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
    }
}