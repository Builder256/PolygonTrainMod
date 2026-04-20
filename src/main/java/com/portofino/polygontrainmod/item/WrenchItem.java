package com.portofino.polygontrainmod.item;

import com.portofino.polygontrainmod.PolygonTrainModComponents;
import com.portofino.polygontrainmod.block.MarkerBlock;
import com.portofino.polygontrainmod.blockentity.LargeRailCoreBlockEntity;
import com.portofino.polygontrainmod.blockentity.MarkerBlockEntity;
import com.portofino.polygontrainmod.rail.util.RailPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool used to move rail preview endpoints.
 */
public class WrenchItem extends Item {
    private static final int SEARCH_DISTANCE = 50;
    private static final int SEARCH_HEIGHT = 10;

    public WrenchItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        clearInvalidPreviewTags(player, level);
        BlockPos clickedPos = context.getClickedPos();
        BlockEntity clickedBe = level.getBlockEntity(clickedPos);
        ItemStack previewStack = findPreviewStack(player);
        if (clickedBe instanceof MarkerBlockEntity marker && level.getBlockState(clickedPos).getBlock() instanceof MarkerBlock) {
            ItemStack targetStack = findRailStack(player);
            if (targetStack.isEmpty()) {
                targetStack = previewStack.isEmpty() ? player.getItemInHand(context.getHand()) : previewStack;
            }
            RailPosition start = marker.getMarkerRP();
            if (start == null) {
                return InteractionResult.PASS;
            }
            List<RailPosition> ends = findOtherMarkers(level, clickedPos, start);
            if (ends.isEmpty()) {
                player.displayClientMessage(Component.literal("接続先のマーカーが見つかりません"), true);
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
            CompoundTag tag = new CompoundTag();
            tag.putInt("X", clickedPos.getX());
            tag.putInt("Y", clickedPos.getY());
            tag.putInt("Z", clickedPos.getZ());
            tag.putBoolean("WrenchMode", true);
            tag.putBoolean("BranchMode", ends.size() > 1 || ((MarkerBlock) level.getBlockState(clickedPos).getBlock()).isSwitch);
            tag.put("StartRP", start.writeToNBT());
            ListTag segments = new ListTag();
            for (RailPosition end : ends) {
                CompoundTag segment = new CompoundTag();
                segment.put("EndRP", end.writeToNBT());
                putDefaultAnchors(segment, start, end);
                segments.add(segment);
            }
            tag.put("RailSegments", segments);
            tag.put("EndRP", ends.get(0).writeToNBT());
            copySegmentAnchorsToRoot(tag, segments.getCompound(0));
            targetStack.set(PolygonTrainModComponents.RAIL_PREVIEW_START.get(), tag);
            player.displayClientMessage(Component.literal("レール調整: 動かしたい場所をレンチで右クリック"), true);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (previewStack.isEmpty() || !moveControlTo(previewStack, context.getClickLocation())) {
            return InteractionResult.PASS;
        }
        showOffsetMessage(level, player, previewStack);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        clearInvalidPreviewTags(player, level);
        ItemStack previewStack = findPreviewStack(player);
        if (!previewStack.isEmpty()) {
            CompoundTag tag = previewStack.get(PolygonTrainModComponents.RAIL_PREVIEW_START.get());
            if (tag != null && tag.getBoolean("WrenchMode")) {
                previewStack.remove(PolygonTrainModComponents.RAIL_PREVIEW_START.get());
                if (level.isClientSide()) {
                    player.displayClientMessage(Component.literal("レール調整を解除しました"), true);
                }
                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
            }
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    /**
     * Moves the nearest rail preview control handle to a world-space point.
     */
    public static boolean moveControlTo(ItemStack stack, Vec3 hit) {
        CompoundTag tag = stack.get(PolygonTrainModComponents.RAIL_PREVIEW_START.get());
        if (tag == null || !tag.contains("StartRP")) {
            return false;
        }
        RailPosition start = RailPosition.readFromNBT(tag.getCompound("StartRP"));
        if (start == null) {
            return false;
        }
        CompoundTag copy = tag.copy();
        ListTag segments = getSegmentList(copy);
        if (segments.isEmpty()) {
            return false;
        }

        int bestIndex = 0;
        boolean bestStart = true;
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        for (int i = 0; i < segments.size(); i++) {
            CompoundTag segment = segments.getCompound(i);
            RailPosition end = RailPosition.readFromNBT(segment.getCompound("EndRP"));
            if (end == null) {
                continue;
            }
            Vec3 startHandle = getStartHandle(segment, start, end);
            Vec3 endHandle = getEndHandle(segment, start, end);
            double startDistanceSq = hit.distanceToSqr(startHandle);
            if (startDistanceSq < bestDistanceSq) {
                bestDistanceSq = startDistanceSq;
                bestIndex = i;
                bestStart = true;
            }
            double endDistanceSq = hit.distanceToSqr(endHandle);
            if (endDistanceSq < bestDistanceSq) {
                bestDistanceSq = endDistanceSq;
                bestIndex = i;
                bestStart = false;
            }
        }

        CompoundTag segment = segments.getCompound(bestIndex);
        if (bestStart) {
            putHandle(segment, "Start", hit);
        } else {
            putHandle(segment, "End", hit);
        }
        segments.set(bestIndex, segment);
        copy.put("RailSegments", segments);
        copy.put("EndRP", segments.getCompound(0).getCompound("EndRP"));
        copySegmentAnchorsToRoot(copy, segments.getCompound(0));
        stack.set(PolygonTrainModComponents.RAIL_PREVIEW_START.get(), copy);
        return true;
    }

    /**
     * Applies control handles stored in a preview tag to one rail endpoint.
     */
    public static RailPosition applyControlHandle(RailPosition source, CompoundTag tag, boolean startHandle) {
        RailPosition copy = RailPosition.readFromNBT(source.writeToNBT());
        if (copy == null || tag == null) {
            return source;
        }
        String prefix = startHandle ? "Start" : "End";
        if (!tag.contains(prefix + "AnchorX")) {
            return copy;
        }
        double ax = tag.getDouble(prefix + "AnchorX");
        double ay = tag.getDouble(prefix + "AnchorY");
        double az = tag.getDouble(prefix + "AnchorZ");
        double dx = ax - copy.posX;
        double dz = az - copy.posZ;
        copy.anchorYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        copy.anchorLengthHorizontal = (float) Math.sqrt(dx * dx + dz * dz);
        double dy = ay - copy.posY;
        copy.anchorPitch = copy.anchorLengthHorizontal <= 1.0e-4F ? 0.0F : (float) Math.toDegrees(Math.atan2(dy, copy.anchorLengthHorizontal));
        copy.anchorLengthVertical = (float) Math.sqrt(copy.anchorLengthHorizontal * copy.anchorLengthHorizontal + dy * dy);
        return copy;
    }

    /**
     * Returns a control handle world position for rendering.
     */
    public static Vec3 getStartHandle(CompoundTag tag, RailPosition start, RailPosition end) {
        return getHandle(tag, "Start", start, end);
    }

    /**
     * Returns a control handle world position for rendering.
     */
    public static Vec3 getEndHandle(CompoundTag tag, RailPosition start, RailPosition end) {
        return getHandle(tag, "End", end, start);
    }

    /**
     * Returns all edited rail segments.
     */
    public static ListTag getSegmentList(CompoundTag tag) {
        if (tag.contains("RailSegments")) {
            return tag.getList("RailSegments", 10).copy();
        }
        ListTag segments = new ListTag();
        if (tag.contains("EndRP")) {
            CompoundTag segment = new CompoundTag();
            segment.put("EndRP", tag.getCompound("EndRP"));
            copyRootAnchorsToSegment(tag, segment);
            segments.add(segment);
        }
        return segments;
    }

    public static ItemStack findPlayerPreviewStack(Player player) {
        return findPreviewStack(player);
    }

    private static ItemStack findPreviewStack(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if ((stack.getItem() instanceof RailItem || stack.getItem() instanceof WrenchItem)
                && stack.get(PolygonTrainModComponents.RAIL_PREVIEW_START.get()) != null) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findRailStack(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof RailItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void clearInvalidPreviewTags(Player player, Level level) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            CompoundTag tag = stack.get(PolygonTrainModComponents.RAIL_PREVIEW_START.get());
            if (tag != null && !isPreviewTagValid(level, tag)) {
                stack.remove(PolygonTrainModComponents.RAIL_PREVIEW_START.get());
            }
        }
    }

    private static boolean isPreviewTagValid(Level level, CompoundTag tag) {
        if (!tag.contains("X") || !tag.contains("Y") || !tag.contains("Z")) {
            return false;
        }
        BlockPos startPos = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        BlockEntity startBe = level.getBlockEntity(startPos);
        if (startBe instanceof MarkerBlockEntity marker) {
            return marker.getMarkerRP() != null;
        }
        if (startBe instanceof LargeRailCoreBlockEntity core) {
            return core.getFirstRailPosition() != null;
        }
        return false;
    }

    private static int clampOffset(int value) {
        return Math.max(-128, Math.min(128, value));
    }

    private static List<RailPosition> findOtherMarkers(Level level, BlockPos origin, RailPosition start) {
        List<RailPosition> markers = new ArrayList<>();
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        for (int dx = -SEARCH_DISTANCE; dx <= SEARCH_DISTANCE; dx++) {
            for (int dy = -SEARCH_HEIGHT; dy <= SEARCH_HEIGHT; dy++) {
                for (int dz = -SEARCH_DISTANCE; dz <= SEARCH_DISTANCE; dz++) {
                    BlockEntity be = level.getBlockEntity(new BlockPos(ox + dx, oy + dy, oz + dz));
                    if (be instanceof MarkerBlockEntity marker) {
                        RailPosition rp = marker.getMarkerRP();
                        if (rp != null && (rp.blockX != start.blockX || rp.blockY != start.blockY || rp.blockZ != start.blockZ)) {
                            markers.add(rp);
                        }
                    }
                }
            }
        }
        markers.sort((a, b) -> {
            double adx = a.posX - start.posX;
            double ady = a.posY - start.posY;
            double adz = a.posZ - start.posZ;
            double bdx = b.posX - start.posX;
            double bdy = b.posY - start.posY;
            double bdz = b.posZ - start.posZ;
            return Double.compare(adx * adx + ady * ady + adz * adz, bdx * bdx + bdy * bdy + bdz * bdz);
        });
        return markers;
    }

    private static void putDefaultAnchors(CompoundTag tag, RailPosition start, RailPosition end) {
        if (!tag.contains("StartAnchorX")) {
            putHandle(tag, "Start", defaultHandle(start, end));
        }
        if (!tag.contains("EndAnchorX")) {
            putHandle(tag, "End", defaultHandle(end, start));
        }
    }

    private static Vec3 defaultHandle(RailPosition source, RailPosition target) {
        double dx = target.posX - source.posX;
        double dz = target.posZ - source.posZ;
        double length = Math.min(10.0D, Math.max(2.0D, Math.sqrt(dx * dx + dz * dz) * 0.35D));
        double yaw = Math.toRadians(source.anchorYaw);
        return new Vec3(
            source.posX + Math.sin(yaw) * length,
            source.posY,
            source.posZ + Math.cos(yaw) * length
        );
    }

    private static Vec3 getHandle(CompoundTag tag, String prefix, RailPosition source, RailPosition target) {
        if (tag.contains(prefix + "AnchorX")) {
            return new Vec3(
                tag.getDouble(prefix + "AnchorX"),
                tag.getDouble(prefix + "AnchorY"),
                tag.getDouble(prefix + "AnchorZ")
            );
        }
        return defaultHandle(source, target);
    }

    private static void putHandle(CompoundTag tag, String prefix, Vec3 point) {
        tag.putDouble(prefix + "AnchorX", point.x);
        tag.putDouble(prefix + "AnchorY", point.y);
        tag.putDouble(prefix + "AnchorZ", point.z);
    }

    private static void copySegmentAnchorsToRoot(CompoundTag root, CompoundTag segment) {
        copyAnchor(segment, root, "Start");
        copyAnchor(segment, root, "End");
    }

    private static void copyRootAnchorsToSegment(CompoundTag root, CompoundTag segment) {
        copyAnchor(root, segment, "Start");
        copyAnchor(root, segment, "End");
    }

    private static void copyAnchor(CompoundTag source, CompoundTag target, String prefix) {
        if (source.contains(prefix + "AnchorX")) {
            target.putDouble(prefix + "AnchorX", source.getDouble(prefix + "AnchorX"));
            target.putDouble(prefix + "AnchorY", source.getDouble(prefix + "AnchorY"));
            target.putDouble(prefix + "AnchorZ", source.getDouble(prefix + "AnchorZ"));
        }
    }

    private static void showOffsetMessage(Level level, Player player, ItemStack stack) {
        if (!level.isClientSide()) {
            return;
        }
        CompoundTag tag = stack.get(PolygonTrainModComponents.RAIL_PREVIEW_START.get());
        int ox = tag == null ? 0 : tag.getInt("OffsetX");
        int oy = tag == null ? 0 : tag.getInt("OffsetY");
        int oz = tag == null ? 0 : tag.getInt("OffsetZ");
        player.displayClientMessage(Component.literal(
            String.format("レール調整 X:%+.2f Y:%+.2f Z:%+.2f", ox / 16.0D, oy / 16.0D, oz / 16.0D)
        ), true);
    }
}
