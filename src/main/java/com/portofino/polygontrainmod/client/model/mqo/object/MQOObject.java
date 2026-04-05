package com.portofino.polygontrainmod.client.model.mqo.object;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record MQOObject(
    String name,
    boolean isSmoothShadingEnabled,
    float autoSmoothAngle,
    int mirrorType,
    boolean isMirrorAxisXEnabled,
    boolean isMirrorAxisYEnabled,
    boolean isMirrorAxisZEnabled,
    float mirrorDistance,
    MQOVertex[] vertices,
    MQOFace[] faces
) {
    @Override
    public String toString() {
        return "MQOObject{" +
            "name='" + name + '\'' +
            ", isSmoothShadingEnabled=" + isSmoothShadingEnabled +
            ", autoSmoothAngle=" + autoSmoothAngle +
            ", mirrorType=" + mirrorType +
            ", isMirrorAxisXEnabled=" + isMirrorAxisXEnabled +
            ", isMirrorAxisYEnabled=" + isMirrorAxisYEnabled +
            ", isMirrorAxisZEnabled=" + isMirrorAxisZEnabled +
            ", mirrorDistance=" + mirrorDistance +
            ", vertices=" + java.util.Arrays.toString(vertices) +
            ", faces=" + java.util.Arrays.toString(faces) +
            '}';
    }
}