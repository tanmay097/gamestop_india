package com.gameswap.ratings;

import com.gameswap.common.api.CursorPage;
import com.gameswap.common.security.SecurityUtils;
import com.gameswap.ratings.RatingDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class RatingsController {

    private final RatingsService ratingsService;

    public RatingsController(RatingsService ratingsService) {
        this.ratingsService = ratingsService;
    }

    @PostMapping("/swaps/{swapId}/ratings")
    public ResponseEntity<UserRatingDto> submitRating(
        @PathVariable UUID swapId,
        @Valid @RequestBody CreateRatingRequestDto request
    ) {
        return ResponseEntity.ok(ratingsService.submitRating(SecurityUtils.currentUserId(), swapId, request));
    }

    @GetMapping("/users/{userId}/ratings")
    public ResponseEntity<CursorPage<UserRatingDto>> listUserRatings(
        @PathVariable UUID userId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(ratingsService.listUserRatings(
            SecurityUtils.currentUserId(), userId, cursor, limit
        ));
    }
}

