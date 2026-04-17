package com.portofino.polygontrainmod.rail.math;

/**
 * Minimal math helpers matching jp.ngt.ngtlib.math.NGTMath usage in RTM rail code.
 */
public final class NgtMath {
    private NgtMath() {
    }

    public static float wrapAngle(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }

    public static double toRadians(float degrees) {
        return Math.toRadians(degrees);
    }

    public static int floor(double v) {
        return (int) Math.floor(v);
    }
}
