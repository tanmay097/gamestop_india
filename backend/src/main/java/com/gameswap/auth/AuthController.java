package com.gameswap.auth;

import com.gameswap.auth.AuthDtos.*;
import com.gameswap.common.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/otp/request")
    public ResponseEntity<OtpRequestResponseDto> requestOtp(@Valid @RequestBody OtpRequestDto request) {
        return ResponseEntity.ok(authService.requestOtp(request.phone()));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<AuthTokensResponseDto> verifyOtp(@Valid @RequestBody OtpVerifyDto request) {
        return ResponseEntity.ok(authService.verifyOtp(request.phone(), request.otp()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokensResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto request) {
        return ResponseEntity.ok(authService.refreshTokens(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody(required = false) Map<String, String> body) {
        String refreshToken = body != null ? body.get("refreshToken") : null;
        authService.logout(SecurityUtils.currentUserId(), refreshToken);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
