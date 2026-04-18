package com.portofino.polygontrainmod;

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
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(PolygonTrainMod.MODID)
public class PolygonTrainMod {
    public static final String MODID = "polygontrainmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
        CREATIVE_MODE_TABS.register("main_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.polygontrainmod"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> PolygonTrainModItems.RAIL_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(PolygonTrainModItems.TRAIN_VEHICLE_ITEM.get());
                output.accept(PolygonTrainModItems.RAIL_ITEM.get());
                output.accept(PolygonTrainModItems.CROWBAR_ITEM.get());
                output.accept(PolygonTrainModItems.TEST_AUTOMATIC_TICKET_GATE_ITEM.get());
                output.accept(PolygonTrainModItems.OVERHEAD_LINE_POLE_ITEM.get());
                output.accept(PolygonTrainModItems.TICKET_ITEM.get());
                output.accept(PolygonTrainModItems.COUPON_TICKET_ITEM.get());
                output.accept(PolygonTrainModItems.IC_CARD_TICKET_ITEM.get());
                output.accept(PolygonTrainModItems.MARKER_ITEM.get());
                output.accept(PolygonTrainModItems.MARKER_SWITCH_ITEM.get());
            }).build());

    public PolygonTrainMod(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerNetwork);

        PolygonTrainModBlocks.BLOCKS.register(modEventBus);
        PolygonTrainModItems.ITEMS.register(modEventBus);
        PolygonTrainModEntities.ENTITIES.register(modEventBus);
        com.portofino.polygontrainmod.registry.PolygonTrainModEntities.ENTITY_TYPES.register(modEventBus);
        PolygonTrainModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        PolygonTrainModComponents.REGISTRAR.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.portofino.polygontrainmod.rail.RailPackLoader.load();
            com.portofino.polygontrainmod.vehicle.VehiclePackLoader.load();
            com.portofino.polygontrainmod.script.TrainScriptSystem.getInstance().initialize();
        });
    }

    private void registerNetwork(RegisterPayloadHandlersEvent event) {
        com.portofino.polygontrainmod.network.PolygonTrainModNetwork.registerPayloadHandlers(event);
    }
}
