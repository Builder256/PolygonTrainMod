package com.portofino.polygondtrainmod.block;

import com.portofino.polygondtrainmod.PolygonTrainMod;
import com.portofino.polygondtrainmod.PolygonTrainModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class GateBlock extends Block {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING; // 四方位

    protected static final VoxelShape COLLISION_SHAPE_NORTH = Block.box(0.0, 0.0, 4.0, 16.0, 24.0, 6.0);
    protected static final VoxelShape COLLISION_SHAPE_EAST = Block.box(10.0, 0.0, 0.0, 12.0, 24.0, 16.0);
    protected static final VoxelShape COLLISION_SHAPE_SOUTH = Block.box(0.0, 0.0, 10.0, 16.0, 24.0, 12.0);
    protected static final VoxelShape COLLISION_SHAPE_WEST = Block.box(4.0, 0.0, 0.0, 6.0, 24.0, 16.0);

    protected static final int AUTO_CLOSE_DELAY_TICK = 100; // ドアが自動で閉じるまでの時間 5秒

    public GateBlock() {
        super(BlockBehaviour.Properties.of().sound(SoundType.STONE).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    /**
     * BlockStateを追加
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACING, OPEN);
    }

    /**
     * 設置時のプレイヤーの視点の向きに応じて設置の向きを設定
     */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        Direction direction = pContext.getHorizontalDirection();
        return this.defaultBlockState().setValue(FACING, direction);
    }

    /**
     * 向きと開閉状態に応じた当たり判定を設定
     */
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

    /**
     * アイテムを持って右クリックされた時の処理
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
//        PolygonTrainMod.LOGGER.info("useItemOn");
        if (PolygonTrainModItems.isValidTicket(stack)) { // isValidTicket()は未実装 常にtrueが返ってくる
//            PolygonTrainMod.LOGGER.info("isValidTicket");
            openDoor(level, pos, state);
            level.scheduleTick(pos, this, AUTO_CLOSE_DELAY_TICK); // 指定したtick後にtick()が発火する ここで呼ぶべきかなぁ？
            return ItemInteractionResult.sidedSuccess(level.isClientSide); // 操作を成功裏に終了させ右クリックパイプラインを終了
        }
//        PolygonTrainMod.LOGGER.info("not valid Ticket");
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // アイテムを持っていない状態での右クリック（`BlockBehaviour#useWithoutItem`）が次に実行される
    }

    /**
     * level#scheduleTick()で設定した遅延後に発火 もうBlockEntityいらないね。
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        closeDoor(level, pos, state);
    }

    private void openDoor(Level level, BlockPos pos, BlockState state) {
//        PolygonTrainMod.LOGGER.info("openDoor");
        BlockState newState = state.setValue(OPEN, true); // BlockState#setValue()は現在の値を更新しないので新しくBlockStateを作る必要がある
        level.setBlock(pos, newState, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
    }

    private void closeDoor(Level level, BlockPos pos, BlockState state) {
//        PolygonTrainMod.LOGGER.info("closeDoor");
        BlockState newState = state.setValue(OPEN, false);
        level.setBlock(pos, newState, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
    }
}