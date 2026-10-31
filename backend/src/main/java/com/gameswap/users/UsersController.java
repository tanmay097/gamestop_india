package com.gameswap.users;

import com.gameswap.common.api.CursorPage;
import com.gameswap.common.security.SecurityUtils;
import com.gameswap.users.UserDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class UsersController {

    private final UsersService usersService;

    public UsersController(UsersService usersService) {
        this.usersService = usersService;
    }

    @PutMapping("/users/me/onboarding")
    public ResponseEntity<MyProfileDto> completeOnboarding(@Valid @RequestBody OnboardingRequestDto request) {
        return ResponseEntity.ok(usersService.completeOnboarding(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/users/me")
    public ResponseEntity<MyProfileDto> getMyProfile() {
        return ResponseEntity.ok(usersService.getMyProfile(SecurityUtils.currentUserId()));
    }

    @PatchMapping("/users/me")
    public ResponseEntity<MyProfileDto> updateMyProfile(@Valid @RequestBody UpdateProfileRequestDto request) {
        return ResponseEntity.ok(usersService.updateProfile(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<PublicUserProfileDto> getPublicProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(usersService.getPublicProfile(SecurityUtils.currentUserId(), userId));
    }

    @GetMapping("/localities")
    public ResponseEntity<CursorPage<LocalityDto>> listLocalities(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(usersService.listLocalities(q, cursor, limit));
    }

    @GetMapping("/meetup-spots")
    public ResponseEntity<CursorPage<MeetupSpotDto>> listMeetupSpots(
        @RequestParam(required = false) UUID localityId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(usersService.listMeetupSpots(localityId, cursor, limit));
    }

    @GetMapping("/notifications")
    public ResponseEntity<CursorPage<NotificationDto>> listNotifications(
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(usersService.listNotifications(SecurityUtils.currentUserId(), cursor, limit));
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Map<String, String>> markAllNotificationsRead() {
        usersService.markAllNotificationsRead(SecurityUtils.currentUserId());
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }
}
