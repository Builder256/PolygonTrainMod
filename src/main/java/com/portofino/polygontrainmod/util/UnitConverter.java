package com.portofino.polygontrainmod.util;

import static com.portofino.polygontrainmod.util.PolygonTrainModConstants.TICK_PER_SECOND;

/// 単位変換メソッド
public interface UnitConverter {
    /// 速度の単位キロメートル毎時をブロック毎ティックに変換する
    static float kph2bpt(float kilometrePerHour) {
        return kilometrePerHour / 3.6f / TICK_PER_SECOND;
    }

    /// 距離の単位センチメートルをメートルに変換する
    static float cm2m(float centimetre) {
        return centimetre / 100;
    }

    /// 時間の単位秒をティックに変換する
    static float s2t(float second) {
        return second * TICK_PER_SECOND;
    }
}
