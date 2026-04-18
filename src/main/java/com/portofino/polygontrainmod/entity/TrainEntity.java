package com.portofino.polygontrainmod.entity;

import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.PolygonTrainModItems;
import com.portofino.polygontrainmod.PolygonTrainModEntities;
import com.portofino.polygontrainmod.block.LargeRailCoreBlock;
import com.portofino.polygontrainmod.block.RailCollisionBlock;
import com.portofino.polygontrainmod.blockentity.LargeRailCoreBlockEntity;
import com.portofino.polygontrainmod.blockentity.RailCollisionBlockEntity;
import com.portofino.polygontrainmod.rail.util.RailMap;
import com.portofino.polygontrainmod.script.TrainScriptSystem;
import com.portofino.polygontrainmod.vehicle.VehicleDefinition;
import com.portofino.polygontrainmod.vehicle.VehicleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import javax.script.ScriptEngine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class TrainEntity extends Entity {

    private static final EntityDataAccessor<String> VEHICLE_ID =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> SPEED =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TRAIN_DISTANCE =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> NOTCH =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HEADLIGHT_ON =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DOOR_OPEN =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DOOR_LEFT_OPEN =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DOOR_RIGHT_OPEN =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LIGHT_MODE =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PANTOGRAPH_UP =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> REVERSE =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> REVERSER =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DESTINATION_INDEX =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SOUND_INDEX =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CUSTOM_BUTTON_BITS =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RAIL_PROGRESS =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> SEAT_ASSIGNMENTS =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.STRING);

    private static final float ACCEL = 0.02f;
    private static final float BRAKE = 0.03f; // 減速度
    private static final float MAX_SPEED = 2.0f;
    private static final float FRICTION = 0.995f;
    private static final float DRAG_BASE = 0.0025f;
    private static final float DRAG_SPEED_FACTOR = 0.0015f;
    private static final int MAX_POWER_NOTCH = 5;
    private static final int MAX_BRAKE_NOTCH = 3;
    private static final float MOVEMENT_SMOOTHING = 0.22f;
    private static final double RAIL_SEARCH_RADIUS = 8.0D;
    private static final double BOGIE_CLICK_RADIUS_SQ = 4.0D;
    private static final double DEFAULT_HALF_WIDTH = 1.35D;
    private static final double DEFAULT_HALF_HEIGHT = 2.2D;
    private static final double TRAIN_BODY_MARGIN = 1.2D;
    // モデル原点より低い床下パーツがレールと干渉しない高さ。
    private static final double RAIL_HEIGHT_OFFSET = 1.08D;

    private final Map<UUID, Integer> seatAssignments = new HashMap<>();
    private UUID coupledFollowerUuid;
    private UUID coupledLeaderUuid;
    private ScriptEngine scriptEngine;
    public final WorldCompat field_70170_p = new WorldCompat(this);
    public int field_70173_aa;
    public float field_70177_z;
    private RailMap activeRailMap;
    private int activeRailSplit;
    private int activeRailIndex = -1;
    private int activeRailDirection = 1;
    private int activeRailBodyDirection = 1;
    private double activeRailPosition = -1.0D;
    private double railDistanceCarry;
    private RailAnchor frontRailAnchor;
    private RailAnchor rearRailAnchor;
    private int clientLerpSteps;
    private double clientLerpX;
    private double clientLerpY;
    private double clientLerpZ;
    private double clientLerpYRot;
    private double clientLerpXRot;
    public float doorMoveL;
    public float doorMoveR;
    public float pantograph_F = 40.0F;
    public float pantograph_B = 40.0F;
    public float seatRotation;
    private static final Map<UUID, CouplingSelection> COUPLING_MODE = new HashMap<>();

    public TrainEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noCulling = true;
    }

    public static TrainEntity create(Level level, String vehicleId, double x, double y, double z, float yRot, float trainDistance) {
        TrainEntity e = PolygonTrainModEntities.TRAIN.get().create(level);
        if (e == null) return null;
        e.setVehicleId(vehicleId);
        e.setTrainDistance(trainDistance);
        // スポーン位置をレール基準で少し上げる（埋まり防止）
        e.moveTo(x, y + RAIL_HEIGHT_OFFSET, z, yRot, 0f);
        e.refreshDimensions();

        // スクリプトはMqoModelLoaderでロードされるため、ここではロードしない
        return e;
    }

    public void initializeOnRail(RailMap map, int split, int index) {
        if (map == null || split <= 0) {
            return;
        }
        activeRailMap = map;
        activeRailSplit = split;
        activeRailIndex = Mth.clamp(index, 0, split);
        activeRailPosition = activeRailIndex;
        activeRailDirection = 1;
        activeRailBodyDirection = 1;
        railDistanceCarry = 0.0D;
        setRailProgress(activeRailIndex / (float) activeRailSplit);

        RailSample requestedCenter = sampleRail(map, split, activeRailIndex);
        RailAnchorPair pair = findBestAnchorPairForCenter(map, split, activeRailIndex, requestedCenter);
        frontRailAnchor = pair.front();
        rearRailAnchor = pair.rear();
        RailSample front = pair.frontSample();
        RailSample rear = pair.rearSample();
        float yaw = getRailYawForBody(map, split, activeRailIndex, activeRailBodyDirection, getYRot());
        float pitch = getRailPitchForBody(map, split, activeRailIndex, activeRailBodyDirection);
        applyPoseFromBogieSamples(front, rear, yaw, pitch, true);
        setDeltaMovement(Vec3.ZERO);
        setSpeed(0.0F);
        setNotch(0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VEHICLE_ID, "");
        builder.define(SPEED, 0.0f);
        builder.define(TRAIN_DISTANCE, 4.5f);
        builder.define(NOTCH, 0);
        builder.define(HEADLIGHT_ON, false);
        builder.define(DOOR_OPEN, false);
        builder.define(DOOR_LEFT_OPEN, false);
        builder.define(DOOR_RIGHT_OPEN, false);
        builder.define(LIGHT_MODE, 0);
        builder.define(PANTOGRAPH_UP, true);
        builder.define(REVERSE, false);
        builder.define(REVERSER, 1);
        builder.define(DESTINATION_INDEX, 0);
        builder.define(SOUND_INDEX, 0);
        builder.define(CUSTOM_BUTTON_BITS, 0);
        builder.define(RAIL_PROGRESS, 0.0F);
        builder.define(SEAT_ASSIGNMENTS, "");
    }

    public String getVehicleId() { return entityData.get(VEHICLE_ID); }
    public void setVehicleId(String id) { entityData.set(VEHICLE_ID, id != null ? id : ""); }
    public float getSpeed() { return entityData.get(SPEED); }
    public void setSpeed(float speed) { entityData.set(SPEED, speed); }
    public float getTrainDistance() { return entityData.get(TRAIN_DISTANCE); }
    public void setTrainDistance(float distance) { entityData.set(TRAIN_DISTANCE, Math.max(2.5f, distance)); }
    public int getNotch() { return entityData.get(NOTCH); }
    public void setNotch(int notch) { entityData.set(NOTCH, Mth.clamp(notch, -8, 8)); }
    public boolean isHeadlightOn() { return entityData.get(HEADLIGHT_ON); }
    public void setHeadlightOn(boolean value) { setLightMode(value ? 1 : 0); }
    public int getLightMode() { return entityData.get(LIGHT_MODE); }
    public void setLightMode(int value) {
        int mode = Mth.clamp(value, 0, 3);
        entityData.set(LIGHT_MODE, mode);
        entityData.set(HEADLIGHT_ON, mode == 1 || mode == 3);
    }
    public void setLightModeForFormation(int value) {
        if (level().isClientSide()) {
            setLightMode(value);
            return;
        }
        forEachFormationTrain(train -> train.setLightMode(value));
    }
    public boolean isDoorOpen() { return entityData.get(DOOR_OPEN); }
    public boolean isDoorLeftOpen() { return entityData.get(DOOR_LEFT_OPEN); }
    public boolean isDoorRightOpen() { return entityData.get(DOOR_RIGHT_OPEN); }
    public void setDoorOpen(boolean value) {
        entityData.set(DOOR_OPEN, value);
        entityData.set(DOOR_LEFT_OPEN, value);
        entityData.set(DOOR_RIGHT_OPEN, value);
    }
    public void setDoorLeftOpen(boolean value) {
        entityData.set(DOOR_LEFT_OPEN, value);
        entityData.set(DOOR_OPEN, value || isDoorRightOpen());
    }
    public void setDoorRightOpen(boolean value) {
        entityData.set(DOOR_RIGHT_OPEN, value);
        entityData.set(DOOR_OPEN, value || isDoorLeftOpen());
    }
    public void toggleDoorForFormation() { setDoorOpenForFormation(!isDoorOpen()); }
    public void setDoorOpenForFormation(boolean value) {
        if (level().isClientSide()) {
            setDoorOpen(value);
            return;
        }
        forEachFormationTrain(train -> train.setDoorOpen(value));
    }
    public void toggleDoorSideForFormation(boolean left) {
        boolean next = left ? !isDoorLeftOpen() : !isDoorRightOpen();
        if (level().isClientSide()) {
            if (left) setDoorLeftOpen(next);
            else setDoorRightOpen(next);
            return;
        }
        forEachFormationTrain(train -> {
            if (left) train.setDoorLeftOpen(next);
            else train.setDoorRightOpen(next);
        });
    }
    public boolean isPantographUp() { return entityData.get(PANTOGRAPH_UP); }
    public void setPantographUp(boolean value) { entityData.set(PANTOGRAPH_UP, value); }
    public void setPantographUpForFormation(boolean value) {
        if (level().isClientSide()) {
            setPantographUp(value);
            return;
        }
        forEachFormationTrain(train -> train.setPantographUp(value));
    }
    public int getReverser() { return entityData.get(REVERSER); }
    public void setReverser(int value) {
        int clamped = Mth.clamp(value, -1, 1);
        entityData.set(REVERSER, clamped);
        entityData.set(REVERSE, clamped < 0);
    }
    public boolean isReverse() { return getReverser() < 0; }
    public void setReverse(boolean value) { setReverser(value ? -1 : 1); }
    public int getDestinationIndex() { return entityData.get(DESTINATION_INDEX); }
    public void setDestinationIndex(int value) { entityData.set(DESTINATION_INDEX, Math.max(0, value)); }
    public void setDestinationIndexForFormation(int value) {
        int index = Math.max(0, value);
        if (level().isClientSide()) {
            setDestinationIndex(index);
            return;
        }
        forEachFormationTrain(train -> train.setDestinationIndex(index));
    }
    public int getSoundIndex() { return entityData.get(SOUND_INDEX); }
    public void setSoundIndex(int value) { entityData.set(SOUND_INDEX, Math.max(0, value)); }
    public int getCustomButtonBits() { return entityData.get(CUSTOM_BUTTON_BITS); }
    public void setCustomButtonBits(int bits) { entityData.set(CUSTOM_BUTTON_BITS, bits); }
    public float getRailProgress() { return entityData.get(RAIL_PROGRESS); }
    public void setRailProgress(float progress) { entityData.set(RAIL_PROGRESS, Mth.clamp(progress, 0.0F, 1.0F)); }
    private String getSeatAssignmentsData() { return entityData.get(SEAT_ASSIGNMENTS); }
    private void setSeatAssignmentsData(String data) { entityData.set(SEAT_ASSIGNMENTS, data == null ? "" : data); }

    public boolean isCustomButtonOn(int index) {
        if (index < 0 || index >= 31) return false;
        return (getCustomButtonBits() & (1 << index)) != 0;
    }

    public void setCustomButton(int index, boolean on) {
        if (index < 0 || index >= 31) return;
        int bits = getCustomButtonBits();
        int mask = 1 << index;
        setCustomButtonBits(on ? (bits | mask) : (bits & ~mask));
    }

    public void toggleCustomButton(int index) {
        if (index < 0 || index >= 31) return;
        setCustomButton(index, !isCustomButtonOn(index));
    }
    public void setScriptEngine(ScriptEngine scriptEngine) { this.scriptEngine = scriptEngine; }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public float getPickRadius() {
        return (float) getTrainHalfWidth();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable((float) (getTrainHalfWidth() * 2.0D), (float) (getTrainHalfHeight() * 2.0D));
    }

    @Override
    protected AABB makeBoundingBox() {
        double halfWidth = getTrainHalfWidth();
        double halfLength = getTrainHalfLength();
        double halfHeight = getTrainHalfHeight();
        double yawRad = Math.toRadians(getYRot());
        double dx = -Math.sin(yawRad) * halfLength;
        double dz = Math.cos(yawRad) * halfLength;
        double px = Math.cos(yawRad) * halfWidth;
        double pz = Math.sin(yawRad) * halfWidth;
        double x = getX();
        double y = getY();
        double z = getZ();
        double minX = Math.min(Math.min(x + dx + px, x + dx - px), Math.min(x - dx + px, x - dx - px));
        double maxX = Math.max(Math.max(x + dx + px, x + dx - px), Math.max(x - dx + px, x - dx - px));
        double minZ = Math.min(Math.min(z + dz + pz, z + dz - pz), Math.min(z - dz + pz, z - dz - pz));
        double maxZ = Math.max(Math.max(z + dz + pz, z + dz - pz), Math.max(z - dz + pz, z - dz - pz));
        return new AABB(minX, y - 0.35D, minZ, maxX, y + halfHeight, maxZ);
    }

    private double getTrainHalfLength() {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        double maxZ = Math.max(2.0D, getTrainDistance());
        if (def != null) {
            for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
                maxZ = Math.max(maxZ, Math.abs(bogie.position().z) + 1.4D);
            }
            for (Vec3 seat : def.getAllSeatPositions()) {
                maxZ = Math.max(maxZ, Math.abs(seat.z) + TRAIN_BODY_MARGIN);
            }
        }
        return maxZ;
    }

    private double getTrainHalfWidth() {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        double maxX = DEFAULT_HALF_WIDTH;
        if (def != null) {
            for (Vec3 seat : def.getAllSeatPositions()) {
                maxX = Math.max(maxX, Math.abs(seat.x) + 0.55D);
            }
            for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
                maxX = Math.max(maxX, Math.abs(bogie.position().x) + 1.0D);
            }
        }
        return maxX;
    }

    private double getTrainHalfHeight() {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        double maxY = DEFAULT_HALF_HEIGHT;
        if (def != null) {
            for (Vec3 seat : def.getAllSeatPositions()) {
                maxY = Math.max(maxY, seat.y + 1.8D);
            }
        }
        return maxY;
    }

    @Override
    public void tick() {
        super.tick();
        field_70173_aa = tickCount;
        field_70177_z = getYRot();
        field_70170_p.field_72995_K = level().isClientSide();

        // スクリプトのtick関数を呼び出す
        updateTrainAnimationState();
        if (scriptEngine != null) {
            TrainScriptSystem.invokeScriptTick(scriptEngine, this);
        }

        if (level().isClientSide()) {
            if (clientLerpSteps > 0) {
                lerpPositionAndRotationStep(clientLerpSteps, clientLerpX, clientLerpY, clientLerpZ, clientLerpYRot, clientLerpXRot);
                clientLerpSteps--;
            } else {
                reapplyPosition();
                setRot(getYRot(), getXRot());
            }
            return;
        }

        if (!level().isClientSide()) {
            if (coupledLeaderUuid != null) {
                syncCoupledFormationFromHead();
                setDeltaMovement(Vec3.ZERO);
                this.hurtMarked = true;
                this.hasImpulse = true;
                return;
            }

            float speed = getSpeed();
            int notch = getNotch();

            Entity controller = getDriverPassenger();

            if (!(controller instanceof Player)) {
                // 運転者不在時は惰行側へ戻す
                notch = 0;
            }
            if (getReverser() == 0 && notch > 0) {
                notch = 0;
            }

            speed = applyNotchPhysics(speed, notch);

            setNotch(notch);

            if (notch <= 0) {
                float drag = DRAG_BASE + Math.abs(speed) * DRAG_SPEED_FACTOR;
                speed = approachZero(speed, drag);
            }
            speed *= FRICTION;
            if (Math.abs(speed) < 0.001f) speed = 0.0f;

            setSpeed(speed);

            if (!travelAlongRail(speed, controller)) {
                // rail が見つからない場合のみ従来移動へフォールバック
                if (speed != 0.0f) {
                    double yawRad = Math.toRadians(getYRot());
                    float dir = getCabDirectionSign(controller);
                    double targetDx = -Math.sin(yawRad) * speed * dir;
                    double targetDz = Math.cos(yawRad) * speed * dir;
                    Vec3 current = getDeltaMovement();
                    double dx = Mth.lerp(MOVEMENT_SMOOTHING, current.x, targetDx);
                    double dz = Mth.lerp(MOVEMENT_SMOOTHING, current.z, targetDz);
                    setDeltaMovement(dx, getDeltaMovement().y, dz);
                } else {
                    setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
                }

                Vec3 nextMove = this.getDeltaMovement();
                if (!canTravelOnRail(this.position().add(nextMove))) {
                    setSpeed(0.0F);
                    setNotch(0);
                    setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
                } else {
                    this.move(MoverType.SELF, nextMove);
                }
            }

            syncCoupledChain();
            tryCompletePendingCoupling();

            hasImpulse = Math.abs(speed) > 0.001F;
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        clientLerpX = x;
        clientLerpY = y;
        clientLerpZ = z;
        clientLerpYRot = yRot;
        clientLerpXRot = xRot;
        clientLerpSteps = Math.max(3, steps + 3);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public double lerpTargetX() {
        return clientLerpSteps > 0 ? clientLerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return clientLerpSteps > 0 ? clientLerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return clientLerpSteps > 0 ? clientLerpZ : getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return clientLerpSteps > 0 ? (float) clientLerpXRot : getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return clientLerpSteps > 0 ? (float) clientLerpYRot : getYRot();
    }

    private float applyNotchPhysics(float speed, int notch) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        float maxSpeed = getConfiguredMaxSpeed(def, notch);
        float accelBase = getConfiguredAcceleration(def);
        float absSpeed = Math.abs(speed);
        float speedRatio = Mth.clamp(absSpeed / maxSpeed, 0.0F, 1.0F);

        if (notch > 0) {
            if (getReverser() == 0) {
                return speed;
            }
            // 力行: 低速強め、高速で伸び鈍化
            float notchFactor = notch / (float) MAX_POWER_NOTCH;
            float accelCurve = accelBase * (0.35F + notchFactor * 1.25F) * (1.0F - (float) Math.pow(speedRatio, 1.45));
            return Mth.clamp(speed + Math.max(0.0005F, accelCurve), -maxSpeed, maxSpeed);
        }

        if (notch < 0) {
            // ブレーキ: 速度が高いほど効きやすい
            float brakeFactor = (-notch) / (float) MAX_BRAKE_NOTCH;
            float brakeCurve = BRAKE * (0.45F + brakeFactor * 1.35F) * (0.45F + speedRatio * 0.75F);
            return approachZero(speed, Math.max(0.0008F, brakeCurve));
        }

        return speed;
    }

    private float getConfiguredMaxSpeed(VehicleDefinition def, int notch) {
        if (def != null && notch > 0 && !def.getNotchMaxSpeeds().isEmpty()) {
            int index = Mth.clamp(notch - 1, 0, def.getNotchMaxSpeeds().size() - 1);
            float configured = def.getNotchMaxSpeeds().get(index);
            if (configured > 0.0F) {
                return configured;
            }
        }
        return MAX_SPEED;
    }

    private float getConfiguredAcceleration(VehicleDefinition def) {
        if (def != null && def.getAcceleration() > 0.0F) {
            return Math.max(ACCEL * 0.35F, def.getAcceleration() * 8.0F);
        }
        return ACCEL;
    }

    private void updateTrainAnimationState() {
        doorMoveL = approach(doorMoveL, isDoorLeftOpen() ? 60.0F : 0.0F, 1.0F);
        doorMoveR = approach(doorMoveR, isDoorRightOpen() ? 60.0F : 0.0F, 1.0F);
        pantograph_F = approach(pantograph_F, isPantographUp() ? 40.0F : 0.0F, 1.0F);
        pantograph_B = approach(pantograph_B, isPantographUp() ? 40.0F : 0.0F, 1.0F);
        Entity driver = getDriverPassenger();
        seatRotation = driver == null ? 0.0F : Mth.wrapDegrees(driver.getYRot() - getYRot());
    }

    private boolean canTravelOnRail(Vec3 worldPos) {
        BlockPos base = BlockPos.containing(worldPos.x, worldPos.y - 0.2, worldPos.z);
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos pos = base.offset(0, dy, 0);
            var block = level().getBlockState(pos).getBlock();
            if (block instanceof RailCollisionBlock || block instanceof LargeRailCoreBlock) {
                return true;
            }
        }
        return false;
    }

    private void syncCoupledFollower() {
        if (coupledFollowerUuid == null || level().isClientSide()) {
            return;
        }
        Entity followerRaw = ((net.minecraft.server.level.ServerLevel) level()).getEntity(coupledFollowerUuid);
        if (!(followerRaw instanceof TrainEntity follower) || !follower.isAlive()) {
            coupledFollowerUuid = null;
            return;
        }

        follower.coupledLeaderUuid = this.getUUID();
        follower.setNotch(this.getNotch());
        follower.setSpeed(this.getSpeed());
        follower.setReverser(this.getReverser());

        double gap = getCoupledGap(this, follower);
        if (!placeCoupledFollowerOnRail(follower, gap)) {
            double yawRad = Math.toRadians(getYRot());
            double x = getX() + Math.sin(yawRad) * gap;
            double z = getZ() - Math.cos(yawRad) * gap;
            follower.moveTo(x, getY(), z, getYRot(), follower.getXRot());
        }
        follower.hurtMarked = true;
        follower.hasImpulse = true;
    }

    private void syncCoupledChain() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }

        TrainEntity current = this;
        int guard = 0;
        while (current.coupledFollowerUuid != null && guard++ < 16) {
            Entity nextRaw = serverLevel.getEntity(current.coupledFollowerUuid);
            if (!(nextRaw instanceof TrainEntity next) || !next.isAlive()) {
                current.coupledFollowerUuid = null;
                break;
            }

            next.coupledLeaderUuid = current.getUUID();
            next.setNotch(current.getNotch());
            next.setSpeed(current.getSpeed());
            next.setReverser(current.getReverser());
            next.setDeltaMovement(Vec3.ZERO);

            double gap = getCoupledGap(current, next);
            if (!current.placeCoupledFollowerOnRail(next, gap)) {
                double yawRad = Math.toRadians(current.getYRot());
                double x = current.getX() + Math.sin(yawRad) * gap;
                double z = current.getZ() - Math.cos(yawRad) * gap;
                next.moveTo(x, current.getY(), z, current.getYRot(), next.getXRot());
            }
            next.hurtMarked = true;
            next.hasImpulse = true;

            current = next;
        }
    }

    private void syncCoupledFormationFromHead() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            syncCoupledChain();
            return;
        }

        TrainEntity head = this;
        int guard = 0;
        while (head.coupledLeaderUuid != null && guard++ < 16) {
            Entity leaderRaw = serverLevel.getEntity(head.coupledLeaderUuid);
            if (leaderRaw instanceof TrainEntity leader && leader.isAlive()) {
                head = leader;
            } else {
                head.coupledLeaderUuid = null;
                break;
            }
        }
        head.syncCoupledChain();
    }

    private boolean placeCoupledFollowerOnRail(TrainEntity follower, double gap) {
        if (follower == null || activeRailMap == null || activeRailSplit <= 0 || activeRailPosition < 0.0D) {
            return false;
        }
        int bodyDirection = activeRailBodyDirection == 0 ? 1 : activeRailBodyDirection;
        double step = Math.max(0.001D, activeRailMap.getLength()) / activeRailSplit;
        double desiredIndex = activeRailPosition - bodyDirection * (gap / step);
        if (desiredIndex < 0.0D || desiredIndex > activeRailSplit) {
            int boundary = desiredIndex < 0.0D ? 0 : activeRailSplit;
            int direction = desiredIndex < 0.0D ? -1 : 1;
            if (findConnectedRailContext(activeRailMap, activeRailSplit, boundary, direction) == null) {
                return false;
            }
        }
        RailResolvedSample center = resolveRailSample(activeRailMap, activeRailSplit, desiredIndex, bodyDirection);
        int followerBodyDirection = center.bodyDirection() == 0 ? bodyDirection : center.bodyDirection();
        follower.activeRailMap = center.map();
        follower.activeRailSplit = center.split();
        follower.activeRailPosition = center.index();
        follower.activeRailIndex = Mth.clamp((int) Math.round(center.index()), 0, center.split());
        follower.activeRailBodyDirection = followerBodyDirection;
        follower.activeRailDirection = activeRailDirection;
        follower.railDistanceCarry = 0.0D;
        follower.setRailProgress(follower.activeRailIndex / (float) follower.activeRailSplit);

        RailAnchorPair pair = follower.createAnchorPairFromCenter(
            follower.activeRailMap,
            follower.activeRailSplit,
            follower.activeRailPosition,
            follower.activeRailBodyDirection
        );
        follower.frontRailAnchor = pair.front();
        follower.rearRailAnchor = pair.rear();
        float yaw = follower.getRailYawForBody(
            follower.activeRailMap,
            follower.activeRailSplit,
            follower.activeRailPosition,
            follower.activeRailBodyDirection,
            follower.getYRot()
        );
        float pitch = follower.getRailPitchForBody(
            follower.activeRailMap,
            follower.activeRailSplit,
            follower.activeRailPosition,
            follower.activeRailBodyDirection
        );
        follower.applyPoseFromBogieSamples(pair.frontSample(), pair.rearSample(), yaw, pitch, false);
        follower.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    private static double getCoupledGap(TrainEntity front, TrainEntity rear) {
        double frontHalf = front == null ? 4.5D : front.getTrainDistance();
        double rearHalf = rear == null ? frontHalf : rear.getTrainDistance();
        return Math.max(4.0D, frontHalf + rearHalf + 1.10D);
    }

    private void forEachFormationTrain(Consumer<TrainEntity> action) {
        if (action == null) {
            return;
        }
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            action.accept(this);
            return;
        }

        TrainEntity head = this;
        int guard = 0;
        while (head.coupledLeaderUuid != null && guard++ < 16) {
            Entity leaderRaw = serverLevel.getEntity(head.coupledLeaderUuid);
            if (leaderRaw instanceof TrainEntity leader && leader.isAlive()) {
                head = leader;
            } else {
                head.coupledLeaderUuid = null;
                break;
            }
        }

        TrainEntity current = head;
        guard = 0;
        while (current != null && guard++ < 16) {
            action.accept(current);
            if (current.coupledFollowerUuid == null) {
                break;
            }
            Entity followerRaw = serverLevel.getEntity(current.coupledFollowerUuid);
            if (followerRaw instanceof TrainEntity follower && follower.isAlive()) {
                current = follower;
            } else {
                current.coupledFollowerUuid = null;
                break;
            }
        }
    }

    private record RailFollowContext(RailMap map, int split, int nearestIndex, double distanceSq) {}
    private record RailResolvedSample(RailSample sample, RailMap map, int split, double index, int bodyDirection) {}
    private record RailAnchor(RailMap map, int split, double index, int travelDirection) {}
    private record RailAnchorPair(RailAnchor front, RailAnchor rear, RailSample frontSample, RailSample rearSample, double distanceSq) {}
    private record RailConnection(RailMap map, int split, double index, int travelDirection, double score) {}
    private record RailSample(double x, double y, double z) {}

    private boolean travelAlongRail(float speed, Entity controller) {
        RailFollowContext context = getActiveRailContext();
        if (context == null) {
            return false;
        }
        activeRailMap = context.map();
        activeRailSplit = context.split();
        if (activeRailPosition < 0.0D) {
            activeRailPosition = context.nearestIndex();
        }
        activeRailIndex = Mth.clamp((int) Math.round(activeRailPosition), 0, activeRailSplit);
        if (activeRailBodyDirection == 0) {
            activeRailBodyDirection = getBodyDirectionOnRail(activeRailMap, activeRailSplit, activeRailIndex, getYRot());
        }

        float cabDirection = getCabDirectionSign(controller);
        int controllerDirection = Math.abs(cabDirection) < 0.5F ? 0 : (cabDirection > 0.0F ? 1 : -1);
        double distance = Math.abs(speed);
        if (distance > 0.0D && controllerDirection == 0) {
            setSpeed(0.0F);
            setNotch(0);
            distance = 0.0D;
        }

        if (distance > 0.0D) {
            int pathDirection = (activeRailBodyDirection == 0 ? 1 : activeRailBodyDirection) * controllerDirection;
            if (!advanceCenterAlongPath(distance, pathDirection, controllerDirection)) {
                setSpeed(0.0F);
                setNotch(0);
                setDeltaMovement(Vec3.ZERO);
                return false;
            }
        }

        RailAnchorPair pair = createAnchorPairFromCenter(activeRailMap, activeRailSplit, activeRailPosition, activeRailBodyDirection);
        frontRailAnchor = pair.front();
        rearRailAnchor = pair.rear();
        RailSample front = pair.frontSample();
        RailSample rear = pair.rearSample();
        float targetYaw = getRailYawForBody(activeRailMap, activeRailSplit, activeRailPosition, activeRailBodyDirection, getYRot());
        float targetPitch = getRailPitchForBody(activeRailMap, activeRailSplit, activeRailPosition, activeRailBodyDirection);
        float yaw = applyPoseFromBogieSamples(front, rear, targetYaw, targetPitch, false);
        setDeltaMovement(Vec3.ZERO);
        activeRailIndex = Mth.clamp((int) Math.round(activeRailPosition), 0, activeRailSplit);
        activeRailDirection = controllerDirection == 0 ? activeRailDirection : controllerDirection;
        activeRailBodyDirection = getBodyDirectionOnRail(activeRailMap, activeRailSplit, activeRailIndex, yaw);
        setRailProgress(activeRailIndex / (float) activeRailSplit);
        return true;
    }

    private boolean advanceCenterAlongPath(double distanceMeters, int pathDirection, int controllerDirection) {
        if (activeRailMap == null || activeRailSplit <= 0 || pathDirection == 0) {
            return false;
        }
        double remaining = distanceMeters;
        int guard = 0;
        while (remaining > 1.0E-5D && guard++ < 8) {
            double step = Math.max(0.001D, activeRailMap.getLength()) / activeRailSplit;
            double samplesToBoundary = pathDirection > 0 ? activeRailSplit - activeRailPosition : activeRailPosition;
            double metersToBoundary = samplesToBoundary * step;
            if (remaining <= metersToBoundary) {
                activeRailPosition += pathDirection * (remaining / step);
                remaining = 0.0D;
                break;
            }

            activeRailPosition = pathDirection > 0 ? activeRailSplit : 0.0D;
            remaining -= Math.max(0.0D, metersToBoundary);
            RailConnection next = findConnectedRailContext(activeRailMap, activeRailSplit, pathDirection > 0 ? activeRailSplit : 0, pathDirection);
            if (next == null) {
                return false;
            }

            activeRailMap = next.map();
            activeRailSplit = next.split();
            activeRailPosition = next.index();
            pathDirection = next.travelDirection();
            activeRailBodyDirection = pathDirection * (controllerDirection == 0 ? 1 : controllerDirection);
        }

        activeRailPosition = Mth.clamp(activeRailPosition, 0.0D, activeRailSplit);
        activeRailIndex = Mth.clamp((int) Math.round(activeRailPosition), 0, activeRailSplit);
        return true;
    }

    private RailAnchorPair createAnchorPairFromCenter(RailMap map, int split, double centerIndex, int bodyDirection) {
        double[] bogieZ = getBogieRailOffsets();
        double sampleStep = Math.max(0.001D, map.getLength()) / split;
        int dir = bodyDirection == 0 ? 1 : bodyDirection;
        RailResolvedSample frontResolved = resolveRailSample(map, split, centerIndex + dir * (bogieZ[1] / sampleStep), dir);
        RailResolvedSample rearResolved = resolveRailSample(map, split, centerIndex + dir * (bogieZ[0] / sampleStep), dir);
        return new RailAnchorPair(
            new RailAnchor(frontResolved.map(), frontResolved.split(), frontResolved.index(), dir),
            new RailAnchor(rearResolved.map(), rearResolved.split(), rearResolved.index(), dir),
            frontResolved.sample(),
            rearResolved.sample(),
            0.0D
        );
    }

    private float applyPoseFromBogieSamples(RailSample front, RailSample rear, float fallbackYaw, float fallbackPitch, boolean move) {
        RailSample center = new RailSample(
            (front.x + rear.x) * 0.5D,
            (front.y + rear.y) * 0.5D,
            (front.z + rear.z) * 0.5D
        );
        double dx = front.x - rear.x;
        double dy = front.y - rear.y;
        double dz = front.z - rear.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = fallbackYaw;
        float pitch = horizontal > 1.0E-4D
            ? (float) Math.toDegrees(Math.atan2(dy, horizontal))
            : fallbackPitch;
        yaw = keepNearestYaw(yaw, getYRot());
        if (move) {
            moveTo(center.x, center.y, center.z, yaw, pitch);
        } else {
            setPos(center.x, center.y, center.z);
        }
        setYRot(yaw);
        setXRot(pitch);
        yRotO = yaw;
        xRotO = pitch;
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        return yaw;
    }

    private float getRailYawForBody(RailMap map, int split, double index, int bodyDirection, float baseYaw) {
        if (map == null || split <= 0) {
            return baseYaw;
        }
        int clampedIndex = Mth.clamp((int) Math.round(index), 0, split);
        float yaw = map.getRailYaw(split, clampedIndex);
        if (bodyDirection < 0) {
            yaw = Mth.wrapDegrees(yaw + 180.0F);
        }
        return keepNearestYaw(yaw, baseYaw);
    }

    private float getRailPitchForBody(RailMap map, int split, double index, int bodyDirection) {
        if (map == null || split <= 0) {
            return getXRot();
        }
        int clampedIndex = Mth.clamp((int) Math.round(index), 0, split);
        float pitch = map.getRailPitch(split, clampedIndex);
        return bodyDirection < 0 ? -pitch : pitch;
    }

    private RailAnchorPair findBestAnchorPairForCenter(RailMap map, int split, double centerIndex, RailSample requestedCenter) {
        double[] bogieZ = getBogieRailOffsets();
        double trackLen = Math.max(0.001D, map.getLength());
        double sampleStep = trackLen / split;
        double frontOffset = bogieZ[1] / sampleStep;
        double rearOffset = bogieZ[0] / sampleStep;
        double minCenter = Math.max(0.0D, -Math.min(frontOffset, rearOffset));
        double maxCenter = Math.min(split, split - Math.max(frontOffset, rearOffset));
        boolean hasFullBogieRange = minCenter <= maxCenter;
        double spanSamples = Math.abs(bogieZ[1] - bogieZ[0]) / sampleStep;
        int searchRadius = Mth.clamp((int) Math.ceil(spanSamples * 0.35D), 4, 48);
        RailAnchorPair best = null;

        for (int offset = -searchRadius; offset <= searchRadius; offset++) {
            double candidateCenter = hasFullBogieRange
                ? Mth.clamp(centerIndex + offset, minCenter, maxCenter)
                : Mth.clamp(centerIndex + offset, 0.0D, split);
            RailResolvedSample frontResolved = resolveRailSample(map, split, candidateCenter + frontOffset, 1);
            RailResolvedSample rearResolved = resolveRailSample(map, split, candidateCenter + rearOffset, 1);
            RailSample front = frontResolved.sample();
            RailSample rear = rearResolved.sample();
            double centerX = (front.x + rear.x) * 0.5D;
            double centerY = (front.y + rear.y) * 0.5D;
            double centerZ = (front.z + rear.z) * 0.5D;
            double dx = centerX - requestedCenter.x;
            double dy = centerY - requestedCenter.y;
            double dz = centerZ - requestedCenter.z;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (best == null || distanceSq < best.distanceSq()) {
                best = new RailAnchorPair(
                    new RailAnchor(frontResolved.map(), frontResolved.split(), frontResolved.index(), 1),
                    new RailAnchor(rearResolved.map(), rearResolved.split(), rearResolved.index(), 1),
                    front,
                    rear,
                    distanceSq
                );
            }
        }

        if (best != null) {
            return best;
        }
        double fallbackCenter = hasFullBogieRange ? Mth.clamp(centerIndex, minCenter, maxCenter) : Mth.clamp(centerIndex, 0.0D, split);
        RailResolvedSample frontResolved = resolveRailSample(map, split, fallbackCenter + frontOffset, 1);
        RailResolvedSample rearResolved = resolveRailSample(map, split, fallbackCenter + rearOffset, 1);
        return new RailAnchorPair(
            new RailAnchor(frontResolved.map(), frontResolved.split(), frontResolved.index(), 1),
            new RailAnchor(rearResolved.map(), rearResolved.split(), rearResolved.index(), 1),
            frontResolved.sample(),
            rearResolved.sample(),
            0.0D
        );
    }

    private boolean ensureBogieAnchors() {
        double[] bogieZ = getBogieRailOffsets();
        if (isRailAnchorUsable(frontRailAnchor) && isRailAnchorUsable(rearRailAnchor)) {
            return true;
        }

        RailFollowContext front = findRailContextNearAny(localToWorld(new Vec3(0.0D, 0.0D, bogieZ[1])), null);
        RailFollowContext rear = findRailContextNearAny(localToWorld(new Vec3(0.0D, 0.0D, bogieZ[0])), null);
        if (front != null && rear != null) {
            frontRailAnchor = new RailAnchor(front.map(), front.split(), front.nearestIndex(), 1);
            rearRailAnchor = new RailAnchor(rear.map(), rear.split(), rear.nearestIndex(), 1);
            return true;
        }

        RailFollowContext center = getActiveRailContext();
        if (center == null) {
            return false;
        }
        double sampleStep = Math.max(0.001D, center.map().getLength()) / center.split();
        int bodyDirection = getBodyDirectionOnRail(center.map(), center.split(), center.nearestIndex(), getYRot());
        frontRailAnchor = new RailAnchor(
            center.map(),
            center.split(),
            Mth.clamp(center.nearestIndex() + bodyDirection * (bogieZ[1] / sampleStep), 0.0D, center.split()),
            bodyDirection
        );
        rearRailAnchor = new RailAnchor(
            center.map(),
            center.split(),
            Mth.clamp(center.nearestIndex() + bodyDirection * (bogieZ[0] / sampleStep), 0.0D, center.split()),
            bodyDirection
        );
        return true;
    }

    private boolean isRailAnchorUsable(RailAnchor anchor) {
        if (anchor == null || anchor.map() == null || anchor.split() <= 0) {
            return false;
        }
        RailSample sample = sampleRail(anchor.map(), anchor.split(), anchor.index());
        return distanceToSqr(sample.x, sample.y, sample.z) < 256.0D;
    }

    private RailAnchor advanceBogieAnchor(RailAnchor anchor, double distanceMeters, int controllerDirection) {
        if (anchor == null || anchor.map() == null || anchor.split() <= 0) {
            return null;
        }
        if (distanceMeters <= 0.0D) {
            return anchor;
        }

        RailMap map = anchor.map();
        int split = anchor.split();
        double index = Mth.clamp(anchor.index(), 0.0D, split);
        int travelDirection = controllerDirection == 0
            ? (anchor.travelDirection() == 0 ? 1 : anchor.travelDirection())
            : getBodyDirectionOnRail(map, split, (int) Math.round(index), getYRot()) * controllerDirection;
        double remaining = distanceMeters;
        int guard = 0;

        while (remaining > 1.0E-5D && guard++ < 8) {
            double step = Math.max(0.001D, map.getLength()) / split;
            double samplesToBoundary = travelDirection > 0 ? split - index : index;
            double metersToBoundary = samplesToBoundary * step;
            if (remaining <= metersToBoundary || metersToBoundary > 0.01D) {
                double moveSamples = remaining / step;
                if (remaining <= metersToBoundary) {
                    index += travelDirection * moveSamples;
                    remaining = 0.0D;
                    break;
                }
            }

            if (remaining < metersToBoundary) {
                break;
            }

            remaining -= Math.max(0.0D, metersToBoundary);
            int boundaryIndex = travelDirection > 0 ? split : 0;
            RailConnection next = findConnectedRailContext(map, split, boundaryIndex, travelDirection);
            if (next == null) {
                return null;
            }

            map = next.map();
            split = next.split();
            index = next.index();
            travelDirection = next.travelDirection();
        }

        if (index < -0.001D || index > split + 0.001D) {
            return null;
        }
        return new RailAnchor(map, split, Mth.clamp(index, 0.0D, split), travelDirection);
    }

    private boolean advanceActiveRailPosition(RailMap startMap, int startSplit, int direction, double deltaSamples) {
        activeRailMap = startMap;
        activeRailSplit = startSplit;
        if (deltaSamples <= 0.0D) {
            activeRailPosition = Mth.clamp(activeRailPosition, 0.0D, activeRailSplit);
            activeRailIndex = Mth.clamp((int) Math.round(activeRailPosition), 0, activeRailSplit);
            return true;
        }

        double nextPosition = activeRailPosition + direction * deltaSamples;
        int guard = 0;
        while ((nextPosition < 0.0D || nextPosition > activeRailSplit) && guard++ < 6) {
            int boundaryIndex = nextPosition > activeRailSplit ? activeRailSplit : 0;
            RailSample boundary = sampleRail(activeRailMap, activeRailSplit, boundaryIndex);
            RailFollowContext next = findRailContextNear(new Vec3(boundary.x, boundary.y, boundary.z), activeRailMap);
            if (next == null) {
                return false;
            }

            double overflow = nextPosition < 0.0D ? -nextPosition : nextPosition - activeRailSplit;
            int nextTravelDirection = next.nearestIndex() <= next.split() * 0.5D ? 1 : -1;
            activeRailBodyDirection = chooseStableBodyDirection(
                next.map(),
                next.split(),
                next.nearestIndex(),
                getYRot(),
                activeRailBodyDirection == 0 ? 1 : activeRailBodyDirection
            );
            activeRailMap = next.map();
            activeRailSplit = next.split();
            activeRailDirection = nextTravelDirection;
            direction = nextTravelDirection;
            nextPosition = next.nearestIndex() + nextTravelDirection * overflow;
        }

        if (nextPosition < 0.0D || nextPosition > activeRailSplit) {
            return false;
        }
        activeRailPosition = Mth.clamp(nextPosition, 0.0D, activeRailSplit);
        activeRailIndex = Mth.clamp((int) Math.round(activeRailPosition), 0, activeRailSplit);
        setRailProgress(activeRailIndex / (float) activeRailSplit);
        return true;
    }

    private float keepNearestYaw(float targetYaw, float currentYaw) {
        float diff = Mth.wrapDegrees(targetYaw - currentYaw);
        if (Math.abs(diff) > 120.0F) {
            targetYaw = Mth.wrapDegrees(targetYaw + 180.0F);
        }
        return targetYaw;
    }

    private RailResolvedSample resolveRailSample(RailMap map, int split, double index, int bodyDirection) {
        if (index >= 0.0D && index <= split) {
            return new RailResolvedSample(sampleRail(map, split, index), map, split, index, bodyDirection);
        }

        int direction = index > split ? 1 : -1;
        int boundaryIndex = direction > 0 ? split : 0;
        RailConnection next = findConnectedRailContext(map, split, boundaryIndex, direction);
        if (next == null) {
            double clamped = Mth.clamp(index, 0.0D, split);
            return new RailResolvedSample(sampleRail(map, split, clamped), map, split, clamped, bodyDirection);
        }

        int nextBodyDirection = chooseStableBodyDirection(next.map(), next.split(), (int) Math.round(next.index()), getYRot(), bodyDirection);
        double overflow = Math.abs(index < 0.0D ? index : index - split);
        double nextIndex = next.index() + next.travelDirection() * overflow;
        nextIndex = Mth.clamp(nextIndex, 0.0D, next.split());
        return new RailResolvedSample(sampleRail(next.map(), next.split(), nextIndex), next.map(), next.split(), nextIndex, nextBodyDirection);
    }

    private RailConnection findConnectedRailContext(RailMap currentMap, int currentSplit, int boundaryIndex, int travelDirection) {
        if (currentMap == null || currentSplit <= 0) {
            return null;
        }

        RailSample boundary = sampleRail(currentMap, currentSplit, boundaryIndex);
        Vec3 boundaryPos = new Vec3(boundary.x, boundary.y, boundary.z);
        float outgoingYaw = currentMap.getRailYaw(currentSplit, Mth.clamp(boundaryIndex, 0, currentSplit));
        if (travelDirection < 0) {
            outgoingYaw = Mth.wrapDegrees(outgoingYaw + 180.0F);
        }

        com.portofino.polygontrainmod.rail.util.RailPosition endpoint =
            boundaryIndex <= 0 ? currentMap.getStartRP() : currentMap.getEndRP();

        BlockPos center = BlockPos.containing(boundary.x, boundary.y, boundary.z);
        RailConnection best = null;
        int radius = 5;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    RailMap[] maps = getRailMapsAt(center.offset(dx, dy, dz));
                    if (maps.length == 0) continue;

                    for (RailMap map : maps) {
                        if (map == null || map == currentMap || map.equals(currentMap)) {
                            continue;
                        }
                        int split = Math.max(64, RailMap.curveSplitForLength(map.getHorizontalPathLength()) * 4);
                        best = betterConnection(best, evaluateRailEndpointConnection(map, split, 0, endpoint, boundaryPos, outgoingYaw));
                        best = betterConnection(best, evaluateRailEndpointConnection(map, split, split, endpoint, boundaryPos, outgoingYaw));
                    }
                }
            }
        }
        return best;
    }

    private RailConnection evaluateRailEndpointConnection(
        RailMap map,
        int split,
        int endpointIndex,
        com.portofino.polygontrainmod.rail.util.RailPosition currentEndpoint,
        Vec3 boundaryPos,
        float outgoingYaw
    ) {
        com.portofino.polygontrainmod.rail.util.RailPosition candidateEndpoint =
            endpointIndex <= 0 ? map.getStartRP() : map.getEndRP();
        boolean sameEndpoint = candidateEndpoint != null && candidateEndpoint.equals(currentEndpoint);
        RailSample sample = sampleRail(map, split, endpointIndex);
        double distSq = new Vec3(sample.x, sample.y, sample.z).distanceToSqr(boundaryPos);
        if (!sameEndpoint && distSq > 6.25D) {
            return null;
        }

        int nextTravelDirection = endpointIndex <= 0 ? 1 : -1;
        float candidateYaw = map.getRailYaw(split, endpointIndex);
        if (nextTravelDirection < 0) {
            candidateYaw = Mth.wrapDegrees(candidateYaw + 180.0F);
        }
        float yawDiff = Math.abs(Mth.wrapDegrees(outgoingYaw - candidateYaw));
        double score = distSq + yawDiff * 0.08D + (sameEndpoint ? -100.0D : 0.0D);
        return new RailConnection(map, split, endpointIndex, nextTravelDirection, score);
    }

    private RailConnection betterConnection(RailConnection current, RailConnection candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.score() < current.score()) {
            return candidate;
        }
        return current;
    }

    private boolean transitionToConnectedRail(RailMap currentMap, int currentSplit, int direction) {
        int boundaryIndex = direction > 0 ? currentSplit : 0;
        RailSample boundary = sampleRail(currentMap, currentSplit, boundaryIndex);
        RailFollowContext next = findRailContextNear(new Vec3(boundary.x, boundary.y, boundary.z), currentMap);
        if (next == null) {
            return false;
        }

        activeRailMap = next.map();
        activeRailSplit = next.split();
        activeRailIndex = next.nearestIndex();
        activeRailPosition = activeRailIndex;
        activeRailBodyDirection = chooseStableBodyDirection(next.map(), next.split(), next.nearestIndex(), getYRot(), activeRailBodyDirection);
        activeRailDirection = next.nearestIndex() <= Math.max(4, next.split() / 8) ? 1 : -1;
        railDistanceCarry = 0.0D;
        setRailProgress(activeRailIndex / (float) activeRailSplit);
        return true;
    }

    private int getBodyDirectionOnRail(RailMap map, int split, int index, float bodyYaw) {
        if (map == null || split <= 0) {
            return activeRailDirection == 0 ? 1 : activeRailDirection;
        }
        int clamped = Mth.clamp(index, 0, split);
        float yawAtRail = map.getRailYaw(split, clamped);
        return Math.abs(Mth.wrapDegrees(bodyYaw - yawAtRail)) <= 90.0F ? 1 : -1;
    }

    private int chooseStableBodyDirection(RailMap map, int split, int index, float bodyYaw, int fallback) {
        if (map == null || split <= 0) {
            return fallback == 0 ? 1 : fallback;
        }
        int byYaw = getBodyDirectionOnRail(map, split, index, bodyYaw);
        if (fallback == 0) {
            return byYaw;
        }
        float railYaw = map.getRailYaw(split, Mth.clamp(index, 0, split));
        if (fallback < 0) {
            railYaw = Mth.wrapDegrees(railYaw + 180.0F);
        }
        float diff = Math.abs(Mth.wrapDegrees(bodyYaw - railYaw));
        return diff <= 120.0F ? fallback : byYaw;
    }

    private RailSample sampleRail(RailMap map, int split, int index) {
        return sampleRail(map, split, (double) index);
    }

    private RailSample sampleRail(RailMap map, int split, double index) {
        double clamped = Mth.clamp(index, 0.0D, split);
        int low = Mth.clamp((int) Math.floor(clamped), 0, split);
        int high = Mth.clamp((int) Math.ceil(clamped), 0, split);
        if (low == high) {
            double[] pos = map.getRailPos(split, low);
            return new RailSample(pos[1], map.getRailHeight(split, low) + RAIL_HEIGHT_OFFSET, pos[0]);
        }
        double t = clamped - low;
        double[] a = map.getRailPos(split, low);
        double[] b = map.getRailPos(split, high);
        double yA = map.getRailHeight(split, low) + RAIL_HEIGHT_OFFSET;
        double yB = map.getRailHeight(split, high) + RAIL_HEIGHT_OFFSET;
        return new RailSample(
            Mth.lerp(t, a[1], b[1]),
            Mth.lerp(t, yA, yB),
            Mth.lerp(t, a[0], b[0])
        );
    }

    private RailSample sampleRailIndex(RailMap map, int split, int index) {
        int clamped = Mth.clamp(index, 0, split);
        double[] pos = map.getRailPos(split, clamped);
        return new RailSample(pos[1], map.getRailHeight(split, clamped) + RAIL_HEIGHT_OFFSET, pos[0]);
    }

    private double[] getBogieRailOffsets() {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null || def.getBogies().isEmpty()) {
            double distance = Math.max(2.0D, getTrainDistance() * 0.7D);
            return new double[]{-distance, distance};
        }
        double rear = Double.POSITIVE_INFINITY;
        double front = Double.NEGATIVE_INFINITY;
        for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
            rear = Math.min(rear, bogie.position().z);
            front = Math.max(front, bogie.position().z);
        }
        if (!Double.isFinite(rear) || !Double.isFinite(front) || Math.abs(front - rear) < 0.5D) {
            double distance = Math.max(2.0D, getTrainDistance() * 0.7D);
            return new double[]{-distance, distance};
        }
        return new double[]{rear, front};
    }

    public float getBogieYawOffset(VehicleDefinition.BogieDefinition bogie) {
        return getBogieYawOffset(bogie, getYRot());
    }

    public float getBogieYawOffset(VehicleDefinition.BogieDefinition bogie, float baseYaw) {
        if (bogie == null) {
            return 0.0F;
        }
        RailAnchor anchor = getNearestAnchorForBogie(bogie);
        if (isRailAnchorUsable(anchor)) {
            return relativeBogieYaw(getAnchorRailYaw(anchor, baseYaw), baseYaw);
        }
        if (activeRailMap != null && activeRailIndex >= 0 && activeRailSplit > 0) {
            double trackLen = Math.max(0.001D, activeRailMap.getLength());
            double sampleStep = trackLen / activeRailSplit;
            int bodyDirection = activeRailBodyDirection == 0 ? 1 : activeRailBodyDirection;
            double bogieIndex = activeRailPosition + bodyDirection * (bogie.position().z / sampleStep);
            RailResolvedSample resolved = resolveRailSample(activeRailMap, activeRailSplit, bogieIndex, bodyDirection);
            int clampedIndex = Mth.clamp((int) Math.round(resolved.index()), 0, resolved.split());
            float railYaw = resolved.map().getRailYaw(resolved.split(), clampedIndex);
            if (resolved.bodyDirection() < 0) {
                railYaw = Mth.wrapDegrees(railYaw + 180.0F);
            }
            return relativeBogieYaw(railYaw, baseYaw);
        }

        RailFollowContext context = findRailContextNearAny(localToWorld(bogie.position()), null);
        if (context == null) {
            return 0.0F;
        }
        int bodyDirection = getBodyDirectionOnRail(context.map(), context.split(), context.nearestIndex(), baseYaw);
        float railYaw = context.map().getRailYaw(context.split(), context.nearestIndex());
        if (bodyDirection < 0) {
            railYaw = Mth.wrapDegrees(railYaw + 180.0F);
        }
        return relativeBogieYaw(railYaw, baseYaw);
    }

    public float getBogieWorldYaw(int bogieIndex) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null || def.getBogies().isEmpty()) {
            return getYRot();
        }
        int clamped = Mth.clamp(bogieIndex, 0, def.getBogies().size() - 1);
        VehicleDefinition.BogieDefinition bogie = def.getBogies().get(clamped);
        RailAnchor anchor = getAnchorForBogieIndex(def, clamped);
        if (isRailAnchorUsable(anchor)) {
            return getAnchorRailYaw(anchor, getYRot());
        }
        return Mth.wrapDegrees(getYRot() + getBogieYawOffset(bogie, getYRot()));
    }

    public float getScriptBogieWorldYaw(int bogieIndex) {
        return getBogieWorldYaw(bogieIndex);
    }

    private RailAnchor getAnchorForBogieIndex(VehicleDefinition def, int bogieIndex) {
        if (def == null || def.getBogies().isEmpty()) {
            return null;
        }
        int clamped = Mth.clamp(bogieIndex, 0, def.getBogies().size() - 1);
        double z = def.getBogies().get(clamped).position().z;
        double rearZ = Double.POSITIVE_INFINITY;
        double frontZ = Double.NEGATIVE_INFINITY;
        for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
            rearZ = Math.min(rearZ, bogie.position().z);
            frontZ = Math.max(frontZ, bogie.position().z);
        }
        if (Math.abs(z - frontZ) <= Math.abs(z - rearZ)) {
            return frontRailAnchor;
        }
        return rearRailAnchor;
    }

    private float getAnchorRailYaw(RailAnchor anchor, float baseYaw) {
        if (!isRailAnchorUsable(anchor)) {
            return baseYaw;
        }
        return getRailYawForBody(anchor.map(), anchor.split(), anchor.index(), anchor.travelDirection(), baseYaw);
    }

    private RailAnchor getNearestAnchorForBogie(VehicleDefinition.BogieDefinition bogie) {
        if (bogie == null) {
            return null;
        }
        Vec3 bogieWorld = localToWorld(bogie.position());
        RailAnchor best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        if (isRailAnchorUsable(frontRailAnchor)) {
            RailSample sample = sampleRail(frontRailAnchor.map(), frontRailAnchor.split(), frontRailAnchor.index());
            bestDistance = new Vec3(sample.x, sample.y, sample.z).distanceToSqr(bogieWorld);
            best = frontRailAnchor;
        }
        if (isRailAnchorUsable(rearRailAnchor)) {
            RailSample sample = sampleRail(rearRailAnchor.map(), rearRailAnchor.split(), rearRailAnchor.index());
            double distance = new Vec3(sample.x, sample.y, sample.z).distanceToSqr(bogieWorld);
            if (distance < bestDistance) {
                best = rearRailAnchor;
            }
        }
        return best;
    }

    private float relativeBogieYaw(float railYaw, float baseYaw) {
        float offset = Mth.wrapDegrees(railYaw - baseYaw);
        if (Math.abs(offset) > 90.0F) {
            offset = Mth.wrapDegrees(offset + 180.0F);
        }
        return Mth.clamp(offset, -45.0F, 45.0F);
    }

    private RailFollowContext getActiveRailContext() {
        if (activeRailMap != null && activeRailIndex >= 0 && activeRailSplit > 0) {
            int index = Mth.clamp(activeRailIndex, 0, activeRailSplit);
            double[] p = activeRailMap.getRailPos(activeRailSplit, index);
            double py = activeRailMap.getRailHeight(activeRailSplit, index) + RAIL_HEIGHT_OFFSET;
            double distSq = distanceToSqr(p[1], py, p[0]);
            if (distSq < 16.0D) {
                return new RailFollowContext(activeRailMap, activeRailSplit, index, distSq);
            }
        }

        RailFollowContext nearest = findNearestRailContext();
        if (nearest != null) {
            activeRailMap = nearest.map();
            activeRailSplit = nearest.split();
            activeRailIndex = nearest.nearestIndex();
            activeRailPosition = activeRailIndex;
            float yawAtRail = activeRailMap.getRailYaw(activeRailSplit, activeRailIndex);
            float yawDiff = Math.abs(Mth.wrapDegrees(getYRot() - yawAtRail));
            int mapForwardSign = yawDiff <= 90.0F ? 1 : -1;
            activeRailBodyDirection = mapForwardSign;
            float cabDirection = getCabDirectionSign(getDriverPassenger());
            activeRailDirection = Math.abs(cabDirection) < 0.5F
                ? mapForwardSign
                : mapForwardSign * (cabDirection > 0.0F ? 1 : -1);
        }
        return nearest;
    }

    private RailFollowContext findRailContextNear(Vec3 worldPos, RailMap exclude) {
        return findRailContextNear(worldPos, exclude, true);
    }

    private RailFollowContext findRailContextNearAny(Vec3 worldPos, RailMap exclude) {
        return findRailContextNear(worldPos, exclude, false);
    }

    private RailFollowContext findRailContextNear(Vec3 worldPos, RailMap exclude, boolean endpointOnly) {
        BlockPos center = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        RailFollowContext best = null;
        int radius = (int) RAIL_SEARCH_RADIUS;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    RailMap[] maps = getRailMapsAt(pos);
                    if (maps.length == 0) continue;

                    for (RailMap map : maps) {
                        if (map == null || map == exclude) continue;
                        int split = Math.max(64, RailMap.curveSplitForLength(map.getHorizontalPathLength()) * 4);
                        int nearest = map.getNearlestPoint(split, worldPos.z, worldPos.x);
                        nearest = Mth.clamp(nearest, 0, split);
                        double[] p = map.getRailPos(split, nearest);
                        double py = map.getRailHeight(split, nearest) + RAIL_HEIGHT_OFFSET;
                        double distSq = new Vec3(p[1], py, p[0]).distanceToSqr(worldPos);
                        boolean nearEndpoint = nearest <= Math.max(6, split / 6) || nearest >= split - Math.max(6, split / 6);
                        double maxDistSq = endpointOnly ? 25.0D : 49.0D;
                        if ((endpointOnly && !nearEndpoint) || distSq > maxDistSq) {
                            continue;
                        }
                        if (best == null || distSq < best.distanceSq()) {
                            best = new RailFollowContext(map, split, nearest, distSq);
                        }
                    }
                }
            }
        }

        return best;
    }

    private RailFollowContext findNearestRailContext() {
        BlockPos center = blockPosition();
        RailFollowContext best = null;
        int radius = (int) RAIL_SEARCH_RADIUS;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    RailMap[] maps = getRailMapsAt(pos);
                    if (maps.length == 0) continue;

                    for (RailMap map : maps) {
                        if (map == null) continue;
                        int split = Math.max(64, RailMap.curveSplitForLength(map.getHorizontalPathLength()) * 4);
                        int nearest = map.getNearlestPoint(split, getZ(), getX());
                        nearest = Mth.clamp(nearest, 0, split);
                        double[] p = map.getRailPos(split, nearest);
                        double py = map.getRailHeight(split, nearest) + RAIL_HEIGHT_OFFSET;
                        double distSq = distanceToSqr(p[1], py, p[0]);

                        if (best == null || distSq < best.distanceSq()) {
                            best = new RailFollowContext(map, split, nearest, distSq);
                        }
                    }
                }
            }
        }

        return best;
    }

    private RailMap[] getRailMapsAt(BlockPos pos) {
        if (level().getBlockEntity(pos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
            return core.getAllRailMaps();
        }
        if (level().getBlockEntity(pos) instanceof RailCollisionBlockEntity collision) {
            BlockPos corePos = collision.getCorePos();
            if (corePos != null && level().getBlockEntity(corePos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
                return core.getAllRailMaps();
            }
        }
        return new RailMap[0];
    }

    public void coupleNearest() {
        if (level().isClientSide()) {
            return;
        }
        TrainEntity nearest = null;
        double best = 9.0D;
        for (TrainEntity other : level().getEntitiesOfClass(TrainEntity.class, getBoundingBox().inflate(6.0D))) {
            if (other == this || !other.isAlive()) continue;
            double d = other.position().distanceToSqr(this.position());
            if (d < best) {
                best = d;
                nearest = other;
            }
        }
        if (nearest != null) {
            TrainEntity tail = this;
            if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                int guard = 0;
                while (tail.coupledFollowerUuid != null && guard++ < 16) {
                    Entity nextRaw = serverLevel.getEntity(tail.coupledFollowerUuid);
                    if (nextRaw instanceof TrainEntity next && next.isAlive()) {
                        tail = next;
                    } else {
                        tail.coupledFollowerUuid = null;
                        break;
                    }
                }
            }
            tail.linkCouplingByPosition(nearest);
        }
    }

    public void coupleWith(TrainEntity other) {
        if (other == null || other == this || level().isClientSide()) {
            return;
        }
        if (other.coupledLeaderUuid != null || this.coupledFollowerUuid != null || other.hasIndirectPassenger(this)) {
            return;
        }
        TrainEntity tail = this;
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            int guard = 0;
            while (tail.coupledFollowerUuid != null && guard++ < 16) {
                Entity nextRaw = serverLevel.getEntity(tail.coupledFollowerUuid);
                if (nextRaw instanceof TrainEntity next && next.isAlive()) {
                    tail = next;
                } else {
                    tail.coupledFollowerUuid = null;
                    break;
                }
            }
        }
        tail.linkCouplingByPosition(other);
        other.setNotch(tail.getNotch());
        other.setSpeed(tail.getSpeed());
        other.setReverser(tail.getReverser());
        other.setLightMode(tail.getLightMode());
        other.setDestinationIndex(tail.getDestinationIndex());
        tail.setDeltaMovement(Vec3.ZERO);
        other.setDeltaMovement(Vec3.ZERO);
        tail.syncCoupledFormationFromHead();
        other.syncCoupledFormationFromHead();
        tail.hurtMarked = true;
        other.hurtMarked = true;
    }

    private void linkCouplingByPosition(TrainEntity other) {
        if (other == null || other == this || isConnectedTo(other)) {
            return;
        }
        boolean otherAhead = isOtherAheadAlongBody(other);
        if (otherAhead && coupledLeaderUuid == null && other.coupledFollowerUuid == null) {
            other.coupledFollowerUuid = getUUID();
            coupledLeaderUuid = other.getUUID();
            return;
        }
        coupledFollowerUuid = other.getUUID();
        other.coupledLeaderUuid = getUUID();
    }

    private boolean isOtherAheadAlongBody(TrainEntity other) {
        Vec3 forward = localToWorld(new Vec3(0.0D, 0.0D, 1.0D)).subtract(position()).normalize();
        Vec3 toOther = other.position().subtract(position());
        return toOther.dot(forward) > 0.0D;
    }

    private Vec3 getCouplerPoint(boolean front) {
        double z = front ? getTrainHalfLength() : -getTrainHalfLength();
        return localToWorld(new Vec3(0.0D, 0.0D, z));
    }

    private double getNearestCouplerDistanceSqr(TrainEntity other) {
        Vec3 front = getCouplerPoint(true);
        Vec3 rear = getCouplerPoint(false);
        Vec3 otherFront = other.getCouplerPoint(true);
        Vec3 otherRear = other.getCouplerPoint(false);
        return Math.min(
            Math.min(front.distanceToSqr(otherFront), front.distanceToSqr(otherRear)),
            Math.min(rear.distanceToSqr(otherFront), rear.distanceToSqr(otherRear))
        );
    }

    private boolean canCompleteCouplingWith(TrainEntity other) {
        if (other == null || other == this || isConnectedTo(other)) {
            return false;
        }
        if (getNearestCouplerDistanceSqr(other) <= 16.0D) {
            return true;
        }
        return getBoundingBox().inflate(4.0D).intersects(other.getBoundingBox().inflate(4.0D));
    }

    private void enterCouplingMode(Player player) {
        if (level().isClientSide()) {
            return;
        }
        UUID playerId = player.getUUID();
        CouplingSelection selection = COUPLING_MODE.get(playerId);
        if (selection == null || selection.isComplete()) {
            COUPLING_MODE.put(playerId, new CouplingSelection(getUUID(), null, level().getGameTime()));
            player.displayClientMessage(Component.literal("連結モード: もう片方の列車の台車を選択してください"), true);
            return;
        }
        if (selection.first().equals(getUUID())) {
            player.displayClientMessage(Component.literal("連結モード: もう片方の列車を選択してください"), true);
            return;
        }
        COUPLING_MODE.put(playerId, new CouplingSelection(selection.first(), getUUID(), level().getGameTime()));
        player.displayClientMessage(Component.literal("連結モード: 2両をゆっくり接触させてください"), true);
    }

    private void tryCompletePendingCoupling() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel) || COUPLING_MODE.isEmpty()) {
            return;
        }
        Map<UUID, CouplingSelection> pending = new HashMap<>(COUPLING_MODE);
        for (Map.Entry<UUID, CouplingSelection> entry : pending.entrySet()) {
            CouplingSelection selection = entry.getValue();
            if (selection == null || !selection.isComplete()) {
                continue;
            }
            if (level().getGameTime() - selection.armedAt() < 6L) {
                continue;
            }
            Entity sourceRaw = serverLevel.getEntity(selection.first());
            Entity targetRaw = serverLevel.getEntity(selection.second());
            if (!(sourceRaw instanceof TrainEntity source) || !source.isAlive()) {
                COUPLING_MODE.remove(entry.getKey());
                continue;
            }
            if (!(targetRaw instanceof TrainEntity target) || !target.isAlive()) {
                COUPLING_MODE.remove(entry.getKey());
                continue;
            }
            if (source == target || source.isConnectedTo(target)) {
                continue;
            }
            if (source.canCompleteCouplingWith(target)) {
                source.coupleWith(target);
                COUPLING_MODE.remove(entry.getKey());
                Player player = serverLevel.getPlayerByUUID(entry.getKey());
                if (player != null) {
                    player.displayClientMessage(Component.literal("連結しました"), true);
                }
            }
        }
    }

    private boolean isConnectedTo(TrainEntity other) {
        if (other == null) {
            return false;
        }
        return other.getUUID().equals(coupledFollowerUuid)
            || other.getUUID().equals(coupledLeaderUuid)
            || this.getUUID().equals(other.coupledFollowerUuid)
            || this.getUUID().equals(other.coupledLeaderUuid);
    }

    public void decouple() {
        if (coupledFollowerUuid != null && level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Entity followerRaw = serverLevel.getEntity(coupledFollowerUuid);
            if (followerRaw instanceof TrainEntity follower) {
                follower.coupledLeaderUuid = null;
            }
        }
        coupledFollowerUuid = null;
        coupledLeaderUuid = null;
    }

    public boolean isConnected() {
        return coupledFollowerUuid != null || coupledLeaderUuid != null;
    }

    public TrainEntity getConnectedTrain() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }
        if (coupledFollowerUuid != null) {
            Entity entity = serverLevel.getEntity(coupledFollowerUuid);
            if (entity instanceof TrainEntity train) return train;
        }
        if (coupledLeaderUuid != null) {
            Entity entity = serverLevel.getEntity(coupledLeaderUuid);
            if (entity instanceof TrainEntity train) return train;
        }
        return null;
    }

    private Entity getDriverPassenger() {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        int frontDriverIndex = resolveFrontSeatIndex(def);
        int rearDriverIndex = resolveRearSeatIndex(def);
        if (frontDriverIndex >= 0 || rearDriverIndex >= 0) {
            for (Entity passenger : getPassengers()) {
                int assignedSeat = getAssignedSeatIndex(passenger);
                if (assignedSeat == frontDriverIndex || assignedSeat == rearDriverIndex) {
                    return passenger;
                }
            }
        }
        return null;
    }

    private float getCabDirectionSign(Entity controller) {
        int reverser = getReverser();
        if (reverser == 0) {
            return 0.0F;
        }
        return -getDriverCabDirection(controller) * reverser;
    }

    private int getDriverCabDirection(Entity controller) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (controller != null && def != null) {
            int assignedSeat = getAssignedSeatIndex(controller);
            if (assignedSeat == resolveRearSeatIndex(def)) {
                return -1;
            }
            if (assignedSeat == resolveFrontSeatIndex(def)) {
                return 1;
            }
            List<Vec3> seats = getSelectableSeats(def);
            if (assignedSeat >= 0 && assignedSeat < seats.size()) {
                return seats.get(assignedSeat).z < 0.0D ? -1 : 1;
            }
        }
        return 1;
    }

    public boolean isDriverSeatIndex(int seatIndex) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null || seatIndex < 0) {
            return false;
        }
        return seatIndex == resolveFrontSeatIndex(def) || seatIndex == resolveRearSeatIndex(def);
    }

    public boolean isDriverPassenger(Entity passenger) {
        if (passenger == null) {
            return false;
        }
        return isDriverSeatIndex(getAssignedSeatIndex(passenger));
    }

    public void applyThrottle(float throttle) {
        float speed = Mth.clamp(getSpeed() + throttle * ACCEL, -MAX_SPEED, MAX_SPEED);
        setSpeed(speed);
    }

    public void stepMascon(int delta) {
        if (delta > 0) {
            setNotch(Math.min(MAX_POWER_NOTCH, getNotch() + delta));
        } else if (delta < 0) {
            setNotch(Math.max(-MAX_BRAKE_NOTCH, getNotch() + delta));
        }
    }

    public void forceDiscardTrain() {
        if (!level().isClientSide()) {
            ejectPassengers();
            decouple();
            seatAssignments.clear();
            syncSeatAssignmentsToEntityData();
            setSpeed(0.0F);
            setNotch(0);
        }
        remove(RemovalReason.DISCARDED);
    }

    public static void clearCouplingModes() {
        COUPLING_MODE.clear();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player) {
            boolean hasCrowbar = player.getMainHandItem().is(PolygonTrainModItems.CROWBAR_ITEM.get())
                || player.getOffhandItem().is(PolygonTrainModItems.CROWBAR_ITEM.get());
            if (hasCrowbar) {
                forceDiscardTrain();
                return true;
            }
        }
        return false;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        Vec3 seat = getAssignedSeatOffset(passenger);
        return localToWorld(seat);
    }

    @Override
    public Vec3 getVehicleAttachmentPoint(Entity passenger) {
        return localToWorld(getAssignedSeatOffset(passenger));
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        int seatCount = Math.max(1, getSeatCount(def));
        return getPassengers().size() < seatCount;
    }

    // ---- 既存スクリプト向けAPI ----
    public float getTrainStateData(int stateType) {
        return getVehicleState(stateType);
    }

    public float getVehicleState(int stateType) {
        return switch (stateType) {
            case 0 -> getReverser() >= 0 ? 0.0F : 1.0F;
            case 1 -> getNotch();
            case 2 -> getRailProgress();
            case 3 -> isDoorLeftOpen() ? 1.0F : 0.0F;
            case 4 -> (isDoorRightOpen() ? 1.0F : 0.0F) + (isDoorLeftOpen() ? 2.0F : 0.0F);
            case 5 -> getLightMode();
            case 8 -> getDestinationIndex();
            case 10 -> getReverser() + 1.0F;
            case 11 -> getLightMode();
            default -> 0.0F;
        };
    }

    public float getTrainDirection() {
        return getReverser();
    }

    public float getDir() {
        return getTrainDirection();
    }

    public float getMoveDir() {
        return getTrainDirection();
    }

    public void syncNotch(int notch) {
        setNotch(notch);
    }

    public void syncVehicleState(int stateType, float value) {
        switch (stateType) {
            case 1 -> setNotch(Math.round(value));
            case 2 -> setRailProgress(value);
            default -> {
            }
        }
    }

    public float getSeatRotation() {
        return 0.0F;
    }

    public FormationCompat getFormation() {
        return new FormationCompat(this);
    }

    public int func_145782_y() {
        return getId();
    }

    public int func_70070_b() {
        return 0x00F000F0;
    }

    // ---- 追加互換API（E259系等で使用） ----
    public Entity func_184207_aI() {
        Entity driver = getDriverPassenger();
        if (driver != null) {
            return driver;
        }
        return getPassengers().isEmpty() ? null : getPassengers().get(0);
    }

    public int getSignal() {
        return 0;
    }

    public boolean isControlCar() {
        return true;
    }

    public Object getModelSet() {
        return new ModelSetCompat(getVehicleId());
    }

    public ResourceStateCompat getResourceState() {
        return new ResourceStateCompat(this);
    }

    public BogieCompat getBogie(int index) {
        return new BogieCompat(this, index);
    }

    public static final class ResourceStateCompat {
        private final TrainEntity train;

        public ResourceStateCompat(TrainEntity train) {
            this.train = train;
        }

        /**
         * Returns a typed map view used by old render scripts.
         */
        public DataMapCompat getDataMap() {
            return new DataMapCompat(train);
        }

        /**
         * Returns the vehicle id for scripts that compare resource names.
         */
        public String getResourceName() {
            return train.getVehicleId();
        }

        /**
         * Returns a config holder compatible with old render scripts.
         */
        public ModelSetCompat getResourceSet() {
            return new ModelSetCompat(train.getVehicleId());
        }
    }

    public static final class DataMapCompat {
        private final TrainEntity train;
        private final Map<String, Object> values = new HashMap<>();

        /**
         * Creates a script-visible data map for a train.
         */
        public DataMapCompat(TrainEntity train) {
            this.train = train;
            refresh();
        }

        /**
         * Returns a raw value by key.
         */
        public Object get(String key) {
            refresh();
            return values.get(key);
        }

        /**
         * Returns an integer value by key.
         */
        public int getInt(String key) {
            Object value = get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof Boolean bool) {
                return bool ? 1 : 0;
            }
            return 0;
        }

        /**
         * Returns a boolean value by key.
         */
        public boolean getBoolean(String key) {
            Object value = get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof Number number) {
                return number.intValue() != 0;
            }
            return false;
        }

        /**
         * Stores a boolean value for the current script frame.
         */
        public void setBoolean(String key, boolean value, int syncType) {
            values.put(key, value);
        }

        /**
         * Stores an integer value for the current script frame.
         */
        public void setInt(String key, int value, int syncType) {
            values.put(key, value);
        }

        private void refresh() {
            if (train == null) {
                return;
            }
            values.put("headLight", train.isHeadlightOn() ? 1 : 0);
            values.put("door", train.isDoorOpen() ? 1 : 0);
            values.put("doorLeft", train.isDoorLeftOpen() ? 1 : 0);
            values.put("doorRight", train.isDoorRightOpen() ? 1 : 0);
            values.put("lightMode", train.getLightMode());
            values.put("pantograph", train.isPantographUp() ? 1 : 0);
            values.put("destination", train.getDestinationIndex());
            values.put("sound", train.getSoundIndex());
            values.put("reverse", train.getReverser() < 0 ? 1 : 0);
            values.put("reverser", train.getReverser());
            values.put("customButtons", train.getCustomButtonBits());
            values.put("railProgress", train.getRailProgress());
            values.put("connected", train.isConnected() ? 1 : 0);
            values.put("carNumber", 0);
            values.put("prevFormationSize", 1);
            values.put("isFormationA", false);
            values.put("isFormationB", false);
            values.put("isFormationError", false);
            values.putIfAbsent("prevRollsignId", train.getDestinationIndex());
            for (int i = 0; i < 16; i++) {
                values.put("Button" + i, train.isCustomButtonOn(i) ? 1 : 0);
            }
        }
    }

    private record CouplingSelection(UUID first, UUID second, long armedAt) {
        private boolean isComplete() {
            return first != null && second != null;
        }
    }

    public static final class FormationCompat {
        private final TrainEntity train;

        /**
         * Creates a script-visible formation view.
         */
        public FormationCompat(TrainEntity train) {
            this.train = train;
        }

        /**
         * Returns the number of cars visible to this script.
         */
        public int size() {
            return 1;
        }

        /**
         * Returns a formation entry by index.
         */
        public FormationEntryCompat get(int index) {
            return index == 0 ? new FormationEntryCompat(0, train) : null;
        }

        /**
         * Returns the entry for a train.
         */
        public FormationEntryCompat getEntry(TrainEntity entity) {
            return new FormationEntryCompat(0, entity == null ? train : entity);
        }

        /**
         * Placeholder for old packet refresh calls.
         */
        public void sendPacket() {
        }
    }

    public static final class FormationEntryCompat {
        public final int entryId;
        public final TrainEntity train;

        /**
         * Creates a script-visible formation entry.
         */
        public FormationEntryCompat(int entryId, TrainEntity train) {
            this.entryId = entryId;
            this.train = train;
        }
    }

    public static final class BogieCompat {
        private final TrainEntity train;
        private final int index;
        public final float field_70177_z;

        public BogieCompat() {
            this(null, 0);
        }

        public BogieCompat(TrainEntity train, int index) {
            this.train = train;
            this.index = index;
            this.field_70177_z = train != null ? train.getScriptBogieWorldYaw(index) : 0.0F;
        }

        public float getRotation() {
            if (train == null) {
                return 0.0F;
            }
            return train.getScriptBogieWorldYaw(index);
        }

        public float getYaw() {
            return getRotation();
        }

        public float getPitch() {
            return train != null ? train.getXRot() : 0.0F;
        }
    }

    public static final class ModelSetCompat {
        private final String id;

        public ModelSetCompat(String id) {
            this.id = id;
        }

        public String getName() {
            return id;
        }

        public String getModelName() {
            return id;
        }

        public String getTextureName() {
            return id;
        }

        /**
         * Returns a minimal config object used by old render scripts.
         */
        public ConfigCompat getConfig() {
            return new ConfigCompat();
        }
    }

    public static final class ConfigCompat {
        public final String[] rollsignNames = {
            "None",
            "Out of service",
            "Test run",
            "Party",
            "Extra",
            "Local",
            "Rapid",
            "Express",
            "NEX_Narita",
            "NEX_Shinjuku",
            "NEX_Ofuna(Shinjuku)",
            "NEX_Ofuna2",
            "NEX_Kamakura",
            "NEX_Yokohama",
            "NEX_Ikebukuro",
            "NEX_Omiya",
            "NEX_Hachioji",
            "NEX_Takao",
            "NEX_Mt_Fuji",
            "Shiosai_Choshi",
            "Shiosai_Tokyo",
            "Odoriko_Izu",
            "Odoriko_Tokyo"
        };
    }

    public static final class WorldCompat {
        private final TrainEntity train;
        public boolean field_72995_K;

        /**
         * Creates a script-visible world view.
         */
        public WorldCompat(TrainEntity train) {
            this.train = train;
        }

        /**
         * Returns whether the current level is client-side.
         */
        public boolean isClientSide() {
            field_72995_K = train != null && train.level().isClientSide();
            return field_72995_K;
        }
    }

    @Override
    public boolean hasIndirectPassenger(Entity passenger) {
        if (super.hasIndirectPassenger(passenger)) {
            return true;
        }
        if (passenger == this) {
            return true;
        }
        return coupledFollowerUuid != null && passenger.getUUID().equals(coupledFollowerUuid);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        super.onPassengerTurned(passenger);
        if (!hasPassenger(passenger)) {
            seatAssignments.remove(passenger.getUUID());
            syncSeatAssignmentsToEntityData();
        }
    }

    public double getPassengersRidingOffset() {
        return 0.0;
    }

    private Vec3 getSeatOffset(int index) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) return new Vec3(0.0, 1.2, 0.0);

        List<Vec3> seats = def.getAllSeatPositions();
        if (!seats.isEmpty()) {
            if (index >= 0 && index < seats.size()) {
                return seats.get(index);
            }
            return seats.get(0);
        }

        if (def.hasSeatOffset()) {
            return def.getSeatOffset();
        }

        return new Vec3(0.0, 1.2, 0.0);
    }

    private Vec3 getAssignedSeatOffset(Entity passenger) {
        int index = getAssignedSeatIndex(passenger);
        return getSeatOffset(index);
    }

    private int getAssignedSeatIndex(Entity passenger) {
        syncSeatAssignmentsFromEntityData();
        UUID id = passenger.getUUID();
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        int seatCount = getSeatCount(def);
        Integer assignedIndex = seatAssignments.get(id);
        if (assignedIndex != null && assignedIndex >= 0 && assignedIndex < seatCount) {
            return assignedIndex;
        }
        if (level().isClientSide()) {
            int passengerIndex = getPassengers().indexOf(passenger);
            return Mth.clamp(passengerIndex, 0, Math.max(0, seatCount - 1));
        }
        return assignSeatIndex(passenger, findNearestSeatIndex(passenger));
    }

    private int getSeatCount(VehicleDefinition def) {
        if (def == null) return 0;
        int count = def.getAllSeatPositions().size();
        if (count == 0 && def.hasSeatOffset()) {
            count = 1;
        }
        return count;
    }

    private int assignSeatIndex(Entity passenger, int desiredIndex) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) return 0;

        int seatCount = getSeatCount(def);
        if (seatCount <= 0) {
            seatAssignments.put(passenger.getUUID(), 0);
            syncSeatAssignmentsToEntityData();
            return 0;
        }

        if (desiredIndex >= 0 && desiredIndex < seatCount && !isSeatTakenByOther(passenger, desiredIndex)) {
            seatAssignments.put(passenger.getUUID(), desiredIndex);
            syncSeatAssignmentsToEntityData();
            return desiredIndex;
        }

        for (int i = 0; i < seatCount; i++) {
            if (!isSeatTakenByOther(passenger, i)) {
                seatAssignments.put(passenger.getUUID(), i);
                syncSeatAssignmentsToEntityData();
                return i;
            }
        }

        int fallback = Math.max(0, Math.min(desiredIndex, seatCount - 1));
        seatAssignments.put(passenger.getUUID(), fallback);
        syncSeatAssignmentsToEntityData();
        return fallback;
    }

    private void syncSeatAssignmentsToEntityData() {
        if (level().isClientSide()) {
            return;
        }
        StringBuilder data = new StringBuilder();
        seatAssignments.forEach((uuid, seatIndex) -> {
            if (!data.isEmpty()) {
                data.append(';');
            }
            data.append(uuid).append('=').append(seatIndex);
        });
        setSeatAssignmentsData(data.toString());
    }

    private void syncSeatAssignmentsFromEntityData() {
        String data = getSeatAssignmentsData();
        if (data == null || data.isBlank()) {
            return;
        }
        String[] entries = data.split(";");
        for (String entry : entries) {
            int sep = entry.indexOf('=');
            if (sep <= 0 || sep >= entry.length() - 1) {
                continue;
            }
            try {
                UUID uuid = UUID.fromString(entry.substring(0, sep));
                int seatIndex = Integer.parseInt(entry.substring(sep + 1));
                seatAssignments.put(uuid, seatIndex);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isSeatTakenByOther(Entity passenger, int seatIndex) {
        UUID passengerId = passenger.getUUID();
        for (Map.Entry<UUID, Integer> entry : seatAssignments.entrySet()) {
            if (entry.getValue() == seatIndex && !entry.getKey().equals(passengerId)) {
                return true;
            }
        }
        return false;
    }

    private int findNearestSeatIndex(Entity passenger) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) return -1;

        var seats = def.getAllSeatPositions();
        if (seats.isEmpty()) {
            return def.hasSeatOffset() ? 0 : -1;
        }

        // プレイヤーの位置から最も近い座席を返す
        Vec3 target = passenger.position();
        int bestIndex = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < seats.size(); i++) {
            Vec3 seatPoint = localToWorld(seats.get(i));
            double distance = seatPoint.distanceToSqr(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private List<Vec3> getSelectableSeats(VehicleDefinition def) {
        if (def == null) {
            return List.of();
        }
        return def.getAllSeatPositions();
    }

    private int resolveFrontSeatIndex(VehicleDefinition def) {
        List<Vec3> seats = getSelectableSeats(def);
        if (seats.isEmpty()) {
            return def != null && def.hasSeatOffset() ? 0 : -1;
        }

        int configured = def != null ? def.getFrontDriverSeatIndex() : -1;
        if (configured >= 0 && configured < seats.size()) {
            return configured;
        }

        int fallbackDriver = def != null ? def.getDriverSeatIndex() : -1;
        if (fallbackDriver >= 0 && fallbackDriver < seats.size()) {
            return fallbackDriver;
        }

        return findExtremeSeatIndexByZ(seats, true);
    }

    private int resolveRearSeatIndex(VehicleDefinition def) {
        List<Vec3> seats = getSelectableSeats(def);
        if (seats.isEmpty()) {
            return def != null && def.hasSeatOffset() ? 0 : -1;
        }

        int configured = def != null ? def.getRearDriverSeatIndex() : -1;
        if (configured >= 0 && configured < seats.size()) {
            return configured;
        }

        return findExtremeSeatIndexByZ(seats, false);
    }

    private int findExtremeSeatIndexByZ(List<Vec3> seats, boolean front) {
        if (seats == null || seats.isEmpty()) {
            return -1;
        }

        int bestIndex = 0;
        double bestZ = seats.get(0).z;
        for (int i = 1; i < seats.size(); i++) {
            double z = seats.get(i).z;
            if (front ? z > bestZ : z < bestZ) {
                bestZ = z;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private int findSeatByClickPosition(Player player, Vec3 clickOffsetWorld) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) {
            return findNearestSeatIndex(player);
        }

        int byBogie = findSeatByClickedBogie(def, clickOffsetWorld);
        if (byBogie >= 0) {
            PolygonTrainMod.LOGGER.debug(
                "Selected seat by bogie click: vehicle={}, seatIndex={}, clickOffset={}, player={}",
                getVehicleId(),
                byBogie,
                clickOffsetWorld,
                player.getName().getString()
            );
            return byBogie;
        }

        int fallback = findNearestSeatToLocalClick(def, worldToLocal(this.position().add(clickOffsetWorld)));
        PolygonTrainMod.LOGGER.debug(
            "Selected seat by nearest JSON seat: vehicle={}, seatIndex={}, clickOffset={}, player={}",
            getVehicleId(),
            fallback,
            clickOffsetWorld,
            player.getName().getString()
        );
        return fallback;
    }

    private int findNearestSeatToLocalClick(VehicleDefinition def, Vec3 localClick) {
        List<Vec3> seats = getSelectableSeats(def);
        if (seats.isEmpty()) {
            return def != null && def.hasSeatOffset() ? 0 : -1;
        }

        int bestIndex = 0;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < seats.size(); i++) {
            Vec3 seat = seats.get(i);
            double dx = Math.abs(seat.x - localClick.x);
            double dz = Math.abs(seat.z - localClick.z);
            double sameSideBonus = Math.abs(localClick.x) > 0.25D && Math.signum(seat.x) == Math.signum(localClick.x) ? -0.25D : 0.0D;
            double score = dz * 3.0D + dx + sameSideBonus;
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private int findSeatByClickedBogie(VehicleDefinition def, Vec3 clickOffsetWorld) {
        var bogies = def.getBogies();
        if (bogies.isEmpty()) {
            return -1;
        }

        Vec3 clickedWorld = this.position().add(clickOffsetWorld);
        VehicleDefinition.BogieDefinition nearest = null;
        int nearestIndex = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < bogies.size(); i++) {
            VehicleDefinition.BogieDefinition bogie = bogies.get(i);
            Vec3 bogieWorld = localToWorld(bogie.position());
            double distance = bogieWorld.distanceToSqr(clickedWorld);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = bogie;
                nearestIndex = i;
            }
        }

        if (nearest == null) {
            return -1;
        }
        if (bestDistance > BOGIE_CLICK_RADIUS_SQ) {
            return -1;
        }

        double yawRad = Math.toRadians(getYRot());
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        Vec3 nearestOffset = localToWorld(nearest.position()).subtract(this.position());
        boolean frontBogie = nearestOffset.dot(forward) >= 0.0;
        int seatIndex = frontBogie ? resolveFrontSeatIndex(def) : resolveRearSeatIndex(def);
        PolygonTrainMod.LOGGER.debug(
            "Clicked bogie resolved to seat: vehicle={}, bogieIndex={}, frontBogie={}, seatIndex={}, clickOffset={}, bestDistance={}",
            getVehicleId(),
            nearestIndex,
            frontBogie,
            seatIndex,
            clickOffsetWorld,
            bestDistance
        );
        return seatIndex;
    }

    private InteractionResult tryRideWithSeat(Player player, int seatIndex) {
        if (seatIndex < 0) {
            PolygonTrainMod.LOGGER.debug("Ride denied: invalid seat index {} for vehicle {}", seatIndex, getVehicleId());
            return InteractionResult.PASS;
        }

        assignSeatIndex(player, seatIndex);
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (isDriverSeatIndex(seatIndex)) {
            setReverser(1);
        }
        PolygonTrainMod.LOGGER.info(
            "Ride request: vehicle={}, player={}, seatIndex={}, isDriverSeat={}, clickPassengers={}",
            getVehicleId(),
            player.getName().getString(),
            seatIndex,
            isDriverSeatIndex(seatIndex),
            getPassengers().size()
        );
        PolygonTrainMod.LOGGER.debug(
            "Try mount: player='{}' vehicle='{}' seat={} passengers={}/{} canAddPassenger={}",
            player.getName().getString(),
            getVehicleId(),
            seatIndex,
            getPassengers().size(),
            Math.max(1, getSeatCount(VehicleRegistry.getById(getVehicleId()))),
            this.canAddPassenger(player)
        );
        if (player.startRiding(this)) {
            PolygonTrainMod.LOGGER.info(
                "Player '{}' mounted vehicle '{}' at seat {}",
                player.getName().getString(),
                getVehicleId(),
                seatIndex
            );
            return InteractionResult.SUCCESS;
        }

        PolygonTrainMod.LOGGER.warn(
            "Player '{}' failed to mount vehicle '{}' at seat {}",
            player.getName().getString(),
            getVehicleId(),
            seatIndex
        );
        seatAssignments.remove(player.getUUID());
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level().isClientSide()) {
            if (player.getItemInHand(hand).is(PolygonTrainModItems.CROWBAR_ITEM.get())) {
                enterCouplingMode(player);
                return InteractionResult.SUCCESS;
            }

            Vec3 clickOffsetWorld = vec != null ? vec : player.position().subtract(this.position());
            PolygonTrainMod.LOGGER.info(
                "Train interactAt: vehicle={}, player={}, hand={}, clickLocal={}, clickWorldOffset={}",
                getVehicleId(),
                player.getName().getString(),
                hand,
                vec,
                clickOffsetWorld
            );
            int seatIndex = findSeatByClickPosition(player, clickOffsetWorld);
            return tryRideWithSeat(player, seatIndex);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        double yawRad = Math.toRadians(this.getYRot());
        double sideX = -Math.sin(yawRad) * 1.5;
        double sideZ = Math.cos(yawRad) * 1.5;
        return new Vec3(getX() + sideX, getY() + 0.5, getZ() + sideZ);
    }

    private Vec3 localToWorld(Vec3 local) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) {
            def = VehicleRegistry.getSelected();
        }

        Vec3 offset = def != null ? def.getModelOffset() : Vec3.ZERO;
        float scale = def != null ? def.getModelScale() : 1.0F;
        // モデルのZ+を前方としてYRotで回転させる。
        double yawRad = Math.toRadians(-this.getYRot());

        double localX = local.x;
        double localY = local.y;
        double localZ = local.z;
        // Z+を前方として回転
        double rotatedX = Math.cos(yawRad) * localX - Math.sin(yawRad) * localZ;
        double rotatedZ = Math.sin(yawRad) * localX + Math.cos(yawRad) * localZ;
        double offsetX = Math.cos(yawRad) * offset.x - Math.sin(yawRad) * offset.z;
        double offsetZ = Math.sin(yawRad) * offset.x + Math.cos(yawRad) * offset.z;

        return new Vec3(
            this.getX() + offsetX + rotatedX * scale,
            this.getY() + offset.y + localY * scale,
            this.getZ() + offsetZ + rotatedZ * scale
        );
    }

    private Vec3 worldToLocal(Vec3 world) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) {
            def = VehicleRegistry.getSelected();
        }

        Vec3 offset = def != null ? def.getModelOffset() : Vec3.ZERO;
        float scale = def != null ? def.getModelScale() : 1.0F;
        double yawRad = Math.toRadians(-this.getYRot());
        double offsetX = Math.cos(yawRad) * offset.x - Math.sin(yawRad) * offset.z;
        double offsetZ = Math.sin(yawRad) * offset.x + Math.cos(yawRad) * offset.z;
        double dx = world.x - this.getX() - offsetX;
        double dy = world.y - this.getY() - offset.y;
        double dz = world.z - this.getZ() - offsetZ;

        double localX = Math.cos(yawRad) * dx + Math.sin(yawRad) * dz;
        double localZ = -Math.sin(yawRad) * dx + Math.cos(yawRad) * dz;
        return new Vec3(localX / scale, dy / scale, localZ / scale);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level().isClientSide()) {
            if (player.getItemInHand(hand).is(PolygonTrainModItems.CROWBAR_ITEM.get())) {
                enterCouplingMode(player);
                return InteractionResult.SUCCESS;
            }

            VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
            Vec3 clickOffsetWorld = player.position().subtract(this.position());
            PolygonTrainMod.LOGGER.info(
                "Train interact: vehicle={}, player={}, hand={}, clickWorldOffset={}",
                getVehicleId(),
                player.getName().getString(),
                hand,
                clickOffsetWorld
            );
            int seatIndex = def == null ? -1 : findSeatByClickPosition(player, clickOffsetWorld);
            return tryRideWithSeat(player, seatIndex);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setVehicleId(tag.getString("VehicleId"));
        setSpeed(tag.getFloat("Speed"));
        if (tag.contains("TrainDistance")) {
            setTrainDistance(tag.getFloat("TrainDistance"));
            refreshDimensions();
        }
        if (tag.contains("Notch")) {
            setNotch(tag.getInt("Notch"));
        }
        if (tag.contains("LightMode")) setLightMode(tag.getInt("LightMode"));
        else if (tag.contains("HeadlightOn")) setHeadlightOn(tag.getBoolean("HeadlightOn"));
        if (tag.contains("DoorOpen")) setDoorOpen(tag.getBoolean("DoorOpen"));
        if (tag.contains("DoorLeftOpen")) setDoorLeftOpen(tag.getBoolean("DoorLeftOpen"));
        if (tag.contains("DoorRightOpen")) setDoorRightOpen(tag.getBoolean("DoorRightOpen"));
        if (tag.contains("PantographUp")) setPantographUp(tag.getBoolean("PantographUp"));
        if (tag.contains("Reverser")) {
            setReverser(tag.getInt("Reverser"));
        } else if (tag.contains("Reverse")) {
            setReverse(tag.getBoolean("Reverse"));
        }
        if (tag.contains("DestinationIndex")) setDestinationIndex(tag.getInt("DestinationIndex"));
        if (tag.contains("SoundIndex")) setSoundIndex(tag.getInt("SoundIndex"));
        if (tag.contains("CustomButtonBits")) setCustomButtonBits(tag.getInt("CustomButtonBits"));
        if (tag.contains("RailProgress")) setRailProgress(tag.getFloat("RailProgress"));
        if (tag.contains("CoupledFollower")) {
            try {
                coupledFollowerUuid = UUID.fromString(tag.getString("CoupledFollower"));
            } catch (Exception ignored) {
                coupledFollowerUuid = null;
            }
        }
        if (tag.contains("CoupledLeader")) {
            try {
                coupledLeaderUuid = UUID.fromString(tag.getString("CoupledLeader"));
            } catch (Exception ignored) {
                coupledLeaderUuid = null;
            }
        }

        seatAssignments.clear();
        if (tag.contains("SeatAssignments")) {
            CompoundTag assignments = tag.getCompound("SeatAssignments");
            VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
            int seatCount = getSeatCount(def);
            for (String key : assignments.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int seatIndex = assignments.getInt(key);
                    if (seatCount <= 0) {
                        seatAssignments.put(uuid, 0);
                    } else if (seatIndex < 0) {
                        seatAssignments.put(uuid, 0);
                    } else if (seatIndex >= seatCount) {
                        seatAssignments.put(uuid, seatCount - 1);
                    } else {
                        seatAssignments.put(uuid, seatIndex);
                    }
                } catch (IllegalArgumentException e) {
                    // ignore malformed UUIDs
                }
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("VehicleId", getVehicleId());
        tag.putFloat("Speed", getSpeed());
        tag.putFloat("TrainDistance", getTrainDistance());
        tag.putInt("Notch", getNotch());
        tag.putBoolean("HeadlightOn", isHeadlightOn());
        tag.putBoolean("DoorOpen", isDoorOpen());
        tag.putBoolean("DoorLeftOpen", isDoorLeftOpen());
        tag.putBoolean("DoorRightOpen", isDoorRightOpen());
        tag.putInt("LightMode", getLightMode());
        tag.putBoolean("PantographUp", isPantographUp());
        tag.putBoolean("Reverse", isReverse());
        tag.putInt("Reverser", getReverser());
        tag.putInt("DestinationIndex", getDestinationIndex());
        tag.putInt("SoundIndex", getSoundIndex());
        tag.putInt("CustomButtonBits", getCustomButtonBits());
        tag.putFloat("RailProgress", getRailProgress());
        if (coupledFollowerUuid != null) {
            tag.putString("CoupledFollower", coupledFollowerUuid.toString());
        }
        if (coupledLeaderUuid != null) {
            tag.putString("CoupledLeader", coupledLeaderUuid.toString());
        }

        if (!seatAssignments.isEmpty()) {
            CompoundTag assignments = new CompoundTag();
            seatAssignments.forEach((uuid, seatIndex) -> assignments.putInt(uuid.toString(), seatIndex));
            tag.put("SeatAssignments", assignments);
        }
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        if (!this.hasPassenger(passenger)) {
            return;
        }

        Vec3 seatPos = getPassengerRidingPosition(passenger);
        moveFunction.accept(passenger, seatPos.x, seatPos.y, seatPos.z);
        // 視点固定しすぎない（左右確認できるようにする）
        if (passenger instanceof LivingEntity living) {
            living.setYBodyRot(this.getYRot());
        }
    }

    private static float approachZero(float value, float step) {
        if (value > 0.0F) {
            return Math.max(0.0F, value - step);
        }
        if (value < 0.0F) {
            return Math.min(0.0F, value + step);
        }
        return 0.0F;
    }

    private static float approach(float value, float target, float step) {
        if (value < target) {
            return Math.min(target, value + step);
        }
        if (value > target) {
            return Math.max(target, value - step);
        }
        return value;
    }
}
