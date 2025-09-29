package com.portofino.polygontrainmod.item.ticket;

import com.portofino.polygontrainmod.PolygonTrainModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * ICカード乗車券クラス
 */
final public class ICCardTicketItem extends TicketItem {
    public ICCardTicketItem() {
        super(
            new Item.Properties().component(PolygonTrainModComponents.IS_ENTERED_TICKET, false)
        );
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        @NotNull Item.TooltipContext context,
        @NotNull List<Component> tooltipComponents,
        @NotNull TooltipFlag tooltipFlag
    ) {
    }

    @Override
    public void passGate(ItemStack stack, Player player) {
    }
}