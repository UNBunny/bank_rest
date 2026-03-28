package com.example.bankcards.service;

import com.example.bankcards.dto.request.AuthLoginRequest;
import com.example.bankcards.dto.request.AuthRegisterRequest;
import com.example.bankcards.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(AuthRegisterRequest request);
    AuthResponse login(AuthLoginRequest request);
}
