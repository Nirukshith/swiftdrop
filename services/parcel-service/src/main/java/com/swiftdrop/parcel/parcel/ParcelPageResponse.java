package com.swiftdrop.parcel.parcel;

import org.springframework.data.domain.Page;

import java.util.List;

public record ParcelPageResponse(
        List<ParcelResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
    public static ParcelPageResponse from(Page<Parcel> page) {
        return new ParcelPageResponse(
                page.getContent().stream().map(ParcelResponse::from).toList(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }
}
