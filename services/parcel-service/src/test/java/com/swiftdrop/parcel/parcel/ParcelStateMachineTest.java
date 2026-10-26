package com.swiftdrop.parcel.parcel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParcelStateMachineTest {

    @Nested
    @DisplayName("Allowed Transitions")
    class AllowedTransitions {

        @ParameterizedTest(name = "Allowed: {0} -> {1}")
        @CsvSource({
                "CREATED, PICKED_UP",
                "CREATED, CANCELLED",
                "PICKED_UP, IN_TRANSIT",
                "IN_TRANSIT, OUT_FOR_DELIVERY",
                "OUT_FOR_DELIVERY, DELIVERED",
                "OUT_FOR_DELIVERY, FAILED",
                "FAILED, CREATED"
        })
        void shouldAllowValidTransitions(ParcelStatus from, ParcelStatus to) {
            assertThat(ParcelStateMachine.isValidTransition(from, to))
                    .as("Expected transition %s -> %s to be valid", from, to)
                    .isTrue();

            ParcelStatus result = ParcelStateMachine.transition(from, to);
            assertThat(result).isEqualTo(to);
        }

        @Test
        @DisplayName("validateTransition does not throw for allowed moves")
        void validateTransitionShouldSucceedForValidMove() {
            ParcelStateMachine.validateTransition(ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
            ParcelStateMachine.validateTransition(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED);
        }
    }

    @Nested
    @DisplayName("Allowed Next Status Sets")
    class AllowedNextStatusSets {

        @Test
        void shouldReturnExpectedAllowedStatusesFromCreated() {
            Set<ParcelStatus> allowed = ParcelStateMachine.getAllowedNextStatuses(ParcelStatus.CREATED);
            assertThat(allowed).containsExactlyInAnyOrder(ParcelStatus.PICKED_UP, ParcelStatus.CANCELLED);
        }

        @Test
        void shouldReturnExpectedAllowedStatusesFromPickedUp() {
            Set<ParcelStatus> allowed = ParcelStateMachine.getAllowedNextStatuses(ParcelStatus.PICKED_UP);
            assertThat(allowed).containsExactly(ParcelStatus.IN_TRANSIT);
        }

        @Test
        void shouldReturnExpectedAllowedStatusesFromInTransit() {
            Set<ParcelStatus> allowed = ParcelStateMachine.getAllowedNextStatuses(ParcelStatus.IN_TRANSIT);
            assertThat(allowed).containsExactly(ParcelStatus.OUT_FOR_DELIVERY);
        }

        @Test
        void shouldReturnExpectedAllowedStatusesFromOutForDelivery() {
            Set<ParcelStatus> allowed = ParcelStateMachine.getAllowedNextStatuses(ParcelStatus.OUT_FOR_DELIVERY);
            assertThat(allowed).containsExactlyInAnyOrder(ParcelStatus.DELIVERED, ParcelStatus.FAILED);
        }

        @Test
        void shouldReturnExpectedAllowedStatusesFromFailed() {
            Set<ParcelStatus> allowed = ParcelStateMachine.getAllowedNextStatuses(ParcelStatus.FAILED);
            assertThat(allowed).containsExactly(ParcelStatus.CREATED);
        }

        @Test
        void shouldReturnEmptySetForTerminalDelivered() {
            Set<ParcelStatus> allowed = ParcelStateMachine.getAllowedNextStatuses(ParcelStatus.DELIVERED);
            assertThat(allowed).isEmpty();
        }

        @Test
        void shouldReturnEmptySetForTerminalCancelled() {
            Set<ParcelStatus> allowed = ParcelStateMachine.getAllowedNextStatuses(ParcelStatus.CANCELLED);
            assertThat(allowed).isEmpty();
        }
    }

    @Nested
    @DisplayName("Illegal Transitions")
    class IllegalTransitions {

        @ParameterizedTest(name = "Illegal: {0} -> {1}")
        @CsvSource({
                "CREATED, IN_TRANSIT",
                "CREATED, OUT_FOR_DELIVERY",
                "CREATED, DELIVERED",
                "CREATED, FAILED",
                "PICKED_UP, CREATED",
                "PICKED_UP, OUT_FOR_DELIVERY",
                "PICKED_UP, DELIVERED",
                "PICKED_UP, FAILED",
                "PICKED_UP, CANCELLED",
                "IN_TRANSIT, CREATED",
                "IN_TRANSIT, PICKED_UP",
                "IN_TRANSIT, DELIVERED",
                "IN_TRANSIT, FAILED",
                "IN_TRANSIT, CANCELLED",
                "OUT_FOR_DELIVERY, CREATED",
                "OUT_FOR_DELIVERY, PICKED_UP",
                "OUT_FOR_DELIVERY, IN_TRANSIT",
                "OUT_FOR_DELIVERY, CANCELLED",
                "DELIVERED, CREATED",
                "DELIVERED, PICKED_UP",
                "DELIVERED, IN_TRANSIT",
                "DELIVERED, OUT_FOR_DELIVERY",
                "DELIVERED, FAILED",
                "DELIVERED, CANCELLED",
                "CANCELLED, CREATED",
                "CANCELLED, PICKED_UP",
                "CANCELLED, IN_TRANSIT",
                "CANCELLED, OUT_FOR_DELIVERY",
                "CANCELLED, DELIVERED",
                "CANCELLED, FAILED",
                "FAILED, PICKED_UP",
                "FAILED, IN_TRANSIT",
                "FAILED, OUT_FOR_DELIVERY",
                "FAILED, DELIVERED",
                "FAILED, CANCELLED"
        })
        void shouldRejectIllegalTransitions(ParcelStatus from, ParcelStatus to) {
            assertThat(ParcelStateMachine.isValidTransition(from, to))
                    .as("Expected transition %s -> %s to be invalid", from, to)
                    .isFalse();

            assertThatThrownBy(() -> ParcelStateMachine.transition(from, to))
                    .isInstanceOf(IllegalStateTransitionException.class)
                    .hasMessageContaining(from.name())
                    .hasMessageContaining(to.name());
        }

        @ParameterizedTest
        @EnumSource(ParcelStatus.class)
        @DisplayName("Self transitions are forbidden")
        void shouldRejectSelfTransitions(ParcelStatus status) {
            assertThat(ParcelStateMachine.isValidTransition(status, status)).isFalse();

            assertThatThrownBy(() -> ParcelStateMachine.transition(status, status))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Exception Details")
    class ExceptionDetails {

        @Test
        void shouldPopulateExceptionFieldsCorrectly() {
            try {
                ParcelStateMachine.transition(ParcelStatus.PICKED_UP, ParcelStatus.DELIVERED);
            } catch (IllegalStateTransitionException ex) {
                assertThat(ex.getFromStatus()).isEqualTo(ParcelStatus.PICKED_UP);
                assertThat(ex.getToStatus()).isEqualTo(ParcelStatus.DELIVERED);
                assertThat(ex.getAllowedNextStatuses()).containsExactly(ParcelStatus.IN_TRANSIT);
                assertThat(ex.getMessage()).contains("Cannot transition parcel from PICKED_UP to DELIVERED");
            }
        }
    }

    @Nested
    @DisplayName("Null Handling")
    class NullHandling {

        @Test
        void shouldReturnFalseForNullInputsInIsValidTransition() {
            assertThat(ParcelStateMachine.isValidTransition(null, ParcelStatus.CREATED)).isFalse();
            assertThat(ParcelStateMachine.isValidTransition(ParcelStatus.CREATED, null)).isFalse();
            assertThat(ParcelStateMachine.isValidTransition(null, null)).isFalse();
        }

        @Test
        void shouldThrowNullPointerExceptionForNullInputsInTransition() {
            assertThatThrownBy(() -> ParcelStateMachine.transition(null, ParcelStatus.CREATED))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Current status must not be null");

            assertThatThrownBy(() -> ParcelStateMachine.transition(ParcelStatus.CREATED, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Next status must not be null");
        }

        @Test
        void shouldThrowNullPointerExceptionForNullInGetAllowed() {
            assertThatThrownBy(() -> ParcelStateMachine.getAllowedNextStatuses(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Semantic Helper Methods")
    class SemanticHelpers {

        @Test
        void shouldIdentifyTerminalStatuses() {
            assertThat(ParcelStateMachine.isTerminal(ParcelStatus.DELIVERED)).isTrue();
            assertThat(ParcelStateMachine.isTerminal(ParcelStatus.CANCELLED)).isTrue();
            assertThat(ParcelStateMachine.isTerminal(ParcelStatus.CREATED)).isFalse();
            assertThat(ParcelStateMachine.isTerminal(ParcelStatus.PICKED_UP)).isFalse();
            assertThat(ParcelStateMachine.isTerminal(ParcelStatus.IN_TRANSIT)).isFalse();
            assertThat(ParcelStateMachine.isTerminal(ParcelStatus.OUT_FOR_DELIVERY)).isFalse();
            assertThat(ParcelStateMachine.isTerminal(ParcelStatus.FAILED)).isFalse();
            assertThat(ParcelStateMachine.isTerminal(null)).isFalse();
        }

        @Test
        void shouldIdentifyRequeue() {
            assertThat(ParcelStateMachine.isRequeue(ParcelStatus.FAILED, ParcelStatus.CREATED)).isTrue();
            assertThat(ParcelStateMachine.isRequeue(ParcelStatus.CREATED, ParcelStatus.PICKED_UP)).isFalse();
            assertThat(ParcelStateMachine.isRequeue(ParcelStatus.CANCELLED, ParcelStatus.CREATED)).isFalse();
        }

        @Test
        void shouldIdentifyCancellation() {
            assertThat(ParcelStateMachine.isCancellation(ParcelStatus.CREATED, ParcelStatus.CANCELLED)).isTrue();
            assertThat(ParcelStateMachine.isCancellation(ParcelStatus.PICKED_UP, ParcelStatus.CANCELLED)).isFalse();
        }

        @Test
        void shouldVerifyParcelStatusEnumHelpers() {
            assertThat(ParcelStatus.CREATED.canCancel()).isTrue();
            assertThat(ParcelStatus.PICKED_UP.canCancel()).isFalse();

            assertThat(ParcelStatus.FAILED.canRequeue()).isTrue();
            assertThat(ParcelStatus.DELIVERED.canRequeue()).isFalse();

            assertThat(ParcelStatus.DELIVERED.isTerminal()).isTrue();
            assertThat(ParcelStatus.CANCELLED.isTerminal()).isTrue();
            assertThat(ParcelStatus.FAILED.isTerminal()).isFalse();
        }
    }
}
