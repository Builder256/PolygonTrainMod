package com.portofino.polygontrainmod;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// このクラスは専用サーバーではロードされません。ここからクライアント側のコードにアクセスしても安全です。
@Mod(value = PolygonTrainMod.MODID, dist = Dist.CLIENT)
// EventBusSubscriber を使用すると、@SubscribeEvent でアノテーションされたクラス内のすべての静的メソッドを自動的に登録できます。
@EventBusSubscriber(modid = PolygonTrainMod.MODID, value = Dist.CLIENT)
public class PolygonTrainModClient {
    public PolygonTrainModClient(ModContainer container) {
        // NeoForgeがこのMODのコンフィグ画面を作成できるようにします。
        // コンフィグ画面は、Mods画面＞自分のModをクリック＞コンフィグをクリックで表示されます。
        // 設定オプションの翻訳をen_us.jsonファイルに追加することを忘れないでください。
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // クライアントのセットアップ・コード
        PolygonTrainMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        PolygonTrainMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
