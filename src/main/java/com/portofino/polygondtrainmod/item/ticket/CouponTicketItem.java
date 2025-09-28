package com.portofino.polygondtrainmod.item.ticket;

import com.portofino.polygondtrainmod.PolygonTrainModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 回数乗車券クラス
 */
public final class CouponTicketItem extends TicketItem {
    public CouponTicketItem() {
        super(
            new Item
                .Properties()
                // デフォルトコンポーネントを設定
                // 使用回数: 10回
                .component(PolygonTrainModComponents.REMAINING_USES, 10)
        );
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        @NotNull Item.TooltipContext context,
        @NotNull List<Component> tooltipComponents,
        @NotNull TooltipFlag tooltipFlag
    ) {
        Boolean isEnteredTicket = stack.get(PolygonTrainModComponents.IS_ENTERED_TICKET.get());
        Integer remaining_uses = stack.get(PolygonTrainModComponents.REMAINING_USES.get());

        // if(isEnteredTicket)じゃダメなのは一体何故
        if (Boolean.TRUE.equals(isEnteredTicket)) {
            tooltipComponents.add(Component.translatable("tooltip.polygontrainmod.ticket.entered"));
        }

        if (remaining_uses != null) {
            MutableComponent translatableText = Component.translatable("tooltip.polygontrainmod.ticket.used_count", remaining_uses);
            tooltipComponents.add(translatableText);
        }
    }

    @Override
    public void passGate(ItemStack stack, Player player) {
        Boolean isEnteredTicket = stack.get(PolygonTrainModComponents.IS_ENTERED_TICKET.get());
        Integer remaining_uses = stack.get(PolygonTrainModComponents.REMAINING_USES.get());

        if (isEnteredTicket == null) isEnteredTicket = false;
        if (remaining_uses == null) remaining_uses = 10; // 回数券のデフォルト使用回数は10

        // インベントリから切符を削除（最大スタック数: 1）
        // 改札機に切符を挿入する動作を再現
        stack.shrink(1);

        if (!isEnteredTicket) {
            // 入場
            if (remaining_uses > 0) {
                // 切符を復活
                stack.grow(1);
                stack.set(PolygonTrainModComponents.IS_ENTERED_TICKET.get(), true);
                // 改札機から切符が返却される動作を再現
                player.getInventory().add(stack);
            }
            // remaining_uses が 0 以下の場合は切符が削除されたまま（使用不可）
        } else {
            // 出場
            remaining_uses--;

            if (remaining_uses > 0) {
                stack.grow(1);
                stack.set(PolygonTrainModComponents.IS_ENTERED_TICKET.get(), false);
                stack.set(PolygonTrainModComponents.REMAINING_USES.get(), remaining_uses);
                // 改札機から切符が返却される動作を再現
                player.getInventory().add(stack);
            }
            // remaining_uses が 0 以下の場合は切符が削除されたまま（使い切った）
        }
    }

}
