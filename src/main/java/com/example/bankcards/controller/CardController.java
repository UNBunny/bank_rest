package com.example.bankcards.controller;

import com.example.bankcards.dto.request.CardTransferRequest;
import com.example.bankcards.dto.response.CardResponse;
import com.example.bankcards.dto.response.PageResponse;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.security.CurrentUserId;
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
            @CurrentUserId UUID userId,
            @RequestParam(required = false) CardStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.debug("getMyCards: userId={}, status={}, page={}, size={}", userId, status, page, size);
        return ResponseEntity.ok(cardService.getCardsByOwner(userId, status,
                PageRequest.of(page, size, Sort.by("id"))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific card by ID")
    public ResponseEntity<CardResponse> getCard(
            @PathVariable UUID id,
            @CurrentUserId UUID userId) {
        log.debug("getCard: cardId={}, userId={}", id, userId);
        return ResponseEntity.ok(cardService.getCardById(id, userId, false));
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Get card balance")
    public ResponseEntity<BigDecimal> getBalance(
            @PathVariable UUID id,
            @CurrentUserId UUID userId) {
        log.debug("getBalance: cardId={}, userId={}", id, userId);
        return ResponseEntity.ok(cardService.getBalance(id, userId));
    }

    @PatchMapping("/{id}/block")
    @Operation(summary = "Request to block own card")
    public ResponseEntity<CardResponse> requestBlock(
            @PathVariable UUID id,
            @CurrentUserId UUID userId) {
        log.info("requestBlock: cardId={}, userId={}", id, userId);
        return ResponseEntity.ok(cardService.requestBlock(id, userId));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer between own cards")
    public ResponseEntity<Void> transfer(
            @Valid @RequestBody CardTransferRequest request,
            @CurrentUserId UUID userId) {
        log.info("transfer: from={}, to={}, amount={}, userId={}",
                request.fromCardId(), request.toCardId(), request.amount(), userId);
        cardService.transfer(request, userId);
        return ResponseEntity.noContent().build();
    }
}
