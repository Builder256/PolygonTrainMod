package com.portofino.polygontrainmod.block;

import com.portofino.polygontrainmod.PolygonTrainModBlockEntities;
import com.portofino.polygontrainmod.blockentity.LargeRailCoreBlockEntity;
import com.portofino.polygontrainmod.blockentity.RailCollisionBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class LargeRailCoreBlock extends BaseEntityBlock {
    public static final MapCodec<LargeRailCoreBlock> CODEC = simpleCodec(LargeRailCoreBlock::new);
    private static final VoxelShape SHAPE = Shapes.empty();

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
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
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
                        removeRemainingCollisionBlocks(level, pos, maps);
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

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LargeRailCoreBlockEntity core) {
            core.updateSignalStrength(level.getBestNeighborSignal(pos));
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, PolygonTrainModBlockEntities.LARGE_RAIL_CORE.get(), LargeRailCoreBlockEntity::tick);
    }

    private static void removeRemainingCollisionBlocks(Level level, BlockPos corePos, RailMap[] maps) {
        int minX = corePos.getX() - 2;
        int maxX = corePos.getX() + 2;
        int minY = corePos.getY() - 2;
        int maxY = corePos.getY() + 2;
        int minZ = corePos.getZ() - 2;
        int maxZ = corePos.getZ() + 2;
        for (RailMap map : maps) {
            if (map == null) {
                continue;
            }
            int split = RailMap.curveSplitForLength(map.getHorizontalPathLength());
            int samples = Math.max(16, split + 1);
            for (int i = 0; i < samples; i++) {
                int j = samples <= 1 ? 0 : (int) Math.round((double) split * i / (samples - 1));
                int index = Math.min(split, j);
                double[] point = map.getRailPos(split, index);
                int x = (int) Math.floor(point[1]);
                int y = (int) Math.floor(map.getRailHeight(split, index));
                int z = (int) Math.floor(point[0]);
                minX = Math.min(minX, x - 2);
                maxX = Math.max(maxX, x + 2);
                minY = Math.min(minY, y - 2);
                maxY = Math.max(maxY, y + 2);
                minZ = Math.min(minZ, z - 2);
                maxZ = Math.max(maxZ, z + 2);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos scanPos = new BlockPos(x, y, z);
                    BlockEntity be = level.getBlockEntity(scanPos);
                    if (be instanceof RailCollisionBlockEntity collision && corePos.equals(collision.getCorePos())) {
                        level.removeBlock(scanPos, false);
                    }
                }
            }
        }
    }
}
