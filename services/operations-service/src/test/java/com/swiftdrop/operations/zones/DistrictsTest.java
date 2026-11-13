package com.swiftdrop.operations.zones;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    @Test
    void validatesEveryCanonicalDistrictIndividually() {
        for (String district : CANONICAL_DISTRICTS) {
            assertThatCode(() -> Districts.validate(district)).doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsNullAndBlankDistricts() {
        assertThatThrownBy(() -> Districts.validate((String) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Districts.validate(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Districts.validate("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullOrEmptyDistrictSet() {
        assertThatThrownBy(() -> Districts.validate((Set<String>) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("districts must not be empty");
        assertThatThrownBy(() -> Districts.validate(Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("districts must not be empty");
    }

    @Test
    void rejectsSetWithUnknownDistrict() {
        assertThatThrownBy(() -> Districts.validate(Set.of("Colombo", "Unknown District")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown district(s): [Unknown District]");
    }
}
