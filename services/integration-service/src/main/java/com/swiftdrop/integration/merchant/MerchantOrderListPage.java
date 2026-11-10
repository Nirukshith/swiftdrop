package com.swiftdrop.integration.merchant;

import java.util.List;

public record MerchantOrderListPage(
        List<MerchantOrderListItem> content,
        long totalElements,
        int totalPages,
        int page,
        int size) {
}

