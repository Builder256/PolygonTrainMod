package com.portofino.polygontrainmod.item;

import com.portofino.polygontrainmod.PolygonTrainModComponents;
import com.portofino.polygontrainmod.client.ClientItemHelper;
import com.portofino.polygontrainmod.rail.RailDefinition;
import com.portofino.polygontrainmod.rail.RailRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class RailItem extends Item {
    public RailItem() {
        super(new Properties());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // This method should only be called when looking at empty space
        // If looking at a block, the block's use method should be called instead
        System.out.println("[DEBUG] RailItem.use() called - this should only happen when looking at empty space");

        // Only open UI when looking at empty space
        if (level.isClientSide) {
            System.out.println("[DEBUG] Opening rail selection UI");
            ClientItemHelper.openRailSelectScreen(player, player.getItemInHand(hand));
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String selectedId = stack.get(PolygonTrainModComponents.SELECTED_MODEL_ID.get());
        if (selectedId != null && !selectedId.isBlank()) {
            RailDefinition def = RailRegistry.getById(selectedId);
            String name = def != null ? def.getDisplayName() : selectedId;
            lines.add(Component.translatable("tooltip.polygontrainmod.model.selected", name)
                .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("tooltip.polygontrainmod.model.none")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
