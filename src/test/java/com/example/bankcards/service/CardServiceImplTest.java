package com.example.bankcards.service;

import com.example.bankcards.dto.request.CardCreateRequest;
import com.example.bankcards.dto.request.CardTransferRequest;
import com.example.bankcards.dto.response.CardResponse;
import com.example.bankcards.dto.response.PageResponse;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.entity.enums.Role;
import com.example.bankcards.exception.CardBlockedException;
import com.example.bankcards.exception.CardNotFoundException;
import com.example.bankcards.exception.InsufficientFundsException;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.mapper.CardMapper;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.impl.CardServiceImpl;
import com.example.bankcards.util.CardNumberEncryptor;
import com.example.bankcards.util.CardNumberMasker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {

    @Mock private CardRepository cardRepository;
    @Mock private UserRepository userRepository;
    @Mock private CardMapper cardMapper;
    @Mock private CardNumberEncryptor cardNumberEncryptor;
    @Mock private CardNumberMasker cardNumberMasker;

    @InjectMocks
    private CardServiceImpl cardService;


    private static final UUID ALICE_ID      = UUID.fromString("a1a1a1a1-0000-0000-0000-000000000001");
    private static final UUID ALICE_CARD_ID = UUID.fromString("c1c1c1c1-0000-0000-0000-000000000001");
    private static final UUID SECOND_CARD_ID = UUID.fromString("c2c2c2c2-0000-0000-0000-000000000002");

    private User alice;
    private Card aliceActiveCard;
    private CardResponse aliceCardResponse;

    @BeforeEach
    void setUp() {
        alice = User.builder()
                .id(ALICE_ID)
                .email("alice.morgan@finbank.io")
                .password("$2a$10$hashedSecret")
                .role(Role.USER)
                .build();

        aliceActiveCard = Card.builder()
                .id(ALICE_CARD_ID)
                .encryptedNumber("AES256:a3f9c8b2d1e0...")
                .maskedNumber("**** **** **** 4821")
                .owner(alice)
                .expirationDate(LocalDate.of(2027, 9, 30))
                .status(CardStatus.ACTIVE)
                .balance(new BigDecimal("3847.29"))
                .build();

        aliceCardResponse = new CardResponse(
                ALICE_CARD_ID,
                "**** **** **** 4821",
                ALICE_ID,
                "alice.morgan@finbank.io",
                LocalDate.of(2027, 9, 30),
                "ACTIVE",
                new BigDecimal("3847.29")
        );
    }

    @Test
    void createCard_success() {
        CardCreateRequest request = new CardCreateRequest(
                ALICE_ID, LocalDate.of(2028, 3, 31), new BigDecimal("500.75")
        );
        when(userRepository.findById(ALICE_ID)).thenReturn(Optional.of(alice));
        when(cardNumberEncryptor.encrypt(any())).thenReturn("AES256:c9fe...");
        when(cardNumberMasker.mask(any())).thenReturn("**** **** **** 4821");
        when(cardRepository.save(any(Card.class))).thenReturn(aliceActiveCard);
        when(cardMapper.toResponse(aliceActiveCard)).thenReturn(aliceCardResponse);

        CardResponse result = cardService.createCard(request);

        assertThat(result)
                .isNotNull()
                .extracting(CardResponse::ownerId, CardResponse::maskedNumber)
                .containsExactly(ALICE_ID, "**** **** **** 4821");
        verify(cardRepository).save(any(Card.class));
    }

    @Test
    void createCard_userNotFound_throws() {
        UUID unknownUserId = UUID.fromString("deadbeef-dead-beef-dead-beefdeadbeef");
        CardCreateRequest request = new CardCreateRequest(
                unknownUserId, LocalDate.of(2028, 6, 30), BigDecimal.ZERO
        );
        when(userRepository.findById(unknownUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.createCard(request))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(cardRepository);
    }

    @Test
    void getCardById_owner_returnsCard() {
        when(cardRepository.findById(ALICE_CARD_ID)).thenReturn(Optional.of(aliceActiveCard));
        when(cardMapper.toResponse(aliceActiveCard)).thenReturn(aliceCardResponse);

        CardResponse result = cardService.getCardById(ALICE_CARD_ID, ALICE_ID, false);

        assertThat(result)
                .isNotNull()
                .extracting(CardResponse::id, CardResponse::status)
                .containsExactly(ALICE_CARD_ID, "ACTIVE");
    }

    @Test
    void getCardById_admin_returnsAnyCard() {
        UUID adminId = UUID.fromString("adadadad-0000-0000-0000-000000000001");
        when(cardRepository.findById(ALICE_CARD_ID)).thenReturn(Optional.of(aliceActiveCard));
        when(cardMapper.toResponse(aliceActiveCard)).thenReturn(aliceCardResponse);

        CardResponse result = cardService.getCardById(ALICE_CARD_ID, adminId, true);

        assertThat(result.id()).isEqualTo(ALICE_CARD_ID);
        assertThat(result.ownerId()).isEqualTo(ALICE_ID);
    }

    @Test
    void getCardById_otherUser_throws() {
        UUID strangerId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        when(cardRepository.findById(ALICE_CARD_ID)).thenReturn(Optional.of(aliceActiveCard));

        assertThatThrownBy(() -> cardService.getCardById(ALICE_CARD_ID, strangerId, false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(ALICE_CARD_ID.toString());
    }

    @Test
    void getCardById_notFound_throws() {
        UUID nonExistentCardId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        when(cardRepository.findById(nonExistentCardId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getCardById(nonExistentCardId, ALICE_ID, false))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void blockCard_setsStatusBlocked() {
        when(cardRepository.findById(ALICE_CARD_ID)).thenReturn(Optional.of(aliceActiveCard));
        when(cardRepository.save(aliceActiveCard)).thenReturn(aliceActiveCard);
        when(cardMapper.toResponse(aliceActiveCard)).thenReturn(aliceCardResponse);

        cardService.blockCard(ALICE_CARD_ID);

        assertThat(aliceActiveCard.getStatus()).isEqualTo(CardStatus.BLOCKED);
        verify(cardRepository).save(aliceActiveCard);
    }

    @Test
    void activateCard_setsStatusActive() {
        aliceActiveCard.setStatus(CardStatus.BLOCKED);
        when(cardRepository.findById(ALICE_CARD_ID)).thenReturn(Optional.of(aliceActiveCard));
        when(cardRepository.save(aliceActiveCard)).thenReturn(aliceActiveCard);
        when(cardMapper.toResponse(aliceActiveCard)).thenReturn(aliceCardResponse);

        cardService.activateCard(ALICE_CARD_ID);

        assertThat(aliceActiveCard.getStatus()).isEqualTo(CardStatus.ACTIVE);
        verify(cardRepository).save(aliceActiveCard);
    }

    @Test
    void activateCard_expiredDate_throws() {
        aliceActiveCard.setExpirationDate(LocalDate.of(2019, 12, 31));
        aliceActiveCard.setStatus(CardStatus.BLOCKED);
        when(cardRepository.findById(ALICE_CARD_ID)).thenReturn(Optional.of(aliceActiveCard));

        assertThatThrownBy(() -> cardService.activateCard(ALICE_CARD_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expiration");
    }

    @Test
    void requestBlock_owner_blocksCard() {
        when(cardRepository.findByIdAndOwnerId(ALICE_CARD_ID, ALICE_ID))
                .thenReturn(Optional.of(aliceActiveCard));
        when(cardRepository.save(aliceActiveCard)).thenReturn(aliceActiveCard);
        when(cardMapper.toResponse(aliceActiveCard)).thenReturn(aliceCardResponse);

        cardService.requestBlock(ALICE_CARD_ID, ALICE_ID);

        assertThat(aliceActiveCard.getStatus()).isEqualTo(CardStatus.BLOCKED);
        verify(cardRepository).save(aliceActiveCard);
    }

    @Test
    void requestBlock_notOwner_throws() {
        UUID bobId = UUID.fromString("b0b0b0b0-0000-0000-0000-000000000002");
        when(cardRepository.findByIdAndOwnerId(ALICE_CARD_ID, bobId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.requestBlock(ALICE_CARD_ID, bobId))
                .isInstanceOf(CardNotFoundException.class);
        verify(cardRepository, never()).save(any());
    }

    @Test
    void deleteCard_success() {
        when(cardRepository.existsById(ALICE_CARD_ID)).thenReturn(true);

        cardService.deleteCard(ALICE_CARD_ID);

        verify(cardRepository).deleteById(ALICE_CARD_ID);
    }

    @Test
    void deleteCard_notFound_throws() {
        UUID nonExistentId = UUID.fromString("00000000-dead-beef-0000-000000000000");
        when(cardRepository.existsById(nonExistentId)).thenReturn(false);

        assertThatThrownBy(() -> cardService.deleteCard(nonExistentId))
                .isInstanceOf(CardNotFoundException.class);
        verify(cardRepository, never()).deleteById(any());
    }

    @Test
    void getAllCards_noFilter_returnsAll() {
        when(cardRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(aliceActiveCard)));
        when(cardMapper.toResponseList(List.of(aliceActiveCard)))
                .thenReturn(List.of(aliceCardResponse));

        PageResponse<CardResponse> result = cardService.getAllCards(null, PageRequest.of(0, 10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        verify(cardRepository).findAll(any(org.springframework.data.domain.Pageable.class));
        verify(cardRepository, never()).findAllByStatus(any(), any());
    }

    @Test
    void getAllCards_withStatusFilter_returnsFiltered() {
        Card blockedCard = Card.builder()
                .id(UUID.fromString("c9c9c9c9-0000-0000-0000-000000000009"))
                .maskedNumber("**** **** **** 0017")
                .owner(alice)
                .expirationDate(LocalDate.of(2026, 5, 31))
                .status(CardStatus.BLOCKED)
                .balance(new BigDecimal("12.50"))
                .build();
        CardResponse blockedResponse = new CardResponse(
                blockedCard.getId(), "**** **** **** 0017", ALICE_ID,
                "alice.morgan@finbank.io", LocalDate.of(2026, 5, 31),
                "BLOCKED", new BigDecimal("12.50")
        );
        when(cardRepository.findAllByStatus(eq(CardStatus.BLOCKED), any()))
                .thenReturn(new PageImpl<>(List.of(blockedCard)));
        when(cardMapper.toResponseList(List.of(blockedCard)))
                .thenReturn(List.of(blockedResponse));

        PageResponse<CardResponse> result = cardService.getAllCards(CardStatus.BLOCKED, PageRequest.of(0, 10));

        assertThat(result.content()).extracting(CardResponse::status).containsOnly("BLOCKED");
        verify(cardRepository).findAllByStatus(eq(CardStatus.BLOCKED), any());
        verify(cardRepository, never()).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void getAllCards_outOfRangePage_returnsEmptyWithMeta() {
        when(cardRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(
                        Collections.emptyList(),
                        PageRequest.of(5, 10),
                        3L
                ));
        when(cardMapper.toResponseList(Collections.emptyList()))
                .thenReturn(Collections.emptyList());

        PageResponse<CardResponse> result = cardService.getAllCards(null, PageRequest.of(5, 10));

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(5);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(3L);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void getCardsByOwner_noFilter_returnsAll() {
        when(cardRepository.findAllByOwnerId(eq(ALICE_ID), any()))
                .thenReturn(new PageImpl<>(List.of(aliceActiveCard)));
        when(cardMapper.toResponseList(List.of(aliceActiveCard)))
                .thenReturn(List.of(aliceCardResponse));

        PageResponse<CardResponse> result = cardService.getCardsByOwner(ALICE_ID, null, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        verify(cardRepository).findAllByOwnerId(eq(ALICE_ID), any());
        verify(cardRepository, never()).findAllByOwnerIdAndStatus(any(), any(), any());
    }

    @Test
    void getCardsByOwner_withStatusFilter_returnsFiltered() {
        when(cardRepository.findAllByOwnerIdAndStatus(eq(ALICE_ID), eq(CardStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(aliceActiveCard)));
        when(cardMapper.toResponseList(List.of(aliceActiveCard)))
                .thenReturn(List.of(aliceCardResponse));

        PageResponse<CardResponse> result = cardService.getCardsByOwner(ALICE_ID, CardStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        verify(cardRepository).findAllByOwnerIdAndStatus(eq(ALICE_ID), eq(CardStatus.ACTIVE), any());
    }

    @Test
    void getBalance_owner_returnsBalance() {
        when(cardRepository.findByIdAndOwnerId(ALICE_CARD_ID, ALICE_ID))
                .thenReturn(Optional.of(aliceActiveCard));

        BigDecimal balance = cardService.getBalance(ALICE_CARD_ID, ALICE_ID);

        assertThat(balance).isEqualByComparingTo(new BigDecimal("3847.29"));
    }

    @Test
    void getBalance_notOwner_throws() {
        UUID bobId = UUID.fromString("b0b0b0b0-0000-0000-0000-000000000002");
        when(cardRepository.findByIdAndOwnerId(ALICE_CARD_ID, bobId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getBalance(ALICE_CARD_ID, bobId))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void transfer_success_updatesBalances() {
        Card aliceSecondCard = Card.builder()
                .id(SECOND_CARD_ID)
                .owner(alice)
                .status(CardStatus.ACTIVE)
                .balance(new BigDecimal("200.00"))
                .expirationDate(LocalDate.of(2028, 11, 30))
                .build();
        CardTransferRequest request = new CardTransferRequest(
                ALICE_CARD_ID, SECOND_CARD_ID, new BigDecimal("1000.00")
        );
        when(cardRepository.findByIdAndOwnerIdForUpdate(ALICE_CARD_ID, ALICE_ID))
                .thenReturn(Optional.of(aliceActiveCard));
        when(cardRepository.findByIdAndOwnerIdForUpdate(SECOND_CARD_ID, ALICE_ID))
                .thenReturn(Optional.of(aliceSecondCard));

        cardService.transfer(request, ALICE_ID);

        assertThat(aliceActiveCard.getBalance()).isEqualByComparingTo(new BigDecimal("2847.29"));
        assertThat(aliceSecondCard.getBalance()).isEqualByComparingTo(new BigDecimal("1200.00"));
        verify(cardRepository, times(2)).save(any(Card.class));
    }

    @Test
    void transfer_insufficientFunds_throws() {
        Card destination = Card.builder()
                .id(SECOND_CARD_ID).owner(alice)
                .status(CardStatus.ACTIVE).balance(BigDecimal.ZERO)
                .build();
        CardTransferRequest request = new CardTransferRequest(
                ALICE_CARD_ID, SECOND_CARD_ID, new BigDecimal("99999.99")
        );
        when(cardRepository.findByIdAndOwnerIdForUpdate(ALICE_CARD_ID, ALICE_ID))
                .thenReturn(Optional.of(aliceActiveCard));
        when(cardRepository.findByIdAndOwnerIdForUpdate(SECOND_CARD_ID, ALICE_ID))
                .thenReturn(Optional.of(destination));

        assertThatThrownBy(() -> cardService.transfer(request, ALICE_ID))
                .isInstanceOf(InsufficientFundsException.class);
        verify(cardRepository, never()).save(any());
    }

    @Test
    void transfer_sourceBlocked_throws() {
        aliceActiveCard.setStatus(CardStatus.BLOCKED);
        Card destination = Card.builder()
                .id(SECOND_CARD_ID).owner(alice)
                .status(CardStatus.ACTIVE).balance(new BigDecimal("500.00"))
                .build();
        CardTransferRequest request = new CardTransferRequest(
                ALICE_CARD_ID, SECOND_CARD_ID, new BigDecimal("100.00")
        );
        when(cardRepository.findByIdAndOwnerIdForUpdate(ALICE_CARD_ID, ALICE_ID))
                .thenReturn(Optional.of(aliceActiveCard));
        when(cardRepository.findByIdAndOwnerIdForUpdate(SECOND_CARD_ID, ALICE_ID))
                .thenReturn(Optional.of(destination));

        assertThatThrownBy(() -> cardService.transfer(request, ALICE_ID))
                .isInstanceOf(CardBlockedException.class)
                .hasMessageContaining(ALICE_CARD_ID.toString());
    }

    @Test
    void transfer_destinationBlocked_throws() {
        Card blockedDestination = Card.builder()
                .id(SECOND_CARD_ID).owner(alice)
                .status(CardStatus.BLOCKED).balance(new BigDecimal("0.00"))
                .build();
        CardTransferRequest request = new CardTransferRequest(
                ALICE_CARD_ID, SECOND_CARD_ID, new BigDecimal("50.17")
        );
        when(cardRepository.findByIdAndOwnerIdForUpdate(ALICE_CARD_ID, ALICE_ID))
                .thenReturn(Optional.of(aliceActiveCard));
        when(cardRepository.findByIdAndOwnerIdForUpdate(SECOND_CARD_ID, ALICE_ID))
                .thenReturn(Optional.of(blockedDestination));

        assertThatThrownBy(() -> cardService.transfer(request, ALICE_ID))
                .isInstanceOf(CardBlockedException.class)
                .hasMessageContaining(SECOND_CARD_ID.toString());
    }

    @Test
    void transfer_destinationExpired_throws() {
        Card expiredDestination = Card.builder()
                .id(SECOND_CARD_ID).owner(alice)
                .status(CardStatus.EXPIRED).balance(BigDecimal.ZERO)
                .build();
        CardTransferRequest request = new CardTransferRequest(
                ALICE_CARD_ID, SECOND_CARD_ID, new BigDecimal("100.00")
        );
        when(cardRepository.findByIdAndOwnerIdForUpdate(ALICE_CARD_ID, ALICE_ID))
                .thenReturn(Optional.of(aliceActiveCard));
        when(cardRepository.findByIdAndOwnerIdForUpdate(SECOND_CARD_ID, ALICE_ID))
                .thenReturn(Optional.of(expiredDestination));

        assertThatThrownBy(() -> cardService.transfer(request, ALICE_ID))
                .isInstanceOf(CardBlockedException.class);
    }

    @Test
    void transfer_destinationNotOwned_throws() {
        CardTransferRequest request = new CardTransferRequest(
                ALICE_CARD_ID, SECOND_CARD_ID, new BigDecimal("50.00")
        );
        when(cardRepository.findByIdAndOwnerIdForUpdate(ALICE_CARD_ID, ALICE_ID))
                .thenReturn(Optional.of(aliceActiveCard));
        when(cardRepository.findByIdAndOwnerIdForUpdate(SECOND_CARD_ID, ALICE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.transfer(request, ALICE_ID))
                .isInstanceOf(CardNotFoundException.class);
        verify(cardRepository, never()).save(any());
    }

    @Test
    void transfer_sameCard_throws() {
        CardTransferRequest request = new CardTransferRequest(
                ALICE_CARD_ID, ALICE_CARD_ID, new BigDecimal("100.00")
        );

        assertThatThrownBy(() -> cardService.transfer(request, ALICE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different");
        verifyNoInteractions(cardRepository);
    }
}
