package com.swiftdrop.parcel.address;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Predefined 25 administrative districts of Sri Lanka per ADR 002.
 */
public enum District {
    Ampara,
    Anuradhapura,
    Badulla,
    Batticaloa,
    Colombo,
    Galle,
    Gampaha,
    Hambantota,
    Jaffna,
    Kalutara,
    Kandy,
    Kegalle,
    Kilinochchi,
    Kurunegala,
    Mannar,
    Matale,
    Matara,
    Monaragala,
    Mullaitivu,
    Nuwara_Eliya("Nuwara Eliya"),
    Polonnaruwa,
    Puttalam,
    Ratnapura,
    Trincomalee,
    Vavuniya;

    private final String displayName;

    District() {
        this.displayName = name();
    }

    District(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    private static final Set<String> VALID_NAMES = Arrays.stream(values())
            .map(District::getDisplayName)
            .collect(Collectors.toUnmodifiableSet());

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (District d : values()) {
            if (d.displayName.equalsIgnoreCase(value.trim()) || d.name().equalsIgnoreCase(value.trim().replace(" ", "_"))) {
                return true;
            }
        }
        return false;
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        for (District d : values()) {
            if (d.displayName.equalsIgnoreCase(value.trim()) || d.name().equalsIgnoreCase(value.trim().replace(" ", "_"))) {
                return d.displayName;
            }
        }
        return value.trim();
    }
}
