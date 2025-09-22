package com.portofino.polygondtrainmod;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PolygonTrainModComponents {
    public static final DeferredRegister.DataComponents REGISTRAR = DeferredRegister.createDataComponents(
        Registries.DATA_COMPONENT_TYPE,
        PolygonTrainMod.MODID
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> EXAMPLE_NUMBER
        = REGISTRAR.registerComponentType(
        "custom_data",
        builder -> builder
            .persistent(Codec.INT)
            .networkSynchronized(ByteBufCodecs.INT));
}
