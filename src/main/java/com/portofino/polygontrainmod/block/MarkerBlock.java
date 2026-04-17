package com.portofino.polygontrainmod.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.portofino.polygontrainmod.PolygonTrainModBlockEntities;
import com.portofino.polygontrainmod.PolygonTrainModBlocks;
import com.portofino.polygontrainmod.PolygonTrainModComponents;
import com.portofino.polygontrainmod.blockentity.LargeRailCoreBlockEntity;
import com.portofino.polygontrainmod.blockentity.MarkerBlockEntity;
import com.portofino.polygontrainmod.blockentity.RailCollisionBlockEntity;
import com.portofino.polygontrainmod.item.RailItem;
import com.portofino.polygontrainmod.rail.RailDefinition;
import com.portofino.polygontrainmod.rail.RailRegistry;
import com.portofino.polygontrainmod.rail.util.RailMap;
import com.portofino.polygontrainmod.rail.util.RailMapBasic;
import com.portofino.polygontrainmod.rail.util.RailPosition;
import com.portofino.polygontrainmod.rail.util.RailProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MarkerBlock extends BaseEntityBlock {
    public static final MapCodec<MarkerBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            Codec.BOOL.fieldOf("is_switch").forGetter(block -> block.isSwitch),
            propertiesCodec()
        ).apply(instance, MarkerBlock::new)
    );
    public static final IntegerProperty FACING = IntegerProperty.create("facing", 0, 7);
    public final boolean isSwitch;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);
    public static final int SEARCH_DISTANCE = 50;
    public static final int SEARCH_HEIGHT = 10;

    public MarkerBlock(boolean isSwitch, BlockBehaviour.Properties properties) {
        super(properties);
        this.isSwitch = isSwitch;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, 0));
    }

    public MarkerBlock(boolean isSwitch) {
        this(isSwitch, BlockBehaviour.Properties.of()
            .sound(SoundType.STONE)
            .strength(1.0F, 1.0F)
            .noOcclusion()
            .noCollission());
    }

    @Override
    public MapCodec<? extends MarkerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null) return defaultBlockState().setValue(FACING, 0);
        return defaultBlockState().setValue(FACING, computeFacing(player));
    }

    public static int computeFacing(Player player) {
        double yaw = player.getYRot();
        return (int) Math.floor((yaw + 22.5D) / 45.0D) & 7;
    }

    /** RTM BlockMarker.getMarkerDir */
    public static int getMarkerDir(int facing) {
        return (8 - facing) & 7;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof RailItem) {
            if (!level.isClientSide()) {
                String selectedId = stack.get(PolygonTrainModComponents.SELECTED_MODEL_ID.get());
                boolean created = onMarkerActivated(level, pos, player, true, selectedId);
                if (created && !player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public boolean onMarkerActivated(Level level, BlockPos pos, @Nullable Player player, boolean makeRail) {
        return onMarkerActivated(level, pos, player, makeRail, null);
    }

    /**
     * RTM BlockMarker.onMarkerActivated に相当。
     * 範囲内の全マーカーを収集し、個数に応じてレール種別を決定する:
     *  2個 → 通常レール
     *  3個以上 → 分岐レール（各マーカーペアで通常レールを複数生成）
     */
    public boolean onMarkerActivated(Level level, BlockPos pos, @Nullable Player player, boolean makeRail, @Nullable String selectedModelId) {
        List<RailPosition> rps = searchAllMarkers(level, pos);
        System.out.println("[DEBUG] Found " + rps.size() + " markers for rail creation");
        for (int i = 0; i < rps.size(); i++) {
            RailPosition rp = rps.get(i);
            System.out.println("[DEBUG] Marker " + i + ": switchType=" + rp.switchType + " at (" + rp.blockX + "," + rp.blockY + "," + rp.blockZ + ")");
        }
        if (rps.size() < 2) return false;

        RailProperties prop = createRailProperties(player, selectedModelId);
        for (RailPosition rp : rps) {
            rp.addHeight((double) (prop.blockHeight - 0.0625F));
        }
        return createRail(level, pos, rps, prop, makeRail, player == null || player.getAbilities().instabuild, selectedModelId);
    }

    /**
     * RTM BlockMarker.searchAllMarker に相当。
     * 範囲内にある全マーカー（自分自身を含む）をスキャンし、RTM と同じ優先度でソートして返す:
     *  1. switchType 降順（分岐マーカー優先）
     *  2. Y 昇順
     *  3. hashCode 昇順（同一 Y のタイブレーカー）
     */
    private List<RailPosition> searchAllMarkers(Level level, BlockPos origin) {
        List<RailPosition> list = new ArrayList<>();
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();

        for (int i = -SEARCH_DISTANCE; i <= SEARCH_DISTANCE; i++) {
            for (int j = -SEARCH_HEIGHT; j <= SEARCH_HEIGHT; j++) {
                for (int k = -SEARCH_DISTANCE; k <= SEARCH_DISTANCE; k++) {
                    BlockPos check = new BlockPos(ox + i, oy + j, oz + k);
                    BlockEntity be = level.getBlockEntity(check);
                    if (be instanceof MarkerBlockEntity me) {
                        RailPosition rp = me.getMarkerRP();
                        if (rp != null) {
                            list.add(copyRailPosition(rp));
                        }
                    }
                }
            }
        }

        // RTM と同じソート順: switchType 降順 → Y 昇順 → hashCode 昇順
        list.sort((a, b) -> {
            if (a.switchType != b.switchType) return b.switchType - a.switchType;
            if (a.blockY != b.blockY) return a.blockY - b.blockY;
            return a.hashCode() - b.hashCode();
        });

        return list;
    }

    private static RailProperties createRailProperties(@Nullable Player player, @Nullable String selectedModelId) {
        RailProperties prop = RailProperties.createDefault();
        RailDefinition selected = (selectedModelId != null && !selectedModelId.isBlank())
            ? RailRegistry.getById(selectedModelId)
            : RailRegistry.getSelected();
        if (selected != null) {
            prop.ballastWidth = 0;
        }
        if (selected != null && selected.getScriptPath() != null) {
            // PackScriptRuntime removed - no script system
        }
        return prop;
    }

    public static boolean createRail(Level level, BlockPos originPos, List<RailPosition> rps, RailProperties prop, boolean makeRail, boolean isCreative) {
        return createRail(level, originPos, rps, prop, makeRail, isCreative, null);
    }

    /**
     * RTM BlockMarker.createRail に相当。
     *  2個 → createNormalRail
     *  3個以上 → createSwitchRail（分岐: 最初のマーカーを起点に各ペアへ通常レールを生成）
     */
    public static boolean createRail(Level level, BlockPos originPos, List<RailPosition> rps, RailProperties prop, boolean makeRail, boolean isCreative, @Nullable String selectedModelId) {
        if (rps.size() == 2) {
            RailPosition rp1 = rps.get(0);
            RailPosition rp2 = rps.get(1);
            RailPosition start = rp2.blockY >= rp1.blockY ? rp1 : rp2;
            RailPosition end   = rp2.blockY >= rp1.blockY ? rp2 : rp1;
            return createNormalRail(level, start, end, prop, makeRail, isCreative, selectedModelId);
        } else if (rps.size() > 2) {
            return createSwitchRail(level, rps, prop, makeRail, isCreative, selectedModelId);
        }
        return false;
    }

    /**
     * 2マーカー間に1本の通常レールを敷設する。RTM createNormalRail 相当。
     */
    private static boolean createNormalRail(Level level, RailPosition start, RailPosition end,
                                            RailProperties prop, boolean makeRail, boolean isCreative,
                                            @Nullable String selectedModelId) {
        RailMap railMap = new RailMapBasic(start, end);
        RailDefinition selected = resolveRailDef(selectedModelId);

        if (!makeRail || !railMap.canPlaceRail(level, isCreative, prop)) {
            return false;
        }

        BlockPos corePos = new BlockPos(start.blockX, start.blockY, start.blockZ);
        Block coreBlock = PolygonTrainModBlocks.LARGE_RAIL_CORE.get();
        level.setBlock(corePos, coreBlock.defaultBlockState(), Block.UPDATE_ALL);

        BlockEntity coreBe = level.getBlockEntity(corePos);
        if (coreBe instanceof LargeRailCoreBlockEntity core) {
            core.setRailPositions(new RailPosition[]{start, end});
            if (selected != null) core.setRailDefinitionId(selected.getId());
            core.createRailMap();
            core.setChanged();
            level.sendBlockUpdated(corePos, core.getBlockState(), core.getBlockState(), Block.UPDATE_ALL);
        }

        placeCollisionBlocks(level, railMap, corePos);
        removeMarkerAt(level, start.blockX, start.blockY, start.blockZ);
        removeMarkerAt(level, end.blockX, end.blockY, end.blockZ);

        if (selected != null && selected.getScriptPath() != null) {
            // PackScriptRuntime removed - no script system
        }
        return true;
    }

    /**
     * 3マーカー以上による分岐レール敷設。
     * RTM の RailMaker / SwitchType は未移植のため、最初のマーカーを「幹線起点」とし、
     * 残り各マーカーとの間に通常レールを1本ずつ生成することで分岐を表現する。
     */
    private static boolean createSwitchRail(Level level, List<RailPosition> rps,
                                            RailProperties prop, boolean makeRail, boolean isCreative,
                                            @Nullable String selectedModelId) {
        RailPosition core = rps.get(0);
        List<RailMap> maps = new ArrayList<>();
        for (int i = 1; i < rps.size(); i++) {
            RailPosition other = rps.get(i);
            maps.add(new RailMapBasic(copyRailPosition(core), copyRailPosition(other)));
        }

        if (!makeRail) {
            return false;
        }

        for (RailMap map : maps) {
            if (!map.canPlaceRail(level, isCreative, prop)) {
                return false;
            }
        }

        BlockPos corePos = new BlockPos(core.blockX, core.blockY, core.blockZ);
        Block coreBlock = PolygonTrainModBlocks.LARGE_RAIL_CORE.get();
        level.setBlock(corePos, coreBlock.defaultBlockState(), Block.UPDATE_ALL);

        BlockEntity coreBe = level.getBlockEntity(corePos);
        if (!(coreBe instanceof LargeRailCoreBlockEntity coreEntity)) {
            return false;
        }

        RailPosition[] positions = new RailPosition[maps.size() * 2];
        for (int i = 0; i < maps.size(); i++) {
            positions[i * 2] = copyRailPosition(core);
            positions[i * 2 + 1] = copyRailPosition(rps.get(i + 1));
        }
        coreEntity.setRailPositions(positions);
        RailDefinition selected = resolveRailDef(selectedModelId);
        if (selected != null) {
            coreEntity.setRailDefinitionId(selected.getId());
        }
        coreEntity.createRailMap();
        coreEntity.setChanged();
        level.sendBlockUpdated(corePos, coreEntity.getBlockState(), coreEntity.getBlockState(), Block.UPDATE_ALL);

        for (RailMap map : maps) {
            placeCollisionBlocks(level, map, corePos);
        }

        removeMarkerAt(level, core.blockX, core.blockY, core.blockZ);
        for (int i = 1; i < rps.size(); i++) {
            RailPosition other = rps.get(i);
            removeMarkerAt(level, other.blockX, other.blockY, other.blockZ);
        }

        if (selected != null && selected.getScriptPath() != null) {
            for (int i = 1; i < rps.size(); i++) {
                // PackScriptRuntime removed - no script system
            }
        }
        return true;
    }

    /** レール中心線に沿って薄いコリジョンブロックを配置する（RTM の道床コリジョン相当）。 */
    private static void placeCollisionBlocks(Level level, RailMap railMap, BlockPos corePos) {
        int split = RailMap.curveSplitForLength(railMap.getHorizontalPathLength());
        int samples = Math.max(3, (int) Math.ceil(railMap.getLength() * 2.0) + 1);
        Set<BlockPos> placed = new HashSet<>();

        for (int i = 0; i < samples; i++) {
            int j = samples <= 1 ? 0 : (int) Math.round((double) split * i / (samples - 1));
            if (j > split) j = split;
            double[] point = railMap.getRailPos(split, j);
            int x = com.portofino.polygontrainmod.rail.math.NgtMath.floor(point[1]);
            int z = com.portofino.polygontrainmod.rail.math.NgtMath.floor(point[0]);
            int y = (int) railMap.getRailHeight(split, j);

            BlockPos p = new BlockPos(x, y - 1, z);
            if (p.equals(corePos) || !placed.add(p)) continue;

            Block existing = level.getBlockState(p).getBlock();
            boolean replaceable = existing == net.minecraft.world.level.block.Blocks.AIR
                || existing == net.minecraft.world.level.block.Blocks.CAVE_AIR
                || existing == net.minecraft.world.level.block.Blocks.VOID_AIR
                || existing instanceof MarkerBlock
                || existing instanceof BallastBlock
                || existing instanceof RailCollisionBlock;
            if (!replaceable) continue;

            level.setBlock(p, PolygonTrainModBlocks.RAIL_COLLISION.get().defaultBlockState(), Block.UPDATE_ALL);
            BlockEntity segBe = level.getBlockEntity(p);
            if (segBe instanceof RailCollisionBlockEntity rbe) {
                rbe.setCorePos(corePos);
                level.sendBlockUpdated(p, rbe.getBlockState(), rbe.getBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void removeMarkerAt(Level level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockState(pos).getBlock() instanceof MarkerBlock) {
            level.removeBlock(pos, false);
        }
    }

    @Nullable
    private static RailDefinition resolveRailDef(@Nullable String selectedModelId) {
        return (selectedModelId != null && !selectedModelId.isBlank())
            ? RailRegistry.getById(selectedModelId)
            : RailRegistry.getSelected();
    }

    private static RailPosition copyRailPosition(RailPosition source) {
        return RailPosition.readFromNBT(source.writeToNBT());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarkerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, PolygonTrainModBlockEntities.MARKER.get(), MarkerBlockEntity::tick);
    }
}
