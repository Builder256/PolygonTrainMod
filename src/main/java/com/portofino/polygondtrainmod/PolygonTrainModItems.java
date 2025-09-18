package com.portofino.polygondtrainmod;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PolygonTrainModItems {
    /**
     * 未実装 有効な切符かどうか判定する
     * @param item 有効な切符の可能性があるItemStack
     * @return 常にtrue
     */
    public static boolean isValidTicket(ItemStack item) {
        return true; // 処理をまだ書いてないので一旦何が来てもtrueにしとく
    }

    // Polygontrainmod "名前空間に登録されるアイテムを保持するために、Deferred Registerを作成する。
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PolygonTrainMod.MODID);
    // テスト改札機のブロックアイテムを登録
    // インベントリに追加するにはブロックアイテムがないといけない
    public static final DeferredItem<BlockItem> TEST_AUTOMATIC_TICKET_GATE_ITEM = ITEMS.registerSimpleBlockItem("test_automatic_ticket_gate", PolygonTrainModBlocks.TEST_AUTOMATIC_TICKET_GATE);
    // テスト切符を登録
    public static final DeferredItem<Item> TEST_TICKET = ITEMS.registerSimpleItem("test_ticket", new Item.Properties().stacksTo(1));
    // id "polygontrainmod:example_id"、栄養度1、彩度2の新しい食品を作成する。
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));
}
