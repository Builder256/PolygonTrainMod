package com.portofino.polygondtrainmod.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
//import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class GateBlock extends Block {
//    public static final BooleanProperty OPEN = BooleanProperty.create("open");
//    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public GateBlock() {
        super(BlockBehaviour.Properties.of().sound(SoundType.STONE).noOcclusion());
    }
}
