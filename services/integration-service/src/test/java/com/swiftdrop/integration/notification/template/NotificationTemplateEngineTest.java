package com.swiftdrop.integration.notification.template;

import com.swiftdrop.common.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateEngineTest {

    private NotificationTemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        templateEngine = new NotificationTemplateEngine();
    }

    @Test
    @DisplayName("Renders parcel.created.v1 template correctly")
    void rendersParcelCreated() {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID().toString(),
                "parcel.created.v1",
                Instant.now(),
                1L,
                "pcl-101",
                "SD-20261028-101",
                null,
                "CREATED",
                null,
                null,
                null,
                "cust-001",
                null
        );

        var rendered = templateEngine.render(event);

        assertThat(rendered.subject()).isEqualTo("SwiftDrop: Parcel Order Confirmed (SD-20261028-101)");
        assertThat(rendered.body()).contains("Track live at /track/SD-20261028-101");
    }

    @Test
    @DisplayName("Renders parcel.assigned.v1 template correctly")
    void rendersParcelAssigned() {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID().toString(),
                "parcel.assigned.v1",
                Instant.now(),
                2L,
                "pcl-101",
                "SD-20261028-101",
                "CREATED",
                "CREATED",
                "courier-1",
                null,
                null,
                "cust-001",
                null
        );

        var rendered = templateEngine.render(event);

        assertThat(rendered.subject()).isEqualTo("SwiftDrop: Courier Assigned (SD-20261028-101)");
        assertThat(rendered.body()).contains("A courier has been assigned to collect your package.");
    }

    @Test
    @DisplayName("Renders parcel.status-changed.v1 for PICKED_UP")
    void rendersPickedUp() {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID().toString(),
                "PARCEL_STATUS_CHANGED",
                Instant.now(),
                3L,
                "pcl-101",
                "SD-20261028-101",
                "CREATED",
                "PICKED_UP",
                "courier-1",
                null,
                null,
                "cust-001",
                null
        );

        var rendered = templateEngine.render(event);

        assertThat(rendered.subject()).isEqualTo("SwiftDrop: Parcel Picked Up");
        assertThat(rendered.body()).contains("Your courier has collected parcel SD-20261028-101.");
    }

    @Test
    @DisplayName("Renders parcel.status-changed.v1 for IN_TRANSIT")
    void rendersInTransit() {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID().toString(),
                "PARCEL_STATUS_CHANGED",
                Instant.now(),
                4L,
                "pcl-101",
                "SD-20261028-101",
                "PICKED_UP",
                "IN_TRANSIT",
                "courier-1",
                null,
                null,
                "cust-001",
                null
        );

        var rendered = templateEngine.render(event);

        assertThat(rendered.subject()).isEqualTo("SwiftDrop: Package In Transit");
        assertThat(rendered.body()).contains("Your parcel SD-20261028-101 is currently moving between sorting facilities.");
    }

    @Test
    @DisplayName("Renders parcel.status-changed.v1 for OUT_FOR_DELIVERY")
    void rendersOutForDelivery() {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID().toString(),
                "PARCEL_STATUS_CHANGED",
                Instant.now(),
                5L,
                "pcl-101",
                "SD-20261028-101",
                "IN_TRANSIT",
                "OUT_FOR_DELIVERY",
                "courier-1",
                null,
                null,
                "cust-001",
                null
        );

        var rendered = templateEngine.render(event);

        assertThat(rendered.subject()).isEqualTo("SwiftDrop: Out for Delivery Today!");
        assertThat(rendered.body()).contains("Courier is on the way to your delivery address with parcel SD-20261028-101.");
    }

    @Test
    @DisplayName("Renders parcel.status-changed.v1 for DELIVERED")
    void rendersDelivered() {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID().toString(),
                "PARCEL_STATUS_CHANGED",
                Instant.now(),
                6L,
                "pcl-101",
                "SD-20261028-101",
                "OUT_FOR_DELIVERY",
                "DELIVERED",
                "courier-1",
                null,
                null,
                "cust-001",
                null
        );

        var rendered = templateEngine.render(event);

        assertThat(rendered.subject()).isEqualTo("SwiftDrop: Parcel Delivered!");
        assertThat(rendered.body()).contains("Your package SD-20261028-101 has been delivered successfully.");
    }

    @Test
    @DisplayName("Renders parcel.status-changed.v1 for FAILED with custom reason")
    void rendersFailedWithReason() {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID().toString(),
                "PARCEL_STATUS_CHANGED",
                Instant.now(),
                6L,
                "pcl-101",
                "SD-20261028-101",
                "OUT_FOR_DELIVERY",
                "FAILED",
                "courier-1",
                null,
                null,
                "cust-001",
                "Customer not available at destination"
        );

        var rendered = templateEngine.render(event);

        assertThat(rendered.subject()).isEqualTo("SwiftDrop: Delivery Attempt Failed");
        assertThat(rendered.body()).contains("Reason: Customer not available at destination");
    }

    @Test
    @DisplayName("Renders parcel.status-changed.v1 for CANCELLED")
    void rendersCancelled() {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID().toString(),
                "PARCEL_STATUS_CHANGED",
                Instant.now(),
                2L,
                "pcl-101",
                "SD-20261028-101",
                "CREATED",
                "CANCELLED",
                null,
                null,
                null,
                "cust-001",
                "Customer requested cancellation"
        );

        var rendered = templateEngine.render(event);

        assertThat(rendered.subject()).isEqualTo("SwiftDrop: Parcel Booking Cancelled");
        assertThat(rendered.body()).contains("Parcel SD-20261028-101 has been cancelled as requested.");
    }

    @Test
    @DisplayName("Renders user.registered.v1 welcome message")
    void rendersUserRegistered() {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID().toString(),
                "user.registered.v1",
                Instant.now(),
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "usr-99",
                null
        );

        var rendered = templateEngine.render(event);

        assertThat(rendered.subject()).isEqualTo("Welcome to SwiftDrop!");
        assertThat(rendered.body()).contains("Your account has been created.");
    }
}

