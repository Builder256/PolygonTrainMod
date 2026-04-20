package com.portofino.polygontrainmod.formation;

import com.portofino.polygontrainmod.PolygonTrainModComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;

public class TrainFormationData {
    public static final String TAG_VEHICLES = "vehicles";
    public static final String TAG_NAME = "name";
    
    public static TrainFormation getFormation(ItemStack stack) {
        CompoundTag tag = stack.get(PolygonTrainModComponents.TRAIN_FORMATION.get());
        if (tag == null) {
            return null;
        }
        
        TrainFormation formation = new TrainFormation();
        formation.setName(tag.getString(TAG_NAME));
        
        ListTag vehiclesList = tag.getList(TAG_VEHICLES, 8); // 8 = String
        for (int i = 0; i < vehiclesList.size(); i++) {
            String vehicleId = vehiclesList.getString(i);
            if (!vehicleId.isEmpty()) {
                formation.addVehicle(vehicleId);
            }
        }
        
        return formation;
    }
    
    public static void setFormation(ItemStack stack, TrainFormation formation) {
        if (formation == null || formation.isEmpty()) {
            stack.set(PolygonTrainModComponents.TRAIN_FORMATION.get(), null);
            return;
        }
        
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_NAME, formation.getName());
        
        ListTag vehiclesList = new ListTag();
        for (String vehicleId : formation.getAllVehicles()) {
            vehiclesList.add(StringTag.valueOf(vehicleId));
        }
        tag.put(TAG_VEHICLES, vehiclesList);
        
        stack.set(PolygonTrainModComponents.TRAIN_FORMATION.get(), tag);
    }
    
    public static boolean hasFormation(ItemStack stack) {
        return stack.get(PolygonTrainModComponents.TRAIN_FORMATION.get()) != null;
    }
    
    public static void clearFormation(ItemStack stack) {
        stack.set(PolygonTrainModComponents.TRAIN_FORMATION.get(), null);
    }
}
