package com.example.bankcards.service;

import com.example.bankcards.dto.response.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {
    List<UserResponse> getAllUsers();
    UserResponse getUserById(UUID id);
    void deleteUser(UUID id);
}
