package com.portofino.polygontrainmod.rail.util;

import com.portofino.polygontrainmod.rail.math.NgtMath;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Port of jp.ngt.rtm.rail.util.RailPosition (subset used by PolygonTrainMod rails).
 */
public final class RailPosition {
    public static final float[][] REVISION = new float[][]{
        {0.0F, -0.5F}, {-0.5F, -0.5F}, {-0.5F, 0.0F}, {-0.5F, 0.499999F},
        {0.0F, 0.499999F}, {0.499999F, 0.499999F}, {0.499999F, 0.0F}, {0.499999F, -0.5F}
    };
    public int blockX;
    public int blockY;
    public int blockZ;
    public final byte switchType;
    public byte direction;
    public byte height;
    public float anchorYaw;
    public float anchorPitch;
    public float anchorLengthHorizontal;
    public float anchorLengthVertical;
    public float cantCenter;
    public float cantEdge;
    public float cantRandom;
    public float constLimitHP;
    public float constLimitHN;
    public float constLimitWP;
    public float constLimitWN;
    public double posX;
    public double posY;
    public double posZ;

    public RailPosition(int x, int y, int z, int dir, int type) {
        this.blockX = x;
        this.blockY = y;
        this.blockZ = z;
        this.direction = (byte) dir;
        this.switchType = (byte) type;
        this.height = 0;
        this.anchorYaw = NgtMath.wrapAngle((float) dir * 45.0F);
        this.anchorLengthHorizontal = -1.0F;
        this.constLimitHP = 3.99F;
        this.constLimitHN = 0.0F;
        this.constLimitWP = 1.49F;
        this.constLimitWN = -1.49F;
        this.init();
    }

    public void init() {
        this.posX = (double) this.blockX + 0.5D + (double) REVISION[this.direction & 7][0];
        this.posY = (double) this.blockY + (double) (this.height + 1) * 0.0625D;
        this.posZ = (double) this.blockZ + 0.5D + (double) REVISION[this.direction & 7][1];
    }

    public void addHeight(double par1) {
        int h2 = (int) (par1 / 0.0625D);
        this.height = (byte) (this.height + h2);
        this.init();
    }

    public static RailPosition readFromNBT(CompoundTag nbt) {
        int[] pos = nbt.getIntArray("BlockPos");
        if (pos.length < 3) return null;
        byte b0 = nbt.getByte("Direction");
        byte b2 = nbt.getByte("SwitchType");
        RailPosition rp = new RailPosition(pos[0], pos[1], pos[2], b0, b2);
        rp.setHeight(nbt.getByte("Height"));
        rp.anchorYaw = nbt.getFloat("A_Direction");
        rp.anchorPitch = nbt.getFloat("A_Pitch");
        rp.anchorLengthHorizontal = nbt.getFloat("A_Length");
        rp.anchorLengthVertical = nbt.getFloat("A_LenV");
        rp.cantCenter = nbt.getFloat("C_Center");
        rp.cantEdge = nbt.getFloat("C_Edge");
        rp.cantRandom = nbt.getFloat("C_Random");
        rp.constLimitHP = nbt.getFloat("Const_Limit_HP");
        rp.constLimitHN = nbt.getFloat("Const_Limit_HN");
        rp.constLimitWP = nbt.getFloat("Const_Limit_WP");
        rp.constLimitWN = nbt.getFloat("Const_Limit_WN");
        rp.init();
        return rp;
    }

    public CompoundTag writeToNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putIntArray("BlockPos", new int[]{this.blockX, this.blockY, this.blockZ});
        nbt.putByte("SwitchType", this.switchType);
        nbt.putByte("Direction", this.direction);
        nbt.putByte("Height", this.height);
        nbt.putFloat("A_Direction", this.anchorYaw);
        nbt.putFloat("A_Pitch", this.anchorPitch);
        nbt.putFloat("A_Length", this.anchorLengthHorizontal);
        nbt.putFloat("A_LenV", this.anchorLengthVertical);
        nbt.putFloat("C_Center", this.cantCenter);
        nbt.putFloat("C_Edge", this.cantEdge);
        nbt.putFloat("C_Random", this.cantRandom);
        nbt.putFloat("Const_Limit_HP", this.constLimitHP);
        nbt.putFloat("Const_Limit_HN", this.constLimitHN);
        nbt.putFloat("Const_Limit_WP", this.constLimitWP);
        nbt.putFloat("Const_Limit_WN", this.constLimitWN);
        return nbt;
    }

    public void setHeight(byte par1) {
        this.height = par1;
        this.posY = (double) this.blockY + (double) (par1 + 1) * 0.0625D;
    }

    public BlockPos getNeighborBlockPos() {
        int x2 = NgtMath.floor(this.posX + (double) REVISION[this.direction & 7][0]);
        int y2 = this.blockY;
        int z2 = NgtMath.floor(this.posZ + (double) REVISION[this.direction & 7][1]);
        return new BlockPos(x2, y2, z2);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RailPosition other)) {
            return false;
        }
        return this.blockX == other.blockX && this.blockY == other.blockY && this.blockZ == other.blockZ
            && this.switchType == other.switchType;
    }

    @Override
    public int hashCode() {
        return this.blockX ^ this.blockZ << 8 ^ this.blockY << 16;
    }
}
