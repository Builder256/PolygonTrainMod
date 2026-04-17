package com.portofino.polygontrainmod;

import com.portofino.polygontrainmod.item.CrowbarItem;
import com.portofino.polygontrainmod.item.MarkerItem;
import com.portofino.polygontrainmod.item.RailItem;
import com.portofino.polygontrainmod.item.TrainItem;
import com.portofino.polygontrainmod.item.TrainVehicleItem;
import com.portofino.polygontrainmod.item.ticket.CouponTicketItem;
import com.portofino.polygontrainmod.item.ticket.ICCardTicketItem;
import com.portofino.polygontrainmod.item.ticket.TicketItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PolygonTrainModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PolygonTrainMod.MODID);

    public static final DeferredItem<BlockItem> TEST_AUTOMATIC_TICKET_GATE_ITEM = ITEMS.registerSimpleBlockItem(
        "test_automatic_ticket_gate", PolygonTrainModBlocks.TEST_AUTOMATIC_TICKET_GATE
    );
    public static final DeferredItem<BlockItem> OVERHEAD_LINE_POLE_ITEM = ITEMS.registerSimpleBlockItem(
        "overhead_line_pole", PolygonTrainModBlocks.OVERHEAD_LINE_POLE
    );
    public static final DeferredItem<TicketItem> TICKET_ITEM = ITEMS.register(
        "ticket", () -> new TicketItem(new Item.Properties())
    );
    public static final DeferredItem<CouponTicketItem> COUPON_TICKET_ITEM = ITEMS.register(
        "coupon_ticket", CouponTicketItem::new
    );
    public static final DeferredItem<ICCardTicketItem> IC_CARD_TICKET_ITEM = ITEMS.register(
        "ic_card_ticket", ICCardTicketItem::new
    );
    public static final DeferredItem<MarkerItem> MARKER_ITEM = ITEMS.register(
        "marker", () -> new MarkerItem(PolygonTrainModBlocks.MARKER.get())
    );
    public static final DeferredItem<MarkerItem> MARKER_SWITCH_ITEM = ITEMS.register(
        "marker_switch", () -> new MarkerItem(PolygonTrainModBlocks.MARKER_SWITCH.get())
    );
    public static final DeferredItem<RailItem> RAIL_ITEM = ITEMS.register(
        "rail", RailItem::new
    );
    public static final DeferredItem<TrainItem> TRAIN_ITEM = ITEMS.register(
        "train", TrainItem::new
    );
    public static final DeferredItem<TrainVehicleItem> TRAIN_VEHICLE_ITEM = ITEMS.register(
        "train_vehicle", TrainVehicleItem::new
    );
    public static final DeferredItem<CrowbarItem> CROWBAR_ITEM = ITEMS.register(
        "crowbar", CrowbarItem::new
    );
}
