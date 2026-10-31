package com.gameswap.listings;

import com.gameswap.common.api.CursorPage;
import com.gameswap.common.security.SecurityUtils;
import com.gameswap.listings.ListingDtos.*;
import com.gameswap.users.UserDtos.ShelfGameDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
public class ListingsController {

    private final ListingsService listingsService;

    public ListingsController(ListingsService listingsService) {
        this.listingsService = listingsService;
    }

    @GetMapping("/listings/have")
    public ResponseEntity<CursorPage<HaveListingDto>> listHaveListings(
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(listingsService.listHaveListings(
            SecurityUtils.currentUserId(), userId, cursor, limit
        ));
    }

    @PostMapping("/listings/have")
    public ResponseEntity<HaveListingDto> createHaveListing(@Valid @RequestBody CreateHaveListingRequestDto request) {
        return ResponseEntity.ok(listingsService.createHaveListing(SecurityUtils.currentUserId(), request));
    }

    @DeleteMapping("/listings/have/{listingId}")
    public ResponseEntity<Map<String, String>> removeHaveListing(@PathVariable UUID listingId) {
        listingsService.removeHaveListing(SecurityUtils.currentUserId(), listingId);
        return ResponseEntity.ok(Map.of("message", "HAVE listing removed"));
    }

    @GetMapping("/listings/want")
    public ResponseEntity<CursorPage<WantListingDto>> listWantListings(
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(listingsService.listWantListings(
            SecurityUtils.currentUserId(), userId, cursor, limit
        ));
    }

    @PostMapping("/listings/want")
    public ResponseEntity<WantListingDto> createWantListing(@Valid @RequestBody CreateWantListingRequestDto request) {
        return ResponseEntity.ok(listingsService.createWantListing(SecurityUtils.currentUserId(), request));
    }

    @DeleteMapping("/listings/want/{listingId}")
    public ResponseEntity<Map<String, String>> removeWantListing(@PathVariable UUID listingId) {
        listingsService.removeWantListing(SecurityUtils.currentUserId(), listingId);
        return ResponseEntity.ok(Map.of("message", "WANT listing removed"));
    }

    @PutMapping("/shelf/{gameId}")
    public ResponseEntity<ShelfGameDto> setShelfStatus(
        @PathVariable UUID gameId,
        @Valid @RequestBody ShelfUpdateRequestDto request
    ) {
        return ResponseEntity.ok(listingsService.setShelfStatus(SecurityUtils.currentUserId(), gameId, request));
    }

    @DeleteMapping("/shelf/{gameId}")
    public ResponseEntity<Map<String, String>> removeFromShelf(@PathVariable UUID gameId) {
        listingsService.removeFromShelf(SecurityUtils.currentUserId(), gameId);
        return ResponseEntity.ok(Map.of("message", "Removed from shelf"));
    }
}

