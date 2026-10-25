# SwiftDrop Notification Subsystem Design

**Document Version:** 1.0.0-SNAPSHOT  
**Owner:** Member M3 (`com.swiftdrop.integration.notification`)  
**Service:** `integration-service` (Port 8084, DB: `integration_db`)  

---

## 1. Overview and Objectives

The SwiftDrop Notification Subsystem is an asynchronous, event-driven module responsible for delivering timely alerts to customers regarding their delivery orders and account events. It supports:
1. **Email Alerts**: Dispatched via `EmailChannel` using Spring Mail connected to Mailpit mock SMTP (`localhost:1025`).
2. **SMS Alerts**: Dispatched via `SmsChannel` (mock log-only implementation) with strict privacy rules ensuring phone numbers are masked.
3. **In-App Notification Bell**: Stored in `notification_log` and queried by the customer Angular portal via REST APIs (`/api/v1/notifications`).

---

## 2. Architectural Design & Patterns

```
                 +--------------------------------------------+
                 |            Apache Kafka Broker             |
                 |  parcel.*.v1  |  user.registered.v1        |
                 +---------------------+----------------------+
                                       |
                                       v
                     +----------------------------------+
                     |  NotificationEventListener       |
                     |  (Spring Kafka @KafkaListener)   |
                     +-----------------+----------------+
                                       |
                                       v
                     +----------------------------------+
                     |  Idempotency Check               |
                     |  (Query notification_log.eventId)|
                     +-----------------+----------------+
                                       | (New event)
                                       v
                     +----------------------------------+
                     |  Contact Resolution Client       |
                     |  GET /internal/v1/users/{id}     |
                     +-----------------+----------------+
                                       |
                                       v
                     +----------------------------------+
                     |  NotificationTemplateEngine      |
                     |  (Status-to-Template Renderer)   |
                     +-----------------+----------------+
                                       |
                 +---------------------+---------------------+
                 |                                           |
                 v                                           v
       +--------------------+                      +--------------------+
       |   EmailChannel     |                      |    SmsChannel      |
       | (JavaMail/Mailpit) |                      | (Masked Log Filer) |
       +--------------------+                      +--------------------+
```

### Key Design Patterns
- **Adapter Pattern (`NotificationChannel`)**: Defines a uniform interface `NotificationChannel` with `EmailChannel` and `SmsChannel` implementations. Enables adding external providers (Twilio, AWS SES, SendGrid) in production without modifying business consumers.
- **Observer / Publish-Subscribe**: Decouples status mutation in `parcel-service` from slow delivery networks (SMTP / SMS gateways) through Kafka.
- **Transactional Idempotency**: Leverages a unique database index on `notification_log(event_id, channel)` to guarantee each event is delivered exactly once even during Kafka consumer group rebalances or network re-deliveries.

---

## 3. Channel Implementations

### 3.1 Interface Definition
```java
package com.swiftdrop.integration.notification.channel;

public interface NotificationChannel {
    String getChannelType(); // EMAIL, SMS, IN_APP
    void send(NotificationMessage message);
}
```

### 3.2 EmailChannel (Mailpit SMTP)
- **Technology**: `org.springframework.boot:spring-boot-starter-mail` (JavaMailSender).
- **Configuration**:
  - `spring.mail.host: localhost` (docker-compose: `mailpit`)
  - `spring.mail.port: 1025`
  - Web UI for local inspection: `http://localhost:8025`
- **Sender Address**: `no-reply@swiftdrop.com`

### 3.3 SmsChannel (Mock Log Channel)
- **Implementation**: Outputs formatted messages to application logs at `INFO` level.
- **Privacy & Masking Rule**: All phone numbers MUST be masked before logging to comply with course security requirements:
  - Input: `+94771234567`
  - Output: `+94 77 *** *567`
  - Code:
    ```java
    public static String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 5) + " *** *" + phone.substring(phone.length() - 3);
    }
    ```

---

## 4. Kafka Event Processing & Idempotency

### 4.1 Consumed Topics
- `parcel.created.v1`
- `parcel.assigned.v1`
- `parcel.status-changed.v1`
- `user.registered.v1`

### 4.2 Idempotency Workflow
1. Upon receiving an `EventEnvelope`, the consumer checks `notification_log` for the existence of `eventId`.
2. If `eventId` already exists for this channel, the message is acknowledged and discarded.
3. If not present:
   - Recipient contact details are resolved.
   - Message is rendered from the status template.
   - Dispatch is executed across active channels (`EmailChannel`, `SmsChannel`, `IN_APP`).
   - A `notification_log` row is committed.

### 4.3 Database Schema: `notification_log`
```sql
CREATE TABLE notification_log (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    parcel_id VARCHAR(36),
    tracking_number VARCHAR(32),
    channel VARCHAR(16) NOT NULL, -- EMAIL, SMS, IN_APP
    recipient_target VARCHAR(255) NOT NULL, -- email address or masked phone
    title VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_notification_event_channel UNIQUE (event_id, channel)
);

CREATE INDEX idx_notification_user ON notification_log(user_id, sent_at DESC);
```

---

## 5. User Contact Resolution

For parcel events, the `EventEnvelope` contains `customerId`. To obtain the customer's email and phone number, `integration-service` makes a synchronous internal REST call to `auth-service`:

- **Endpoint**: `GET /internal/v1/users/{id}/contact`
- **Response**:
  ```json
  {
    "userId": "usr-88412",
    "fullName": "Jane Perera",
    "email": "jane.p@example.com",
    "phoneNumber": "+94771234567",
    "role": "CUSTOMER"
  }
  ```
- **Resilience**: Configured with a 2-second HTTP timeout and transient retry policy using Spring's `RestClient` or `WebClient`.

---

## 6. Status-to-Template Mapping

| Trigger Event | To Status | Email Subject | Message Body Summary |
| :--- | :--- | :--- | :--- |
| `parcel.created.v1` | `CREATED` | SwiftDrop: Parcel Order Confirmed (`{trackingNo}`) | Thank you for booking with SwiftDrop. Your delivery order has been created. Track live at `/track/{trackingNo}`. |
| `parcel.assigned.v1` | `CREATED` | SwiftDrop: Courier Assigned (`{trackingNo}`) | A courier has been assigned to collect your package. |
| `parcel.status-changed.v1` | `PICKED_UP` | SwiftDrop: Parcel Picked Up | Your courier has collected parcel `{trackingNo}`. |
| `parcel.status-changed.v1` | `IN_TRANSIT` | SwiftDrop: Package In Transit | Your parcel `{trackingNo}` is currently moving between sorting facilities. |
| `parcel.status-changed.v1` | `OUT_FOR_DELIVERY`| SwiftDrop: Out for Delivery Today! | Get ready! Courier is on the way to your delivery address with parcel `{trackingNo}`. |
| `parcel.status-changed.v1` | `DELIVERED` | SwiftDrop: Parcel Delivered! | Your package `{trackingNo}` has been delivered successfully. Thank you for using SwiftDrop. |
| `parcel.status-changed.v1` | `FAILED` | SwiftDrop: Delivery Attempt Failed | Delivery for `{trackingNo}` could not be completed. Reason: `{reason}`. A re-attempt will be scheduled. |
| `parcel.status-changed.v1` | `CANCELLED` | SwiftDrop: Parcel Booking Cancelled | Parcel `{trackingNo}` has been cancelled as requested. |
| `user.registered.v1` | N/A | Welcome to SwiftDrop! | Your account has been created. Start booking parcel deliveries anytime. |

---

## 7. In-App Notification Bell API

Customers can view and interact with alerts directly inside the portal:

1. **`GET /api/v1/notifications?page=0&size=20&unreadOnly=false`**
   - Returns paginated list of notifications for the logged-in customer extracted from the JWT token `sub`.
2. **`POST /api/v1/notifications/{id}/read`**
   - Sets `read_at = NOW()` for the specified notification, ensuring the unread badge count updates immediately.

---

## 8. Fault Tolerance & Dead Letter Strategy

- **Spring Kafka `@RetryableTopic`**:
  - Automatically routes failed notification processing to `parcel.*.retry` topics.
  - Backoff: `1000ms`, `2000ms`, `4000ms`, `8000ms`, `16000ms` (5 attempts).
  - Unrecoverable or exhausted failures move to `parcel.*.dlt`.
- **Alerting & Dead Letter Inspection**:
  - DLT messages preserve original error details in headers for easy troubleshooting via developer tooling.
