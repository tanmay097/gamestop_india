package com.gameswap.matching;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gameswap.common.api.CursorPage;
import com.gameswap.common.security.SecurityUtils;
import com.gameswap.matching.MatchingDtos.*;
import com.gameswap.matching.MatchingDtos.MapPinDto;
import com.gameswap.matching.MatchingDtos.MatchCardDto;

@RestController
@RequestMapping("/api/v1")
public class MatchingController {

    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        
        this.matchingService = matchingService;
    }

    @GetMapping("/matches")
    public ResponseEntity<CursorPage<MatchCardDto>> findMatches(
        @RequestParam(required = false) Double radiusKm,
        @RequestParam(required = false) String platform,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(matchingService.findMatches(
            SecurityUtils.currentUserId(), radiusKm, platform, cursor, limit
        ));
    }

    @GetMapping("/map/pins")
    public ResponseEntity<CursorPage<MapPinDto>> findMapPins(
        @RequestParam(required = false) Double radiusKm,
        @RequestParam(required = false) UUID gameId,
        @RequestParam(required = false) String platform,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(matchingService.findMapPins(
            SecurityUtils.currentUserId(), radiusKm, gameId, platform, cursor, limit
        ));
    }
}

