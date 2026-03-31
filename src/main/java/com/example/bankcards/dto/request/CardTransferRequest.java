package com.example.bankcards.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CardTransferRequest(
        @NotNull(message = "Source card ID is required")
        UUID fromCardId,

        @NotNull(message = "Target card ID is required")
        UUID toCardId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Transfer amount must be at least 0.01")
        BigDecimal amount
) {}
