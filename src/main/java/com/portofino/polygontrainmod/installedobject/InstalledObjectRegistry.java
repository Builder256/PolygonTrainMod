package com.portofino.polygontrainmod.installedobject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InstalledObjectRegistry {
    private static final List<InstalledObjectDefinition> ALL = new ArrayList<>();
    private static final Map<String, InstalledObjectDefinition> BY_ID = new HashMap<>();
    private static final Map<InstalledObjectCategory, List<InstalledObjectDefinition>> BY_CATEGORY =
        new EnumMap<>(InstalledObjectCategory.class);

    private InstalledObjectRegistry() {
    }

    public static void setDefinitions(List<InstalledObjectDefinition> definitions) {
        ALL.clear();
        BY_ID.clear();
        BY_CATEGORY.clear();
        for (InstalledObjectDefinition definition : definitions) {
            InstalledObjectDefinition previous = BY_ID.put(definition.getId(), definition);
            if (previous != null) {
                ALL.remove(previous);
                List<InstalledObjectDefinition> existing = BY_CATEGORY.get(previous.getCategory());
                if (existing != null) {
                    existing.remove(previous);
                }
            }
            ALL.add(definition);
            BY_CATEGORY.computeIfAbsent(definition.getCategory(), key -> new ArrayList<>()).add(definition);
        }
        ALL.sort(Comparator.comparing(InstalledObjectDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        for (List<InstalledObjectDefinition> list : BY_CATEGORY.values()) {
            list.sort(Comparator.comparing(InstalledObjectDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        }
    }

    public static InstalledObjectDefinition getById(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static List<InstalledObjectDefinition> getByCategory(InstalledObjectCategory category) {
        return List.copyOf(BY_CATEGORY.getOrDefault(category, List.of()));
    }

    public static List<InstalledObjectDefinition> getAll() {
        return List.copyOf(ALL);
    }
}
