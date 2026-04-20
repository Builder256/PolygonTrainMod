package com.portofino.polygontrainmod;

import com.portofino.polygontrainmod.block.BallastBlock;
import com.portofino.polygontrainmod.block.CrossingGateBlock;
import com.portofino.polygontrainmod.block.GateBlock;
import com.portofino.polygontrainmod.block.InstalledObjectBlock;
import com.portofino.polygontrainmod.block.LargeRailCoreBlock;
import com.portofino.polygontrainmod.block.MarkerBlock;
import com.portofino.polygontrainmod.block.OverheadLinePoleBlock;
import com.portofino.polygontrainmod.block.RailCollisionBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PolygonTrainModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PolygonTrainMod.MODID);

    public static final DeferredBlock<GateBlock> TEST_AUTOMATIC_TICKET_GATE
        = BLOCKS.register("test_automatic_ticket_gate", GateBlock::new);
    public static final DeferredBlock<CrossingGateBlock> CROSSING_GATE
        = BLOCKS.register("crossing_gate", () -> new CrossingGateBlock());
    public static final DeferredBlock<OverheadLinePoleBlock> OVERHEAD_LINE_POLE
        = BLOCKS.registerBlock(
        "overhead_line_pole",
        OverheadLinePoleBlock::new,
        BlockBehaviour.Properties.of().sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<MarkerBlock> MARKER
        = BLOCKS.register("marker", () -> new MarkerBlock(false));
    public static final DeferredBlock<MarkerBlock> MARKER_SWITCH
        = BLOCKS.register("marker_switch", () -> new MarkerBlock(true));

    /** 道床ブロック（レールと独立した物理ブロック） */
    public static final DeferredBlock<BallastBlock> BALLAST
        = BLOCKS.register("ballast", BallastBlock::new);

    /** レールコアブロック（起点1個のみ、MQOモデル描画を担当） */
    public static final DeferredBlock<LargeRailCoreBlock> LARGE_RAIL_CORE
        = BLOCKS.register("large_rail_core", () -> new LargeRailCoreBlock());

    /** レール当たり判定ブロック（非表示・薄い） */
    public static final DeferredBlock<RailCollisionBlock> RAIL_COLLISION
        = BLOCKS.register("rail_collision", () -> new RailCollisionBlock());

    public static final DeferredBlock<InstalledObjectBlock> INSTALLED_OBJECT
        = BLOCKS.register("installed_object", () -> new InstalledObjectBlock());
}
