package com.portofino.polygontrainmod.blockentity;

import com.portofino.polygontrainmod.PolygonTrainModBlockEntities;
import com.portofino.polygontrainmod.rail.util.RailMap;
import com.portofino.polygontrainmod.rail.util.RailMapBasic;
import com.portofino.polygontrainmod.rail.util.RailPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LargeRailCoreBlockEntity extends BlockEntity {
    private RailPosition[] railPositions;
    private RailMap railMap;
    private String railDefinitionId = "";

    public LargeRailCoreBlockEntity(BlockPos pos, BlockState blockState) {
        super(PolygonTrainModBlockEntities.LARGE_RAIL_CORE.get(), pos, blockState);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (railPositions != null && railPositions.length >= 2) {
            ListTag list = new ListTag();
            for (int i = 0; i + 1 < railPositions.length; i += 2) {
                RailPosition start = railPositions[i];
                RailPosition end = railPositions[i + 1];
                if (start == null || end == null) continue;
                CompoundTag segment = new CompoundTag();
                segment.put("StartRP", start.writeToNBT());
                segment.put("EndRP", end.writeToNBT());
                list.add(segment);
            }
            tag.put("RailSegments", list);
        }
        tag.putString("RailDefinitionId", railDefinitionId == null ? "" : railDefinitionId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("RailSegments")) {
            ListTag list = tag.getList("RailSegments", 10);
            if (!list.isEmpty()) {
                railPositions = new RailPosition[list.size() * 2];
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag segment = list.getCompound(i);
                    railPositions[i * 2] = RailPosition.readFromNBT(segment.getCompound("StartRP"));
                    railPositions[i * 2 + 1] = RailPosition.readFromNBT(segment.getCompound("EndRP"));
                }
                createRailMap();
            }
        } else if (tag.contains("StartRP") && tag.contains("EndRP")) {
            railPositions = new RailPosition[2];
            railPositions[0] = RailPosition.readFromNBT(tag.getCompound("StartRP"));
            railPositions[1] = RailPosition.readFromNBT(tag.getCompound("EndRP"));
            createRailMap();
        }
        this.railDefinitionId = tag.getString("RailDefinitionId");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void setRailPositions(RailPosition[] positions) {
        this.railPositions = positions == null ? null : positions.clone();
        this.railMap = null;
    }

    public RailPosition[] getRailPositions() {
        return this.railPositions == null ? new RailPosition[0] : this.railPositions.clone();
    }

    public void createRailMap() {
        if (railPositions != null && railPositions.length >= 2
            && railPositions[0] != null && railPositions[1] != null) {
            railMap = new RailMapBasic(railPositions[0], railPositions[1]);
        }
    }

    public RailMap getRailMap() {
        return railMap;
    }

    public RailMap[] getAllRailMaps() {
        if (railPositions == null || railPositions.length < 2) {
            return new RailMap[0];
        }
        int count = railPositions.length / 2;
        RailMap[] maps = new RailMap[count];
        for (int i = 0; i < count; i++) {
            maps[i] = new RailMapBasic(railPositions[i * 2], railPositions[i * 2 + 1]);
        }
        return maps;
    }

    public void setRailDefinitionId(String railDefinitionId) {
        this.railDefinitionId = railDefinitionId == null ? "" : railDefinitionId;
        this.setChanged();
    }

    public String getRailDefinitionId() {
        return railDefinitionId;
    }

    public boolean isLoaded() {
        return railPositions != null && railPositions.length >= 2 && railMap != null;
    }
}
