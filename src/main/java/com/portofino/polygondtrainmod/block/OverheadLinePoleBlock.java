package com.portofino.polygondtrainmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class OverheadLinePoleBlock extends Block {
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");

    public OverheadLinePoleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(
            stateDefinition
                .any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, WEST, SOUTH);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        Block blockNorth = level.getBlockState(blockPos.north()).getBlock();
        Block blockEast = level.getBlockState(blockPos.east()).getBlock();
        Block blockSouth = level.getBlockState(blockPos.south()).getBlock();
        Block blockWest = level.getBlockState(blockPos.west()).getBlock();

        boolean isNorthBlockOverheadLinePole = blockNorth instanceof OverheadLinePoleBlock;
        boolean isEastBlockOverheadLinePole = blockEast instanceof OverheadLinePoleBlock;
        boolean isSouthBlockOverheadLinePole = blockSouth instanceof OverheadLinePoleBlock;
        boolean isWestBlockOverheadLinePole = blockWest instanceof OverheadLinePoleBlock;

        return this
            .defaultBlockState()
            .setValue(NORTH, isNorthBlockOverheadLinePole)
            .setValue(EAST, isEastBlockOverheadLinePole)
            .setValue(SOUTH, isSouthBlockOverheadLinePole)
            .setValue(WEST, isWestBlockOverheadLinePole);
    }

    @Override
    @NotNull
    public BlockState updateShape(
        @NotNull BlockState state,
        Direction facing,
        @NotNull BlockState facingState,
        @NotNull LevelAccessor level,
        @NotNull BlockPos currentPos,
        @NotNull BlockPos facingPos
    ) {
        if (!facing.getAxis().isHorizontal()) return state;

        BooleanProperty prop = switch (facing) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> null;
        };

        if (prop == null) return state;

        boolean connected = facingState.getBlock() instanceof OverheadLinePoleBlock;
        return state.setValue(prop, connected);
    }

}
