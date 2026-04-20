package com.portofino.polygontrainmod.item;

import com.portofino.polygontrainmod.PolygonTrainModBlocks;
import com.portofino.polygontrainmod.PolygonTrainModComponents;
import com.portofino.polygontrainmod.blockentity.InstalledObjectBlockEntity;
import com.portofino.polygontrainmod.client.ClientItemHelper;
import com.portofino.polygontrainmod.client.screen.ModelSelectScreen;
import com.portofino.polygontrainmod.installedobject.InstalledObjectCategory;
import com.portofino.polygontrainmod.installedobject.InstalledObjectDefinition;
import com.portofino.polygontrainmod.installedobject.InstalledObjectRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class WireItem extends Item implements ModelSelectableItem {
    public WireItem() {
        super(new Properties());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            ClientItemHelper.openInstalledObjectSelectScreen(player, player.getItemInHand(hand), InstalledObjectCategory.WIRE);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        String selectedId = stack.get(PolygonTrainModComponents.SELECTED_MODEL_ID.get());
        InstalledObjectDefinition definition = InstalledObjectRegistry.getById(selectedId);
        if (definition == null || definition.getCategory() != InstalledObjectCategory.WIRE) {
            if (level.isClientSide) {
                ClientItemHelper.openInstalledObjectSelectScreen(player, stack, InstalledObjectCategory.WIRE);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos clickedPos = context.getClickedPos();
        if (!(level.getBlockEntity(clickedPos) instanceof InstalledObjectBlockEntity clicked)
            || clicked.getCategory() != InstalledObjectCategory.INSULATOR) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("ワイヤーは碍子同士でのみ設置できます"), true);
            }
            return InteractionResult.FAIL;
        }

        CompoundTag startTag = stack.get(PolygonTrainModComponents.WIRE_PLACEMENT_START.get());
        if (startTag == null || !startTag.contains("X")) {
            if (!level.isClientSide) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("X", clickedPos.getX());
                tag.putInt("Y", clickedPos.getY());
                tag.putInt("Z", clickedPos.getZ());
                stack.set(PolygonTrainModComponents.WIRE_PLACEMENT_START.get(), tag);
                player.displayClientMessage(Component.literal("始点の碍子を記録しました"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos startPos = new BlockPos(startTag.getInt("X"), startTag.getInt("Y"), startTag.getInt("Z"));
        if (startPos.equals(clickedPos)) {
            if (!level.isClientSide) {
                stack.remove(PolygonTrainModComponents.WIRE_PLACEMENT_START.get());
                player.displayClientMessage(Component.literal("ワイヤー設置を解除しました"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos mid = new BlockPos((startPos.getX() + clickedPos.getX()) >> 1, (startPos.getY() + clickedPos.getY()) >> 1, (startPos.getZ() + clickedPos.getZ()) >> 1);
        BlockState state = level.getBlockState(mid);
        if (!state.canBeReplaced()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("ワイヤー中央にブロックがあるため設置できません"), true);
            }
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            level.setBlock(mid, PolygonTrainModBlocks.INSTALLED_OBJECT.get().defaultBlockState(), 3);
            if (level.getBlockEntity(mid) instanceof InstalledObjectBlockEntity blockEntity) {
                blockEntity.setDefinition(definition.getId(), InstalledObjectCategory.WIRE, player.getYRot());
                blockEntity.setWireEndpoints(startPos, clickedPos);
                level.sendBlockUpdated(mid, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            }
            stack.remove(PolygonTrainModComponents.WIRE_PLACEMENT_START.get());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.literal("ワイヤーを設置しました"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String selectedId = stack.get(PolygonTrainModComponents.SELECTED_MODEL_ID.get());
        if (selectedId != null && !selectedId.isBlank()) {
            InstalledObjectDefinition def = InstalledObjectRegistry.getById(selectedId);
            String name = def != null ? def.getDisplayName() : selectedId;
            lines.add(Component.translatable("tooltip.polygontrainmod.model.selected", name).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("tooltip.polygontrainmod.model.none").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public List<ModelSelectScreen.ModelInfo> getSelectableModels() {
        return InstalledObjectRegistry.getByCategory(InstalledObjectCategory.WIRE).stream()
            .map(def -> new ModelSelectScreen.ModelInfo(def.getId(), def.getDisplayName()))
            .toList();
    }
}
