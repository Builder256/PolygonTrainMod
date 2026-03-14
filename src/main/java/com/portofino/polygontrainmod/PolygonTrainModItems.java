package com.portofino.polygontrainmod;

import com.portofino.polygontrainmod.item.ticket.CouponTicketItem;
import com.portofino.polygontrainmod.item.ticket.ICCardTicketItem;
import com.portofino.polygontrainmod.item.ticket.TicketItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PolygonTrainModItems {
    // アイテムを保持するDeferredRegisterを作成し、すべてを名前空間"polygontrainmod"に登録する
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PolygonTrainMod.MODID);

    // BlockのBlockItemを登録
    // 1.7.10と違い、BlockItemを明示的に実装する必要がある
    // 炎ブロックなど、インベントリに配置する必要のない一部のブロック以外はBlockItemの登録が必須
    public static final DeferredItem<BlockItem> TEST_AUTOMATIC_TICKET_GATE_ITEM = ITEMS.registerSimpleBlockItem(
        "test_automatic_ticket_gate", PolygonTrainModBlocks.TEST_AUTOMATIC_TICKET_GATE
    );
    public static final DeferredItem<BlockItem> OVERHEAD_LINE_POLE_ITEM = ITEMS.registerSimpleBlockItem(
        "overhead_line_pole", PolygonTrainModBlocks.OVERHEAD_LINE_POLE
    );

    // 通常の乗車券を登録
    public static final DeferredItem<TicketItem> TICKET
        = ITEMS.registerItem("ticket", TicketItem::new, new Item.Properties());
    // 回数乗車券を登録
    public static final DeferredItem<CouponTicketItem> COUPON_TICKET
        = ITEMS.register("coupon_ticket", CouponTicketItem::new);
    // ICカード乗車券を登録
    public static final DeferredItem<ICCardTicketItem> IC_CARD_TICKET
        = ITEMS.register("ic_card_ticket", ICCardTicketItem::new);

    // 栄養度1、彩度2の性質を持つ新しい食品EXAMPLE_ITEMを登録する
    public static final DeferredItem<Item> EXAMPLE_ITEM
        = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
        .alwaysEdible().nutrition(1).saturationModifier(2f).build()));
}
