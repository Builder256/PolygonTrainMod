package com.portofino.polygontrainmod.item;

import com.portofino.polygontrainmod.client.screen.ModelSelectScreen;

import java.util.List;

public interface ModelSelectableItem {
    List<ModelSelectScreen.ModelInfo> getSelectableModels();
}
