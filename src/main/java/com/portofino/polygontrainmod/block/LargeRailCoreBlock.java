package com.portofino.polygontrainmod.block;

import com.portofino.polygontrainmod.PolygonTrainModBlockEntities;
import com.portofino.polygontrainmod.blockentity.LargeRailCoreBlockEntity;
import com.portofino.polygontrainmod.rail.util.RailMap;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class LargeRailCoreBlock extends BaseEntityBlock {
    public static final MapCodec<LargeRailCoreBlock> CODEC = simpleCodec(LargeRailCoreBlock::new);
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 8, 16); // half-block height

    public LargeRailCoreBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    public LargeRailCoreBlock() {
        this(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(0.5F, 6.0F).noOcclusion());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LargeRailCoreBlockEntity core) {
                RailMap[] maps = core.getAllRailMaps();
                if (maps.length > 0) {
                    // Prevent collision blocks from recursively trying to delete this core.
                    boolean prev = com.portofino.polygontrainmod.rail.util.RailMap.suppressRailRemoval;
                    com.portofino.polygontrainmod.rail.util.RailMap.suppressRailRemoval = true;
                    try {
                        for (RailMap map : maps) {
                            if (map != null) {
                                map.removeRailBlocks(level);
                            }
                        }
                    } finally {
                        com.portofino.polygontrainmod.rail.util.RailMap.suppressRailRemoval = prev;
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LargeRailCoreBlockEntity(pos, state);
    }

    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }
}
