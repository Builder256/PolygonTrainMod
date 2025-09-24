package com.portofino.polygondtrainmod;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * DataComponentsを追加するクラス
 * DataComponentsは、1.20.5よりNBTタグの代替としてItemStackに導入された状態管理手段。
 * 今後は、NBTタグではなくこちらを利用することが推奨されている。
 */
public class PolygonTrainModComponents {
    public static final DeferredRegister.DataComponents REGISTRAR = DeferredRegister.createDataComponents(
        Registries.DATA_COMPONENT_TYPE,
        PolygonTrainMod.MODID
    );

//    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> EXAMPLE_NUMBER
//        = REGISTRAR.registerComponentType(
//        "custom_data",
//        builder -> builder
//            .persistent(Codec.INT)
//            .networkSynchronized(ByteBufCodecs.INT)
//    );

    // 切符が改札内に入場済みどうかを保存するDataComponent
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> IS_ENTERED_TICKET
        = REGISTRAR.registerComponentType(
        "is_entered_ticket",
        builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL)
    );


//    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> IS_TICKET_USED
//        = REGISTRAR.registerComponentType(
//        "is_ticket_used",
//        builder -> builder.persistent(Codec.BOOL)
//            .networkSynchronized(ByteBufCodecs.BOOL)
//    );

}