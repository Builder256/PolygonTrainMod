package com.portofino.polygondtrainmod;

import com.portofino.polygondtrainmod.item.ticket.CouponTicketItem;
import com.portofino.polygondtrainmod.item.ticket.ICCardTicketItem;
import com.portofino.polygondtrainmod.item.ticket.TicketItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PolygonTrainModItems {

    // Polygontrainmod "名前空間に登録されるアイテムを保持するために、Deferred Registerを作成する。
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PolygonTrainMod.MODID);

    // テスト改札機のブロックアイテムを登録
    // インベントリに追加するにはブロックアイテムがないといけない
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

    // id "polygontrainmod:example_id"、栄養度1、彩度2の新しい食品を作成する。
    public static final DeferredItem<Item> EXAMPLE_ITEM
        = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
        .alwaysEdible().nutrition(1).saturationModifier(2f).build()));
}
