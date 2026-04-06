package com.portofino.polygontrainmod.client.model.mqo;

/// MQOモデルのパース結果のステータス
public enum MQOParseResultStatus {
    /// 正常に終了したときの値
    SUCCESS,
    /// ロードが禁止されていたときの値
    FORBIDDEN,
    /// ロードに必要なデータが欠落していたときの値
    MISSING
}
