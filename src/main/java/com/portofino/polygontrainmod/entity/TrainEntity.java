package com.portofino.polygontrainmod.entity;

import com.portofino.polygontrainmod.PolygonTrainMod;
import com.portofino.polygontrainmod.PolygonTrainModItems;
import com.portofino.polygontrainmod.PolygonTrainModEntities;
import com.portofino.polygontrainmod.block.LargeRailCoreBlock;
import com.portofino.polygontrainmod.block.RailCollisionBlock;
import com.portofino.polygontrainmod.blockentity.LargeRailCoreBlockEntity;
import com.portofino.polygontrainmod.blockentity.RailCollisionBlockEntity;
import com.portofino.polygontrainmod.rail.util.RailMap;
import com.portofino.polygontrainmod.script.RTMScriptSystem;
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
    private static final EntityDataAccessor<Boolean> PANTOGRAPH_UP =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> REVERSE =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
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

    private static final float ACCEL = 0.02f; // 加速度をRTMに近づける
    private static final float BRAKE = 0.03f; // 減速度
    private static final float MAX_SPEED = 2.0f;
    private static final float FRICTION = 0.995f; // 摩擦を減らしてRTMに近づける
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
    // RTM系MQOは台車/床下がモデル原点より約1m下にあるため、原点をレール上へ少し持ち上げる。
    private static final double RAIL_HEIGHT_OFFSET = 1.08D;

    private final Map<UUID, Integer> seatAssignments = new HashMap<>();
    private UUID coupledFollowerUuid;
    private UUID coupledLeaderUuid;
    private ScriptEngine scriptEngine;
    private RailMap activeRailMap;
    private int activeRailSplit;
    private int activeRailIndex = -1;
    private int activeRailDirection = 1;
    private double railDistanceCarry;
    public float doorMoveL;
    public float doorMoveR;
    public float pantograph_F = 40.0F;
    public float pantograph_B = 40.0F;
    public float seatRotation;
    private static final Map<UUID, UUID> COUPLING_MODE = new HashMap<>();

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

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VEHICLE_ID, "");
        builder.define(SPEED, 0.0f);
        builder.define(TRAIN_DISTANCE, 4.5f);
        builder.define(NOTCH, 0);
        builder.define(HEADLIGHT_ON, false);
        builder.define(DOOR_OPEN, false);
        builder.define(PANTOGRAPH_UP, true);
        builder.define(REVERSE, false);
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
    public void setHeadlightOn(boolean value) { entityData.set(HEADLIGHT_ON, value); }
    public boolean isDoorOpen() { return entityData.get(DOOR_OPEN); }
    public void setDoorOpen(boolean value) { entityData.set(DOOR_OPEN, value); }
    public boolean isPantographUp() { return entityData.get(PANTOGRAPH_UP); }
    public void setPantographUp(boolean value) { entityData.set(PANTOGRAPH_UP, value); }
    public boolean isReverse() { return entityData.get(REVERSE); }
    public void setReverse(boolean value) { entityData.set(REVERSE, value); }
    public int getDestinationIndex() { return entityData.get(DESTINATION_INDEX); }
    public void setDestinationIndex(int value) { entityData.set(DESTINATION_INDEX, Math.max(0, value)); }
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

        // スクリプトのtick関数を呼び出す
        updateRTMAnimationState();
        if (scriptEngine != null) {
            RTMScriptSystem.invokeScriptTick(scriptEngine, this);
        }

        if (!level().isClientSide()) {
            float speed = getSpeed();
            int notch = getNotch();

            Entity controller = getDriverPassenger();

            if (!(controller instanceof Player)) {
                // 運転者不在時は惰行側へ戻す
                notch = 0;
            }

            // ノッチに応じたRTM風カーブ
            speed = applyNotchPhysics(speed, notch);

            setNotch(notch);

            // 惰行＋空気抵抗（RTM風の減速感）。力行中はマスコン位置を保持して加速を優先する。
            if (notch <= 0) {
                float drag = DRAG_BASE + Math.abs(speed) * DRAG_SPEED_FACTOR;
                speed = approachZero(speed, drag);
            }
            speed *= FRICTION;
            if (Math.abs(speed) < 0.001f) speed = 0.0f;

            setSpeed(speed);

            if (!travelAlongRail(speed)) {
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

            // サーバー同期を強める
            this.hurtMarked = true;
            this.hasImpulse = true;
        }
    }

    private float applyNotchPhysics(float speed, int notch) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        float maxSpeed = getConfiguredMaxSpeed(def, notch);
        float accelBase = getConfiguredAcceleration(def);
        float absSpeed = Math.abs(speed);
        float speedRatio = Mth.clamp(absSpeed / maxSpeed, 0.0F, 1.0F);

        if (notch > 0) {
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

    private void updateRTMAnimationState() {
        doorMoveL = approach(doorMoveL, isDoorOpen() ? 60.0F : 0.0F, 1.0F);
        doorMoveR = approach(doorMoveR, isDoorOpen() ? 60.0F : 0.0F, 1.0F);
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
        follower.setReverse(this.isReverse());

        double yawRad = Math.toRadians(getYRot());
        double gap = Math.max(3.0D, getTrainDistance() * 2.0D);
        double x = getX() + Math.sin(yawRad) * gap;
        double z = getZ() - Math.cos(yawRad) * gap;
        follower.moveTo(x, getY(), z, getYRot(), follower.getXRot());
        follower.hurtMarked = true;
        follower.hasImpulse = true;
    }

    private void syncCoupledChain() {
        syncCoupledFollower();
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
            next.setReverse(current.isReverse());

            double yawRad = Math.toRadians(current.getYRot());
            double gap = Math.max(3.0D, current.getTrainDistance() * 1.9D);
            double x = current.getX() + Math.sin(yawRad) * gap;
            double z = current.getZ() - Math.cos(yawRad) * gap;
            next.moveTo(x, current.getY(), z, current.getYRot(), next.getXRot());
            next.hurtMarked = true;
            next.hasImpulse = true;

            current = next;
        }
    }

    private record RailFollowContext(RailMap map, int split, int nearestIndex, double distanceSq) {}
    private record RailSample(double x, double y, double z) {}

    private boolean travelAlongRail(float speed) {
        RailFollowContext context = getActiveRailContext();
        if (context == null) {
            return false;
        }

        RailMap map = context.map();
        int split = context.split();
        if (split <= 0) {
            return false;
        }

        int nearest = activeRailMap == map && activeRailIndex >= 0 ? activeRailIndex : context.nearestIndex();
        float progress = nearest / (float) split;
        setRailProgress(progress);

        double trackLen = Math.max(0.001D, map.getLength());
        int direction = activeRailDirection == 0 ? 1 : activeRailDirection;

        double nearestDistance = progress * trackLen;
        double sampleStep = trackLen / split;
        if (Math.abs(speed) < 0.0005F) {
            railDistanceCarry = 0.0D;
        }
        double requestedDistance = Math.abs(speed) + railDistanceCarry;
        int stepSamples = requestedDistance < sampleStep ? 0 : (int) Math.floor(requestedDistance / sampleStep);
        railDistanceCarry = requestedDistance - stepSamples * sampleStep;
        int nextIndex = nearest + direction * stepSamples;
        if (nextIndex < 0 || nextIndex > split) {
            if (transitionToConnectedRail(map, split, direction)) {
                return true;
            }
            setSpeed(0.0F);
            setNotch(0);
            setDeltaMovement(Vec3.ZERO);
            return true;
        }

        double[] bogieZ = getBogieRailOffsets();
        int frontIndex = Mth.clamp(nextIndex + direction * (int) Math.round(bogieZ[1] / sampleStep), 0, split);
        int rearIndex = Mth.clamp(nextIndex + direction * (int) Math.round(bogieZ[0] / sampleStep), 0, split);
        RailSample front = sampleRail(map, split, frontIndex);
        RailSample rear = sampleRail(map, split, rearIndex);
        RailSample center = sampleRail(map, split, nextIndex);
        double dxBogie = front.x - rear.x;
        double dyBogie = front.y - rear.y;
        double dzBogie = front.z - rear.z;
        double horizontal = Math.sqrt(dxBogie * dxBogie + dzBogie * dzBogie);
        float yaw = horizontal > 1.0E-4D
            ? (float) Math.toDegrees(Math.atan2(-dxBogie, dzBogie))
            : map.getRailYaw(split, nextIndex);
        float pitch = horizontal > 1.0E-4D
            ? (float) Math.toDegrees(Math.atan2(dyBogie, horizontal))
            : map.getRailPitch(split, nextIndex);

        Vec3 currentPos = position();
        Vec3 target = new Vec3(center.x, center.y, center.z);
        Vec3 smoothed = Math.abs(speed) < 0.0005F
            ? new Vec3(currentPos.x, center.y, currentPos.z)
            : currentPos.lerp(target, 0.55D);

        moveTo(smoothed.x, smoothed.y, smoothed.z, yaw, pitch);
        setYRot(yaw);
        setXRot(pitch);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        setRailProgress(nextIndex / (float) split);
        activeRailMap = map;
        activeRailSplit = split;
        activeRailIndex = nextIndex;
        return true;
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
        activeRailDirection = next.nearestIndex() <= next.split() / 2 ? 1 : -1;
        railDistanceCarry = 0.0D;
        setRailProgress(activeRailIndex / (float) activeRailSplit);
        return true;
    }

    private RailSample sampleRail(RailMap map, int split, int index) {
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
        if (activeRailMap != null && activeRailIndex >= 0 && activeRailSplit > 0) {
            double trackLen = Math.max(0.001D, activeRailMap.getLength());
            double sampleStep = trackLen / activeRailSplit;
            int bogieIndex = activeRailIndex + activeRailDirection * (int) Math.round(bogie.position().z / sampleStep);
            bogieIndex = Mth.clamp(bogieIndex, 0, activeRailSplit);
            return relativeBogieYaw(activeRailMap.getRailYaw(activeRailSplit, bogieIndex), baseYaw);
        }

        RailFollowContext context = findRailContextNear(localToWorld(bogie.position()), null);
        if (context == null) {
            return 0.0F;
        }
        return relativeBogieYaw(context.map().getRailYaw(context.split(), context.nearestIndex()), baseYaw);
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
            float yawAtRail = activeRailMap.getRailYaw(activeRailSplit, activeRailIndex);
            float yawDiff = Math.abs(Mth.wrapDegrees(getYRot() - yawAtRail));
            int mapForwardSign = yawDiff <= 90.0F ? 1 : -1;
            int cabSign = getCabDirectionSign(getDriverPassenger()) >= 0.0F ? 1 : -1;
            activeRailDirection = mapForwardSign * cabSign;
        }
        return nearest;
    }

    private RailFollowContext findRailContextNear(Vec3 worldPos, RailMap exclude) {
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
                        boolean nearEndpoint = nearest <= Math.max(4, split / 8) || nearest >= split - Math.max(4, split / 8);
                        if (!nearEndpoint || distSq > 9.0D) {
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
            tail.coupledFollowerUuid = nearest.getUUID();
            nearest.coupledLeaderUuid = tail.getUUID();
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
        tail.coupledFollowerUuid = other.getUUID();
        other.coupledLeaderUuid = tail.getUUID();
        other.setNotch(this.getNotch());
        other.setSpeed(this.getSpeed());
        other.setReverse(this.isReverse());
    }

    private void enterCouplingMode(Player player) {
        if (level().isClientSide()) {
            return;
        }
        COUPLING_MODE.put(player.getUUID(), this.getUUID());
        player.displayClientMessage(Component.literal("連結モード: 連結したい列車にゆっくり接触させてください"), true);
    }

    private void tryCompletePendingCoupling() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel) || COUPLING_MODE.isEmpty()) {
            return;
        }
        Map<UUID, UUID> pending = new HashMap<>(COUPLING_MODE);
        for (Map.Entry<UUID, UUID> entry : pending.entrySet()) {
            Entity sourceRaw = serverLevel.getEntity(entry.getValue());
            if (!(sourceRaw instanceof TrainEntity source) || !source.isAlive()) {
                COUPLING_MODE.remove(entry.getKey());
                continue;
            }
            if (source == this || !this.isAlive() || source.isConnectedTo(this)) {
                continue;
            }
            if (source.getBoundingBox().inflate(0.75D).intersects(this.getBoundingBox().inflate(0.75D))) {
                source.coupleWith(this);
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
        if (controller == null) {
            return isReverse() ? -1.0F : 1.0F;
        }
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        int assignedSeat = getAssignedSeatIndex(controller);
        int rearDriverIndex = resolveRearSeatIndex(def);
        float cabSign = assignedSeat == rearDriverIndex ? -1.0F : 1.0F;
        return isReverse() ? -cabSign : cabSign;
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

    // ---- RTM互換API（既存スクリプト向け） ----
    public float getTrainStateData(int stateType) {
        return getVehicleState(stateType);
    }

    public float getVehicleState(int stateType) {
        return switch (stateType) {
            case 0, 10 -> getSpeed() >= 0 ? 1.0F : -1.0F;
            case 1 -> getNotch();
            case 2 -> getRailProgress();
            default -> 0.0F;
        };
    }

    public float getTrainDirection() {
        return isReverse() ? -1.0F : 1.0F;
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

    public int getFormation() {
        return 0;
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

        public java.util.Map<String, Object> getDataMap() {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("headLight", train.isHeadlightOn() ? 1 : 0);
            map.put("door", train.isDoorOpen() ? 1 : 0);
            map.put("pantograph", train.isPantographUp() ? 1 : 0);
            map.put("destination", train.getDestinationIndex());
            map.put("sound", train.getSoundIndex());
            map.put("reverse", train.isReverse() ? 1 : 0);
            map.put("customButtons", train.getCustomButtonBits());
            map.put("railProgress", train.getRailProgress());
            map.put("connected", train.isConnected() ? 1 : 0);
            return map;
        }
    }

    public static final class BogieCompat {
        private final TrainEntity train;
        private final int index;

        public BogieCompat() {
            this(null, 0);
        }

        public BogieCompat(TrainEntity train, int index) {
            this.train = train;
            this.index = index;
        }

        public float getRotation() {
            if (train == null) {
                return 0.0F;
            }
            VehicleDefinition def = VehicleRegistry.getById(train.getVehicleId());
            if (def == null || def.getBogies().isEmpty()) {
                return train.getYRot();
            }
            int clamped = Mth.clamp(index, 0, def.getBogies().size() - 1);
            Vec3 bogie = def.getBogies().get(clamped).position();
            double yawRad = Math.toRadians(-train.getYRot());
            double forward = bogie.z * Math.cos(yawRad) + bogie.x * Math.sin(yawRad);
            return (float) (train.getYRot() + Math.signum(forward) * train.getSpeed() * 22.0F);
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

        int legacy = def != null ? def.getDriverSeatIndex() : -1;
        if (legacy >= 0 && legacy < seats.size()) {
            return legacy;
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
        // RTMの座標系: Z+が前方、MinecraftのYRot: 南が0、西が90（反時計回り）
        // RTMのモデルはZ+を前方として定義されているため、YRotで回転させる
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
        if (tag.contains("HeadlightOn")) setHeadlightOn(tag.getBoolean("HeadlightOn"));
        if (tag.contains("DoorOpen")) setDoorOpen(tag.getBoolean("DoorOpen"));
        if (tag.contains("PantographUp")) setPantographUp(tag.getBoolean("PantographUp"));
        if (tag.contains("Reverse")) setReverse(tag.getBoolean("Reverse"));
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
        tag.putBoolean("PantographUp", isPantographUp());
        tag.putBoolean("Reverse", isReverse());
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
