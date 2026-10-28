package com.swiftdrop.integration.merchant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyRequestHasher {

    private final ObjectMapper objectMapper;

    public IdempotencyRequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String hash(String operation, Object request) {
        try {
            JsonNode requestNode = objectMapper.valueToTree(request);
            String canonical = objectMapper.writer().with(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
                    .writeValueAsString(canonicalize(requestNode));
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((operation + "\n" + canonical).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not create idempotency request hash", exception);
        }
    }

    public String downstreamKey(UUID merchantId, String operation, String idempotencyKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((merchantId + ":" + operation + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
            return "m6-" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
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
            return objectMapper.getNodeFactory().numberNode(normalized.signum() == 0 ? BigDecimal.ZERO : normalized);
        }
        return node;
    }
}
