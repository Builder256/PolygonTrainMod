package com.portofino.polygontrainmod;

import com.portofino.polygontrainmod.client.renderer.PolygonTrainModRenderers;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import static com.portofino.polygontrainmod.PolygonTrainMod.LOGGER;

// Modアノテーションに、dist = Dist.CLIENTを指定しているため、このクラスはクライアントサイドにのみ存在するようになるらしい
@Mod(value = PolygonTrainMod.MODID, dist = Dist.CLIENT)
// EventBusSubscriberを使用すると、@SubscribeEventアノテーションのあるこのクラス内のすべての静的メソッドが、
// Modコンストラクタで登録せずとも自動的に登録されるらしい
@EventBusSubscriber(modid = PolygonTrainMod.MODID, value = Dist.CLIENT)
public class PolygonTrainModClient {
    public PolygonTrainModClient(IEventBus modEventBus, ModContainer container) {
        // NeoForgeがこのMODのコンフィグ画面を作成できるようにします。
        // コンフィグ画面は、Mods画面＞自分のModをクリック＞コンフィグをクリックで表示されます。
        // 設定オプションの翻訳をen_us.jsonファイルに追加することを忘れないでください。
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // Entityのレンダラを登録
        modEventBus.addListener(PolygonTrainModRenderers::registerEntityRenderers);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // クライアントのセットアップ・コード
        LOGGER.info("HELLO FROM CLIENT SETUP");
        LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
