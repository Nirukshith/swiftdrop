package com.swiftdrop.parcel.parcel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.parcel.internal.InternalCreateParcelRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class MerchantParcelRequestHasher {

    private final ObjectMapper objectMapper;

    public MerchantParcelRequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String hash(InternalCreateParcelRequest request) {
        try {
            JsonNode requestNode = objectMapper.valueToTree(request);
            String canonical = objectMapper.writeValueAsString(canonicalize(requestNode));
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not create parcel idempotency fingerprint", exception);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            fieldNames.sort(String::compareTo);
            for (String fieldName : fieldNames) {
                sorted.set(fieldName, canonicalize(node.get(fieldName)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(value -> array.add(canonicalize(value)));
            return array;
        }
        if (node.isNumber()) {
            BigDecimal normalized = node.decimalValue().stripTrailingZeros();
            return objectMapper.getNodeFactory().numberNode(
                    normalized.signum() == 0 ? BigDecimal.ZERO : normalized
            );
        }
        return node;
    }
}
