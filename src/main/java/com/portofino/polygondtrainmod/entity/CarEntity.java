package com.portofino.polygondtrainmod.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class CarEntity extends Entity {
    public CarEntity(EntityType<? extends CarEntity> entityType, Level level) {
        super(entityType, level);
    }

    protected void readAdditionalSaveData(CompoundTag compound) {

    }

    protected void addAdditionalSaveData(CompoundTag compound) {
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public void tick(){

    }

}