package com.portofino.polygontrainmod;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * DataComponentsを追加するクラス<br>
 * DataComponentsは、1.20.5よりNBTタグの代替としてItemStackに導入された状態管理手段。<br>
 * 今後のアップデートでアイテムだけでなくNBTを使用するあらゆる要素に拡大していくと予測されており、<br>
 * これからはNBTタグではなくこちらを利用することが推奨されている。
 */
public class PolygonTrainModComponents {
    public static final DeferredRegister.DataComponents REGISTRAR = DeferredRegister.createDataComponents(
        Registries.DATA_COMPONENT_TYPE,
        PolygonTrainMod.MODID
    );

    // 以下、切符の実装に関係するDataComponents
    // 合計の改札通過回数を保存して、その偶奇性で判断することも考えたが、
    // 乗り換え改札などの発展的な改札機能を考えると今の形式の方が拡張性があると思った。
    // もっとエレガントなアルゴリズムを思いつく方は是非教えてくださいませ。
    /**
     * 乗車券が改札内に入場済みどうか
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> IS_ENTERED_TICKET
        = REGISTRAR.registerComponentType(
        "is_entered_ticket",
        builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL)
    );

    /**
     * 回数乗車券の残り使用回数
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> REMAINING_USES
        = REGISTRAR.registerComponentType(
        "remaining_uses",
        builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.INT)
    );


}