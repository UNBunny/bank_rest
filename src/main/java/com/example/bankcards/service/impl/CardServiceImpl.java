package com.example.bankcards.service.impl;

import com.example.bankcards.dto.request.CardCreateRequest;
import com.example.bankcards.dto.request.CardTransferRequest;
import com.example.bankcards.dto.response.CardResponse;
import com.example.bankcards.dto.response.PageResponse;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.exception.CardBlockedException;
import com.example.bankcards.exception.CardNotFoundException;
import com.example.bankcards.exception.InsufficientFundsException;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.mapper.CardMapper;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.CardService;
import com.example.bankcards.util.CardNumberEncryptor;
import com.example.bankcards.util.CardNumberMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final CardMapper cardMapper;
    private final CardNumberEncryptor cardNumberEncryptor;
    private final CardNumberMasker cardNumberMasker;

    @Override
    @Transactional
    public CardResponse createCard(CardCreateRequest request) {
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new UserNotFoundException(request.ownerId()));

        String rawNumber = generateCardNumber();
        String encryptedNumber = cardNumberEncryptor.encrypt(rawNumber);
        String maskedNumber = cardNumberMasker.mask(rawNumber);

        Card card = Card.builder()
                .encryptedNumber(encryptedNumber)
                .maskedNumber(maskedNumber)
                .owner(owner)
                .expirationDate(request.expirationDate())
                .status(CardStatus.ACTIVE)
                .balance(request.initialBalance())
                .build();

        CardResponse response = cardMapper.toResponse(cardRepository.save(card));
        log.info("Card created: id={}, owner={}, masked={}", response.id(), request.ownerId(), response.maskedNumber());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CardResponse getCardById(UUID id, UUID requesterId, boolean isAdmin) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardNotFoundException(id));
        if (!isAdmin && !card.getOwner().getId().equals(requesterId)) {
            throw new AccessDeniedException("Access denied to card: " + id);
        }
        return cardMapper.toResponse(card);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CardResponse> getAllCards(Pageable pageable) {
        Page<Card> page = cardRepository.findAll(pageable);
        return toPageResponse(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CardResponse> getCardsByOwner(UUID ownerId, CardStatus status, Pageable pageable) {
        Page<Card> page = (status != null)
                ? cardRepository.findAllByOwnerIdAndStatus(ownerId, status, pageable)
                : cardRepository.findAllByOwnerId(ownerId, pageable);
        return toPageResponse(page);
    }

    @Override
    @Transactional
    public CardResponse blockCard(UUID id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardNotFoundException(id));
        card.setStatus(CardStatus.BLOCKED);
        log.info("Card blocked by admin: id={}", id);
        return cardMapper.toResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public CardResponse activateCard(UUID id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardNotFoundException(id));
        card.setStatus(CardStatus.ACTIVE);
        log.info("Card activated by admin: id={}", id);
        return cardMapper.toResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public CardResponse requestBlock(UUID id, UUID requesterId) {
        Card card = cardRepository.findByIdAndOwnerId(id, requesterId)
                .orElseThrow(() -> new CardNotFoundException(id));
        card.setStatus(CardStatus.BLOCKED);
        log.info("Block requested by user: cardId={}, userId={}", id, requesterId);
        return cardMapper.toResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public void deleteCard(UUID id) {
        if (!cardRepository.existsById(id)) {
            throw new CardNotFoundException(id);
        }
        cardRepository.deleteById(id);
        log.info("Card deleted: id={}", id);
    }

    @Override
    @Transactional
    public void transfer(CardTransferRequest request, UUID requesterId) {
        Card from = cardRepository.findByIdAndOwnerId(request.fromCardId(), requesterId)
                .orElseThrow(() -> new CardNotFoundException(request.fromCardId()));
        Card to = cardRepository.findByIdAndOwnerId(request.toCardId(), requesterId)
                .orElseThrow(() -> new CardNotFoundException(request.toCardId()));
        if (from.getStatus() == CardStatus.BLOCKED) { throw new CardBlockedException(from.getId()); }
        if (to.getStatus() == CardStatus.BLOCKED)   { throw new CardBlockedException(to.getId()); }
        if (from.getBalance().compareTo(request.amount()) < 0) { throw new InsufficientFundsException(); }
        from.setBalance(from.getBalance().subtract(request.amount()));
        to.setBalance(to.getBalance().add(request.amount()));
        cardRepository.save(from);
        cardRepository.save(to);
        log.info("Transfer completed: from={}, to={}, amount={}", request.fromCardId(), request.toCardId(), request.amount());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID id, UUID requesterId) {
        Card card = cardRepository.findByIdAndOwnerId(id, requesterId)
                .orElseThrow(() -> new CardNotFoundException(id));
        return card.getBalance();
    }

    private String generateCardNumber() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append((int) (Math.random() * 10));
        }
        return sb.toString();
    }

    private PageResponse<CardResponse> toPageResponse(Page<Card> page) {
        return new PageResponse<>(
                cardMapper.toResponseList(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
