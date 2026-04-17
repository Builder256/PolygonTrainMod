package com.portofino.polygontrainmod;

import com.portofino.polygontrainmod.client.renderer.RailCoreBlockEntityRenderer;
import com.portofino.polygontrainmod.client.renderer.TrainEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = PolygonTrainMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PolygonTrainModClientModEvents {
    private PolygonTrainModClientModEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // レールコアのブロックエンティティレンダラーを登録（MQOモデル描画）
        event.registerBlockEntityRenderer(
            PolygonTrainModBlockEntities.LARGE_RAIL_CORE.get(),
            RailCoreBlockEntityRenderer::new
        );
        if (PolygonTrainModEntities.TRAIN.isBound()) {
            event.registerEntityRenderer(
                PolygonTrainModEntities.TRAIN.get(),
                TrainEntityRenderer::new
            );
        }
    }

    /** RTM と同じく tintindex=0 で色を差し替える。通常マーカー=赤、分岐マーカー=青。 */
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        // 通常マーカー: 赤 (0xFF0000)
        event.register(
            (state, tintGetter, pos, tintIndex) -> tintIndex == 0 ? 0xFF0000 : 0xFFFFFF,
            PolygonTrainModBlocks.MARKER.get()
        );
        // 分岐マーカー: 青 (0x0000FF)
        event.register(
            (state, tintGetter, pos, tintIndex) -> tintIndex == 0 ? 0x0000FF : 0xFFFFFF,
            PolygonTrainModBlocks.MARKER_SWITCH.get()
        );
    }

    /** アイテムのティントも同様に設定する。 */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // 通常マーカーアイテム: 赤
        event.register(
            (stack, tintIndex) -> tintIndex == 0 ? 0xFF0000 : 0xFFFFFF,
            PolygonTrainModItems.MARKER_ITEM.get()
        );
        // 分岐マーカーアイテム: 青
        event.register(
            (stack, tintIndex) -> tintIndex == 0 ? 0x0000FF : 0xFFFFFF,
            PolygonTrainModItems.MARKER_SWITCH_ITEM.get()
        );
    }

}
