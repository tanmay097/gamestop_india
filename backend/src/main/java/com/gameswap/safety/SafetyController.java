package com.gameswap.safety;

import com.gameswap.common.api.CursorPage;
import com.gameswap.common.security.SecurityUtils;
import com.gameswap.safety.SafetyDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/safety")
public class SafetyController {

    private final SafetyService safetyService;

    public SafetyController(SafetyService safetyService) {
        this.safetyService = safetyService;
    }

    @PostMapping("/blocks/{targetUserId}")
    public ResponseEntity<Map<String, String>> blockUser(@PathVariable UUID targetUserId) {
        safetyService.blockUser(SecurityUtils.currentUserId(), targetUserId);
        return ResponseEntity.ok(Map.of("message", "User blocked successfully"));
    }

    @DeleteMapping("/blocks/{targetUserId}")
    public ResponseEntity<Map<String, String>> unblockUser(@PathVariable UUID targetUserId) {
        safetyService.unblockUser(SecurityUtils.currentUserId(), targetUserId);
        return ResponseEntity.ok(Map.of("message", "User unblocked successfully"));
    }

    @GetMapping("/blocks")
    public ResponseEntity<CursorPage<BlockedUserDto>> listBlockedUsers(
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(safetyService.listBlockedUsers(SecurityUtils.currentUserId(), cursor, limit));
    }

    @PostMapping("/reports")
    public ResponseEntity<UserReportDto> reportUser(@Valid @RequestBody ReportUserRequestDto request) {
        return ResponseEntity.ok(safetyService.reportUser(SecurityUtils.currentUserId(), request));
    }
}

