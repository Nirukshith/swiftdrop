package com.swiftdrop.integration.merchant;

import java.util.List;
import org.springframework.data.domain.Page;

public record MerchantPageResponse(List<MerchantSummary> content, long totalElements,
                                   int totalPages, int page, int size) {
    public static MerchantPageResponse from(Page<Merchant> merchants) {
        return new MerchantPageResponse(merchants.getContent().stream().map(MerchantSummary::from).toList(),
                merchants.getTotalElements(), merchants.getTotalPages(), merchants.getNumber(), merchants.getSize());
    }
}
