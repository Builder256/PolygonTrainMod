package com.portofino.polygondtrainmod.item;

import com.portofino.polygondtrainmod.PolygonTrainModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class TicketItem extends Item {
    public TicketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        Integer currentNumber = stack.get(PolygonTrainModComponents.EXAMPLE_NUMBER.get());
        if (currentNumber == null) {
            currentNumber = 0;
        }

        stack.set(PolygonTrainModComponents.EXAMPLE_NUMBER.get(), currentNumber + 1);

        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        Item.TooltipContext context,
        List<Component> tooltipComponents,
        TooltipFlag tooltipFlag
    ) {
        Integer number = stack.get(PolygonTrainModComponents.EXAMPLE_NUMBER.get());
        if (number != null) {
            tooltipComponents.add(Component.literal("Number: " + number));
        }
    }

    /**
     * [未実装] 有効な切符かどうか判定する
     *
     * @param item 有効な切符の可能性があるItemStack
     * @return 常にtrue
     */
    public static boolean isValidTicket(ItemStack item) {
        return true; // 処理をまだ書いてないので一旦何が来てもtrueにしとく
    }
}
