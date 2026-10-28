package com.swiftdrop.operations.zones;

import java.util.Set;

public final class Districts {

    public static final Set<String> ALL = Set.of(
            "Ampara", "Anuradhapura", "Badulla", "Batticaloa", "Colombo",
            "Galle", "Gampaha", "Hambantota", "Jaffna", "Kalutara", "Kandy",
            "Kegalle", "Kilinochchi", "Kurunegala", "Mannar", "Matale",
            "Matara", "Monaragala", "Mullaitivu", "Nuwara Eliya", "Polonnaruwa",
            "Puttalam", "Ratnapura", "Trincomalee", "Vavuniya");

    private Districts() {
    }

    public static void validate(Set<String> districts) {
        if (districts == null || districts.isEmpty()) {
            throw new IllegalArgumentException("districts must not be empty");
        }
        Set<String> unknown = districts.stream()
                .filter(district -> !ALL.contains(district))
                .collect(java.util.stream.Collectors.toSet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown district(s): " + unknown);
        }
    }

    public static void validate(String district) {
        if (district == null || !ALL.contains(district)) {
            throw new IllegalArgumentException("Unknown district: " + district);
        }
    }
}
