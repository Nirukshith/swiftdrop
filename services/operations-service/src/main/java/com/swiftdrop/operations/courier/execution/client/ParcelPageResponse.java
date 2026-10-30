package com.swiftdrop.operations.courier.execution.client;

import java.util.List;

public record ParcelPageResponse(
    List<ParcelResponse> content,
    Integer totalElements,
    Integer totalPages,
    Integer page,
    Integer size
) {}
