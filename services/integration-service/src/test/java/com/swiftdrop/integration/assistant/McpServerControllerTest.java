package com.swiftdrop.integration.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.swiftdrop.integration.assistant.mcp.McpServerController;
import com.swiftdrop.integration.assistant.tools.AssistantTools;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerControllerTest {

    @Test
    void listsOnlyApprovedReadOnlyTools() {
        var controller = new McpServerController(mock(AssistantTools.class));

        var response = controller.handle(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list"), null,
                "Bearer token");

        assertEquals(200, response.getStatusCode().value());
        var result = (Map<?, ?>) response.getBody().get("result");
        assertEquals(6, ((java.util.List<?>) result.get("tools")).size());
    }

    @Test
    void advertisesRequiredArgumentsInToolSchemas() {
        var controller = new McpServerController(mock(AssistantTools.class));

        var response = controller.handle(Map.of("jsonrpc", "2.0", "id", 4, "method", "tools/list"), null,
                "Bearer token");
        var result = (Map<?, ?>) response.getBody().get("result");
        var tools = (java.util.List<?>) result.get("tools");
        var trackingTool = (Map<?, ?>) tools.stream()
                .map(Map.class::cast)
                .filter(tool -> "get_parcel_by_tracking_number".equals(tool.get("name")))
                .findFirst()
                .orElseThrow();
        var schema = (Map<?, ?>) trackingTool.get("inputSchema");

        assertEquals(java.util.List.of("trackingNumber"), schema.get("required"));
    }

    @Test
    void rejectsUnknownToolWithProtocolError() {
        var controller = new McpServerController(mock(AssistantTools.class));

        var response = controller.handle(Map.of(
                        "jsonrpc", "2.0", "id", 2, "method", "tools/call",
                        "params", Map.of("name", "delete_parcel", "arguments", Map.of())),
                null, "Bearer token");

        var error = (Map<?, ?>) response.getBody().get("error");
        assertEquals(-32602, error.get("code"));
    }

    @Test
    void rejectsMissingAuthentication() {
        var controller = new McpServerController(mock(AssistantTools.class));

        var response = controller.handle(Map.of("jsonrpc", "2.0", "id", 3, "method", "tools/list"), null, null);

        var error = (Map<?, ?>) response.getBody().get("error");
        assertEquals(-32001, error.get("code"));
    }
}
