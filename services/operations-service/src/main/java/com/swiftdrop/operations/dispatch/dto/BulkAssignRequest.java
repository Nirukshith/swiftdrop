package com.swiftdrop.operations.dispatch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkAssignRequest(
    @NotEmpty(message = "assignments list must not be empty")
    List<@Valid AssignParcelRequest> assignments
) {}
