package com.portofino.polygontrainmod.item;

import com.portofino.polygontrainmod.PolygonTrainModBlocks;
import com.portofino.polygontrainmod.PolygonTrainModComponents;
import com.portofino.polygontrainmod.client.ClientItemHelper;
import com.portofino.polygontrainmod.client.screen.ModelSelectScreen;
import com.portofino.polygontrainmod.installedobject.InstalledObjectCategory;
import com.portofino.polygontrainmod.installedobject.InstalledObjectDefinition;
import com.portofino.polygontrainmod.installedobject.InstalledObjectRegistry;
import com.portofino.polygontrainmod.blockentity.InstalledObjectBlockEntity;
import com.portofino.polygontrainmod.block.OverheadLinePoleBlock;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class InstalledObjectItem extends Item implements ModelSelectableItem {
    private final InstalledObjectCategory category;

    public InstalledObjectItem(InstalledObjectCategory category) {
        super(new Properties());
        this.category = category;
    }

    public InstalledObjectCategory getCategory() {
        return category;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            ClientItemHelper.openInstalledObjectSelectScreen(player, player.getItemInHand(hand), category);
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
        if (definition == null || definition.getCategory() != category) {
            if (level.isClientSide) {
                ClientItemHelper.openInstalledObjectSelectScreen(player, stack, category);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos placePos = context.getClickedPos().relative(context.getClickedFace());
        if (category == InstalledObjectCategory.SIGNAL
            && !(level.getBlockState(context.getClickedPos()).getBlock() instanceof OverheadLinePoleBlock)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("信号は架線柱にのみ設置できます"), true);
            }
            return InteractionResult.FAIL;
        }
        BlockState state = level.getBlockState(placePos);
        if (!state.canBeReplaced()) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            level.setBlock(placePos, PolygonTrainModBlocks.INSTALLED_OBJECT.get().defaultBlockState(), 3);
            if (level.getBlockEntity(placePos) instanceof InstalledObjectBlockEntity blockEntity) {
                blockEntity.setDefinition(definition.getId(), category, player.getYRot());
                if (category == InstalledObjectCategory.SIGNAL) {
                    // 当たり判定は変えず、見た目だけ柱の内側へ寄せる。
                    double yawRad = Math.toRadians(player.getYRot());
                    double inwardX = -Math.sin(yawRad) * 0.72D;
                    double inwardZ = Math.cos(yawRad) * 0.72D;
                    blockEntity.setRenderOffset(inwardX, 0.0D, inwardZ);
                } else {
                    blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                }
                level.sendBlockUpdated(placePos, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
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
        return InstalledObjectRegistry.getByCategory(category).stream()
            .map(def -> new ModelSelectScreen.ModelInfo(def.getId(), def.getDisplayName()))
            .toList();
    }
}
