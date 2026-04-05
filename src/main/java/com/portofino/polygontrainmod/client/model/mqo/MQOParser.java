package com.portofino.polygontrainmod.client.model.mqo;

import com.portofino.polygontrainmod.client.model.mqo.object.MQOFace;
import com.portofino.polygontrainmod.client.model.mqo.object.MQOObject;
import com.portofino.polygontrainmod.client.model.mqo.object.MQOVector;
import com.portofino.polygontrainmod.client.model.mqo.object.MQOVertex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/// MQOファイルのパーサー
@OnlyIn(Dist.CLIENT)
public final class MQOParser {

    ///  ファイルをパースする
    ///
    /// @param modelReader MQOファイルをロードしたBufferedReader
    /// @return パース結果
    /// @throws IOException modelReader.readlineが投げる可能性がある
    public static @NotNull MQOParseResult parse(@NotNull BufferedReader modelReader) throws IOException {
        @Nullable String line;

        MQOMaterial[] materials = null;
        var objects = new ArrayList<MQOObject>();

        while ((line = modelReader.readLine()) != null) {
            final var isGlobalChunkInitialLine = isGlobalChunkInitialLine(line);
            if (!isGlobalChunkInitialLine) continue;

            final MQOGlobalChunk currentGlobalChunk = extractGlobalChunkName(line);

            switch (currentGlobalChunk) {
                case MATERIAL:
//                    LOGGER.info("processing material chunk!");
                    final var materialQuantity = extractChunkQuantity(line);
                    final var currentMaterials = new MQOMaterial[materialQuantity];
                    var matI = 0;
                    while (isNotChunkFinish(line = modelReader.readLine())) {
                        final var materialName = extractMaterialName(line);
                        final var currentMaterial = new MQOMaterial(materialName);
                        currentMaterials[matI++] = currentMaterial;
                    }
                    materials = currentMaterials;
//                    LOGGER.info("materials: {}", Arrays.toString(materials));
                    break;
                case OBJECT:
//                    LOGGER.info("processing object chunk!");
                    final var name = extractObjectName(line);
                    var isSmoothShadingEnabled = false;
                    var autoSmoothAngle = 59.5f;
                    var mirrorType = 0;
                    var isMirrorAxisXEnabled = false;
                    var isMirrorAxisYEnabled = false;
                    var isMirrorAxisZEnabled = false;
                    var mirrorDistance = 0.0f;
                    MQOVertex[] vertices = null;
                    MQOFace[] faces = null;

                    while (isNotChunkFinish(line = modelReader.readLine())) {
                        final var propKey = extractObjectPropKey(line);
                        switch (propKey) {
                            case "shading":
//                                LOGGER.info("processing shading chunk!");
                                final var shadingValue = line.charAt(line.length() - 1); // 行の最後の一字
                                isSmoothShadingEnabled = shadingValue == '1';
                                break;
                            case "facet":
//                                LOGGER.info("processing facet chunk!");
                                autoSmoothAngle = extractLastStringAsFloat(line);
                                break;
                            case "mirror":
//                                LOGGER.info("processing mirror chunk!");
                                mirrorType = extractLastStringAsInt(line);
                                break;
                            case "mirror_axis":
//                                LOGGER.info("processing mirror_axis chunk!");
                                final var mirrorAxisValue = extractLastStringAsInt(line);
                                isMirrorAxisXEnabled = (mirrorAxisValue & 1) != 0;
                                isMirrorAxisYEnabled = (mirrorAxisValue & 2) != 0;
                                isMirrorAxisZEnabled = (mirrorAxisValue & 4) != 0;
                                break;
                            case "mirror_dis":
//                                LOGGER.info("processing mirror_dis chunk!");
                                mirrorDistance = extractLastStringAsFloat(line);
                                break;
                            case "vertex":
//                                LOGGER.info("processing vertex chunk!");
                                final var vertexQuantity = extractChunkQuantity(line);
                                final var currentVertices = new MQOVertex[vertexQuantity];
                                var vertexI = 0;
                                while (isNotChunkFinish(line = modelReader.readLine())) {
                                    currentVertices[vertexI++] = parseVertexLine(line);
                                }
                                vertices = currentVertices;
                                break;
                            case "face":
//                                LOGGER.info("processing face chunk!");
                                final var faceQuantity = extractChunkQuantity(line);
                                final var currentFaces = new MQOFace[faceQuantity];
                                var faceI = 0;
                                while (isNotChunkFinish(line = modelReader.readLine())) {
                                    currentFaces[faceI++] = parseFaceLine(line);
                                }
                                faces = currentFaces;
                                break;
                            default:
                                break;
                        }
                    }

                    final var object = new MQOObject(
                        name,
                        isSmoothShadingEnabled,
                        autoSmoothAngle,
                        mirrorType,
                        isMirrorAxisXEnabled,
                        isMirrorAxisYEnabled,
                        isMirrorAxisZEnabled,
                        mirrorDistance,
                        vertices,
                        faces
                    );

                    objects.add(object);
                    break;
                default:
                    break;
            }
        }

        final var model = new MQOModel(materials, objects);

        return new MQOParseResult(model, MQOParseResultStatus.SUCCESS);
    }

    private static MQOVertex parseVertexLine(@NotNull String vertexLine) {
        final var len = vertexLine.length();
        var i = 0;
        var start = i; // 現在パース中のデータの開始
        var x = 0.0f;
        var y = 0.0f;
        var z = 0.0f;

        // 値の前の空白をスキップ
        while (i < len && vertexLine.charAt(i) <= ' ') i++;
        start = i;
        // X座標の終わりまでスキップ
        while (i < len && vertexLine.charAt(i) > ' ') i++;
        x = parseFloat(vertexLine.substring(start, i));

        // Y,Z座標にも繰り返し
        while (i < len && vertexLine.charAt(i) <= ' ') i++;
        start = i;
        while (i < len && vertexLine.charAt(i) > ' ') i++;
        y = parseFloat(vertexLine.substring(start, i));

        while (i < len && vertexLine.charAt(i) <= ' ') i++;
        start = i;
        while (i < len && vertexLine.charAt(i) > ' ') i++;
        z = parseFloat(vertexLine.substring(start, i));

//        LOGGER.info("vertex: {},{},{}", x, y, z);
        return new MQOVertex(x, y, z);
    }

    private static MQOFace parseFaceLine(@NotNull String faceLine) {
//        LOGGER.info("parsing face line!");
        final var len = faceLine.length();
        final var s = faceLine.toCharArray();
        var i = 0;

        // 頂点数
        var vertices = -1;
        // それぞれの頂点が、オブジェクト内の保存順で何番目の頂点か
        int[] vertexIndices = null;
        // 面にどの材質を適用するか
        int material = -2;
        // それぞれの頂点のUV座標
        float[][] uvs = null;
        // それぞれの頂点の法線ベクトル
        MQOVector[] normals = null;

//        LOGGER.info("I'm entering to the main loop!");

        while (i < len) {
            final var c = s[i];

            // 以下の分岐は、チャンク開始のキーワードにしか興味がない
            // そのため、不要なチャンクがどんなに含まれていても無視して処理を続けるはずである

            // 頂点数
            if (Character.isDigit(c) && vertices == -1) { // 頂点数はプレーンな数字 verticesがすでにある場合は、不要チャンク中の値なので無視
//                LOGGER.info("parsing vertex quantity!");

                final var start = i;
                while (i < len && Character.isDigit(s[i])) i++; // 何桁かわからないのでくりかえす
                vertices = Integer.parseInt(new String(s, start, i - start)); // 切り出して保存

//                LOGGER.info("vertices: {}", vertices);
                continue;
            }

            // Metasequoiaによって保存された正規のファイルでは、頂点数は行の先頭に必ずあるので以後verticesの値は実際の頂点数だとみなす
            // そうでないファイルは知らん、クラッシュしとけ。

            // 頂点インデックス
            if (c == 'V' && i + 1 < len && s[i + 1] == '(') { // 頂点インデックスチャンクはV(で始まる
//                LOGGER.info("parsing vertex indices!");
                i += 2; // V(をスキップ

                // パース結果を入れていく
                var temp = new int[vertices == -1 ? 16 : vertices]; // 要素数は頂点数が先にわかっていればそれ、なければ16
                var count = 0; // 現在見つかっている頂点数

                while (i < len && s[i] != ')') { // チャンクが終わる直前まで繰り返し
                    if (Character.isDigit(s[i])) { // データがある
                        var start = i;
                        while (i < len && Character.isDigit(s[i])) i++; // 何桁かわからないので数字が終わるまでスキップ
                        if (count == temp.length) { // tempを既に一杯まで使っている
                            temp = Arrays.copyOf(temp, temp.length * 2); // 倍に増やす
                        }
                        temp[count++] = Integer.parseInt(new String(s, start, i - start));
                    } else { // 区切りの空白
                        i++; // スキップ
                    }
                }
                i++; // )をスキップ

                vertexIndices = Arrays.copyOf(temp, count); // 使ってる分だけ切り詰めて保存
//                LOGGER.info("vertexIndices: {}", Arrays.toString(vertexIndices));
                continue;
            }

            // 材質
            if (c == 'M' && i + 1 < len && s[i + 1] == '(') { // 材質チャンクはM(で始まる
//                LOGGER.info("parsing face material!");
                i += 2;

                final var start = i;
                while (i < len && ((s[i] >= '0' && s[i] <= '9') || s[i] == '-')) i++;
                material = Integer.parseInt(new String(s, start, i - start));
                i++;

//                LOGGER.info("material: {}", material);
                continue;
            }

            // UV座標
            if (c == 'U' && i + 2 < len && s[i + 1] == 'V' && s[i + 2] == '(') { // UV座標チャンクはUV(で始まる
//                LOGGER.info("parsing UV coordinates!");
                i += 3; // UV(をスキップ

                var temp = new float[vertices == -1 ? 32 : vertices * 2]; // 1頂点にUV座標は1個ずつ（2倍）
                var count = 0;

                while (i < len && s[i] != ')') {
                    if ((s[i] >= '0' && s[i] <= '9') || s[i] == '-' || s[i] == '.') { // 実数っぽい文字で始まる
                        final var start = i;
                        while (i < len && "0123456789eE+-.".indexOf(s[i]) >= 0) i++; // ぽい文字が終わるまでスキップ
                        if (count == temp.length) {
                            temp = Arrays.copyOf(temp, temp.length * 2);
                        }
                        temp[count++] = parseFloat(new String(s, start, i - start));
                    } else {
                        i++;
                    }
                }
                i++;

                // 1頂点分ごとに配列を分割する
                final var pairCount = count / 2; // 1頂点に座標の数値は2個
                uvs = new float[pairCount][2];
                for (var j = 0; j < pairCount; j++) {
                    // 頂点jのU座標
                    uvs[j][0] = temp[j * 2];
                    // V座標
                    uvs[j][1] = temp[j * 2 + 1];
                }
//                LOGGER.info("uvs: {}", Arrays.deepToString(uvs));
                continue;
            }

            // 法線ベクトル
            if (c == 'N' && i + 1 < len && s[i + 1] == '(') { // 法線ベクトルチャンクはN(で始まる
//                LOGGER.info("parsing normal vectors!");
                i += 2;

                // まずは同様に処理
                var temp = new float[vertices == -1 ? 48 : vertices];
                var count = 0;

                while (i < len && s[i] != ')') {
                    if ((s[i] >= '0' && s[i] <= '9') || s[i] == '-' || s[i] == '.') {
                        var start = i;
                        while (i < len && "0123456789.eE+-".indexOf(s[i]) >= 0) i++;
                        if (count == temp.length) {
                            temp = Arrays.copyOf(temp, temp.length * 2);
                        }
//                        LOGGER.info("{}, {}", start, i);
                        temp[count++] = parseFloat(new String(s, start, i - start));
                    } else {
                        i++;
                    }
                }
                i++;

                // tempをindexと法線データに分割する

                // 各indexの頂点がカスタム法線をもつか
                var indices = new int[vertices];
                for (var j = 0; j < vertices; j++) {
                    indices[j] = (int) temp[j];
                }

                // tempでの法線データ分の要素数
                var remaining = count - vertices;
                // それが頂点いくつ分か->カスタム法線の頂点をいくつ持つか
                var normalCount = remaining / 3;

                // カスタム法線を頂点ごとに保存
                var normalTable = new MQOVector[normalCount];
                for (var j = 0; j < normalCount; j++) {
                    normalTable[j] = new MQOVector(
                        temp[vertices + j * 3],
                        temp[vertices + j * 3 + 1],
                        temp[vertices + j * 3 + 2]
                    );
                }

                // 最終結果
                normals = new MQOVector[vertices];

                var normalCursor = 0;
                for (int j = 0; j < vertices; j++) {
                    if (indices[j] == 0) { // カスタム法線を持たない
                        normals[j] = null;
                    } else { // 持つ
                        // 保存
                        normals[j] = normalTable[normalCursor++]; // 次のカスタム頂点に移動
                    }
                }
//                LOGGER.info("normals: {}", Arrays.toString(normals));
                continue;
            }

//            LOGGER.info("passed line: {}", c);
            i++;
        }

        return new MQOFace(vertices, vertexIndices, material, uvs, normals);
    }

    /// 行がグローバルチャンクの開始行かどうか
    ///
    /// @param mqoLine どこか一行
    private static boolean isGlobalChunkInitialLine(@Nullable String mqoLine) {
        if (mqoLine == null || mqoLine.isEmpty()) return false;

        // 最初の文字が大小アルファベットかどうかで判定
        final var c = mqoLine.charAt(0);
        return isAlphabet(c);
    }

    /// 行がチャンクの終了行かどうか
    ///
    /// @param mqoLine どこか一行
    private static boolean isNotChunkFinish(@Nullable String mqoLine) {
        if (mqoLine == null || mqoLine.isEmpty()) return true;

        final var lastChar = mqoLine.charAt(mqoLine.length() - 1);
        return lastChar != '}';
    }

    /// グローバルチャンクの開始行からグローバルチャンク名を抽出する
    ///
    /// @param globalChunkInitialLine グローバルチャンクの開始行
    private static @NotNull MQOGlobalChunk extractGlobalChunkName(@NotNull String globalChunkInitialLine) {
        String chunkName = null;
        for (int i = 0; i < globalChunkInitialLine.length(); i++) {
            if (globalChunkInitialLine.charAt(i) == ' ') {
                chunkName = globalChunkInitialLine.substring(0, i);
                break;
            }
        }
        if (chunkName == null) return MQOGlobalChunk.OTHER; // 無名チャンクは存在しないのでエラーにしてもいいかも？
        if ("Material".equalsIgnoreCase(chunkName)) return MQOGlobalChunk.MATERIAL;
        if ("Object".equalsIgnoreCase(chunkName)) return MQOGlobalChunk.OBJECT;
        return MQOGlobalChunk.OTHER;
    }

    /// チャンクのデータ数量を抽出する
    ///
    /// @param chunkInitialLine データ数量が含まれるチャンクの開始行
    private static int extractChunkQuantity(@NotNull String chunkInitialLine) {
//        LOGGER.info("extracting chunk quantity!");
        final var len = chunkInitialLine.length();
        var i = 0;

        // 冒頭の空白をスキップ
        while (i < len && chunkInitialLine.charAt(i) <= ' ') i++;

        // チャンク名の終了までスキップ
        while (i < len && chunkInitialLine.charAt(i) > ' ') i++;

        // チャンク名の後の空白をスキップ
        while (i < len && chunkInitialLine.charAt(i) <= ' ') i++;

        final var start = i;

        // 値の終了までスキップ
        while (i < len && Character.isDigit(chunkInitialLine.charAt(i))) i++;

        return Integer.parseInt(chunkInitialLine.substring(start, i)); // 終了したところで返却
    }

    /// 材質名を抽出する
    ///
    /// @param materialLine Materialチャンク内の行
    private static @NotNull String extractMaterialName(@NotNull String materialLine) {
        var start = -1; // 材質名の開始インデックス

        for (var i = 0; i < materialLine.length(); i++) {
            final var c = materialLine.charAt(i);
            if (c != '"') continue;

            if (start == -1) { // 開始の"を発見
                start = i + 1; // 次の文字を記録
            } else { // 開始でないときは終了（通常、内部に"が含まれることはない）
                return materialLine.substring(start, i);
            }
        }
        return "";
    }

    /// オブジェクト名を抽出する
    ///
    /// @param objectChunkInitialLine Objectチャンクの開始行
    private static @NotNull String extractObjectName(@NotNull String objectChunkInitialLine) {
        final var start = "Object \"".length(); // オブジェクト名の開始インデックス オブジェクト名は8文字目から 7文字目は"

        for (var i = start; i < objectChunkInitialLine.length(); i++) {
            final var c = objectChunkInitialLine.charAt(i);
            if (c != '"') continue;

            return objectChunkInitialLine.substring(start, i);
        }
        return "";
    }

    /// Objectのプロパティキーを抽出する
    ///
    /// @param objectLine Objectチャンク内の行
    private static @NotNull String extractObjectPropKey(@NotNull String objectLine) {
        // 文字列中の半角スペースで区切られた最初の箇所を取得する
        var start = -1;

        for (var i = 0; i < objectLine.length(); i++) {
            final var c = objectLine.charAt(i);

            if (start == -1) { // キーの最初が見つかってない
                // キーの最初の文字が英字以外で始まるチャンクが実装されないことを信じる
                if (isAlphabet(c)) { // キーの最初を発見
                    start = i; // 場所を記録
                }
            } else { // キーの最初が見つかっている
                if (c == ' ') { // キーの最後を発見
                    return objectLine.substring(start, i); // 返却
                }
            }
        }
        return "";
    }

    /// スペースで区切られた最後の文字列を抽出する
    ///
    /// @param mqoLine どこか一行
    private static @NotNull String extractLastString(@NotNull String mqoLine) {

        for (var i = mqoLine.length() - 1; i > 0; i--) {
            final var c = mqoLine.charAt(i);
            if (c != ' ') continue;

            return mqoLine.substring(i + 1);
        }
        return "";
    }

    private static float extractLastStringAsFloat(@NotNull String line) {
        return parseFloat(extractLastString(line));
    }

    private static int extractLastStringAsInt(@NotNull String line) {
        return Integer.parseInt(extractLastString(line));
    }

    /// 文字が`[A-za-z]`かどうか
    private static boolean isAlphabet(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static float parseFloat(@NotNull String str) {
        // もしボトルネックになったらカスタム実装
        return Float.parseFloat(str);
    }

    /// パース結果を表すオブジェクト
    ///
    /// @param model  パースしたオブジェクト 失敗したときは`null`
    /// @param status パースのステータス
    public record MQOParseResult(@Nullable MQOModel model, @NotNull MQOParseResultStatus status) {
    }
}