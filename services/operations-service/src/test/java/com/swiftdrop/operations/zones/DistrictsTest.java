package com.swiftdrop.operations.zones;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistrictsTest {

    private static final Set<String> CANONICAL_DISTRICTS = Set.of(
            "Ampara", "Anuradhapura", "Badulla", "Batticaloa", "Colombo",
            "Galle", "Gampaha", "Hambantota", "Jaffna", "Kalutara", "Kandy",
            "Kegalle", "Kilinochchi", "Kurunegala", "Mannar", "Matale",
            "Matara", "Monaragala", "Mullaitivu", "Nuwara Eliya", "Polonnaruwa",
            "Puttalam", "Ratnapura", "Trincomalee", "Vavuniya");

    @Test
    void containsAllCanonicalSriLankanDistricts() {
        assertThat(Districts.ALL).containsExactlyInAnyOrderElementsOf(CANONICAL_DISTRICTS);
        assertThat(Districts.ALL).hasSize(25);
    }

    @Test
    void rejectsUnknownDistricts() {
        assertThatThrownBy(() -> Districts.validate("Unknown District"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown district: Unknown District");
    }
}
