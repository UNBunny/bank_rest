package com.example.bankcards.exception;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        int status,
        String error,
        LocalDateTime timestamp,
        Map<String, String> details
) {}
