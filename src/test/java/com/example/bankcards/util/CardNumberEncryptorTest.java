package com.example.bankcards.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardNumberEncryptorTest {

    private CardNumberEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new CardNumberEncryptor("0123456789abcdef");
    }

    @Test
    void encrypt_thenDecrypt_returnsOriginal() {
        String original = "1234567890123456";
        String encrypted = encryptor.encrypt(original);
        String decrypted = encryptor.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void encrypt_producesDifferentValue() {
        String original = "1234567890123456";
        String encrypted = encryptor.encrypt(original);
        assertThat(encrypted).isNotEqualTo(original);
    }

    @Test
    void decrypt_withInvalidData_throwsException() {
        assertThatThrownBy(() -> encryptor.decrypt("not-valid-base64!!!"))
                .isInstanceOf(IllegalStateException.class);
    }
}
