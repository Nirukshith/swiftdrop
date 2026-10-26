package com.swiftdrop.parcel.parcel;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure Java finite state machine governing parcel status transitions.
 * Enforces the deterministic lifecycle defined in SRS Section 5.3 and Architecture Decision Record 005.
 *
 * <pre>
 * Lifecycle:
 * CREATED ──► PICKED_UP ──► IN_TRANSIT ──► OUT_FOR_DELIVERY ──► DELIVERED (Terminal)
 *    │                                                    └──► FAILED (Terminal Attempt)
 *    │                                                            │
 *    │                                                            ▼ (Re-queue)
 *    │                                                         CREATED
 *    └──► CANCELLED (Terminal, before pickup)
 * </pre>
 *
 * This class has no dependency on Spring, JPA, or Kafka, ensuring fast, deterministic unit testability.
 */
public final class ParcelStateMachine {

    private static final Map<ParcelStatus, Set<ParcelStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<ParcelStatus, Set<ParcelStatus>> map = new EnumMap<>(ParcelStatus.class);

        // CREATED can transition to PICKED_UP (courier scan) or CANCELLED (customer/merchant cancel)
        map.put(ParcelStatus.CREATED, Collections.unmodifiableSet(EnumSet.of(
                ParcelStatus.PICKED_UP,
                ParcelStatus.CANCELLED
        )));

        // PICKED_UP transitions to IN_TRANSIT
        map.put(ParcelStatus.PICKED_UP, Collections.unmodifiableSet(EnumSet.of(
                ParcelStatus.IN_TRANSIT
        )));

        // IN_TRANSIT transitions to OUT_FOR_DELIVERY
        map.put(ParcelStatus.IN_TRANSIT, Collections.unmodifiableSet(EnumSet.of(
                ParcelStatus.OUT_FOR_DELIVERY
        )));

        // OUT_FOR_DELIVERY transitions to DELIVERED (success with proof) or FAILED (with reason)
        map.put(ParcelStatus.OUT_FOR_DELIVERY, Collections.unmodifiableSet(EnumSet.of(
                ParcelStatus.DELIVERED,
                ParcelStatus.FAILED
        )));

        // FAILED may be re-queued by dispatcher back to CREATED (clears courier, increments attempt)
        map.put(ParcelStatus.FAILED, Collections.unmodifiableSet(EnumSet.of(
                ParcelStatus.CREATED
        )));

        // DELIVERED is terminal - no outgoing transitions
        map.put(ParcelStatus.DELIVERED, Collections.emptySet());

        // CANCELLED is terminal - no outgoing transitions
        map.put(ParcelStatus.CANCELLED, Collections.emptySet());

        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(map);
    }

    private ParcelStateMachine() {
        // utility class
    }

    /**
     * Checks whether a status transition is permitted by the state machine.
     *
     * @param from the current status (must not be null)
     * @param to   the target status (must not be null)
     * @return true if the transition is allowed, false otherwise
     */
    public static boolean isValidTransition(ParcelStatus from, ParcelStatus to) {
        if (from == null || to == null) {
            return false;
        }
        Set<ParcelStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Returns an unmodifiable set of allowed next statuses from the given status.
     *
     * @param current the current status
     * @return unmodifiable set of allowed next statuses
     * @throws NullPointerException if current is null
     */
    public static Set<ParcelStatus> getAllowedNextStatuses(ParcelStatus current) {
        Objects.requireNonNull(current, "Current status must not be null");
        return ALLOWED_TRANSITIONS.getOrDefault(current, Collections.emptySet());
    }

    /**
     * Validates and executes a transition from current to next status.
     *
     * @param current the current status
     * @param next    the target status
     * @return the target status if transition is valid
     * @throws IllegalStateTransitionException if transition is not permitted
     * @throws NullPointerException            if current or next is null
     */
    public static ParcelStatus transition(ParcelStatus current, ParcelStatus next) {
        Objects.requireNonNull(current, "Current status must not be null");
        Objects.requireNonNull(next, "Next status must not be null");

        if (!isValidTransition(current, next)) {
            throw new IllegalStateTransitionException(current, next, getAllowedNextStatuses(current));
        }
        return next;
    }

    /**
     * Validates that a transition from current to next status is permitted without returning a value.
     *
     * @param current the current status
     * @param next    the target status
     * @throws IllegalStateTransitionException if transition is not permitted
     * @throws NullPointerException            if current or next is null
     */
    public static void validateTransition(ParcelStatus current, ParcelStatus next) {
        transition(current, next);
    }

    /**
     * Checks if the status is a terminal state (no further transitions permitted).
     * DELIVERED and CANCELLED are terminal.
     */
    public static boolean isTerminal(ParcelStatus status) {
        return status != null && status.isTerminal();
    }

    /**
     * Checks if a transition represents a dispatcher re-queue of a failed parcel.
     * Re-queue returns a FAILED parcel to CREATED, clearing the courier and incrementing attemptCount.
     */
    public static boolean isRequeue(ParcelStatus from, ParcelStatus to) {
        return from == ParcelStatus.FAILED && to == ParcelStatus.CREATED;
    }

    /**
     * Checks if a transition represents a pre-pickup cancellation.
     */
    public static boolean isCancellation(ParcelStatus from, ParcelStatus to) {
        return from == ParcelStatus.CREATED && to == ParcelStatus.CANCELLED;
    }
}
