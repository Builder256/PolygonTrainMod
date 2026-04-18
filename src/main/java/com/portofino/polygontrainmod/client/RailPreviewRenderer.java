package com.portofino.polygontrainmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.PolygonTrainModComponents;
import com.portofino.polygontrainmod.block.MarkerBlock;
import com.portofino.polygontrainmod.blockentity.MarkerBlockEntity;
import com.portofino.polygontrainmod.item.RailItem;
import com.portofino.polygontrainmod.rail.util.RailMap;
import com.portofino.polygontrainmod.rail.util.RailMapBasic;
import com.portofino.polygontrainmod.rail.util.RailPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof RailItem)) {
            stack = mc.player.getOffhandItem();
        }
        if (!(stack.getItem() instanceof RailItem)) {
            return;
        }
        CompoundTag startTag = stack.get(PolygonTrainModComponents.RAIL_PREVIEW_START.get());
        if (startTag == null || !startTag.contains("X") || !startTag.contains("Y") || !startTag.contains("Z")) {
            return;
        }

        BlockPos startPos = new BlockPos(startTag.getInt("X"), startTag.getInt("Y"), startTag.getInt("Z"));
        BlockEntity startBe = mc.level.getBlockEntity(startPos);
        if (!(startBe instanceof MarkerBlockEntity startMarker)) {
            return;
        }
        RailPosition start = startMarker.getMarkerRP();
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
            renderStartMarkerHint(poseStack, consumer, startPos);
            if (mc.hitResult instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                && !blockHit.getBlockPos().equals(startPos)) {
                BlockEntity targetBe = mc.level.getBlockEntity(blockHit.getBlockPos());
                if (targetBe instanceof MarkerBlockEntity marker) {
                    RailPosition end = marker.getMarkerRP();
                    if (end != null) {
                        end = applyPreviewOffset(end, startTag);
                        renderPreviewRail(poseStack, consumer, start, end);
                    }
                }
            }
        } finally {
            poseStack.popPose();
            bufferSource.endBatch(RenderType.lines());
        }
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

    private static void renderPreviewRail(PoseStack poseStack, VertexConsumer consumer, RailPosition rawStart, RailPosition rawEnd) {
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
                0.1F, 0.85F, 1.0F, 0.45F
            );
        }
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
}
