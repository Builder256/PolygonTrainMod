package com.portofino.polygontrainmod.util;


public final class PolygonTrainModConstants {
    private PolygonTrainModConstants() {
    }

    /// 1秒あたりのティック数
    public static final float TICK_PER_SECOND = 20.0f; // あるいは、LevelがあればTickRateManager#tirkrate？
    /// 1ティックの秒数
    public static final float SECONDS_IN_TICK = 1.0f / TICK_PER_SECOND;
}