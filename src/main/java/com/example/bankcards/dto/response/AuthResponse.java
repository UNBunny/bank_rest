package com.example.bankcards.dto.response;

public record AuthResponse(
        String token,
        String email,
        String role
) {}
