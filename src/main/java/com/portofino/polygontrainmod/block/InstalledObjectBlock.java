package com.portofino.polygontrainmod.block;

import com.mojang.serialization.MapCodec;
import com.portofino.polygontrainmod.PolygonTrainModBlockEntities;
import com.portofino.polygontrainmod.blockentity.InstalledObjectBlockEntity;
import com.portofino.polygontrainmod.installedobject.InstalledObjectCategory;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class InstalledObjectBlock extends BaseEntityBlock {
    public static final MapCodec<InstalledObjectBlock> CODEC = simpleCodec(InstalledObjectBlock::new);

    public InstalledObjectBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public InstalledObjectBlock() {
        this(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(0.4F, 2.0F).noOcclusion());
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
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity && blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        return box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity && blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        return box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.portofino.polygontrainmod.blockentity.InstalledObjectBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, PolygonTrainModBlockEntities.INSTALLED_OBJECT.get(), InstalledObjectBlockEntity::tick);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide) {
            updatePoweredState(level, pos);
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            updatePoweredState(level, pos);
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            removeAttachedWires(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static void removeAttachedWires(Level level, BlockPos pos) {
        int radius = 64;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    if (!(level.getBlockEntity(checkPos) instanceof InstalledObjectBlockEntity blockEntity)) {
                        continue;
                    }
                    BlockPos start = blockEntity.getWireStart();
                    BlockPos end = blockEntity.getWireEnd();
                    if (pos.equals(start) || pos.equals(end)) {
                        level.removeBlock(checkPos, false);
                    }
                }
            }
        }
    }

    private static void updatePoweredState(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity)) {
            return;
        }
        if (blockEntity.getCategory() != InstalledObjectCategory.CROSSING) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        blockEntity.setPowered(powered);
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }
}
