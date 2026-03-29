package com.example.bankcards.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CardResponse(
        UUID id,
        String maskedNumber,
        UUID ownerId,
        String ownerEmail,
        LocalDate expirationDate,
        String status,
        BigDecimal balance
) {}
