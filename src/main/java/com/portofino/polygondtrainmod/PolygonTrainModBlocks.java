package com.portofino.polygondtrainmod;

import com.portofino.polygondtrainmod.block.GateBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PolygonTrainModBlocks {
    // すべてのブロックを "polygontrainmod "名前空間に登録するために、Deferred Registerを作成する。
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PolygonTrainMod.MODID);
    // テスト改札機を登録
    public static final DeferredBlock<GateBlock> TEST_AUTOMATIC_TICKET_GATE = BLOCKS.register("test_automatic_ticket_gate", ()-> new GateBlock(BlockBehaviour.Properties.of().sound(SoundType.STONE)));
}
