package com.portofino.polygontrainmod.client.model.mqo;

import com.portofino.polygontrainmod.client.model.mqo.object.MQOObject;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// MQOモデルを表すオブジェクト
///
/// @param materials 材質
/// @param objects   オブジェクト
@OnlyIn(Dist.CLIENT)
public record MQOModel(MQOMaterial[] materials, List<MQOObject> objects) {
    // materialsは読み込みの最初で個数がわかるため配列、objectsはわからないためCollectionにするしかない

    /// MQOに存在する際、そのファイルの読み込みが禁止されるグローバルチャンク名
    private static final String[] GLOBAL_CHUNK_FORBIDDEN = {"TrialNoise"};
    /// 必要不可欠なグローバルチャンク名 これらが無い場合は読み込めない
    private static final String[] GLOBAL_CHUNK_NECESSARY = {"Material", "Object"};
    /// 省略可能なグローバルチャンク名 これらに含まれないチャンクが検出されたときに、それが未知の新フォーマットである可能性を考慮すべき
    private static final String[] GLOBAL_CHUNK_OMITTABLE = {
        "Metasequoia",
        "Format",
        "CodePage",
        "IncludeXml",
        "Thumbnail",
        "Scene",
        "Scene2",
        "BackImage",
        "MaterialEx2",
        "Blob",
        "Eof"
    };

    /// MQOに存在する際、そのファイルの読み込みが禁止されるグローバルチャンク名
    public static final Set<String> forbiddenGlobalChunkNames;
    /// 必要不可欠なグローバルチャンク名 これらが無い場合は読み込めない
    public static final Set<String> necessaryGlobalChunkNames;
    /// 省略可能なグローバルチャンク名 これらに含まれないチャンクが検出されたときに、それが未知の新フォーマットである可能性を考慮すべき
    public static final Set<String> omittableGlobalChunkNames;

    static {
        forbiddenGlobalChunkNames = Arrays.stream(GLOBAL_CHUNK_FORBIDDEN).filter(Objects::nonNull).map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
        necessaryGlobalChunkNames = Arrays.stream(GLOBAL_CHUNK_NECESSARY).filter(Objects::nonNull).map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
        omittableGlobalChunkNames = Arrays.stream(GLOBAL_CHUNK_OMITTABLE).filter(Objects::nonNull).map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public @NotNull String toString() {
        return "MQOModel[materials=" + Arrays.toString(materials) + ", objects=" + objects.toString() + "]";
    }
}

// MQOファイルのフォーマットについて
// 公式ドキュメントhttps://www.metaseq.net/jp/format.htmlも確認
//
// CodePageチャンク
//   ファイルのエンコーディングについて記録されている
//   日本語文字を含む場合、`CodePage 932`と保存される。これはMicrosoftコードページ932≒Shift_JISのことだと思われる。
//   韓国語や中国語を含む場合、それに対応した文字エンコーディングの数値が保存されると考えられるが、具体的にどの文字コードなのかは不明。
//   また仕様によると、Metasequoia 4.7以降ではUTF-8エンコーディングをサポートしているとされているため、`CodePage utf8`となっている可能性が考えられる。
//   ASCII文字のみを含む場合、CodePageチャンクは**存在しない**。
// Thumbnailチャンク
//   サムネイル画像のチャンク
//   仕様は非公開のため、以下の情報は推測
//
//   サムネイルは画像だが、ファイル内では画像の各ピクセルのカラーコードの16進数文字列としてASCIIエンコードで保存されていると考えられる。
//   例えば、`7A2B3F6H43C3`…とあった場合、`#7A2B3F`のピクセルの右に`#6H43C3`のピクセル…という意味になり、
//   実際Metasequoia内でのサムネイル表示と同じ色に見える。
//   解像度は`128px*128px=16384px`であり、これはサムネイル文字列の1行193文字（`192/6=32px+インデント分のTab1文字`）行数512行（`32px*512=16384px`）と合致する。

