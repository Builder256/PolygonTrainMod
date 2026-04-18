package com.portofino.polygontrainmod;

import com.portofino.polygontrainmod.blockentity.LargeRailCoreBlockEntity;
import com.portofino.polygontrainmod.blockentity.MarkerBlockEntity;
import com.portofino.polygontrainmod.blockentity.RailCollisionBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PolygonTrainModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, PolygonTrainMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MarkerBlockEntity>> MARKER =
        BLOCK_ENTITY_TYPES.register("marker", () -> BlockEntityType.Builder.of(MarkerBlockEntity::new,
            PolygonTrainModBlocks.MARKER.get(), PolygonTrainModBlocks.MARKER_SWITCH.get()).build(null));

    /** レールコア: 起点ブロック1個。道床とは無関係。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeRailCoreBlockEntity>> LARGE_RAIL_CORE =
        BLOCK_ENTITY_TYPES.register("large_rail_core", () -> BlockEntityType.Builder.of(LargeRailCoreBlockEntity::new,
            PolygonTrainModBlocks.LARGE_RAIL_CORE.get()).build(null));

    /** レール当たり判定ブロック: レールコア削除に追従する。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RailCollisionBlockEntity>> RAIL_COLLISION =
        BLOCK_ENTITY_TYPES.register("rail_collision", () -> BlockEntityType.Builder.of(RailCollisionBlockEntity::new,
            PolygonTrainModBlocks.RAIL_COLLISION.get()).build(null));
}
