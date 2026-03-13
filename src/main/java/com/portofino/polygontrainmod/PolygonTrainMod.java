package com.portofino.polygontrainmod;

import com.portofino.polygontrainmod.client.renderer.PolygonTrainModRenderers;
import com.portofino.polygontrainmod.registry.PolygonTrainModEntities;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// この値はMETA-INF/neoforge.mods.tomlファイルの入力と一致する必要があります。
// Modアノテーション、1.21.1になっても1.7.10と機能は同じ？
@Mod(PolygonTrainMod.MODID)
public class PolygonTrainMod {
    // コード全体から参照できるmodのid
    public static final String MODID = "polygontrainmod";
    // PolygonTrainModからのログと識別するため、そのMod専用のロガーを取得する。1.7.10時代のLog4jとは違い、SLF4Jはロギングライブラリではないらしい
    public static final Logger LOGGER = LogUtils.getLogger();
    // CreativeModeTabsを保持するDeferredRegisterを作成し、すべてを名前空間"polygontrainmod"に登録する。
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // DeferredRegisterは、MinecraftのRegistriesに登録されているタイプの要素の新しいバリエーションを実装する場合に必要になる
    // 対象となるのは、BlockやItem、Entityなど、`minecraft:object_id`の形式で表すタイプのオブジェクト？
    // GameRegistryを自由に呼びつけるのは危険なため、代わりにこれが使用されるようになったらしい

    // 名前空間とパスを組み合わせて、id "polygontrainmod:example_block "で新しいブロックを作成します。
    // public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // 名前空間とパスを組み合わせて、id "polygontrainmod:example_block "の新しいBlockItemを作成します。
    // public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // 作ったDeferredRegisterに登録されるそれぞれの要素を表すのが、DeferredHolder
    // FMLPreInitializationEventでGameRegistryに登録していた時代は、BlockやItemをコード内で使用する際に、そのオブジェクトのインスタンスが必ず生成されていることが明らかだったが
    // Modローダーの判断で生成されるシステムのDeferredRegisterではそれがない
    // そのため、実際のオブジェクトの代わりにコードで使用されるためにつくられたのが、DeferredHolderだと思う

    // クリエイティブタブ"polygontrainmod:main_tab"を登録
    @SuppressWarnings("unused")
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main_tab", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.polygontrainmod")) //CreativeModeTabのタイトルの言語キー
        .withTabsBefore(CreativeModeTabs.COMBAT)
        // タブのアイコンを切符アイテムに設定
        .icon(() -> PolygonTrainModItems.TICKET.get().getDefaultInstance())
        .displayItems((parameters, output) -> {
            // EXAMPLE_ITEMをこのタブに追加
            // Mod独自のタブにアイテムを追加する場合は、後述のEventよりもこの方法が推奨されるらしい
            output.accept(PolygonTrainModItems.EXAMPLE_ITEM.get());
            // 通常の乗車券をクリエイティブタブに追加
            output.accept(PolygonTrainModItems.TICKET.get());
            // 回数乗車券をクリエイティブタブに追加
            output.accept(PolygonTrainModItems.COUPON_TICKET.get());
            // ICカード乗車券をクリエイティブタブに追加
            output.accept(PolygonTrainModItems.IC_CARD_TICKET.get());
            // 各種BlockののBlockItemをクリエイティブタブ追加する
            // 今回はItemを指定しているが、Blockを直接登録した場合も、自動で対応するBlockItemを探索してやってくれるらしい
            output.accept(PolygonTrainModItems.TEST_AUTOMATIC_TICKET_GATE_ITEM.get());
            output.accept(PolygonTrainModItems.OVERHEAD_LINE_POLE_ITEM.get());
        }).build());

    // MODクラスのコンストラクタは、MODがロードされたときに最初に実行されるらしい
    // FMLはIEventBusやModContainerのようないくつかの引数の型を認識し、自動的に渡すらしい
    public PolygonTrainMod(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        // Modローディング時に行われるセットアップのメソッドを登録する
        // メソッド参照で、そのメソッドの引数のイベントの型が渡される
        // modEventBus.addListener(this::commonSetup);

        // Dist.CLIENTをチェックして、クライアントサイドでのみ実行されるようにする
        if (dist == Dist.CLIENT) {
            // Entityのレンダラーの描画イベントを購読するメソッド
            modEventBus.addListener(PolygonTrainModRenderers::registerEntityRenderers);
        }

        // DeferredRegisterをNeoForgeのMODEventButに登録し、DeferredRegisterに登録された各ブロックがゲームに登録されるようにする
        PolygonTrainModBlocks.BLOCKS.register(modEventBus);
        PolygonTrainModItems.ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        PolygonTrainModComponents.REGISTRAR.register(modEventBus);
        PolygonTrainModEntities.ENTITY_TYPES.register(modEventBus);

        // 利用したいサーバー、あるいはその他のゲームイベントに登録する
        // これは、このPolygonTrainModクラスがEventに直接反応することを望む場合にのみ必要であることに注意するらしい
        // 以下のonServerStarting()のような@SubscribeEventアノテーション関数がこのクラスにない場合は、この行を追加してはならないらしい
        // NeoForge.EVENT_BUS.register(this);

        // アイテムをクリエイティブタブに登録する
        // modEventBus.addListener(this::addCreative);

        // modのModConfigSpecを登録し、FMLがConfigファイルを作成して読み込むようにする
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    // これがFMLInitializationEventに近いらしい
    // private void commonSetup(FMLCommonSetupEvent event) {
        // 一般的なセットアップコード
        // LOGGER.info("HELLO FROM COMMON SETUP");

        // if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
        //     LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        // }

        // LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        // Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    // }

    // 既存のクリエイティブタブにブロックを追加する例
    // 建築ブロックタブにSAMPLE_BLOCKを追加する
    // 好きなEventを購読し、その際の処理を追加できるらしい
    // 購読できるEventは、net.neoforged.neoforge.eventにあるっぽい
    // private void addCreative(BuildCreativeModeTabContentsEvent event) {
    //     if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
    //         event.accept(EXAMPLE_BLOCK_ITEM);
    //     }
    // }

    // SubscribeEventを使用し、EventBusに呼び出すメソッドを発見させることができるらしい
    // @SubscribeEvent
    // public void onServerStarting(ServerStartingEvent event) {
    // サーバー起動時に何かする
    //     LOGGER.info("HELLO from server starting");
    // }
}
