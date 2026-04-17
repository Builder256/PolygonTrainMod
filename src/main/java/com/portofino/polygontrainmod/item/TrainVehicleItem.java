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
import net.minecraft.util.Mth;
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
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            if (isRailBlock(level, pos)) {
                targetPos = pos;
            }
        }

        if (targetPos == null) {
            targetPos = findNearestBlockAlongRay(level, start, end);
        }

        if (targetPos == null) {
            return false;
        }

        return trySpawnTrainAtTarget(level, player, stack, targetPos);
    }

    private boolean trySpawnTrainAtTarget(Level level, Player player, ItemStack stack, BlockPos targetPos) {
        String selectedId = stack.get(PolygonTrainModComponents.SELECTED_MODEL_ID.get());
        VehicleDefinition def = VehicleRegistry.getById(selectedId);
        if (def == null) {
            def = VehicleRegistry.getSelected();
        }
        if (def == null) {
            player.displayClientMessage(Component.translatable("message.polygontrainmod.train.must_be_on_rail"), true);
            return false;
        }

        RailSpawnData spawnData = findNearestRailSpawn(level, targetPos);
        if (spawnData == null) {
            player.displayClientMessage(Component.translatable("message.polygontrainmod.train.must_be_on_rail"), true);
            return false;
        }
        spawnData = moveSpawnAwayFromPlayer(spawnData, player, def);
        if (isPlayerTooCloseToSpawn(player, spawnData, def)) {
            player.displayClientMessage(Component.literal("列車が近すぎます。少し離れてから置いてください。"), true);
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

        level.addFreshEntity(train);
        return true;
    }

    private static boolean isPlayerTooCloseToSpawn(Player player, RailSpawnData spawnData, VehicleDefinition def) {
        double safeDistance = getSpawnHalfLength(def) + 1.5D;
        double dx = player.getX() - spawnData.x();
        double dz = player.getZ() - spawnData.z();
        return dx * dx + dz * dz < safeDistance * safeDistance;
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

    private static RailSpawnData moveSpawnAwayFromPlayer(RailSpawnData spawnData, Player player, VehicleDefinition def) {
        if (spawnData == null || spawnData.map() == null) {
            return spawnData;
        }

        double halfLength = getSpawnHalfLength(def);
        Vec3 playerPos = player.position();
        double dx = playerPos.x - spawnData.x();
        double dz = playerPos.z - spawnData.z();
        double distSq = dx * dx + dz * dz;
        double safeDistance = halfLength + 2.0D;
        if (distSq >= safeDistance * safeDistance) {
            return spawnData;
        }

        int split = spawnData.split();
        double trackLen = Math.max(0.001D, spawnData.map().getLength());
        double sampleStep = Math.max(0.001D, trackLen / Math.max(1, split));
        int shiftSamples = Math.max(1, (int) Math.ceil(safeDistance / sampleStep));

        double yawRad = Math.toRadians(spawnData.yaw());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double playerAlongRail = dx * forwardX + dz * forwardZ;
        int preferredIndex = spawnData.index() + (playerAlongRail >= 0.0D ? -shiftSamples : shiftSamples);
        int clamped = Mth.clamp(preferredIndex, 0, split);
        if (Math.abs(clamped - spawnData.index()) < shiftSamples / 2) {
            clamped = Mth.clamp(spawnData.index() + (playerAlongRail >= 0.0D ? shiftSamples : -shiftSamples), 0, split);
        }
        return createSpawnData(spawnData.map(), split, clamped);
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

    private static RailSpawnData findNearestRailSpawn(Level level, BlockPos clickedPos) {
        RailMap clickedRailMap = getRailMapAt(level, clickedPos);
        if (clickedRailMap != null) {
            return findNearestPointOnMap(clickedRailMap, clickedPos);
        }

        RailSpawnData best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        int radius = 16;
        double cx = clickedPos.getX() + 0.5;
        double cy = clickedPos.getY() + 0.5;
        double cz = clickedPos.getZ() + 0.5;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = clickedPos.offset(dx, dy, dz);
                    RailMap map = getRailMapAt(level, pos);
                    if (map == null) continue;
                    int max = Math.max(2, (int) (map.getLength() * 2.0));
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
        if (level.getBlockEntity(pos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
            return getNearestRailMap(core, pos);
        }
        if (level.getBlockEntity(pos) instanceof RailCollisionBlockEntity collision) {
            BlockPos corePos = collision.getCorePos();
            if (corePos != null && level.getBlockEntity(corePos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
                return getNearestRailMap(core, pos);
            }
        }
        return null;
    }

    private static RailMap getNearestRailMap(LargeRailCoreBlockEntity core, BlockPos pos) {
        RailMap[] maps = core.getAllRailMaps();
        if (maps.length == 0) return null;
        if (maps.length == 1) return maps[0];
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        RailMap best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (RailMap map : maps) {
            int max = Math.max(2, (int) (map.getLength() * 2.0));
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

    private static RailSpawnData findNearestPointOnMap(RailMap map, BlockPos clickedPos) {
        if (map == null) return null;
        RailSpawnData best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        int max = Math.max(2, (int) (map.getLength() * 2.0));
        double cx = clickedPos.getX() + 0.5;
        double cy = clickedPos.getY() + 0.5;
        double cz = clickedPos.getZ() + 0.5;
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
        int i0 = Math.max(0, index - 1);
        int i1 = Math.min(max, index + 1);
        double[] p0 = map.getRailPos(max, i0);
        double[] p1 = map.getRailPos(max, i1);
        double dx = p1[1] - p0[1];
        double dz = p1[0] - p0[0];
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return new RailSpawnData(map, max, index, x, y, z, yaw);
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
