package com.portofino.polygontrainmod.item;

import com.portofino.polygontrainmod.PolygonTrainModComponents;
import com.portofino.polygontrainmod.block.LargeRailCoreBlock;
import com.portofino.polygontrainmod.blockentity.LargeRailCoreBlockEntity;
import com.portofino.polygontrainmod.blockentity.RailCollisionBlockEntity;
import com.portofino.polygontrainmod.client.ClientItemHelper;
import com.portofino.polygontrainmod.entity.TrainEntity;
import com.portofino.polygontrainmod.formation.TrainFormation;
import com.portofino.polygontrainmod.formation.TrainFormationData;
import com.portofino.polygontrainmod.rail.util.RailMap;
import com.portofino.polygontrainmod.vehicle.VehicleDefinition;
import com.portofino.polygontrainmod.vehicle.VehicleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class TrainVehicleItem extends Item {
    private static final int SPAWN_COOLDOWN_TICKS = 4;
    private static final double RAYCAST_DISTANCE = 5.0;

    public TrainVehicleItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            if (!isLookingAtRail(level, player)) {
                ClientItemHelper.openTrainSelectScreen(player, stack);
            }
            return InteractionResultHolder.success(stack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (trySpawnTrain(level, player, stack)) {
            player.getCooldowns().addCooldown(this, SPAWN_COOLDOWN_TICKS);
            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.PASS;
        }
        if (trySpawnTrainAtTarget(level, player, context.getItemInHand(), context.getClickedPos())) {
            player.getCooldowns().addCooldown(this, SPAWN_COOLDOWN_TICKS);
            return InteractionResult.sidedSuccess(false);
        }
        return InteractionResult.PASS;
    }

    private boolean isLookingAtRail(Level level, Player player) {
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 end = start.add(player.getViewVector(1.0f).scale(RAYCAST_DISTANCE));
        HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        return isRailBlock(level, pos);
    }

    private boolean trySpawnTrain(Level level, Player player, ItemStack stack) {
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 end = start.add(player.getViewVector(1.0f).scale(RAYCAST_DISTANCE));
        BlockPos targetPos = null;

        HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 targetPoint = null;
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            if (isRailBlock(level, pos)) {
                targetPos = pos;
                targetPoint = hit.getLocation();
            }
        }

        if (targetPos == null) {
            targetPos = findNearestBlockAlongRay(level, start, end);
            if (targetPos != null) {
                targetPoint = Vec3.atCenterOf(targetPos);
            }
        }

        if (targetPos == null) {
            return false;
        }

        return trySpawnTrainAtTarget(level, player, stack, targetPos, targetPoint);
    }

    private boolean trySpawnTrainAtTarget(Level level, Player player, ItemStack stack, BlockPos targetPos) {
        return trySpawnTrainAtTarget(level, player, stack, targetPos, Vec3.atCenterOf(targetPos));
    }

    private boolean trySpawnTrainAtTarget(Level level, Player player, ItemStack stack, BlockPos targetPos, Vec3 targetPoint) {
        String selectedId = stack.get(PolygonTrainModComponents.SELECTED_MODEL_ID.get());
        VehicleDefinition def = VehicleRegistry.getById(selectedId);
        if (def == null) {
            def = VehicleRegistry.getSelected();
        }
        if (def == null) {
            player.displayClientMessage(Component.translatable("message.polygontrainmod.train.must_be_on_rail"), true);
            return false;
        }

        RailSpawnData spawnData = findNearestRailSpawn(level, targetPos, targetPoint);
        if (spawnData == null) {
            player.displayClientMessage(Component.translatable("message.polygontrainmod.train.must_be_on_rail"), true);
            return false;
        }
        double spawnY = spawnData.y();
        if (isOccupiedSpawnArea(level, spawnData.x(), spawnY + 0.25D, spawnData.z(), def)) {
            player.displayClientMessage(Component.literal("この場所には既に電車があります。別の場所を選んでください。"), true);
            return false;
        }

        TrainEntity train = TrainEntity.create(level, def.getId(), spawnData.x(), spawnY, spawnData.z(), spawnData.yaw(), def.getTrainDistance());
        if (train == null) {
            return false;
        }
        train.initializeOnRail(spawnData.map(), spawnData.split(), spawnData.index());

        level.addFreshEntity(train);
        return true;
    }

    private boolean isOccupiedSpawnArea(Level level, double x, double y, double z, VehicleDefinition def) {
        double radius = Math.max(2.5, getSpawnHalfLength(def));
        var bounds = new net.minecraft.world.phys.AABB(
            x - radius,
            y - 0.75,
            z - radius,
            x + radius,
            y + 4.0,
            z + radius
        );
        return !level.getEntitiesOfClass(TrainEntity.class, bounds, entity -> true).isEmpty();
    }

    private static double getSpawnHalfLength(VehicleDefinition def) {
        if (def == null) {
            return 4.5D;
        }
        double halfLength = Math.max(2.5D, def.getTrainDistance());
        for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
            halfLength = Math.max(halfLength, Math.abs(bogie.position().z) + 1.4D);
        }
        for (Vec3 seat : def.getAllSeatPositions()) {
            halfLength = Math.max(halfLength, Math.abs(seat.z) + 1.2D);
        }
        return halfLength;
    }

    private BlockPos findNearestBlockAlongRay(Level level, Vec3 start, Vec3 end) {
        int samples = 8;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / samples;
            Vec3 pos = start.add(end.subtract(start).scale(t));
            BlockPos blockPos = BlockPos.containing(pos.x, pos.y, pos.z);
            if (isRailBlock(level, blockPos)) {
                return blockPos;
            }
        }
        return null;
    }

    private boolean isRailBlock(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof LargeRailCoreBlock) {
            return true;
        }
        if (level.getBlockEntity(pos) instanceof RailCollisionBlockEntity) {
            return true;
        }
        return false;
    }

    private static RailSpawnData findNearestRailSpawn(Level level, BlockPos clickedPos, Vec3 clickedPoint) {
        RailMap clickedRailMap = getRailMapAt(level, clickedPos, clickedPoint);
        if (clickedRailMap != null) {
            return findNearestPointOnMap(clickedRailMap, clickedPoint);
        }

        RailSpawnData best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        int radius = 16;
        double cx = clickedPoint.x;
        double cy = clickedPoint.y;
        double cz = clickedPoint.z;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = clickedPos.offset(dx, dy, dz);
                    RailMap map = getRailMapAt(level, pos, clickedPoint);
                    if (map == null) continue;
                    int max = getSpawnSplit(map);
                    for (int i = 0; i <= max; i++) {
                        double[] posData = map.getRailPos(max, i);
                        double x = posData[1];
                        double y = map.getRailHeight(max, i);
                        double z = posData[0];
                        double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
                        if (d2 < bestDistSq) {
                            bestDistSq = d2;
                            best = createSpawnData(map, max, i);
                        }
                    }
                }
            }
        }
        return bestDistSq <= 64.0 ? best : null;
    }

    private static RailMap getRailMapAt(Level level, BlockPos pos) {
        return getRailMapAt(level, pos, Vec3.atCenterOf(pos));
    }

    private static RailMap getRailMapAt(Level level, BlockPos pos, Vec3 targetPoint) {
        if (level.getBlockEntity(pos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
            return getNearestRailMap(core, targetPoint);
        }
        if (level.getBlockEntity(pos) instanceof RailCollisionBlockEntity collision) {
            BlockPos corePos = collision.getCorePos();
            if (corePos != null && level.getBlockEntity(corePos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
                return getNearestRailMap(core, targetPoint);
            }
        }
        return null;
    }

    private static RailMap getNearestRailMap(LargeRailCoreBlockEntity core, Vec3 targetPoint) {
        RailMap[] maps = core.getAllRailMaps();
        if (maps.length == 0) return null;
        if (maps.length == 1) return maps[0];
        double cx = targetPoint.x;
        double cy = targetPoint.y;
        double cz = targetPoint.z;
        RailMap best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (RailMap map : maps) {
            int max = getSpawnSplit(map);
            for (int i = 0; i <= max; i++) {
                double[] posData = map.getRailPos(max, i);
                double x = posData[1];
                double y = map.getRailHeight(max, i);
                double z = posData[0];
                double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
                if (d2 < bestDistSq) {
                    bestDistSq = d2;
                    best = map;
                }
            }
        }
        return best;
    }

    private static RailSpawnData findNearestPointOnMap(RailMap map, Vec3 clickedPoint) {
        if (map == null) return null;
        RailSpawnData best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        int max = getSpawnSplit(map);
        double cx = clickedPoint.x;
        double cy = clickedPoint.y;
        double cz = clickedPoint.z;
        for (int i = 0; i <= max; i++) {
            double[] posData = map.getRailPos(max, i);
            double x = posData[1];
            double y = map.getRailHeight(max, i);
            double z = posData[0];
            double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = createSpawnData(map, max, i);
            }
        }
        return best;
    }

    private static RailSpawnData createSpawnData(RailMap map, int max, int index) {
        double[] p = map.getRailPos(max, index);
        double x = p[1];
        double y = map.getRailHeight(max, index);
        double z = p[0];
        float yaw = map.getRailYaw(max, index);
        return new RailSpawnData(map, max, index, x, y, z, yaw);
    }

    private static int getSpawnSplit(RailMap map) {
        if (map == null) {
            return 64;
        }
        return Math.max(96, RailMap.curveSplitForLength(map.getHorizontalPathLength()) * 6);
    }

    private record RailSpawnData(RailMap map, int split, int index, double x, double y, double z, float yaw) {
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
        TrainFormation formation = TrainFormationData.getFormation(stack);
        if (formation != null && !formation.isEmpty()) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.polygontrainmod.train_formation.cars", formation.getCarCount()));
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.polygontrainmod.train_formation.formation", formation.getDisplayName()));
        } else {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.polygontrainmod.train_formation.empty"));
        }

        String selectedModel = stack.get(PolygonTrainModComponents.SELECTED_MODEL_ID.get());
        if (selectedModel != null && !selectedModel.isBlank()) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.polygontrainmod.selected_model", selectedModel)
                .withStyle(net.minecraft.ChatFormatting.GREEN));
        }
    }
}
