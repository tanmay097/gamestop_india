package com.gameswap.swaps;

import com.gameswap.common.security.SecurityUtils;
import com.gameswap.swaps.SwapDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/swaps")
public class SwapsController {

    private final SwapsService swapsService;

    public SwapsController(SwapsService swapsService) {
        this.swapsService = swapsService;
    }

    @PostMapping("/propose")
    public ResponseEntity<SwapDto> proposeSwap(@Valid @RequestBody ProposeSwapRequestDto request) {
        return ResponseEntity.ok(swapsService.proposeSwap(SecurityUtils.currentUserId(), request));
    }

    @PostMapping("/{swapId}/accept")
    public ResponseEntity<SwapDto> acceptSwap(@PathVariable UUID swapId) {
        return ResponseEntity.ok(swapsService.acceptSwap(SecurityUtils.currentUserId(), swapId));
    }

    @PostMapping("/{swapId}/decline")
    public ResponseEntity<SwapDto> declineSwap(@PathVariable UUID swapId) {
        return ResponseEntity.ok(swapsService.declineSwap(SecurityUtils.currentUserId(), swapId));
    }

    @PostMapping("/{swapId}/cancel")
    public ResponseEntity<SwapDto> cancelSwap(@PathVariable UUID swapId) {
        return ResponseEntity.ok(swapsService.cancelSwap(SecurityUtils.currentUserId(), swapId));
    }

    @PostMapping("/{swapId}/confirm")
    public ResponseEntity<SwapDto> confirmSwap(@PathVariable UUID swapId) {
        return ResponseEntity.ok(swapsService.confirmSwapCompletion(SecurityUtils.currentUserId(), swapId));
    }

    @GetMapping("/{swapId}")
    public ResponseEntity<SwapDto> getSwap(@PathVariable UUID swapId) {
        return ResponseEntity.ok(swapsService.getSwapById(SecurityUtils.currentUserId(), swapId));
    }
}

