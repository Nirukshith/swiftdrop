package com.swiftdrop.integration.assistant.mcp;

import com.swiftdrop.integration.assistant.tools.AssistantTools;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing the AI Assistant's tools via Model Context Protocol (MCP) JSON-RPC 2.0.
 */
@RestController
@RequestMapping("/mcp")
public class McpServerController {

    private final AssistantTools assistantTools;

    public McpServerController(AssistantTools assistantTools) {
        this.assistantTools = assistantTools;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleMcp(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", request.get("id"));

        String method = (String) request.get("method");
        if ("tools/list".equals(method)) {
            response.put("result", Map.of(
                "tools", List.of(
                    Map.of(
                        "name", "get_parcel_status",
                        "description", "Retrieve tracking and status information for a parcel by tracking number",
                        "inputSchema", Map.of(
                            "type", "object",
                            "properties", Map.of("trackingNo", Map.of("type", "string")),
                            "required", List.of("trackingNo")
                        )
                    ),
                    Map.of(
                        "name", "estimate_fee",
                        "description", "Estimate delivery fee by weight in kg, size category, and destination district",
                        "inputSchema", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                "weightKg", Map.of("type", "number"),
                                "sizeCategory", Map.of("type", "string"),
                                "destinationDistrict", Map.of("type", "string")
                            ),
                            "required", List.of("weightKg", "destinationDistrict")
                        )
                    ),
                    Map.of(
                        "name", "list_my_recent_parcels",
                        "description", "List the authenticated caller's recent parcels",
                        "inputSchema", Map.of("type", "object")
                    )
                )
            ));
        } else {
            response.put("result", Map.of("status", "acknowledged", "method", method != null ? method : ""));
        }

        return ResponseEntity.ok(response);
    }
}
