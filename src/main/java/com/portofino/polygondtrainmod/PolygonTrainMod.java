package com.portofino.polygondtrainmod;

import com.portofino.polygondtrainmod.block.GateBlock;
import net.minecraft.world.level.block.SoundType;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

//import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
//import net.minecraft.world.level.block.Block;
//import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
//import net.minecraft.world.level.material.MapColor;
//import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
//import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//import net.neoforged.neoforge.common.NeoForge;
//import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
//import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// この値はMETA-INF/neoforge.mods.tomlファイルの入力と一致する必要があります。
@Mod(PolygonTrainMod.MODID)
public class PolygonTrainMod {
    // すべての人が参照できるように、共通の場所にmodのidを定義する。
    public static final String MODID = "polygontrainmod";
    // slf4j ロガーを直接参照する
    public static final Logger LOGGER = LogUtils.getLogger();
    // すべてのブロックを "polygontrainmod "名前空間に登録するために、Deferred Registerを作成する。
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Polygontrainmod "名前空間に登録されるアイテムを保持するために、Deferred Registerを作成する。
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // CreativeModeTabsを保持するDeferred Registerを作成し、すべて "polygontrainmod "ネームスペースに登録する。
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // 名前空間とパスを組み合わせて、id "polygontrainmod:example_block "で新しいブロックを作成します。
//    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // 名前空間とパスを組み合わせて、id "polygontrainmod:example_block "の新しいBlockItemを作成します。
//    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // id "polygontrainmod:example_id"、栄養度1、彩度2の新しい食品を作成する。
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));


    /* ~~~~~~~~~~ 以下DeferredHolderへの登録 ~~~~~~~~~~ */

    // テスト切符を登録
    public static final DeferredItem<Item> TEST_TICKET = ITEMS.registerSimpleItem("test_ticket", new Item.Properties().stacksTo(1));
    // テスト改札機を登録
    public static final DeferredBlock<GateBlock> TEST_AUTOMATIC_TICKET_GATE = BLOCKS.register("test_automatic_ticket_gate", ()-> new GateBlock(BlockBehaviour.Properties.of().sound(SoundType.STONE)));
    // テスト改札機のブロックアイテムを登録
    // インベントリに追加するにはブロックアイテムがないといけない
    public static final DeferredItem<BlockItem> TEST_AUTOMATIC_TICKET_GATE_ITEM = ITEMS.registerSimpleBlockItem("test_automatic_ticket_gate", TEST_AUTOMATIC_TICKET_GATE);

    // 戦闘タブの後に配置される、idが"polygontrainmod:example_tab"のクリエイティブタブを登録
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.polygontrainmod")) //CreativeModeTabのタイトルの言語キー
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> TEST_TICKET.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_ITEM.get()); // サンプルアイテムをタブに追加します。独自のタブの場合は、イベントよりもこの方法が推奨されます。

                output.accept(TEST_TICKET.get());
                // インベントリに作成したブロックアイテムを追加する、ブロックを直接登録しても自動でブロックアイテムを探索してやってくれるらしい
                output.accept(TEST_AUTOMATIC_TICKET_GATE_ITEM.get());
            }).build());

    // MODクラスのコンストラクタは、MODがロードされたときに最初に実行されるコードです。
    // FML は IEventBus や ModContainer のようないくつかのパラメータタイプを認識し、 自動的に渡してくれます。
    public PolygonTrainMod(IEventBus modEventBus, ModContainer modContainer) {
        // modloading用のcommonSetupメソッドを登録する。
        modEventBus.addListener(this::commonSetup);

        // DeferredレジスタをMODイベントバスに登録し、ブロックが登録されるようにする
        BLOCKS.register(modEventBus);
        // MODイベントバスにDeferred Registerを登録し、アイテムが登録されるようにする。
        ITEMS.register(modEventBus);
        // MODイベントバスにDeferred Registerを登録し、タブが登録されるようにする。
        CREATIVE_MODE_TABS.register(modEventBus);

        // 興味のあるサーバーやその他のゲームイベントに登録する。
        // これは、*この*クラス（PolygonTrainMod）がイベントに直接反応することを望む場合にのみ必要であることに注意してください。
        // 以下の onServerStarting() のように、このクラスに @SubscribeEvent アノテーション関数がない場合は、この行を追加しないでください。
//        NeoForge.EVENT_BUS.register(this);

        // アイテムをクリエイティブタブに登録する
//        modEventBus.addListener(this::addCreative);

        // modのModConfigSpecを登録し、FMLが設定ファイルを作成して読み込むようにする。
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // 一般的なセットアップコード
//        LOGGER.info("HELLO FROM COMMON SETUP");

//        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
//            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
//        }

//        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

//        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // ビルディング・ブロック・タブにサンプル・ブロック項目を追加する
//    private void addCreative(BuildCreativeModeTabContentsEvent event) {
//        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
//            event.accept(EXAMPLE_BLOCK_ITEM);
//        }
//    }

    // SubscribeEvent を使用し、イベントバスに呼び出すメソッドを発見させることができます
//    @SubscribeEvent
//    public void onServerStarting(ServerStartingEvent event) {
        // サーバー起動時に何かする
//        LOGGER.info("HELLO from server starting");
//    }
}
