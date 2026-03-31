package com.example.bankcards.controller;

import com.example.bankcards.dto.request.CardTransferRequest;
import com.example.bankcards.dto.response.CardResponse;
import com.example.bankcards.dto.response.PageResponse;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.security.CustomUserDetails;
import com.example.bankcards.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Tag(name = "Cards (User)")
@SecurityRequirement(name = "bearerAuth")
public class CardController {

    private final CardService cardService;

    @GetMapping
    @Operation(summary = "Get own cards with optional status filter and pagination")
    public ResponseEntity<PageResponse<CardResponse>> getMyCards(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) CardStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.debug("getMyCards: userId={}, status={}, page={}, size={}", userDetails.getId(), status, page, size);
        return ResponseEntity.ok(cardService.getCardsByOwner(userDetails.getId(), status,
                PageRequest.of(page, size, Sort.by("id"))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific card by ID")
    public ResponseEntity<CardResponse> getCard(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("getCard: cardId={}, userId={}", id, userDetails.getId());
        return ResponseEntity.ok(cardService.getCardById(id, userDetails.getId(), false));
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Get card balance")
    public ResponseEntity<BigDecimal> getBalance(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("getBalance: cardId={}, userId={}", id, userDetails.getId());
        return ResponseEntity.ok(cardService.getBalance(id, userDetails.getId()));
    }

    @PatchMapping("/{id}/block")
    @Operation(summary = "Request to block own card")
    public ResponseEntity<CardResponse> requestBlock(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("requestBlock: cardId={}, userId={}", id, userDetails.getId());
        return ResponseEntity.ok(cardService.requestBlock(id, userDetails.getId()));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer between own cards")
    public ResponseEntity<Void> transfer(
            @Valid @RequestBody CardTransferRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("transfer: from={}, to={}, amount={}, userId={}",
                request.fromCardId(), request.toCardId(), request.amount(), userDetails.getId());
        cardService.transfer(request, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
