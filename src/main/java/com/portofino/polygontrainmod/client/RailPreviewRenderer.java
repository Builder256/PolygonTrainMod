package com.portofino.polygontrainmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.PolygonTrainModComponents;
import com.portofino.polygontrainmod.block.MarkerBlock;
import com.portofino.polygontrainmod.blockentity.LargeRailCoreBlockEntity;
import com.portofino.polygontrainmod.blockentity.MarkerBlockEntity;
import com.portofino.polygontrainmod.item.RailItem;
import com.portofino.polygontrainmod.item.WrenchItem;
import com.portofino.polygontrainmod.rail.util.RailMap;
import com.portofino.polygontrainmod.rail.util.RailMapBasic;
import com.portofino.polygontrainmod.rail.util.RailPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = PolygonTrainMod.MODID, value = Dist.CLIENT)
public final class RailPreviewRenderer {
    private static final int SEARCH_DISTANCE = 50;
    private static final int SEARCH_HEIGHT = 10;

    private RailPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        WrenchItem.clearInvalidPreviewTags(mc.player, mc.level);
        ItemStack stack = findPreviewStack(mc);
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag startTag = stack.get(PolygonTrainModComponents.RAIL_PREVIEW_START.get());
        if (startTag == null || !startTag.contains("X") || !startTag.contains("Y") || !startTag.contains("Z")) {
            return;
        }

        BlockPos startPos = new BlockPos(startTag.getInt("X"), startTag.getInt("Y"), startTag.getInt("Z"));
        BlockEntity startBe = mc.level.getBlockEntity(startPos);
        RailPosition start = resolveStartPosition(startBe, startTag);
        if (start == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        var bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        try {
            if (startTag.getBoolean("WrenchMode")) {
                ListTag segments = WrenchItem.getSegmentList(startTag);
                if (segments.isEmpty()) {
                    return;
                }
                renderStartMarkerHint(poseStack, consumer, startPos);
                for (int i = 0; i < segments.size(); i++) {
                    CompoundTag segment = segments.getCompound(i);
                    RailPosition rawEnd = RailPosition.readFromNBT(segment.getCompound("EndRP"));
                    if (rawEnd == null) {
                        continue;
                    }
                    RailPosition controlledStart = WrenchItem.applyControlHandle(start, segment, true);
                    RailPosition controlledEnd = WrenchItem.applyControlHandle(rawEnd, segment, false);
                    renderPreviewRail(poseStack, consumer, controlledStart, controlledEnd, false);
                    renderControlLine(poseStack, consumer, start, WrenchItem.getStartHandle(segment, start, rawEnd));
                    renderControlLine(poseStack, consumer, rawEnd, WrenchItem.getEndHandle(segment, start, rawEnd));
                }
                return;
            }
            renderStartMarkerHint(poseStack, consumer, startPos);
            if (mc.hitResult instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                && !blockHit.getBlockPos().equals(startPos)) {
                BlockEntity targetBe = mc.level.getBlockEntity(blockHit.getBlockPos());
                if (targetBe instanceof MarkerBlockEntity marker) {
                    RailPosition end = marker.getMarkerRP();
                    if (end != null) {
                        end = applyPreviewOffset(end, startTag);
                        renderPreviewRail(poseStack, consumer, start, end, false);
                    }
                }
            }
        } finally {
            poseStack.popPose();
            bufferSource.endBatch(RenderType.lines());
        }
    }

    private static ItemStack findPreviewStack(Minecraft mc) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = mc.player.getItemInHand(hand);
            if (stack.getItem() instanceof RailItem || stack.getItem() instanceof WrenchItem) {
                if (stack.get(PolygonTrainModComponents.RAIL_PREVIEW_START.get()) != null) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static void renderStartMarkerHint(PoseStack poseStack, VertexConsumer consumer, BlockPos startPos) {
        double x = startPos.getX() + 0.5D;
        double y = startPos.getY() + 0.12D;
        double z = startPos.getZ() + 0.5D;
        LevelRenderer.renderLineBox(
            poseStack,
            consumer,
            x - 0.45D, y - 0.04D, z - 0.45D,
            x + 0.45D, y + 0.04D, z + 0.45D,
            0.1F, 0.85F, 1.0F, 0.8F
        );
    }

    private static void renderPreviewRail(PoseStack poseStack, VertexConsumer consumer, RailPosition rawStart, RailPosition rawEnd, boolean editLine) {
        RailPosition start = RailPosition.readFromNBT(rawStart.writeToNBT());
        RailPosition end = RailPosition.readFromNBT(rawEnd.writeToNBT());
        RailMap map = new RailMapBasic(start, end);
        int split = Math.max(8, RailMap.curveSplitForLength(map.getHorizontalPathLength()));
        int samples = Math.min(96, Math.max(16, (int) Math.ceil(map.getLength() * 2.0D)));
        for (int i = 0; i <= samples; i++) {
            int index = (int) Math.round(split * (i / (double) samples));
            double[] p = map.getRailPos(split, index);
            double x = p[1];
            double y = map.getRailHeight(split, index) + 0.08D;
            double z = p[0];
            LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                x - 0.11D, y - 0.03D, z - 0.11D,
                x + 0.11D, y + 0.03D, z + 0.11D,
                editLine ? 0.2F : 0.1F,
                editLine ? 1.0F : 0.85F,
                editLine ? 0.2F : 1.0F,
                0.45F
            );
        }
    }

    private static void renderControlLine(PoseStack poseStack, VertexConsumer consumer, RailPosition source, Vec3 handle) {
        int samples = 24;
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            double x = source.posX + (handle.x - source.posX) * t;
            double y = source.posY + (handle.y - source.posY) * t + 0.08D;
            double z = source.posZ + (handle.z - source.posZ) * t;
            LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                x - 0.045D, y - 0.045D, z - 0.045D,
                x + 0.045D, y + 0.045D, z + 0.045D,
                0.15F, 1.0F, 0.15F, 0.75F
            );
        }
        LevelRenderer.renderLineBox(
            poseStack,
            consumer,
            handle.x - 0.22D, handle.y - 0.02D, handle.z - 0.22D,
            handle.x + 0.22D, handle.y + 0.08D, handle.z + 0.22D,
            1.0F, 0.1F, 0.1F, 0.85F
        );
    }

    private static RailPosition applyPreviewOffset(RailPosition raw, CompoundTag tag) {
        RailPosition copy = RailPosition.readFromNBT(raw.writeToNBT());
        if (copy == null || tag == null) {
            return raw;
        }
        copy.posX += tag.getInt("OffsetX") / 16.0D;
        copy.posY += tag.getInt("OffsetY") / 16.0D;
        copy.posZ += tag.getInt("OffsetZ") / 16.0D;
        return copy;
    }

    private static RailPosition resolveStartPosition(BlockEntity startBe, CompoundTag tag) {
        if (startBe instanceof MarkerBlockEntity marker) {
            return marker.getMarkerRP();
        }
        if (startBe instanceof LargeRailCoreBlockEntity core) {
            RailPosition first = core.getFirstRailPosition();
            if (first != null) {
                return first;
            }
        }
        if (tag.contains("StartRP")) {
            return RailPosition.readFromNBT(tag.getCompound("StartRP"));
        }
        return null;
    }

}
