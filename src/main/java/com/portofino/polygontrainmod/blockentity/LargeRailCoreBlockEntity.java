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
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LargeRailCoreBlockEntity extends BlockEntity {
    private RailPosition[] railPositions;
    private RailMap railMap;
    private String railDefinitionId = "";
    private int activeSegmentIndex;
    private int previousSegmentIndex;
    private float switchProgress = 1.0F;
    private int lastSignalStrength = -1;

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
        tag.putInt("ActiveSegmentIndex", activeSegmentIndex);
        tag.putInt("PreviousSegmentIndex", previousSegmentIndex);
        tag.putFloat("SwitchProgress", switchProgress);
        tag.putInt("LastSignalStrength", lastSignalStrength);
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
        this.activeSegmentIndex = tag.getInt("ActiveSegmentIndex");
        this.previousSegmentIndex = tag.getInt("PreviousSegmentIndex");
        this.switchProgress = tag.contains("SwitchProgress") ? tag.getFloat("SwitchProgress") : 1.0F;
        this.lastSignalStrength = tag.contains("LastSignalStrength") ? tag.getInt("LastSignalStrength") : -1;
        clampActiveSegment();
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
        clampActiveSegment();
    }

    public RailPosition[] getRailPositions() {
        return this.railPositions == null ? new RailPosition[0] : this.railPositions.clone();
    }

    public void createRailMap() {
        if (railPositions != null && railPositions.length >= 2
            && railPositions[0] != null && railPositions[1] != null) {
            railMap = new RailMapBasic(railPositions[0], railPositions[1]);
        }
        clampActiveSegment();
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

    /**
     * Returns the rail maps that should be used by moving trains.
     */
    public RailMap[] getActiveRailMaps() {
        RailMap[] maps = getAllRailMaps();
        if (maps.length <= 1) {
            return maps;
        }
        int index = Mth.clamp(activeSegmentIndex, 0, maps.length - 1);
        return new RailMap[]{maps[index]};
    }

    /**
     * Adds one branch segment to this core.
     */
    public void appendRailSegment(RailPosition start, RailPosition end) {
        if (start == null || end == null) {
            return;
        }
        int oldLength = railPositions == null ? 0 : railPositions.length;
        RailPosition[] next = new RailPosition[oldLength + 2];
        if (railPositions != null) {
            System.arraycopy(railPositions, 0, next, 0, railPositions.length);
        }
        next[oldLength] = RailPosition.readFromNBT(start.writeToNBT());
        next[oldLength + 1] = RailPosition.readFromNBT(end.writeToNBT());
        setRailPositions(next);
        createRailMap();
        setChanged();
    }

    /**
     * Returns the first point stored in this core.
     */
    public RailPosition getFirstRailPosition() {
        if (railPositions == null || railPositions.length == 0 || railPositions[0] == null) {
            return null;
        }
        return RailPosition.readFromNBT(railPositions[0].writeToNBT());
    }

    /**
     * Updates the active branch from redstone strength.
     */
    public void updateSignalStrength(int signalStrength) {
        int segmentCount = getSegmentCount();
        int nextIndex = segmentCount <= 1 || signalStrength <= 0
            ? 0
            : Mth.clamp(signalStrength, 0, segmentCount - 1);
        lastSignalStrength = signalStrength;
        if (nextIndex != activeSegmentIndex) {
            previousSegmentIndex = activeSegmentIndex;
            activeSegmentIndex = nextIndex;
            switchProgress = 0.0F;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    /**
     * Returns the currently selected branch index.
     */
    public int getActiveSegmentIndex() {
        return activeSegmentIndex;
    }

    /**
     * Returns the previous branch index used during client-side animation.
     */
    public int getPreviousSegmentIndex() {
        return previousSegmentIndex;
    }

    /**
     * Returns branch animation progress.
     */
    public float getSwitchProgress(float partialTick) {
        return Mth.clamp(switchProgress + partialTick * 0.15F, 0.0F, 1.0F);
    }

    /**
     * Ticks redstone state and branch animation.
     */
    public static void tick(Level level, BlockPos pos, BlockState state, LargeRailCoreBlockEntity be) {
        if (!level.isClientSide()) {
            int signal = level.getBestNeighborSignal(pos);
            if (signal != be.lastSignalStrength) {
                be.updateSignalStrength(signal);
            }
        }
        if (be.switchProgress < 1.0F) {
            be.switchProgress = Math.min(1.0F, be.switchProgress + 0.15F);
            if (level.isClientSide()) {
                be.requestModelDataUpdate();
            } else {
                be.setChanged();
            }
        }
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

    private int getSegmentCount() {
        return railPositions == null ? 0 : railPositions.length / 2;
    }

    private void clampActiveSegment() {
        int max = Math.max(0, getSegmentCount() - 1);
        activeSegmentIndex = Mth.clamp(activeSegmentIndex, 0, max);
        previousSegmentIndex = Mth.clamp(previousSegmentIndex, 0, max);
    }
}
