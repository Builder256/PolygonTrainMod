package com.portofino.polygontrainmod.block;

import com.mojang.serialization.MapCodec;
import com.portofino.polygontrainmod.PolygonTrainModItems;
import com.portofino.polygontrainmod.blockentity.RailCollisionBlockEntity;
import com.portofino.polygontrainmod.rail.util.RailMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * レールの「薄い当たり判定」専用ブロック。
 * 見た目は描かず、破壊されたら対応するレールコアも削除する。
 */
public class RailCollisionBlock extends BaseEntityBlock {
    public static final MapCodec<RailCollisionBlock> CODEC = simpleCodec(RailCollisionBlock::new);
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 8, 16); // half-block height

    public RailCollisionBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    public RailCollisionBlock() {
        this(BlockBehaviour.Properties.of()
            .sound(SoundType.METAL)
            .strength(0.1F, 0.1F)
            .noOcclusion()
            .isSuffocating((s, g, p) -> false)
            .isViewBlocking((s, g, p) -> false));
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
        if (!level.isClientSide && state.getBlock() != newState.getBlock() && !RailMap.suppressRailRemoval) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RailCollisionBlockEntity rbe) {
                BlockPos corePos = rbe.getCorePos();
                if (corePos != null && level.getBlockState(corePos).getBlock() instanceof LargeRailCoreBlock) {
                    level.removeBlock(corePos, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RailCollisionBlockEntity(pos, state);
    }

    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.PASS;
        }
        if (!player.getItemInHand(hand).is(PolygonTrainModItems.CROWBAR_ITEM.get())) {
            return InteractionResult.PASS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RailCollisionBlockEntity collision) {
            BlockPos corePos = collision.getCorePos();
            if (corePos != null && level.getBlockState(corePos).getBlock() instanceof LargeRailCoreBlock) {
                level.removeBlock(corePos, false);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}

