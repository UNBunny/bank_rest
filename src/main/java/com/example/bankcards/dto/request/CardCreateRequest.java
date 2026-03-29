package com.example.bankcards.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CardCreateRequest(
        @NotNull(message = "Owner ID is required")
        UUID ownerId,

        @NotNull(message = "Expiration date is required")
        @Future(message = "Expiration date must be in the future")
        LocalDate expirationDate,

        @NotNull(message = "Initial balance is required")
        @DecimalMin(value = "0.0", message = "Initial balance must be non-negative")
        BigDecimal initialBalance
) {}
