package com.swiftdrop.integration.merchant;

import java.util.List;

public record MerchantBulkResponse(int totalRows, int acceptedRows, int rejectedRows, List<Result> results) {
    public static MerchantBulkResponse from(List<Result> results) {
        int accepted = (int) results.stream().filter(Result::accepted).count();
        return new MerchantBulkResponse(results.size(), accepted, results.size() - accepted, results);
    }
    public record Result(int rowNumber, boolean accepted, MerchantOrderResponse order, List<String> errors) {}
}
