package com.example.bankcards.repository;

import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.enums.CardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {
    Page<Card> findAllByOwnerId(UUID ownerId, Pageable pageable);
    Page<Card> findAllByOwnerIdAndStatus(UUID ownerId, CardStatus status, Pageable pageable);
    Optional<Card> findByIdAndOwnerId(UUID id, UUID ownerId);
}
