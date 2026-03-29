package com.example.bankcards.controller;

import com.example.bankcards.dto.request.CardCreateRequest;
import com.example.bankcards.dto.response.CardResponse;
import com.example.bankcards.dto.response.PageResponse;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/cards")
@RequiredArgsConstructor
@Tag(name = "Cards (Admin)")
@SecurityRequirement(name = "bearerAuth")
public class AdminCardController {

    private final CardService cardService;

    @GetMapping
    @Operation(summary = "Get all cards with optional status filter and pagination")
    public ResponseEntity<PageResponse<CardResponse>> getAllCards(
            @RequestParam(required = false) CardStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("[ADMIN] getAllCards: status={}, page={}, size={}", status, page, size);
        return ResponseEntity.ok(cardService.getAllCards(PageRequest.of(page, size, Sort.by("id"))));
    }

    @PostMapping
    @Operation(summary = "Create a card for a user")
    public ResponseEntity<CardResponse> createCard(@Valid @RequestBody CardCreateRequest request) {
        log.info("[ADMIN] createCard: ownerId={}", request.ownerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(cardService.createCard(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a card by ID")
    public ResponseEntity<Void> deleteCard(@PathVariable UUID id) {
        log.info("[ADMIN] deleteCard: id={}", id);
        cardService.deleteCard(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/block")
    @Operation(summary = "Block a card")
    public ResponseEntity<CardResponse> blockCard(@PathVariable UUID id) {
        log.info("[ADMIN] blockCard: id={}", id);
        return ResponseEntity.ok(cardService.blockCard(id));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a card")
    public ResponseEntity<CardResponse> activateCard(@PathVariable UUID id) {
        log.info("[ADMIN] activateCard: id={}", id);
        return ResponseEntity.ok(cardService.activateCard(id));
    }
}
