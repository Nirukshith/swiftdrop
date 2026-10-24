package com.swiftdrop.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EventEnvelopeTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldSerializeAndDeserializeEventEnvelopeSuccessfully() throws Exception {
        Instant now = Instant.parse("2026-10-24T12:00:00Z");
        EventEnvelope original = new EventEnvelope(
            UUID.randomUUID().toString(),
            "PARCEL_CREATED",
            now,
            1L,
            "parcel-12345",
            "SWIFT-987654321",
            "CREATED",
            "ASSIGNED",
            "courier-42",
            null,
            "merchant-99",
            "customer-100",
            "Initial assignment to courier"
        );

        String json = objectMapper.writeValueAsString(original);
        assertNotNull(json);

        EventEnvelope deserialized = objectMapper.readValue(json, EventEnvelope.class);
        assertEquals(original, deserialized);
    }
}
