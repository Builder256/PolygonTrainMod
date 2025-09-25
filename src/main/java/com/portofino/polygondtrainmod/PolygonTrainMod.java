package com.portofino.polygondtrainmod;

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
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// この値はMETA-INF/neoforge.mods.tomlファイルの入力と一致する必要があります。
@Mod(PolygonTrainMod.MODID)
public class PolygonTrainMod {
    // すべての人が参照できるように、共通の場所にmodのidを定義する。
    public static final String MODID = "polygontrainmod";
    // slf4j ロガーを直接参照する
    public static final Logger LOGGER = LogUtils.getLogger();
    // CreativeModeTabsを保持するDeferred Registerを作成し、すべて "polygontrainmod "ネームスペースに登録する。
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // 名前空間とパスを組み合わせて、id "polygontrainmod:example_block "で新しいブロックを作成します。
//    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // 名前空間とパスを組み合わせて、id "polygontrainmod:example_block "の新しいBlockItemを作成します。
//    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    /* ~~~~~~~~~~ 以下DeferredHolderへの登録 ~~~~~~~~~~ */

    // 戦闘タブの後に配置される、idが"polygontrainmod:example_tab"のクリエイティブタブを登録
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.polygontrainmod")) //CreativeModeTabのタイトルの言語キー
            .withTabsBefore(CreativeModeTabs.COMBAT)
            // タブのアイコンを切符アイテムに設定
            .icon(() -> PolygonTrainModItems.TICKET.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(PolygonTrainModItems.EXAMPLE_ITEM.get()); // サンプルアイテムをタブに追加します。独自のタブの場合は、イベントよりもこの方法が推奨されます。

                // 通常の乗車券をインベントリに追加
                output.accept(PolygonTrainModItems.TICKET.get());
                // 回数乗車券をインベントリに追加
                output.accept(PolygonTrainModItems.COUPON_TICKET.get());
                // インベントリに作成したブロックアイテムを追加する、ブロックを直接登録しても自動でブロックアイテムを探索してやってくれるらしい
                output.accept(PolygonTrainModItems.TEST_AUTOMATIC_TICKET_GATE_ITEM.get());
            }).build());

    // MODクラスのコンストラクタは、MODがロードされたときに最初に実行されるコードです。
    // FML は IEventBus や ModContainer のようないくつかのパラメータタイプを認識し、 自動的に渡してくれます。
    public PolygonTrainMod(IEventBus modEventBus, ModContainer modContainer) {
        // modloading用のcommonSetupメソッドを登録する。
        modEventBus.addListener(this::commonSetup);

        // DeferredレジスタをMODイベントバスに登録し、ブロックが登録されるようにする
        PolygonTrainModBlocks.BLOCKS.register(modEventBus);
        // MODイベントバスにDeferred Registerを登録し、アイテムが登録されるようにする。
        PolygonTrainModItems.ITEMS.register(modEventBus);
        // MODイベントバスにDeferred Registerを登録し、タブが登録されるようにする。
        CREATIVE_MODE_TABS.register(modEventBus);
        // MODイベントバスに
        PolygonTrainModComponents.REGISTRAR.register(modEventBus);

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
