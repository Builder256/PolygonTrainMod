package com.portofino.polygontrainmod.vehicle;

import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class VehicleDefinition {
    public record BogieDefinition(String modelFile, Map<String, String> textureOverrides, Vec3 position) {
    }

    public record DoorAnimationDefinition(List<String> objects, Vec3 closedPosition, Vec3 openTranslation) {
    }

    private final String id;
    private final String displayName;
    private final String packName;
    private final String modelFile;
    private final Map<String, String> textureOverrides;
    private final Vec3 modelOffset;
    private final float modelScale;
    private final List<BogieDefinition> bogies;
    private final List<Vec3> seatPositions;
    private final List<Vec3> playerPositions;
    private final Vec3 seatOffset;
    private final String scriptPath;
    private final String doorType;
    private final float trainDistance;
    private final int driverSeatIndex;
    private final int frontDriverSeatIndex;
    private final int rearDriverSeatIndex;
    private final List<DoorAnimationDefinition> leftDoors;
    private final List<DoorAnimationDefinition> rightDoors;
    private final List<Float> notchMaxSpeeds;
    private final float acceleration;
    private final boolean smoothing;

    public VehicleDefinition(
        String id,
        String displayName,
        String packName,
        String modelFile,
        Map<String, String> textureOverrides,
        Vec3 modelOffset,
        float modelScale,
        List<BogieDefinition> bogies,
        List<Vec3> seatPositions,
        List<Vec3> playerPositions,
        Vec3 seatOffset,
        String scriptPath,
        String doorType,
        float trainDistance,
        int driverSeatIndex,
        int frontDriverSeatIndex,
        int rearDriverSeatIndex,
        List<DoorAnimationDefinition> leftDoors,
        List<DoorAnimationDefinition> rightDoors,
        List<Float> notchMaxSpeeds,
        float acceleration,
        boolean smoothing
    ) {
        this.id = id;
        this.displayName = displayName;
        this.packName = packName;
        this.modelFile = modelFile;
        this.textureOverrides = textureOverrides == null ? Map.of() : Map.copyOf(textureOverrides);
        this.modelOffset = modelOffset == null ? Vec3.ZERO : modelOffset;
        this.modelScale = modelScale <= 0 ? 1.0F : modelScale;
        this.bogies = bogies == null ? List.of() : List.copyOf(bogies);
        this.seatPositions = seatPositions == null ? List.of() : List.copyOf(seatPositions);
        this.playerPositions = playerPositions == null ? List.of() : List.copyOf(playerPositions);
        this.seatOffset = seatOffset;
        this.scriptPath = scriptPath == null ? "" : scriptPath;
        this.doorType = doorType == null ? "manual" : doorType;
        this.trainDistance = trainDistance > 0 ? trainDistance : 4.5F;
        this.driverSeatIndex = driverSeatIndex;
        this.frontDriverSeatIndex = frontDriverSeatIndex;
        this.rearDriverSeatIndex = rearDriverSeatIndex;
        this.leftDoors = leftDoors == null ? List.of() : List.copyOf(leftDoors);
        this.rightDoors = rightDoors == null ? List.of() : List.copyOf(rightDoors);
        this.notchMaxSpeeds = notchMaxSpeeds == null ? List.of() : List.copyOf(notchMaxSpeeds);
        this.acceleration = acceleration > 0 ? acceleration : 0.00243F;
        this.smoothing = smoothing;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getPackName() { return packName; }
    public String getModelFile() { return modelFile; }
    public Map<String, String> getTextureOverrides() { return textureOverrides; }
    public Vec3 getModelOffset() { return modelOffset; }
    public float getModelScale() { return modelScale; }
    public List<BogieDefinition> getBogies() { return bogies; }
    public List<Vec3> getBogiePositions() { 
        List<Vec3> positions = new ArrayList<>();
        for (BogieDefinition bogie : bogies) {
            positions.add(bogie.position);
        }
        return positions;
    }
    public List<Vec3> getSeatPositions() { return seatPositions; }
    public List<Vec3> getPlayerPositions() { return playerPositions; }

    public List<Vec3> getAllSeatPositions() {
        if (playerPositions.isEmpty()) {
            return seatPositions;
        }
        if (seatPositions.isEmpty()) {
            return playerPositions;
        }
        List<Vec3> seats = new ArrayList<>(playerPositions.size() + seatPositions.size());
        seats.addAll(playerPositions);
        seats.addAll(seatPositions);
        return List.copyOf(seats);
    }

    public boolean hasSeatOffset() {
        return seatOffset != null;
    }

    public Vec3 getSeatOffset() {
        return seatOffset == null ? Vec3.ZERO : seatOffset;
    }

    public boolean hasScript() {
        return scriptPath != null && !scriptPath.isBlank();
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public String getDoorType() {
        return doorType;
    }

    public boolean hasAutomaticDoor() {
        return "automatic".equalsIgnoreCase(doorType) || "auto".equalsIgnoreCase(doorType);
    }

    public float getTrainDistance() {
        return trainDistance;
    }

    public int getDriverSeatIndex() {
        return driverSeatIndex;
    }

    public int getFrontDriverSeatIndex() {
        return frontDriverSeatIndex;
    }

    public int getRearDriverSeatIndex() {
        return rearDriverSeatIndex;
    }

    public List<DoorAnimationDefinition> getLeftDoors() {
        return leftDoors;
    }

    public List<DoorAnimationDefinition> getRightDoors() {
        return rightDoors;
    }

    public boolean hasDoorAnimations() {
        return !leftDoors.isEmpty() || !rightDoors.isEmpty();
    }

    public List<Float> getNotchMaxSpeeds() {
        return notchMaxSpeeds;
    }

    public float getAcceleration() {
        return acceleration;
    }

    public boolean isSmoothing() {
        return smoothing;
    }
}
