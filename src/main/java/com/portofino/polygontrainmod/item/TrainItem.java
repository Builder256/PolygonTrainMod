package com.portofino.polygontrainmod.item;

import com.portofino.polygontrainmod.PolygonTrainModComponents;
import com.portofino.polygontrainmod.blockentity.LargeRailCoreBlockEntity;
import com.portofino.polygontrainmod.blockentity.RailCollisionBlockEntity;
import com.portofino.polygontrainmod.client.ClientItemHelper;
import com.portofino.polygontrainmod.entity.TrainEntity;
import com.portofino.polygontrainmod.rail.util.RailMap;
import com.portofino.polygontrainmod.vehicle.VehicleDefinition;
import com.portofino.polygontrainmod.vehicle.VehicleRegistry;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

public class TrainItem extends Item {
    public TrainItem() {
        super(new Properties());
    }

    /** スポーンクールダウン: 0.2秒 = 4 ticks */
    private static final int SPAWN_COOLDOWN_TICKS = 4;

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        // クールダウン中はスポーン不可
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        String selectedId = stack.get(PolygonTrainModComponents.SELECTED_MODEL_ID.get());
        VehicleDefinition def = VehicleRegistry.getById(selectedId);
        if (def == null) {
            def = VehicleRegistry.getSelected();
        }
        if (def == null) {
            return InteractionResult.PASS;
        }
        RailSpawnData spawnData = findNearestRailSpawn(level, context.getClickedPos(), player.getYRot());
        if (spawnData == null) {
            player.displayClientMessage(Component.translatable("message.polygontrainmod.train.must_be_on_rail"), true);
            return InteractionResult.FAIL;
        }
        double offsetY = Math.max(0.0, def.getModelOffset().y);
        double sx = spawnData.x;
        double sy = spawnData.y + 1.0 + offsetY;
        double sz = spawnData.z;
        TrainEntity train = TrainEntity.create(level, def.getId(), sx, sy, sz, spawnData.yaw(), def.getTrainDistance());
        if (train == null) {
            return InteractionResult.PASS;
        }
        level.addFreshEntity(train);
        // クールダウン付与（サーバー側。クライアントにも自動同期される）
        player.getCooldowns().addCooldown(this, SPAWN_COOLDOWN_TICKS);
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            ClientItemHelper.openTrainSelectScreen(player, player.getItemInHand(hand));
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String selectedId = stack.get(PolygonTrainModComponents.SELECTED_MODEL_ID.get());
        if (selectedId != null && !selectedId.isBlank()) {
            VehicleDefinition def = VehicleRegistry.getById(selectedId);
            String name = def != null ? def.getDisplayName() : selectedId;
            tooltip.add(Component.translatable("tooltip.polygontrainmod.model.selected", name));
        } else {
            tooltip.add(Component.translatable("tooltip.polygontrainmod.model.none"));
        }
    }

    private static RailSpawnData findNearestRailSpawn(Level level, BlockPos clickedPos, float preferredYaw) {
        RailMap clickedRailMap = getRailMapAt(level, clickedPos);
        if (clickedRailMap != null) {
            return findNearestPointOnMap(clickedRailMap, clickedPos, preferredYaw);
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
                    BlockPos p = clickedPos.offset(dx, dy, dz);
                    RailMap map = getRailMapAt(level, p);
                    if (map == null) continue;
                    int max = Math.max(2, (int) (map.getLength() * 2.0));
                    for (int i = 0; i <= max; i++) {
                        double[] pos = map.getRailPos(max, i);
                        double x = pos[1];
                        double y = map.getRailHeight(max, i);
                        double z = pos[0];
                        double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
                        if (d2 < bestDistSq) {
                            bestDistSq = d2;
                            best = createSpawnData(map, max, i, preferredYaw);
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

    private static RailSpawnData findNearestPointOnMap(RailMap map, BlockPos clickedPos, float preferredYaw) {
        if (map == null) return null;
        RailSpawnData best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        int max = Math.max(2, (int) (map.getLength() * 2.0));
        double cx = clickedPos.getX() + 0.5;
        double cy = clickedPos.getY() + 0.5;
        double cz = clickedPos.getZ() + 0.5;
        for (int i = 0; i <= max; i++) {
            double[] pos = map.getRailPos(max, i);
            double x = pos[1];
            double y = map.getRailHeight(max, i);
            double z = pos[0];
            double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = createSpawnData(map, max, i, preferredYaw);
            }
        }
        return best;
    }

    private static RailSpawnData createSpawnData(RailMap map, int max, int index, float preferredYaw) {
        double[] p = map.getRailPos(max, index);
        double x = p[1];
        double y = map.getRailHeight(max, index);
        double z = p[0];
        float yaw = choosePreferredRailYaw(map.getRailYaw(max, index), preferredYaw);
        return new RailSpawnData(x, y, z, yaw);
    }

    private static float choosePreferredRailYaw(float railYaw, float preferredYaw) {
        float forwardDiff = Math.abs(Mth.wrapDegrees(railYaw - preferredYaw));
        float reverseYaw = Mth.wrapDegrees(railYaw + 180.0F);
        float reverseDiff = Math.abs(Mth.wrapDegrees(reverseYaw - preferredYaw));
        return reverseDiff < forwardDiff ? reverseYaw : railYaw;
    }

    private record RailSpawnData(double x, double y, double z, float yaw) {
    }
}
