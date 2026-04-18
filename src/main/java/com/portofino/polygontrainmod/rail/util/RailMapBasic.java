package com.portofino.polygontrainmod.rail.util;

import com.portofino.polygontrainmod.rail.math.BezierCurve;
import com.portofino.polygontrainmod.rail.math.ILine;
import com.portofino.polygontrainmod.rail.math.CurveMath;
import com.portofino.polygontrainmod.rail.math.StraightLine;

/**
 * Port of jp.legacy.legacy.rail.util.RailMapBasic.
 */
public class RailMapBasic extends RailMap {
    protected final RailPosition startRP;
    protected final RailPosition endRP;
    protected double length;
    protected ILine lineHorizontal;
    protected ILine lineVertical;

    public RailMapBasic(RailPosition par1, RailPosition par2) {
        this.startRP = par1;
        this.endRP = par2;
        this.endRP.cantCenter = this.startRP.cantCenter;
        this.createLine();
    }

    protected void createLine() {
        double x0 = this.startRP.posX;
        double y0 = this.startRP.posY;
        double z0 = this.startRP.posZ;
        double x1 = this.endRP.posX;
        double y1 = this.endRP.posY;
        double z1 = this.endRP.posZ;
        // RailPosition uses small non-integer revisions (e.g., 0.499999), so comparisons must be tolerant.
        final double eps = 1.0e-3;
        double dz = z1 - z0;
        double dx = x1 - x0;
        boolean axisAligned = Math.abs(dz) < eps || Math.abs(dx) < eps;
        boolean diagonal45 = Math.abs(Math.abs(dz) - Math.abs(dx)) < eps;

        boolean flag1 = (this.endRP.direction - this.startRP.direction) % 4 == 0;
        boolean flag2 = z0 == z1 || x0 == x1;
        boolean flag3 = Math.abs(z0 - z1) == Math.abs(x0 - x1)
            && this.startRP.direction % 2 != 0 && this.endRP.direction % 2 != 0;

        if (!flag1 || (!flag2 && !flag3)) {
            double lenXZ1 = Math.abs(z1 - z0);
            double ddx = Math.abs(x1 - x0);
            double max = Math.max(lenXZ1, ddx);
            double min = Math.min(lenXZ1, ddx);

            if (this.startRP.anchorLengthHorizontal <= 0.0F) {
                boolean b0 = this.startRP.direction % 2 == 0;
                double d1 = b0 ? max : min;
                this.startRP.anchorLengthHorizontal = (float) (d1 * 0.5522847771644592D);
            }
            if (this.endRP.anchorLengthHorizontal <= 0.0F) {
                boolean b0 = this.endRP.direction % 2 == 0;
                double d1 = b0 ? max : min;
                this.endRP.anchorLengthHorizontal = (float) (d1 * 0.5522847771644592D);
            }

            double d1s = (double) (cos(this.startRP.anchorYaw) * this.startRP.anchorLengthHorizontal);
            double d2s = (double) (sin(this.startRP.anchorYaw) * this.startRP.anchorLengthHorizontal);
            double d3e = (double) (cos(this.endRP.anchorYaw) * this.endRP.anchorLengthHorizontal);
            double d4e = (double) (sin(this.endRP.anchorYaw) * this.endRP.anchorLengthHorizontal);
            this.lineHorizontal = new BezierCurve(z0, x0, z0 + d1s, x0 + d2s, z1 + d3e, x1 + d4e, z1, x1);
        } else {
            this.lineHorizontal = new StraightLine(z0, x0, z1, x1);
        }

        double lenXZ = Math.sqrt(pow(x1 - x0, 2) + pow(z1 - z0, 2));
        if (this.startRP.anchorLengthVertical == 0.0F && this.endRP.anchorLengthVertical == 0.0F) {
            this.lineVertical = new StraightLine(0.0D, y0, lenXZ, y1);
        } else {
            double d1v = (double) (cos(this.startRP.anchorPitch) * this.startRP.anchorLengthVertical);
            double d2v = (double) (sin(this.startRP.anchorPitch) * this.startRP.anchorLengthVertical);
            double d3v = (double) (cos(this.endRP.anchorPitch) * this.endRP.anchorLengthVertical);
            double d4v = (double) (sin(this.endRP.anchorPitch) * this.endRP.anchorLengthVertical);
            this.lineVertical = new BezierCurve(0.0D, y0, d1v, y0 + d2v, lenXZ - d3v, y1 + d4v, lenXZ, y1);
        }
    }

    @Override
    public RailPosition getStartRP() {
        return this.startRP;
    }

    @Override
    public RailPosition getEndRP() {
        return this.endRP;
    }

    @Override
    public double getLength() {
        if (this.length <= 0.0D) {
            double height = this.endRP.posY - this.startRP.posY;
            if (height == 0.0D) {
                this.length = this.lineHorizontal.getLength();
            } else {
                double d0 = this.lineHorizontal.getLength();
                this.length = Math.sqrt(d0 * d0 + height * height);
            }
        }
        return this.length;
    }

    @Override
    public boolean isStraightTrack() {
        return this.lineHorizontal instanceof StraightLine;
    }

    @Override
    public double getHorizontalPathLength() {
        return this.lineHorizontal != null ? this.lineHorizontal.getLength() : this.getLength();
    }

    @Override
    public int getNearlestPoint(int par1, double par2, double par3) {
        return this.lineHorizontal.getNearlestPoint(par1, par2, par3);
    }

    @Override
    public double[] getRailPos(int par1, int par2) {
        return this.lineHorizontal.getPoint(par1, par2);
    }

    @Override
    public double getRailHeight(int par1, int par2) {
        float railWidth = 3.0F;
        double height = this.lineVertical.getPoint(par1, par2)[1];
        float cant = this.getCant(par1, par2);
        if (cant != 0.0F) {
            double h2 = Math.abs((double) (float) Math.sin(Math.toRadians(cant)) * railWidth * 0.5F);
            height += h2;
        }
        return height;
    }

    @Override
    public float getRailYaw(int par1, int par2) {
        return (float) Math.toDegrees(this.lineHorizontal.getSlope(par1, par2));
    }

    @Override
    public float getRailPitch(int par1, int par2) {
        return (float) Math.toDegrees(this.lineVertical.getSlope(par1, par2));
    }

    @Override
    public float getRailRoll(int split, int t) {
        float ft = 2.0F * (float) t / (float) split;
        float c1 = ft <= 1.0F ? (1.0F - ft) * this.startRP.cantEdge : (ft - 1.0F) * -this.endRP.cantEdge;
        float c2 = ft <= 1.0F ? ft * this.startRP.cantCenter : (2.0F - ft) * this.startRP.cantCenter;
        return c1 + c2;
    }

    private static float sin(float degrees) {
        return (float) Math.sin(Math.toRadians(degrees));
    }

    private static float cos(float degrees) {
        return (float) Math.cos(Math.toRadians(degrees));
    }

    private static double pow(double val, int exp) {
        double result = 1.0D;
        for (int i = 0; i < exp; i++) result *= val;
        return result;
    }
}
