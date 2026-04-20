package com.portofino.polygontrainmod;

import com.portofino.polygontrainmod.entity.TrainEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PolygonTrainModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, PolygonTrainMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<TrainEntity>> TRAIN =
        ENTITIES.register("train",
            () -> EntityType.Builder.<TrainEntity>of(TrainEntity::new, MobCategory.MISC)
                .sized(2.0F, 2.0F)
                .fireImmune()
                .clientTrackingRange(10)
                .build("train"));

    private PolygonTrainModEntities() {
    }
}
