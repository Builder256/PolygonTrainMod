package com.portofino.polygontrainmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 道床ブロック。レールモデルとは独立した物理ブロック。
 * 高さ4px(4/16)のスラブ形状。見た目あり（バラスト/砂利テクスチャ）。
 * ブロックエンティティなし — レールコアと無関係に配置・撤去できる。
 */
public class BallastBlock extends Block {
    /** 高さ4px の薄いスラブ */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    public BallastBlock() {
        super(BlockBehaviour.Properties.of()
            .sound(SoundType.GRAVEL)
            .strength(0.6F, 3.0F)
            .noOcclusion()
            .isSuffocating((s, g, p) -> false)
            .isViewBlocking((s, g, p) -> false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
