package com.example.bankcards.exception;

import java.util.UUID;

public class CardBlockedException extends RuntimeException {
    public CardBlockedException(UUID id) {
        super("Card is blocked: " + id);
    }
}
