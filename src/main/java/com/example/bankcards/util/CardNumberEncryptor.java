package com.example.bankcards.util;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
@Component
public class CardNumberEncryptor {
    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";
    private final SecretKeySpec secretKeySpec;
    public CardNumberEncryptor(@Value("${card.encryption.key}") String encryptionKey) {
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        this.secretKeySpec = new SecretKeySpec(keyBytes, "AES");
    }
    public String encrypt(String cardNumber) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(cardNumber.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Failed to encrypt card number", e); }
    }
    public String decrypt(String encryptedCardNumber) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedCardNumber)), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Failed to decrypt card number", e); }
    }
}