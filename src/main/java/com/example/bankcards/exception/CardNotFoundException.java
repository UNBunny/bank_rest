package com.example.bankcards.exception;

import java.util.UUID;

public class CardNotFoundException extends RuntimeException {
    public CardNotFoundException(UUID id) {
        super("Card not found: " + id);
    }
}
