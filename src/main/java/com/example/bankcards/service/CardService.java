package com.example.bankcards.service;

import com.example.bankcards.dto.request.CardCreateRequest;
import com.example.bankcards.dto.request.CardTransferRequest;
import com.example.bankcards.dto.response.CardResponse;
import com.example.bankcards.dto.response.PageResponse;
import com.example.bankcards.entity.enums.CardStatus;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface CardService {
    CardResponse createCard(CardCreateRequest request);
    CardResponse getCardById(UUID id, UUID requesterId, boolean isAdmin);
    PageResponse<CardResponse> getAllCards(CardStatus status, Pageable pageable);
    PageResponse<CardResponse> getCardsByOwner(UUID ownerId, CardStatus status, Pageable pageable);
    CardResponse blockCard(UUID id);
    CardResponse activateCard(UUID id);
    CardResponse requestBlock(UUID id, UUID requesterId);
    void deleteCard(UUID id);
    void transfer(CardTransferRequest request, UUID requesterId);
    BigDecimal getBalance(UUID id, UUID requesterId);
}
