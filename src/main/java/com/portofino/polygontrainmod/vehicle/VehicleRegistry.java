package com.portofino.polygontrainmod.vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VehicleRegistry {
    private static final List<VehicleDefinition> DEFINITIONS = new ArrayList<>();
    private static final Map<String, VehicleDefinition> BY_ID = new HashMap<>();
    private static int selectedIndex = 0;

    private VehicleRegistry() {
    }

    public static void setDefinitions(List<VehicleDefinition> defs) {
        DEFINITIONS.clear();
        BY_ID.clear();
        for (VehicleDefinition d : defs) {
            DEFINITIONS.add(d);
            BY_ID.put(d.getId(), d);
        }
        if (selectedIndex >= DEFINITIONS.size()) selectedIndex = 0;
    }

    public static List<VehicleDefinition> getAll() {
        return List.copyOf(DEFINITIONS);
    }

    public static VehicleDefinition getById(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static VehicleDefinition getSelected() {
        if (DEFINITIONS.isEmpty()) return null;
        if (selectedIndex < 0 || selectedIndex >= DEFINITIONS.size()) selectedIndex = 0;
        return DEFINITIONS.get(selectedIndex);
    }

    public static void setSelectedIndex(int i) {
        if (i >= 0 && i < DEFINITIONS.size()) selectedIndex = i;
    }
}
