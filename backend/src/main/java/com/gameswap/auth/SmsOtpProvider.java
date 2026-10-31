package com.gameswap.auth;

/**
 * Pluggable SMS OTP provider interface.
 * In development, {@link ConsoleMockOtpProvider} logs the OTP to console.
 * In production, plug in MSG91 or Twilio implementation via {@code gameswap.auth.otp-provider}.
 */
public interface SmsOtpProvider {

    void sendOtp(String normalizedPhone, String otpCode);

    String providerName();
}
