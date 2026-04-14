package com.portofino.polygontrainmod.util;


public interface PolygonTrainModConstants {
    /// 1秒あたりのティック数
    float TICK_PER_SECOND = 20; // あるいは、LevelがあればTickRateManager#tirkrate？
    /// 1ティックの秒数
    float SECONDS_IN_TICK = 1 / TICK_PER_SECOND;
}
