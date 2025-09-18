package com.portofino.polygondtrainmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class GateBlock extends Block {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected static final VoxelShape COLLISION_SHAPE_NORTH = Block.box(0.0, 0.0, 4.0, 16.0, 24.0, 6.0);
    protected static final VoxelShape COLLISION_SHAPE_EAST = Block.box(10.0, 0.0, 0.0, 12.0, 24.0, 16.0);
    protected static final VoxelShape COLLISION_SHAPE_SOUTH = Block.box(0.0, 0.0, 10.0, 16.0, 24.0, 12.0);
    protected static final VoxelShape COLLISION_SHAPE_WEST = Block.box(4.0, 0.0, 0.0, 6.0, 24.0, 16.0);

    public GateBlock() {
        super(BlockBehaviour.Properties.of().sound(SoundType.STONE).noOcclusion());

        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACING, OPEN);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        Direction direction = pContext.getHorizontalDirection();
        return this.defaultBlockState().setValue(FACING, direction);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction direction = state.getValue(FACING);

        if (state.getValue(OPEN)) return Shapes.empty();
        if (direction == Direction.NORTH) return COLLISION_SHAPE_NORTH;
        if (direction == Direction.EAST) return COLLISION_SHAPE_EAST;
        if (direction == Direction.SOUTH) return COLLISION_SHAPE_SOUTH;
        if (direction == Direction.WEST) return COLLISION_SHAPE_WEST;
        return COLLISION_SHAPE_NORTH;
    }
}
