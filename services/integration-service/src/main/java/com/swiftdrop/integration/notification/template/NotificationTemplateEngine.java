package com.swiftdrop.integration.notification.template;

import com.swiftdrop.common.EventEnvelope;
import org.springframework.stereotype.Component;

@Component
public class NotificationTemplateEngine {

    public record RenderedTemplate(String subject, String body) {}

    public RenderedTemplate render(EventEnvelope event) {
        String trackingNo = (event.trackingNumber() != null && !event.trackingNumber().isBlank())
                ? event.trackingNumber()
                : (event.parcelId() != null ? event.parcelId() : "N/A");

        String eventType = event.eventType() != null ? event.eventType() : "";
        String toStatus = event.toStatus() != null ? event.toStatus() : "";

        if ("PARCEL_CREATED".equalsIgnoreCase(eventType) || "parcel.created.v1".equalsIgnoreCase(eventType)) {
            return new RenderedTemplate(
                    "SwiftDrop: Parcel Order Confirmed (" + trackingNo + ")",
                    "Thank you for booking with SwiftDrop. Your delivery order has been created. Track live at /track/" + trackingNo + "."
            );
        }

        if ("PARCEL_ASSIGNED".equalsIgnoreCase(eventType) || "parcel.assigned.v1".equalsIgnoreCase(eventType)) {
            return new RenderedTemplate(
                    "SwiftDrop: Courier Assigned (" + trackingNo + ")",
                    "A courier has been assigned to collect your package."
            );
        }

        if ("USER_REGISTERED".equalsIgnoreCase(eventType) || "user.registered.v1".equalsIgnoreCase(eventType)) {
            return new RenderedTemplate(
                    "Welcome to SwiftDrop!",
                    "Your account has been created. Start booking parcel deliveries anytime."
            );
        }

        // Status changed events
        return switch (toStatus.toUpperCase()) {
            case "PICKED_UP" -> new RenderedTemplate(
                    "SwiftDrop: Parcel Picked Up",
                    "Your courier has collected parcel " + trackingNo + "."
            );
            case "IN_TRANSIT" -> new RenderedTemplate(
                    "SwiftDrop: Package In Transit",
                    "Your parcel " + trackingNo + " is currently moving between sorting facilities."
            );
            case "OUT_FOR_DELIVERY" -> new RenderedTemplate(
                    "SwiftDrop: Out for Delivery Today!",
                    "Get ready! Courier is on the way to your delivery address with parcel " + trackingNo + "."
            );
            case "DELIVERED" -> new RenderedTemplate(
                    "SwiftDrop: Parcel Delivered!",
                    "Your package " + trackingNo + " has been delivered successfully. Thank you for using SwiftDrop."
            );
            case "FAILED" -> {
                String reason = (event.reason() != null && !event.reason().isBlank())
                        ? event.reason()
                        : "Delivery attempt unsuccessful";
                yield new RenderedTemplate(
                        "SwiftDrop: Delivery Attempt Failed",
                        "Delivery for " + trackingNo + " could not be completed. Reason: " + reason + ". A re-attempt will be scheduled."
                );
            }
            case "CANCELLED" -> new RenderedTemplate(
                    "SwiftDrop: Parcel Booking Cancelled",
                    "Parcel " + trackingNo + " has been cancelled as requested."
            );
            case "CREATED" -> new RenderedTemplate(
                    "SwiftDrop: Parcel Order Confirmed (" + trackingNo + ")",
                    "Thank you for booking with SwiftDrop. Your delivery order has been created. Track live at /track/" + trackingNo + "."
            );
            default -> new RenderedTemplate(
                    "SwiftDrop: Status Update for " + trackingNo,
                    "Your parcel " + trackingNo + " status has changed to " + toStatus + "."
            );
        };
    }
}

