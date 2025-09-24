package com.portofino.polygondtrainmod.block;

import com.portofino.polygondtrainmod.PolygonTrainModComponents;
import com.portofino.polygondtrainmod.item.ticket.TicketItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class GateBlock extends Block {
    // BlockState
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING; // 四方位

    // 当たり判定ボックスのサイズ
    protected static final VoxelShape COLLISION_SHAPE_NORTH = Block.box(0.0, 0.0, 4.0, 16.0, 24.0, 6.0);
    protected static final VoxelShape COLLISION_SHAPE_EAST = Block.box(10.0, 0.0, 0.0, 12.0, 24.0, 16.0);
    protected static final VoxelShape COLLISION_SHAPE_SOUTH = Block.box(0.0, 0.0, 10.0, 16.0, 24.0, 12.0);
    protected static final VoxelShape COLLISION_SHAPE_WEST = Block.box(4.0, 0.0, 0.0, 6.0, 24.0, 16.0);

    // ドアが自動で閉じるまでの時間 5秒
    protected static final int AUTO_CLOSE_DELAY_TICK = 100;

    public GateBlock() {
        super(BlockBehaviour.Properties.of().sound(SoundType.STONE).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    /**
     * BlockStateを実際に追加する
     *
     * @param builder 謎
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    /**
     * ブロックが設置された時の処理
     *
     * @param context ブロックが設置された時の状態
     * @return 謎
     */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 設置時のプレイヤーの視点の向きに応じて設置の向きを設定する
        Direction direction = context.getHorizontalDirection();
        return this.defaultBlockState().setValue(FACING, direction);
    }

    /**
     * 実際の当たり判定を設定する
     *
     * @param state   当たり判定を設定するブロックのBlockState
     * @param level   謎 ブロックがあるLevel？
     * @param pos     謎 ブロックがある座標？
     * @param context 謎
     * @return 当たり判定
     */
    @Override
    @NotNull
    protected VoxelShape getCollisionShape(
        BlockState state,
        @NotNull BlockGetter level,
        @NotNull BlockPos pos,
        @NotNull CollisionContext context
    ) {
        Direction direction = state.getValue(FACING);

        // ドアが開いているときは当たり判定を削除
        if (state.getValue(OPEN)) return Shapes.empty();
        // ブロックの向きの四方位に応じて当たり判定を設定
        if (direction == Direction.NORTH) return COLLISION_SHAPE_NORTH;
        if (direction == Direction.EAST) return COLLISION_SHAPE_EAST;
        if (direction == Direction.SOUTH) return COLLISION_SHAPE_SOUTH;
        if (direction == Direction.WEST) return COLLISION_SHAPE_WEST;

        return COLLISION_SHAPE_NORTH;
    }

    /**
     * アイテムを持って右クリックされた時の処理
     *
     * @param stack     持っているアイテム
     * @param state     右クリックされたブロックのBlockState
     * @param level     右クリックされたブロックがあるLevel
     * @param pos       右クリックされたブロックがある座標
     * @param player    右クリックしたプレイヤー
     * @param hand      右クリックした手
     * @param hitResult 謎
     * @return 終了状態
     */
    @Override
    @NotNull
    protected ItemInteractionResult useItemOn(
        @NotNull ItemStack stack,
        @NotNull BlockState state,
        @NotNull Level level,
        @NotNull BlockPos pos,
        @NotNull Player player,
        @NotNull InteractionHand hand,
        @NotNull BlockHitResult hitResult
    ) {
        // 今後有効な切符を持っていることを保証
        // アイテムを持っていない状態での右クリック（`BlockBehaviour#useWithoutItem`）が次に実行される
        if (!isValidTicket(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        updateTicket(stack);
        openDoor(level, pos, state);
        // ドアを閉じる処理を予約する
        level.scheduleTick(pos, this, AUTO_CLOSE_DELAY_TICK); // 指定したtick後にtick()が発火する ここで呼ぶべきかなぁ？

        // 操作を成功裏に終了させ右クリックパイプラインを終了する
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * level#scheduleTickにより設定した遅延後に発火する もうBlockEntityいらないね。
     *
     * @param state  発火するブロックのBlockState
     * @param level  発火するブロックのあるLevel
     * @param pos    発火するブロックの座標
     * @param random 謎 ランダムに動作するもので使用？
     */
    @Override
    protected void tick(
        @NotNull BlockState state,
        @NotNull ServerLevel level,
        @NotNull BlockPos pos,
        @NotNull RandomSource random
    ) {
        closeDoor(level, pos, state);
    }

    private void openDoor(Level level, BlockPos pos, BlockState state) {
        BlockState newState = state.setValue(OPEN, true); // BlockState#setValue()は現在の値を更新しないので新しくBlockStateを作る必要がある
        level.setBlock(pos, newState, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
    }

    private void closeDoor(Level level, BlockPos pos, BlockState state) {
        BlockState newState = state.setValue(OPEN, false);
        level.setBlock(pos, newState, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
    }

    private boolean isValidTicket(ItemStack itemStack) {
        // 今後itemがTicketItemであることを保証
        if (!(itemStack.getItem() instanceof TicketItem ticket)) return false;
        return ticket.canPassGate(itemStack);
    }

    private void updateTicket(ItemStack stack){
        Item item = stack.getItem();
        stack.set(PolygonTrainModComponents.IS_ENTERED_TICKET.get(), true);
    }
}