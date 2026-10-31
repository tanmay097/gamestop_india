package com.gameswap.games;

import com.gameswap.common.api.CursorPage;
import com.gameswap.common.security.SecurityUtils;
import com.gameswap.games.GameDtos.*;
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
@RequestMapping("/api/v1/games")
public class GamesController {

    private final GamesService gamesService;

    public GamesController(GamesService gamesService) {
        this.gamesService = gamesService;
    }

    @GetMapping("/search")
    public ResponseEntity<CursorPage<GameDto>> searchGames(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String platform,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(gamesService.searchGames(q, platform, cursor, limit));
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<GameDto> getGame(@PathVariable UUID gameId) {
        return ResponseEntity.ok(gamesService.getGameById(gameId));
    }

    @GetMapping("/{gameId}/owners")
    public ResponseEntity<CursorPage<LocalGameOwnerDto>> listLocalOwners(
        @PathVariable UUID gameId,
        @RequestParam(required = false) String platform,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(gamesService.listLocalOwners(
            SecurityUtils.currentUserId(), gameId, platform, cursor, limit
        ));
    }

    @GetMapping("/{gameId}/reviews")
    public ResponseEntity<CursorPage<GameReviewDto>> listReviews(
        @PathVariable UUID gameId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(gamesService.listReviews(gameId, cursor, limit));
    }

    @PostMapping("/{gameId}/reviews")
    public ResponseEntity<GameReviewDto> upsertReview(
        @PathVariable UUID gameId,
        @Valid @RequestBody CreateGameReviewRequestDto request
    ) {
        return ResponseEntity.ok(gamesService.upsertReview(SecurityUtils.currentUserId(), gameId, request));
    }

    @GetMapping("/{gameId}/discussions")
    public ResponseEntity<CursorPage<GameDiscussionDto>> listDiscussions(
        @PathVariable UUID gameId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(gamesService.listDiscussions(gameId, cursor, limit));
    }

    @PostMapping("/{gameId}/discussions")
    public ResponseEntity<GameDiscussionDto> createDiscussionPost(
        @PathVariable UUID gameId,
        @Valid @RequestBody CreateGameDiscussionRequestDto request
    ) {
        return ResponseEntity.ok(gamesService.createDiscussionPost(SecurityUtils.currentUserId(), gameId, request));
    }
}

