package com.swiftdrop.integration.merchant;

import jakarta.validation.constraints.Size;

public record CancelMerchantOrderRequest(@Size(max = 500) String reason) {}
