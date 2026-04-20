package com.portofino.polygontrainmod.blockentity;

import com.portofino.polygontrainmod.PolygonTrainModBlockEntities;
import com.portofino.polygontrainmod.installedobject.InstalledObjectCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class InstalledObjectBlockEntity extends BlockEntity {
    private String definitionId = "";
    private String category = InstalledObjectCategory.LIGHT.name();
    private float yaw;
    private BlockPos wireStart;
    private BlockPos wireEnd;
    private boolean powered;
    private int barMoveCount;
    private int lightCount = -1;
    private int tickCountOnActive;

    public InstalledObjectBlockEntity(BlockPos pos, BlockState blockState) {
        super(PolygonTrainModBlockEntities.INSTALLED_OBJECT.get(), pos, blockState);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("DefinitionId", definitionId);
        tag.putString("Category", category);
        tag.putFloat("Yaw", yaw);
        if (wireStart != null) {
            tag.putInt("WireStartX", wireStart.getX());
            tag.putInt("WireStartY", wireStart.getY());
            tag.putInt("WireStartZ", wireStart.getZ());
        }
        if (wireEnd != null) {
            tag.putInt("WireEndX", wireEnd.getX());
            tag.putInt("WireEndY", wireEnd.getY());
            tag.putInt("WireEndZ", wireEnd.getZ());
        }
        tag.putBoolean("Powered", powered);
        tag.putInt("BarMoveCount", barMoveCount);
        tag.putInt("LightCount", lightCount);
        tag.putInt("TickCountOnActive", tickCountOnActive);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        definitionId = tag.getString("DefinitionId");
        category = tag.contains("Category") ? tag.getString("Category") : InstalledObjectCategory.LIGHT.name();
        yaw = tag.getFloat("Yaw");
        wireStart = tag.contains("WireStartX") ? new BlockPos(tag.getInt("WireStartX"), tag.getInt("WireStartY"), tag.getInt("WireStartZ")) : null;
        wireEnd = tag.contains("WireEndX") ? new BlockPos(tag.getInt("WireEndX"), tag.getInt("WireEndY"), tag.getInt("WireEndZ")) : null;
        powered = tag.getBoolean("Powered");
        barMoveCount = tag.getInt("BarMoveCount");
        lightCount = tag.contains("LightCount") ? tag.getInt("LightCount") : -1;
        tickCountOnActive = tag.getInt("TickCountOnActive");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    public void setDefinition(String definitionId, InstalledObjectCategory category, float yaw) {
        this.definitionId = definitionId == null ? "" : definitionId;
        this.category = category == null ? InstalledObjectCategory.LIGHT.name() : category.name();
        this.yaw = yaw;
        setChanged();
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public InstalledObjectCategory getCategory() {
        try {
            return InstalledObjectCategory.valueOf(category);
        } catch (Exception e) {
            return InstalledObjectCategory.LIGHT;
        }
    }

    public float getYaw() {
        return yaw;
    }

    public void setWireEndpoints(BlockPos start, BlockPos end) {
        this.wireStart = start;
        this.wireEnd = end;
        setChanged();
    }

    public BlockPos getWireStart() {
        return wireStart;
    }

    public BlockPos getWireEnd() {
        return wireEnd;
    }

    public Vec3 getRenderCenter() {
        return Vec3.atCenterOf(getBlockPos());
    }

    public void setPowered(boolean powered) {
        if (this.powered != powered) {
            this.powered = powered;
            setChanged();
        }
    }

    public boolean isPowered() {
        return powered;
    }

    public int getBarMoveCount() {
        return barMoveCount;
    }

    public int getLightCount() {
        return lightCount;
    }

    public String getModelName() {
        int index = definitionId.lastIndexOf(':');
        return index >= 0 ? definitionId.substring(index + 1) : definitionId;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, InstalledObjectBlockEntity be) {
        if (level.isClientSide || be.getCategory() != InstalledObjectCategory.CROSSING) {
            return;
        }
        boolean changed = false;
        if (be.powered) {
            if (be.barMoveCount < 90) {
                be.barMoveCount++;
                changed = true;
            }
            be.tickCountOnActive = (be.tickCountOnActive + 1) % 360;
            int previousLight = be.lightCount;
            if (be.lightCount < 0) {
                be.lightCount = 0;
            } else if (be.tickCountOnActive % 10 == 0) {
                be.lightCount = (be.lightCount + 1) % 2;
            }
            changed |= previousLight != be.lightCount;
        } else {
            if (be.barMoveCount > 0) {
                be.barMoveCount--;
                changed = true;
            }
            if (be.tickCountOnActive != 0 || be.lightCount != -1) {
                be.tickCountOnActive = 0;
                be.lightCount = -1;
                changed = true;
            }
        }
        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }
}
