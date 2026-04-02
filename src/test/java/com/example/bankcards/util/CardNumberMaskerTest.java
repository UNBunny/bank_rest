package com.example.bankcards.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardNumberMaskerTest {

    private CardNumberMasker masker;

    @BeforeEach
    void setUp() {
        masker = new CardNumberMasker();
    }

    @Test
    void mask_returnsCorrectFormat() {
        String result = masker.mask("1234567890123456");
        assertThat(result).isEqualTo("**** **** **** 3456");
    }

    @Test
    void mask_stripsSpacesBeforeMasking() {
        String result = masker.mask("1234 5678 9012 3456");
        assertThat(result).isEqualTo("**** **** **** 3456");
    }

    @Test
    void mask_nullInput_throwsException() {
        assertThatThrownBy(() -> masker.mask(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mask_tooShortInput_throwsException() {
        assertThatThrownBy(() -> masker.mask("123"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
