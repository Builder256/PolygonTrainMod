package com.portofino.polygontrainmod.entity;

import com.portofino.polygontrainmod.PolygonTrainMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 自動車Entityクラス<br>
 * <strong>Entityについて</strong><br>
 * Entityは、BlockやItemと異なり、1つの実体に対して必ず1つのインスタンスを持つ。それによって、より多くの状態と処理を実装できる。
 */
public class CarEntity extends Entity {
//    private static final EntityDataAccessor<Float> DATA_SPEED =
//        SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);

    private float momentum;
    private float deltaRotation;
    private float acceleration = 0.0f;
    private final float maxSpeed = 0.4f;
    private float friction = 0.95f;
    private final float turnSpeed = 2.0f;

    public CarEntity(EntityType<? extends CarEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
//        this.acceleration = tag.getFloat("Acceleration");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
//        tag.putFloat("Acceleration", this.acceleration);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
//        builder.define(DATA_SPEED, 0.0f);
    }

    /**
     * 右クリックされた時の処理
     *
     * @param player 右クリックしたプレイヤー
     * @param hand   メインハンドまたはオフハンド
     * @return 処理の完了状態
     */
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

    /**
     * 操縦している乗客<br>
     * 本来は常にnullなので乗客がいた時にそれを返却するように更新
     *
     * @return 乗客
     */
    @Override
    public LivingEntity getControllingPassenger() {
        Entity controllingEntity = this.getPassengers().isEmpty() ? null : this.getPassengers().getFirst();
        return controllingEntity instanceof LivingEntity controllingLivingEntity ? controllingLivingEntity : null;
    }

    /**
     * よくわからん。
     *
     * @param passenger
     * @param dimensions
     * @param scale
     * @return
     */
    @Override
    @NotNull
    protected Vec3 getPassengerAttachmentPoint(@NotNull Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0D, dimensions.height() * 0.75D, 0.0D);
    }

    /**
     * 謎
     */
    @Override
    public boolean canCollideWith(@NotNull Entity entity) {
        return true;
    }

    /**
     * 体当たりをして押せるかどうかだと思われる。
     *
     * @return 常に偽 自動車だし押せなくていいよね
     */
    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * クリック判定を発生させるかどうかのようだ
     *
     * @return もちろん発生させる じゃないと乗れない
     */
    @Override
    public boolean isPickable() {
        return true;
    }


    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }

    /**
     * 毎Tick呼び出される
     */
    @Override
    public void tick() {
        super.tick();

//        if (!this.level().isClientSide()) {
//            this.serverTick();
//        } else {
//            this.clientTick();
//        }


//        if (this.isControlledByLocalInstance()) {
//            this.updateInputs();
//            this.moveVehicle();

//            if (this.level().isClientSide()) {
//                this.controlVehicle();
//            }
//        } else {
//            this.setDeltaMovement(Vec3.ZERO);
//        }

//        this.checkInsideBlocks();

        if (this.isControlledByLocalInstance()) {
            Entity driver = this.getControllingPassenger();
            if (driver instanceof Player player) handlePlayerInput(player);

//            this.setDeltaMovement(1.0F, this.getDeltaMovement().y, this.getDeltaMovement().z);
            this.move(MoverType.SELF, this.getDeltaMovement());
        }

    }

    private void updateInputs() {
        Entity controller = this.getControllingPassenger();
        if (controller instanceof Player) {
//            PolygonTrainMod.LOGGER.info("the controller is a Player!");
            this.momentum = 0.0F;
        } else {
//            PolygonTrainMod.LOGGER.info("the controller is not a Player!");
        }
    }

    private void controlVehicle() {
        Entity controller = this.getControllingPassenger();
        if (controller instanceof Player player) {
            float forward = player.zza;
            float strafe = player.xxa;
            String str = String.valueOf(forward + ',' + strafe);
            PolygonTrainMod.LOGGER.info(str);

            if (forward > 0.0F) {
                this.momentum += this.acceleration;
            } else if (forward < 0.0F) {
                this.momentum -= this.acceleration * 0.5F;
            } else {
                this.momentum *= 0.95F;
            }

            // 最大速度内に制限
            this.momentum = Math.clamp(this.momentum, -this.maxSpeed, this.maxSpeed);

            if (Math.abs(this.momentum) > 0.01F) {
                this.deltaRotation = -strafe;
            } else {
                this.deltaRotation = 0.0F;
            }
        } else {
            this.momentum = 0.0F;
            this.deltaRotation = 0.0F;
        }
    }

    private void moveVehicle() {
        // ちょっとでも動いているときだけ車体を回転させる（超信地旋回を拒否）
        if (Math.abs(this.deltaRotation) > 0.001F && Math.abs(this.momentum) > 0.01F) {
            float rotation = this.deltaRotation * this.turnSpeed;
            this.setYRot(this.getYRot() + rotation);
        }

        double motionX = 0.0;
        double motionY = this.getDeltaMovement().y;
        double motionZ = 0.0;

        if (Math.abs(this.momentum) > 0.001F) {
            float yaw = this.getYRot() * ((float) Math.PI / 180F);
            motionX = (double) (-Mth.sin(yaw) * this.momentum);
            motionZ = (double) (Mth.cos(yaw) * this.momentum);
        }

        if (!this.onGround()) {
            motionY -= 0.04;
        } else {
            motionY = 0.0;
        }

        this.setDeltaMovement(motionX, motionY, motionZ);

        if (this.isControlledByLocalInstance()) {
//            PolygonTrainMod.LOGGER.info("call this.move!");
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
    }


    private void handlePlayerInput(Player player) {
        // 前後進の係数
        float forward = 0.0f;
        // Wキーが押されると0.98, Sキーが押されると-0.98, 両方は知らん
        float W_S = player.zza;
        // Aキーが押されると0.98, Dキーが押されると-0.98, 両方は知らん
        float A_D = player.xxa;

        PolygonTrainMod.LOGGER.info(String.valueOf(W_S) + ',' + A_D);
        // 前進
        if (W_S > 0) forward = 1.0f;
        // 後進
        if (W_S < 0) forward = -1.0f;

        float turn = 0.0f;
        // 左旋回
        if (A_D > 0) turn = 1.0f;
        // 右旋回
        if (A_D < 0) turn = -1.0f;

        // それはそうと適当に操作を反映
        this.setDeltaMovement(-turn, this.getDeltaMovement().y, -forward);

//
//        if (forward != 0) {
//            acceleration += forward * 0.02f;
//            acceleration = Math.clamp(acceleration, -maxSpeed, maxSpeed);
//            // 移動方向
//            // Entity#yRotはたぶん度数法
//            float yaw = this.getYRot() * (float) Math.PI / 180.0f;
//            Vec3 forwardVec = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
//            Vec3 motion = forwardVec.scale(acceleration);
//
//            // 問答無用でX+へ移動
//            this.setDeltaMovement(1.0f, this.getDeltaMovement().y, this.getDeltaMovement().z);
//            // 回転処理（移動中のみ）
//            if (Math.abs(acceleration) > 0.01f) {
//                this.setYRot(this.getYRot() + turn * turnSpeed * Math.signum(acceleration));
//            }
//        }
//        this.getEntityData().set(DATA_SPEED, Math.abs(acceleration));
    }
}