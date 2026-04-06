package com.portofino.polygontrainmod.client.model.mqo;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record MQOMaterial(String name) {}
