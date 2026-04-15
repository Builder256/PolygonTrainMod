package com.portofino.polygontrainmod.registry;

import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.entity.CarEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class PolygonTrainModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES
        = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, PolygonTrainMod.MODID);

    public static final Supplier<EntityType<CarEntity>> CAR = ENTITY_TYPES.register(
        "car",
        () -> EntityType.Builder.of(CarEntity::new, MobCategory.MISC)
            .sized(2.0f, 2.0f)
            .clientTrackingRange(10)
            .updateInterval(1)
            .build("car")
    );
}
