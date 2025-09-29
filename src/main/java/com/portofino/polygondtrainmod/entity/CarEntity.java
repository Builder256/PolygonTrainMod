package com.portofino.polygondtrainmod.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class CarEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_SPEED =
        SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);

    private float acceleration = 0.0f;
    private final float maxSpeed = 0.4f;
    private float friction = 0.95f;
    private final float turnSpeed = 2.0f;

    public CarEntity(EntityType<? extends CarEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.acceleration = tag.getFloat("Acceleration");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Acceleration", this.acceleration);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SPEED, 0.0f);
    }

    @Override
    @NotNull
    public InteractionResult interact(Player player, InteractionHand hand) {
        // 今後サーバーサイドであることを保証
        if (this.level().isClientSide) return InteractionResult.PASS;

        if (this.getPassengers().isEmpty()) {
            // 誰も乗っていない
            player.startRiding(this);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        Entity controllingEntity = this.getPassengers().isEmpty() ? null : this.getPassengers().getFirst();
        return controllingEntity instanceof LivingEntity controllingLivingEntity ? controllingLivingEntity : null;
    }

    @Override
    @NotNull
    protected Vec3 getPassengerAttachmentPoint(@NotNull Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0D, dimensions.height() * 0.75D, 0.0D);
    }

    @Override
    public boolean canCollideWith(@NotNull Entity entity) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * クリック判定を発生させるかどうか
     *
     * @return 発生させるか
     */
    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            this.serverTick();
        } else {
            this.clientTick();
        }
    }

    private void serverTick() {
        // 空中にいるときに落下を再現
        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.08, 0));
        }
        Entity driver = this.getControllingPassenger();
        if (driver instanceof Player player) {
            // プレイヤーの操作を受け付ける
            this.handlePlayerInput(player);
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    private void clientTick() {

    }

    private void handlePlayerInput(Player player) {
//
//        // 前後進の係数
//        float forward = 0.0f;
//        // Wキー
//        if (player.zza > 0) forward = 1.0f;
//        // Sキー
//        if (player.zza < 0) forward = -0.5f;
//
//        float turn = 0.0f;
//        // Dキー
//        if (player.xxa > 0) turn = 1.0f;
//        // Aキー
//        if (player.xxa < 0) turn = -1.0f;
//
//        if (forward != 0) {
//            acceleration += forward * 0.02f;
//            acceleration = Math.clamp(acceleration, -maxSpeed, maxSpeed);
            // 移動方向
//            // Entity#yRotはたぶん度数法
//            float yaw = this.getYRot() * (float) Math.PI / 180.0f;
//            Vec3 forwardVec = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
//            Vec3 motion = forwardVec.scale(acceleration);

            // 問答無用でX+へ移動
            this.setDeltaMovement(1.0f, this.getDeltaMovement().y, this.getDeltaMovement().z);
            // 回転処理（移動中のみ）
//            if (Math.abs(acceleration) > 0.01f) {
//                this.setYRot(this.getYRot() + turn * turnSpeed * Math.signum(acceleration));
//            }
//        }
//        this.getEntityData().set(DATA_SPEED, Math.abs(acceleration));
    }
}