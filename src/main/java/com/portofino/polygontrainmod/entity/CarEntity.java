package com.portofino.polygontrainmod.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import static com.portofino.polygontrainmod.util.PolygonTrainModConstants.TICK_PER_SECOND;
import static com.portofino.polygontrainmod.util.UnitConverter.cm2m;
import static com.portofino.polygontrainmod.util.UnitConverter.kph2bpt;

/// 自動車Entityクラス
public final class CarEntity extends Entity {
    /// 車輪のX座標オフセット
    public static final float WHEEL_X_COORD = cm2m(72.47766876220703f);
    //    private static final EntityDataAccessor<Float> DATA_SPEED =
//        SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    // 自動車の情報

    /// 乗車定員
    private static final int RIDING_CAPACITY = 5;

    // モデル情報
    /// 前輪のZ座標
    public static final float WHEEL_F_COORD = cm2m(158.62274169921875f);
    /// 後輪のZ座標
    public static final float WHEEL_R_COORD = cm2m(-164.98480224609375f);
    /// 車輪のY座標
    public static final float WHEEL_Y_COORD = cm2m(37.28034973144531f);
    /// 車輪の半径
    public static final float WHEEL_RADIUS = WHEEL_Y_COORD;
    /// ホイールベースの距離
    private static final float WHEELBASE = WHEEL_F_COORD - WHEEL_R_COORD;

    // 性能
    /// 加速度（ブロック毎ティック毎ティック）
    private static final float ACCELERATION = 0.001f;
    /// 減速度 正の値（ブロック毎ティック毎ティック）
    private static final float DECELERATION = 0.02f;
    /// 惰性の減速度 正の値（ブロック毎ティック毎ティック）
    private static final float SLOWDOWN_DECELERATION = 0.1f;
    /// 前進の最高速度 120km/h -> 33.33…m/s -> 1.666…block/tick
    private static final float MAX_SPEED = kph2bpt(120.0f);

    /// 車両が停止しているとみなす速度の閾値
    private static final float SPEED_STOP_THRESHOLD = 0.01f;
    /// ステアリングレシオ
    public static final float STEERING_RATIO = 1 / 12.0f; // ステアリング角度は、ハンドルの回転角度の12分の1

    /// 左右入力中の1tick当たりのハンドル回転角度（度毎ティック）
    private static final float STEERING_WHEEL_ANGULAR_VELOCITY_MANIPULATED = 10.0f;
    /// セルフセンタリングによる1tick当たりのハンドル回転係数（度毎ティック）
    private static final float STEERING_WHEEL_ANGULAR_VELOCITY_SELF_CENTERING = 5.0f;
    /// ハンドルの最大回転角度 左右に1.75回転ずつ（度）
    private static final float STEERING_WHEEL_MAX_ANGLE = 630.0f;
    /// ハンドルの回転角度
    public float currentSteeringWheelAngle = 0.0f; // 単位: 度
    /// 前回tickでのハンドルの回転角度
    public float prevSteeringWheelAngle = 0.0f;

    /// 車輪の回転角度 クライアントのみ
    public float wheelRotation = 0.0f;
    /// 前tickでの車輪の回転角度 クライアントのみ
    public float prevWheelRotation = 0.0f;

    /// 踏んでいる間のアクセル開度の変化量
    private static final float ACCELERATOR_STROKE_CHANGE_RATE = 1.0f / TICK_PER_SECOND / 3.0f; // 3秒でベタ踏み
    /// アクセル開度 0~1
    private float acceleratorStroke = 0.0f;
    /// 踏んでいる間のブレーキストロークの変化量
    private static final float BRAKE_STROKE_CHANGE_RATE = 1.0f / TICK_PER_SECOND; // 1秒でベタ踏み
    /// ブレーキのストローク量 0~1
    private float brakeStroke = 0.0f;
    /// ギアをリバースに入れているか
    private boolean isReversing = false;
    /// 現在ブレーキ中か
    private boolean isBraking = false;
    /// 前tickでのwSの値
    private float prevWs = 0.0f;
    /// ブレーキ中に停止してもキーを押し続けた際に、方向転換をロックする
    private boolean isReversalLocked = false;
    /// 速度 前進方向が正、後進方向が負
    public float speed = 0.0f;
    /// 現在のtickでのヨーの変化量（度）
    private float deltaYaw = 0.0f;


    public CarEntity(EntityType<? extends CarEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
//        this.acceleration = tag.getFloat("Acceleration");
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
//        tag.putFloat("Acceleration", this.acceleration);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
//        builder.define(DATA_SPEED, 0.0f);
    }

    /// 右クリックされた時の処理
    ///
    /// @param player 右クリックしたプレイヤー
    /// @param hand   メインハンドまたはオフハンド
    /// @return 処理の完了状態
    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        @SuppressWarnings("resource") // this.levelがAutoCloseableの警告を黙らす
        final var level = this.level();
        // 今後サーバーサイドであることを保証
        if (level.isClientSide) return InteractionResult.PASS;

        if (this.canAddPassenger(player)) {
            // 誰も乗っていない
            player.startRiding(this);
            return InteractionResult.sidedSuccess(false);
        }

        return InteractionResult.PASS;
    }

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) {
        return this.getPassengers().size() < RIDING_CAPACITY;
    }


    /// 操縦しているLivingEntity
    ///
    /// @return あればそのLivingEntity、なければnull
    @Override
    public LivingEntity getControllingPassenger() {
        final var passengers = this.getPassengers();
        final var controllingEntity = passengers.isEmpty() ? null : passengers.getFirst();
        return controllingEntity instanceof LivingEntity controllingLivingEntity ? controllingLivingEntity : null;
    }

    /// 渡された乗客Entityの着席位置
    ///
    /// @param passenger   乗客Entity
    /// @param dimensions  自動車の情報 寸法、目の高さなど
    /// @param partialTick なぜ？
    /// @return 位置のベクトル
    @Override
    @NotNull
    protected Vec3 getPassengerAttachmentPoint(@NotNull Entity passenger, @NotNull EntityDimensions dimensions, float partialTick) {
        // 友達がいないのでデバッグできません(泣)
        final var index = this.getPassengers().indexOf(passenger);

        final var baseOffset = calcBaseOffset(index, dimensions);

        final var yRot = this.getViewYRot(partialTick);
        final var rotatedHorizontalOffset = baseOffset.yRot((float) -Math.toRadians(yRot));

        return new Vec3(rotatedHorizontalOffset.x, baseOffset.y, rotatedHorizontalOffset.z);
    }

    private Vec3 calcBaseOffset(int index, EntityDimensions dimensions) {
        final var heightBase = dimensions.height() * 0.2;
        return switch (index) {
            case 0 -> new Vec3(-0.42, heightBase, 0.1);
            case 1 -> new Vec3(0.42, heightBase, 0.1);
            case 2 -> new Vec3(0.42, heightBase, -1.0);
            case 3 -> new Vec3(-0.42, heightBase, -1.0);
            case 4 -> new Vec3(0.0, heightBase, -1.0);
            default -> new Vec3(0.0, dimensions.height() * 0.9, 0.0); // nullが返せないので、Mr.ビーンの場所にしとく
        };
    }

    /// 乗客の向きを車両と同期するために使用 詳細不明
    @Override
    protected void positionRider(@NotNull Entity passenger, Entity.@NotNull MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (!(passenger instanceof Player player)) return;
        player.setYRot(player.getYRot() + this.deltaYaw);
    }

    /// 謎
    @Override
    public boolean canCollideWith(@NotNull Entity entity) {
        return true;
    }

    /// 体当たりをして押せるかどうかだと思われる
    ///
    /// @return 常に偽 自動車だし押せなくていいよね
    @Override
    public boolean isPushable() {
        return false;
    }

    /// クリック判定を発生させるかどうかだと思われる
    ///
    /// @return もちろん発生させる じゃないと乗れない
    @Override
    public boolean isPickable() {
        return true;
    }

    /// 用途不明
    @Override
    @NotNull
    public Packet<ClientGamePacketListener> getAddEntityPacket(@NotNull ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }

    /// 毎Tick呼び出される
    @Override
    public void tick() {
        super.tick();

        @SuppressWarnings("resource") final var level = this.level();

        this.prevSteeringWheelAngle = this.currentSteeringWheelAngle; // アニメーションのために前回tickの回転角度を保存

        // Entity#isControlledByLocalInstance は、自身が乗っている場合はクライアント、そうでなければサーバーでtrue
        // Entityの移動操作に使うとよいっぽい
        if (!this.isControlledByLocalInstance()) return;

        final var driver = this.getControllingPassenger();
        if (driver instanceof Player drivingPlayer) {
            this.handlePlayerInput(drivingPlayer);
        } else {
            // 操縦手がいない
            this.updatePedals(); // ペダルのストロークを更新
            this.updateSteeringAngle(); // ステアリング角度を更新
        }

        this.updateSpeed(); // 速度を更新
        this.applyMovement(); // 移動量を計算

        if (level.isClientSide) {
            updateWheelRotationInClient(); // 車輪の回転を反映
        }

        // 移動を実行
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    /// 運転しているプレイヤーの操作を反映する
    ///
    /// ### 動作
    /// - 進行方向のキーでアクセル（`acceleratorStroke`を増加）
    /// - 進行方向反対のキーでブレーキ（`brakeStroke`を増加）
    /// - ブレーキで停止したのち、一度離してもう一度押し始めると反対側に進行方向を転換（`isReversing`を逆転）し、
    ///   そのままその方向に加速を始める
    private void handlePlayerInput(Player player) {

        // プレイヤーの操作
        final float wS = player.zza; // W: 0.98, S: -0.98
        final float aD = player.xxa; // A: 0.98, D: -0.98

        if (wS > 0.0f) { // 前キー（W）
            final var justStartedW = this.prevWs <= 0.0f;
            if (!this.isReversing) { // 前進（アクセル）
                this.brakeStroke = 0.0f;
                this.isBraking = false;

                final var stroke = this.acceleratorStroke + ACCELERATOR_STROKE_CHANGE_RATE; // 踏む量を増やす
                this.acceleratorStroke = Math.clamp(stroke, 0.0f, 1.0f);
            } else { // 後進（ブレーキ）
                this.acceleratorStroke = 0;

                if (this.isStopping() && justStartedW && !this.isReversalLocked) { // 新たに押され、ロックされていない
                    this.isReversing = false; // 前進を開始
                    this.brakeStroke = 0.0f;

                    final var stroke = this.acceleratorStroke + ACCELERATOR_STROKE_CHANGE_RATE;
                    this.acceleratorStroke = Math.clamp(stroke, 0.0f, 1.0f);
                } else {
                    // 通常のブレーキ処理
                    if (this.speed >= -SPEED_STOP_THRESHOLD) { // 転換しない場合ロックをセット
                        this.isReversalLocked = true;
                    }
                    final var stroke = this.brakeStroke + BRAKE_STROKE_CHANGE_RATE;
                    this.brakeStroke = Math.clamp(stroke, 0.0f, 1.0f);
                }
            }
        } else if (wS < 0.0f) { // 後ろキー（S）
            final var justStartedS = this.prevWs >= 0.0f;
            if (this.isReversing) { // 後進（アクセル）
                this.brakeStroke = 0;
                this.isBraking = false;

                final var stroke = this.acceleratorStroke + ACCELERATOR_STROKE_CHANGE_RATE;
                this.acceleratorStroke = Math.clamp(stroke, 0.0f, 1.0f);
            } else { // 前進（ブレーキ）
                this.acceleratorStroke = 0.0f;

                if (this.isStopping() && justStartedS && !this.isReversalLocked) { // 新たに押され、ロックされていない
                    this.isReversing = true;
                    this.brakeStroke = 0.0f;

                    final var stroke = this.acceleratorStroke + ACCELERATOR_STROKE_CHANGE_RATE;
                    this.acceleratorStroke = Math.clamp(stroke, 0.0f, 1.0f);
                } else {
                    // 通常のブレーキ処理
                    if (this.speed <= SPEED_STOP_THRESHOLD) {
                        this.isReversalLocked = true;
                    }
                    final var stroke = this.brakeStroke + BRAKE_STROKE_CHANGE_RATE;
                    this.brakeStroke = Math.clamp(stroke, 0.0f, 1.0f);
                }
            }
        } else { // 操作されていない
            this.isReversalLocked = false; // 方向転換停止を解除

            // 無人の時と同様に処理
            this.updatePedals();
        }

        this.prevWs = wS; // wSを保存

        if (aD != 0) {
            final var angle = this.currentSteeringWheelAngle + Math.signum(aD) * -STEERING_WHEEL_ANGULAR_VELOCITY_MANIPULATED; // Aが正、Dが負だが、ヨーは逆
            this.currentSteeringWheelAngle = Math.clamp(angle, -STEERING_WHEEL_MAX_ANGLE, STEERING_WHEEL_MAX_ANGLE);
        } else { // 操作されていない
            // 無人の時と同様に処理
            this.updateSteeringAngle();
        }
    }
//         実際の移動
//        this.setYRot(this.getYRot() + turn);
//        this.setDeltaMovement(this.getDeltaMovement().x, this.getDeltaMovement().y, this.getDeltaMovement().z + forward);

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

    /// 操作されていないときに自然にペダルを処理する
    private void updatePedals() {
        // 踏んだ時と同じ割合で減らす
        // あるいは即時0？ どちらが実際の運転の感覚と似ているだろうか
        final var accelStroke = this.acceleratorStroke - ACCELERATOR_STROKE_CHANGE_RATE;
        this.acceleratorStroke = Math.clamp(accelStroke, 0.0f, 1.0f);
        final var brakeStroke = this.brakeStroke - BRAKE_STROKE_CHANGE_RATE;
        this.brakeStroke = Math.clamp(brakeStroke, 0.0f, 1.0f);
    }

    /// 操作されていないときに自然にステアリングを処理する
    private void updateSteeringAngle() {
        // 速度に応じてセルフセンタリングさせる処理
        // 前進では切れ角を減らし、後進では切れ角を増やす
    }

    /// 速度を更新する
    private void updateSpeed() {
        var speed = 0f;
        if (!isReversing) { // 前進
            if (this.acceleratorStroke > 0.0f) speed = this.speed + this.acceleratorStroke * ACCELERATION; // 加速
            if (this.brakeStroke > 0.0f) speed = this.speed - this.brakeStroke * DECELERATION; // 減速
            speed = Math.clamp(speed, 0.0f, MAX_SPEED);
        } else { // 後進
            if (this.acceleratorStroke > 0.0f) speed = this.speed - this.acceleratorStroke * ACCELERATION; // 後ろに加速
            if (this.brakeStroke > 0.0f) speed = this.speed + this.brakeStroke * DECELERATION; // 減速
            speed = Math.clamp(speed, -MAX_SPEED * 0.2f, 0.0f);
        }

        if (this.acceleratorStroke == 0 && this.brakeStroke == 0.0f) {
            if (this.speed > 0.0f) { // 前進
                speed = this.speed - this.speed * SLOWDOWN_DECELERATION; // 惰性での減速
                speed = Math.clamp(speed, 0, this.speed);
            } else if (this.speed < 0.0f) { // 後進
                speed = this.speed - this.speed * SLOWDOWN_DECELERATION;
                speed = Math.clamp(speed, this.speed, 0.0f);
            }
        }

        this.speed = Math.clamp(speed, -MAX_SPEED * 0.2f, MAX_SPEED);
    }


    /// アッカーマンジオメトリを遵守した四輪自動車の移動と回転の結果でdeltaMovementとyawを更新
    private void applyMovement() {
        // 正接で面倒が起きないようにステアリング角度が0度に近い場合は直接前進
        if (Math.abs(this.currentSteeringWheelAngle) < 1) {
            final var movement = Vec3.directionFromRotation(0, this.getYRot()).scale(this.speed);
            this.setDeltaMovement(movement);
            return;
        }

        // 実舵角（ラジアン）
        final double steerAngle = Math.toRadians(this.currentSteeringWheelAngle * STEERING_RATIO); // [要確認] STEERING_RATIO

        // 後輪軸基準の旋回半径
        final double R = WHEELBASE / Math.tan(steerAngle);

        // 1tick あたりのヨー変化量（後輪軸速度 = this.speed と仮定）
        final double dYawRad = this.speed / R;
        final float dYawDeg = (float) Math.toDegrees(dYawRad);
        this.deltaYaw = dYawDeg;

        final float currentYaw = this.getYRot();
        final Vec3 forward = Vec3.directionFromRotation(0, currentYaw);

        // ① エンティティ原点 → 後輪軸のワールド座標
        //    WHEEL_R_COORD < 0 なので forward.scale(WHEEL_R_COORD) は後方向
        final Vec3 rearAxlePos = this.position().add(forward.scale(WHEEL_R_COORD));

        // ② ICR = 後輪軸の右方向に距離 R
        //    Minecraft の YRot は時計回りが正なので +90 で右方向になる
        final Vec3 rightVec = Vec3.directionFromRotation(0, currentYaw + 90.0f);
        final Vec3 icrPos = rearAxlePos.add(rightVec.scale(R));

        // ③ 後輪軸を ICR 周りに dYawRad だけ回転
        //    Minecraft XZ 平面（上から見て時計回りが正）の回転行列:
        //      x' = cx + cos(θ)·dx - sin(θ)·dz
        //      z' = cz + sin(θ)·dx + cos(θ)·dz
        final double dx = rearAxlePos.x - icrPos.x;
        final double dz = rearAxlePos.z - icrPos.z;
        final double cos = Math.cos(dYawRad);
        final double sin = Math.sin(dYawRad);
        final Vec3 newRearAxlePos = new Vec3(
            icrPos.x + cos * dx - sin * dz,
            rearAxlePos.y,
            icrPos.z + sin * dx + cos * dz
        );

        // ④ 新しいヨーと前方向を確定
        final float newYaw = currentYaw + dYawDeg;
        final Vec3 newForward = Vec3.directionFromRotation(0, newYaw);

        // ⑤ 後輪軸からエンティティ原点を逆算
        //    entityPos = rearAxlePos - forward * WHEEL_R_COORD
        //              = rearAxlePos + forward * |WHEEL_R_COORD|  （WHEEL_R_COORD < 0）
        final Vec3 newEntityPos = newRearAxlePos.subtract(newForward.scale(WHEEL_R_COORD));

        // ⑥ deltaMovement と yaw を設定
        this.setDeltaMovement(newEntityPos.subtract(this.position()));
        this.setYRot(newYaw);

    }

    /// 車輪の回転角度を更新する クライアントのみ
    private void updateWheelRotationInClient() {
        this.prevWheelRotation = this.wheelRotation;

        final var deltaRotation = this.speed / WHEEL_RADIUS;
        this.wheelRotation += deltaRotation; // 直接加算 340潤ラジアン回ることは多分ないと信じる
    }

    private boolean isStopping() {
        return Math.abs(this.speed) < SPEED_STOP_THRESHOLD;
    }

    private void placeMarker(double x, double y, double z) {
        @SuppressWarnings("resource") final var level = this.level();
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
    }

    private void placeMarker(Vec3 vector) {
        this.placeMarker(vector.x, vector.y, vector.z);
    }

    private void placeLine(Vec3 start, Vec3 end) {
        final var direction = end.subtract(start).normalize();
        final var distance = start.distanceTo(end);

        for (var d = 0.0; d < distance; d += 0.1) {
            var pos = start.add(direction.scale(d));
            this.placeMarker(pos);
        }
    }
}