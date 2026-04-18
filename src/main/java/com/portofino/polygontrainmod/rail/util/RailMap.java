package com.portofino.polygontrainmod.rail.util;

import com.portofino.polygontrainmod.block.BallastBlock;
import com.portofino.polygontrainmod.block.RailCollisionBlock;
import com.portofino.polygontrainmod.block.MarkerBlock;
import com.portofino.polygontrainmod.rail.math.BezierCurve;
import com.portofino.polygontrainmod.rail.math.CurveMath;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.List;

/**
 * legacy RailMap 移植。道床用の座標列生成（レガシー）と、レール配置可否の判定を担当。
 * レールビジュアル(MQOモデル)は LargeRailCoreBlockEntity が別途担当する。
 * 道床ブロックのワールド配置は行わない（軽量化のため中心線のみ検査・撤去）。
 */
public abstract class RailMap {
    public static boolean suppressRailRemoval = false;
    protected final List<int[]> rails = new ArrayList<>();

    public abstract RailPosition getStartRP();
    public abstract RailPosition getEndRP();
    public abstract double getLength();
    public abstract int getNearlestPoint(int split, double x, double z);
    public abstract double[] getRailPos(int split, int index);
    public abstract double getRailHeight(int split, int index);
    public abstract float getRailYaw(int split, int index);
    public abstract float getRailPitch(int split, int index);
    public abstract float getRailRoll(int split, int index);

    public float getCant(int split, int index) {
        return this.getRailRoll(split, index);
    }

    /** legacy の {@code getRailRotation} と同じ（ヨー角）。 */
    public float getRailRotation(int split, int index) {
        return this.getRailYaw(split, index);
    }

    /** 水平が直線のレール区間か（見た目のゲージ調整用）。 */
    public boolean isStraightTrack() {
        return false;
    }

    /**
     * 水平ベジェの弧長に基づく分割数。{@link BezierCurve#splitForLength(double)} と同一（レンダラの {@code max} 用）。
     * {@link #getLength()} は勾配で 3D 長になるため、曲線のサンプル数には {@link #getHorizontalPathLength()} を使うこと。
     */
    public double getHorizontalPathLength() {
        return this.getLength();
    }

    /**
     * 曲線計算に渡す分割数。{@link BezierCurve} の内部 {@code split} と一致させる。
     */
    public static int curveSplitForLength(double length) {
        return BezierCurve.splitForLength(length);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RailMap rm)) {
            return false;
        }
        return this.getStartRP().equals(rm.getStartRP())
            && this.getEndRP().equals(rm.getEndRP());
    }

    @Override
    public int hashCode() {
        int result = getStartRP().hashCode();
        result = 31 * result + getEndRP().hashCode();
        return result;
    }

    /**
     * 道床ブロック位置リストを生成する。
     * ballastWidth に応じて線路幅方向にブロックを展開する。
     */
    protected void createRailList(RailProperties prop) {
        this.rails.clear();
        int halfWidth = prop.ballastWidth >> 1;
        int split = (int) (this.getLength() * 4.0D);
        if (split < 2) return;
        double halfPi = Math.PI / 2.0D;

        for (int j = 1; j < split - 1; ++j) {
            double[] point = this.getRailPos(split, j);
            double x = point[1];
            double z = point[0];
            double slope = CurveMath.toRadians(this.getRailYaw(split, j));
            double height = this.getRailHeight(split, j);
            int y = (int) height;

            for (int w = 0; w <= halfWidth; ++w) {
                double d0 = (double) w + 0.25D;
                int x1 = CurveMath.floor(x + Math.sin(slope + halfPi) * d0);
                int z1 = CurveMath.floor(z + Math.cos(slope + halfPi) * d0);
                this.addRailBlock(x1, y, z1);
                int x2 = CurveMath.floor(x + Math.sin(slope - halfPi) * d0);
                int z2 = CurveMath.floor(z + Math.cos(slope - halfPi) * d0);
                this.addRailBlock(x2, y, z2);
            }
            int x0 = CurveMath.floor(x);
            int z0 = CurveMath.floor(z);
            this.addRailBlock(x0, y, z0);
        }
    }

    protected void addRailBlock(int x, int y, int z) {
        for (int i = 0; i < this.rails.size(); ++i) {
            int[] ia = this.rails.get(i);
            if (ia[0] == x && ia[2] == z) {
                if (ia[1] <= y) return;
                this.rails.remove(i);
                --i;
            }
        }
        BlockPos pos = new BlockPos(x, y, z);
        if (!pos.equals(this.getStartRP().getNeighborBlockPos()) && !pos.equals(this.getEndRP().getNeighborBlockPos())) {
            this.rails.add(new int[]{x, y, z});
        }
    }

    /**
     * 道床ブロックを配置する。ブロックエンティティへの参照は持たない（道床とレールの分離）。
     */
    public void setRail(Level level, Block ballastBlock, int x0, int y0, int z0, RailProperties prop) {
        this.createRailList(prop);
        for (int[] rail : this.rails) {
            BlockPos pos = new BlockPos(rail[0], rail[1], rail[2]);
            Block existing = level.getBlockState(pos).getBlock();
            // 既存の道床・空気・マーカーの上のみ配置
            if (existing instanceof BallastBlock || existing == Blocks.AIR
                    || existing == Blocks.CAVE_AIR || existing == Blocks.VOID_AIR
                    || existing instanceof MarkerBlock) {
                level.setBlock(pos, ballastBlock.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        this.rails.clear();
    }

    /**
     * レールを置けるか。道床ボリューム全体は走査せず、中心線をブロック程度の間隔でサンプルする
     * （legacyの道床グリッド検査より軽い）。
     */
    public boolean canPlaceRail(Level level, boolean isCreative, RailProperties prop) {
        double len = this.getLength();
        int samples = Math.max(3, (int) Math.ceil(len) + 1);
        int split = curveSplitForLength(this.getHorizontalPathLength());
        BlockPos startNeighbor = this.getStartRP().getNeighborBlockPos();
        BlockPos endNeighbor = this.getEndRP().getNeighborBlockPos();
        boolean allClear = true;
        for (int i = 0; i < samples; i++) {
            int j = samples <= 1 ? 0 : (int) Math.round((double) split * i / (samples - 1));
            if (j > split) j = split;
            double[] point = this.getRailPos(split, j);
            int x = CurveMath.floor(point[1]);
            int z = CurveMath.floor(point[0]);
            int y = (int) this.getRailHeight(split, j);
            BlockPos pos = new BlockPos(x, y, z);
            if (pos.equals(startNeighbor) || pos.equals(endNeighbor)) {
                continue;
            }
            Block block = level.getBlockState(pos).getBlock();
            boolean passable = block == Blocks.AIR
                || block == Blocks.CAVE_AIR
                || block == Blocks.VOID_AIR
                || block instanceof MarkerBlock
                || block instanceof BallastBlock;
            if (!passable) {
                allClear = false;
                if (!isCreative) return false;
            }
        }
        return isCreative || allClear;
    }

    public List<int[]> getRailBlockList(RailProperties prop, boolean regenerate) {
        if (this.rails.isEmpty() || regenerate) {
            this.createRailList(prop);
        }
        return new ArrayList<>(this.rails);
    }

    /** 旧道床ブロックを中心線沿いに軽量スキャンして撤去する。レールコアは別途削除する。 */
    public void removeRailBlocks(Level level) {
        double len = this.getLength();
        int split = curveSplitForLength(this.getHorizontalPathLength());
        int samples = Math.max(3, split + 1);
        for (int i = 0; i < samples; i++) {
            int j = samples <= 1 ? 0 : (int) Math.round((double) split * i / (samples - 1));
            if (j > split) j = split;
            double[] point = this.getRailPos(split, j);
            int x = CurveMath.floor(point[1]);
            int z = CurveMath.floor(point[0]);
            int y = (int) this.getRailHeight(split, j);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 0; dy++) {
                        BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
                        Block block = level.getBlockState(pos).getBlock();
                        if (block instanceof BallastBlock || block instanceof RailCollisionBlock) {
                            level.removeBlock(pos, false);
                        }
                    }
                }
            }
        }
    }
}
