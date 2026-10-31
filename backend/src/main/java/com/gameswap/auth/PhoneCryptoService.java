package com.gameswap.auth;

import com.gameswap.common.config.GameSwapProperties;
import com.gameswap.common.error.ApiException;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Service
public class PhoneCryptoService {

    private static final Pattern INDIAN_PHONE_PATTERN = Pattern.compile("^(?:\\+91|91|0)?([6-9]\\d{9})$");
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BIT_LENGTH = 128;

    private final byte[] hmacSecret;
    private final SecretKeySpec aesKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public PhoneCryptoService(GameSwapProperties properties) {
        this.hmacSecret = properties.auth().phoneHashSecret().getBytes(StandardCharsets.UTF_8);
        byte[] rawAes = properties.auth().phoneEncryptionSecret().getBytes(StandardCharsets.UTF_8);
        this.aesKey = new SecretKeySpec(Arrays.copyOf(rawAes, 32), "AES");
    }

    public String normalizePhone(String rawPhone) {
        if (rawPhone == null) {
            throw ApiException.badRequest("INVALID_PHONE", "Phone number is required");
        }
        String cleaned = rawPhone.replaceAll("[\\s\\-()]", "");
        var matcher = INDIAN_PHONE_PATTERN.matcher(cleaned);
        if (!matcher.matches()) {
            throw ApiException.badRequest("INVALID_PHONE", "Enter a valid 10-digit Indian mobile number");
        }
        return "+91" + matcher.group(1);
    }

    public String hashPhone(String normalizedPhone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash phone number", e);
        }
    }

    public String encryptPhone(String normalizedPhone) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BIT_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt phone number", e);
        }
    }
}
