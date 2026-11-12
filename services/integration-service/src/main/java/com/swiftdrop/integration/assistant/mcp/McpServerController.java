package com.swiftdrop.integration.assistant.mcp;

import com.swiftdrop.integration.assistant.tools.AssistantTools;
import com.swiftdrop.integration.assistant.tools.AssistantToolsImpl;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

@RestController
@RequestMapping("/mcp")
public class McpServerController {

    private static final String PROTOCOL_VERSION = "2024-11-05";
    private final AssistantTools tools;

    public McpServerController(AssistantTools tools) {
        this.tools = tools;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handle(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Object id = request.get("id");
        if (!"2.0".equals(request.get("jsonrpc")) || !(request.get("method") instanceof String method)) {
            return ResponseEntity.ok(error(id, -32600, "Invalid JSON-RPC request"));
        }
        String token = jwt != null ? jwt.getTokenValue() : bearerToken(authorization);
        if (token == null || token.isBlank()) {
            return ResponseEntity.ok(error(id, -32001, "Authenticated customer token is required"));
        }
        try {
            return ResponseEntity.ok(switch (method) {
                case "initialize" -> result(id, Map.of(
                        "protocolVersion", PROTOCOL_VERSION,
                        "capabilities", Map.of("tools", Map.of()),
                        "serverInfo", Map.of("name", "swiftdrop-assistant", "version", "1.0")));
                case "notifications/initialized" -> result(id, Map.of());
                case "tools/list" -> result(id, Map.of("tools", toolDefinitions()));
                case "tools/call" -> call(id, request.get("params"), token);
                default -> error(id, -32601, "Method is not supported");
            });
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.ok(error(id, -32602, ex.getMessage()));
        } catch (AssistantToolsImpl.ParcelNotFoundException ex) {
            return ResponseEntity.ok(error(id, -32004, "Parcel was not found for the authenticated customer"));
        } catch (RestClientException | IllegalStateException ex) {
            return ResponseEntity.ok(error(id, -32000, "Parcel service is temporarily unavailable"));
        }
    }

    private Map<String, Object> call(Object id, Object rawParams, String token) {
        if (!(rawParams instanceof Map<?, ?> params) || !(params.get("name") instanceof String name)) {
            return error(id, -32602, "Tool name is required");
        }
        Map<?, ?> arguments = params.get("arguments") instanceof Map<?, ?> map ? map : Map.of();
        Object value = switch (name) {
            case "get_parcel_by_tracking_number" ->
                    tools.getParcelByTrackingNumber(string(arguments, "trackingNumber"), token);
            case "get_customer_parcels" ->
                    tools.getCustomerParcels(optionalString(arguments, "status"), integer(arguments, "page", 0),
                            integer(arguments, "size", 20), token);
            case "get_tracking_history" ->
                    tools.getTrackingHistory(string(arguments, "trackingNumber"), token);
            case "get_delivery_information" ->
                    tools.getDeliveryInformation(string(arguments, "trackingNumber"), token);
            case "get_parcel_summary" -> tools.getParcelSummary(token);
            case "estimate_delivery_fee" -> tools.estimateFee(
                    string(arguments, "pickupDistrict"), string(arguments, "destinationDistrict"),
                    number(arguments, "weightKg"), string(arguments, "sizeCategory"), token);
            default -> throw new IllegalArgumentException("Unknown tool");
        };
        return result(id, Map.of("content", List.of(Map.of("type", "text", "text", value.toString())),
                "structuredContent", value));
    }

    private static List<Map<String, Object>> toolDefinitions() {
        return List.of(
                tool("get_parcel_by_tracking_number", "Get an owned parcel's current status", Map.of(
                        "trackingNumber", Map.of("type", "string", "pattern", "^SD-\\d{8}-[A-Z0-9]{6}$"))),
                tool("get_customer_parcels", "List the authenticated customer's parcels", Map.of(
                        "status", Map.of("type", "string"), "page", Map.of("type", "integer", "minimum", 0),
                        "size", Map.of("type", "integer", "minimum", 1, "maximum", 50))),
                tool("get_tracking_history", "Get an owned parcel's status timeline", Map.of(
                        "trackingNumber", Map.of("type", "string"))),
                tool("get_delivery_information", "Get an owned parcel's delivery assignment information", Map.of(
                        "trackingNumber", Map.of("type", "string"))),
                tool("get_parcel_summary", "Summarize the authenticated customer's parcel statuses", Map.of()),
                tool("estimate_delivery_fee", "Get a calculated delivery fee quote", Map.of(
                        "pickupDistrict", Map.of("type", "string"), "destinationDistrict", Map.of("type", "string"),
                        "weightKg", Map.of("type", "number", "minimum", 0.1, "maximum", 50),
                        "sizeCategory", Map.of("type", "string"))));
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> properties) {
        return Map.of("name", name, "description", description,
                "inputSchema", Map.of("type", "object", "properties", properties));
    }

    private static Map<String, Object> result(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private static Map<String, Object> error(Object id, int code, String message) {
        return Map.of("jsonrpc", "2.0", "id", id, "error", Map.of("code", code, "message", message));
    }

    private static String string(Map<?, ?> values, String name) {
        String value = optionalString(values, name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String optionalString(Map<?, ?> values, String name) {
        return values.get(name) instanceof String value ? value : null;
    }

    private static int integer(Map<?, ?> values, String name, int fallback) {
        return values.get(name) instanceof Number number ? number.intValue() : fallback;
    }

    private static double number(Map<?, ?> values, String name) {
        if (!(values.get(name) instanceof Number number)) {
            throw new IllegalArgumentException(name + " is required");
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    private static String bearerToken(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
    }

}
