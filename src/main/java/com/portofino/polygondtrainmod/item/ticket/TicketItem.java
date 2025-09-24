package com.portofino.polygondtrainmod.item.ticket;

import com.portofino.polygondtrainmod.PolygonTrainModComponents;
import net.minecraft.network.chat.Component;
//import net.minecraft.world.InteractionHand;
//import net.minecraft.world.InteractionResultHolder;
//import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 切符アイテムクラス
 */
public class TicketItem extends Item {
    public TicketItem(Properties properties) {
        super(properties);
    }

//    /**
//     * [テスト用]アイテムを手に持って右クリックされた時の処理
//     *
//     * @param level    右クリックされたlevel
//     * @param player   右クリックしたplayer
//     * @param usedHand 右クリックに使用した手
//     * @return パイプラインが成功かどうか
//     */
//    @Override
//    @NotNull
//    public InteractionResultHolder<ItemStack> use(
//        @NotNull Level level,
//        Player player,
//        @NotNull InteractionHand usedHand
//    ) {
//        ItemStack stack = player.getItemInHand(usedHand);
//
//        Integer currentNumber = stack.get(PolygonTrainModComponents.EXAMPLE_NUMBER.get());
//        if (currentNumber == null) {
//            currentNumber = 0;
//        }
//        stack.set(PolygonTrainModComponents.EXAMPLE_NUMBER.get(), currentNumber + 1);
//
//        Boolean isTicketUsed = stack.get(PolygonTrainModComponents.IS_TICKET_USED.get());
//        if (isTicketUsed == null) {
//            isTicketUsed = false;
//        }
//        stack.set(PolygonTrainModComponents.IS_TICKET_USED.get(), !isTicketUsed);
//
//        return InteractionResultHolder.success(stack);
//    }

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
     * 正直いらないが今後の発展性のために
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

        return true;
    }

    public boolean passGate(ItemStack stack) {
        return true;
    }
}
