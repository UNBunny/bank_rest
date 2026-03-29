package com.example.bankcards.util;

import org.springframework.stereotype.Component;

@Component
public class CardNumberMasker {

    public String mask(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            throw new IllegalArgumentException("Card number must be at least 4 digits");
        }
        String digits = cardNumber.replaceAll("\\s", "");
        String lastFour = digits.substring(digits.length() - 4);
        return "**** **** **** " + lastFour;
    }
}
