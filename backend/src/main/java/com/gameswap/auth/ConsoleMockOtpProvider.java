package com.gameswap.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "gameswap.auth.otp-provider", havingValue = "mock", matchIfMissing = true)
public class ConsoleMockOtpProvider implements SmsOtpProvider {

    private static final Logger log = LoggerFactory.getLogger(ConsoleMockOtpProvider.class);

    @Override
    public void sendOtp(String normalizedPhone, String otpCode) {
        String maskedPhone = normalizedPhone.length() >= 4
            ? "******" + normalizedPhone.substring(normalizedPhone.length() - 4)
            : "****";
        log.info("==========================================================");
        log.info("[GAMESWAP DEV OTP] Phone: {} ({}) | OTP Code: {}", maskedPhone, normalizedPhone, otpCode);
        log.info("==========================================================");
    }

    @Override
    public String providerName() {
        return "mock";
    }
}
