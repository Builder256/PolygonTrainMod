package com.portofino.polygontrainmod.item.ticket;

import com.portofino.polygontrainmod.PolygonTrainModComponents;
import net.minecraft.network.chat.Component;
//import net.minecraft.world.InteractionHand;
//import net.minecraft.world.InteractionResultHolder;
//import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 切符アイテムクラス
 * <ul>
 *     <li>通常の乗車券: TicketItem</li>
 *     <li>回数乗車券: CouponTicketItem</li>
 *     <li>ICカード乗車券: ICCardTicketItem</li>
 * </ul>
 */
public class TicketItem extends Item {
    public TicketItem(Item.Properties properties) {
        super(
            properties
                .stacksTo(1)
                // デフォルトコンポーネントを設定
                .component(PolygonTrainModComponents.IS_ENTERED_TICKET, false)
        );
    }

    /**
     * ツールチップテキストを追加
     *
     * @param stack             ツールチップを表示するItemStack
     * @param context           表示する状況 少なくともLevelにはアクセスできるっぽい
     * @param tooltipComponents 実際に表示するツールチップの中身 謎
     * @param tooltipFlag       表示する条件をなんか指定できるっぽい
     */
    @Override
    public void appendHoverText(
        ItemStack stack,
        @NotNull Item.TooltipContext context,
        @NotNull List<Component> tooltipComponents,
        @NotNull TooltipFlag tooltipFlag
    ) {
        Boolean isEnteredTicket = stack.get(PolygonTrainModComponents.IS_ENTERED_TICKET.get());

        // if(isEnteredTicket)じゃダメなのは一体何故
        if (Boolean.TRUE.equals(isEnteredTicket)) {
            tooltipComponents.add(Component.literal("入場済"));
        }
        // 適当なデータコンポーネントの値
//        Integer number = stack.get(PolygonTrainModComponents.EXAMPLE_NUMBER.get());
//        Boolean isTicketUsed = stack.get(PolygonTrainModComponents.IS_TICKET_USED.get());
//        if (number != null && isTicketUsed != null) {
        // ツールチップに行を追加
//            tooltipComponents.add(Component.literal("Number: " + number));
//            tooltipComponents.add(Component.literal("used: " + isTicketUsed));
//        }
    }

    /**
     * その切符で改札を通ることができるか
     * 正直いらないが将来的な拡張性のために
     *
     * @param stack 検証する切符
     * @return 通ることができるか
     */
    public boolean canPassGate(ItemStack stack) {
        Item item = stack.getItem();
        // 今後itemが切符であることを保証
        if (!(item instanceof TicketItem)) return false;

        // プリミティブなbooleanだとエラーになる なんで？
        Boolean isEnteredTicket = stack.get(PolygonTrainModComponents.IS_ENTERED_TICKET.get());
        // 入場状態がなければ未入場で更新
        if (isEnteredTicket == null) isEnteredTicket = false;

        // それはそうとtrueを返す
        return true;
    }

    /**
     * 改札を通るときの処理
     *
     * @param stack  切符
     * @param player プレイヤー
     */
    public void passGate(ItemStack stack, Player player) {
        Boolean isEnteredTicket = stack.get(PolygonTrainModComponents.IS_ENTERED_TICKET.get());
        if (isEnteredTicket == null) isEnteredTicket = false;

        // インベントリから切符を削除（最大スタック数: 1）
        // 改札機に切符を挿入する動作を再現
        stack.shrink(1);
        if (!isEnteredTicket) {
            // 切符を復活
            stack.grow(1);
            stack.set(PolygonTrainModComponents.IS_ENTERED_TICKET.get(), true);
            // 改札機から切符が返却される動作を再現
            player.getInventory().add(stack);
        }
    }
}
