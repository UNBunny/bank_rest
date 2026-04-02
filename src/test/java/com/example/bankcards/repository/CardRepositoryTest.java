package com.example.bankcards.repository;

import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.entity.enums.Role;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CardRepositoryTest {

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User owner;
    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);

        owner = userRepository.save(User.builder()
                .email("ivan.petrov@finbank.io")
                .password("$2a$10$hashed")
                .role(Role.USER)
                .build());
    }

    private Card buildCard(CardStatus status, String maskedNumber, BigDecimal balance) {
        return Card.builder()
                .encryptedNumber("AES256:encrypted")
                .maskedNumber(maskedNumber)
                .owner(owner)
                .expirationDate(LocalDate.of(2028, 6, 30))
                .status(status)
                .balance(balance)
                .build();
    }

    @Test
    void findAllByOwnerId_returnsBothCards() {
        cardRepository.save(buildCard(CardStatus.ACTIVE,  "**** **** **** 4821", new BigDecimal("1250.75")));
        cardRepository.save(buildCard(CardStatus.BLOCKED, "**** **** **** 0033", new BigDecimal("0.01")));

        Page<Card> page = cardRepository.findAllByOwnerId(owner.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .hasSize(2)
                .extracting(Card::getMaskedNumber)
                .containsExactlyInAnyOrder("**** **** **** 4821", "**** **** **** 0033");
    }

    @Test
    void findAllByOwnerId_outOfRangePage_returnsEmpty() {
        cardRepository.save(buildCard(CardStatus.ACTIVE,  "**** **** **** 7711", new BigDecimal("500.00")));
        cardRepository.save(buildCard(CardStatus.BLOCKED, "**** **** **** 3302", new BigDecimal("200.50")));

        Page<Card> page = cardRepository.findAllByOwnerId(owner.getId(), PageRequest.of(5, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(1);
        assertThat(page.getNumber()).isEqualTo(5);
    }

    @Test
    void findAllByOwnerIdAndStatus_returnsOnlyMatchingStatus() {
        cardRepository.save(buildCard(CardStatus.ACTIVE,  "**** **** **** 9900", new BigDecimal("3200.00")));
        cardRepository.save(buildCard(CardStatus.BLOCKED, "**** **** **** 1144", new BigDecimal("15.99")));

        Page<Card> activePage = cardRepository.findAllByOwnerIdAndStatus(
                owner.getId(), CardStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(activePage.getTotalElements()).isEqualTo(1);
        assertThat(activePage.getContent())
                .first()
                .extracting(Card::getStatus, Card::getMaskedNumber)
                .containsExactly(CardStatus.ACTIVE, "**** **** **** 9900");
    }

    @Test
    void findAllByOwnerIdAndStatus_outOfRangePage_returnsEmpty() {
        cardRepository.save(buildCard(CardStatus.ACTIVE, "**** **** **** 5577", new BigDecimal("800.00")));

        Page<Card> page = cardRepository.findAllByOwnerIdAndStatus(
                owner.getId(), CardStatus.ACTIVE, PageRequest.of(3, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

    @Test
    void findAllByStatus_filtersEachStatusCorrectly() {
        cardRepository.save(buildCard(CardStatus.ACTIVE,  "**** **** **** 6612", new BigDecimal("2000.00")));
        cardRepository.save(buildCard(CardStatus.BLOCKED, "**** **** **** 7723", new BigDecimal("50.25")));
        cardRepository.save(buildCard(CardStatus.EXPIRED, "**** **** **** 8834", new BigDecimal("0.00")));

        Page<Card> active  = cardRepository.findAllByStatus(CardStatus.ACTIVE,  PageRequest.of(0, 10));
        Page<Card> blocked = cardRepository.findAllByStatus(CardStatus.BLOCKED, PageRequest.of(0, 10));
        Page<Card> expired = cardRepository.findAllByStatus(CardStatus.EXPIRED, PageRequest.of(0, 10));

        assertThat(active.getTotalElements()).isEqualTo(1);
        assertThat(active.getContent()).extracting(Card::getStatus).containsOnly(CardStatus.ACTIVE);

        assertThat(blocked.getTotalElements()).isEqualTo(1);
        assertThat(blocked.getContent()).extracting(Card::getStatus).containsOnly(CardStatus.BLOCKED);

        assertThat(expired.getTotalElements()).isEqualTo(1);
        assertThat(expired.getContent()).extracting(Card::getStatus).containsOnly(CardStatus.EXPIRED);
    }

    @Test
    void findByIdAndOwnerId_correctOwner_returnsCard() {
        Card saved = cardRepository.save(
                buildCard(CardStatus.ACTIVE, "**** **** **** 3388", new BigDecimal("750.00")));

        Optional<Card> found = cardRepository.findByIdAndOwnerId(saved.getId(), owner.getId());

        assertThat(found)
                .isPresent()
                .get()
                .extracting(Card::getId, Card::getMaskedNumber)
                .containsExactly(saved.getId(), "**** **** **** 3388");
    }

    @Test
    void findByIdAndOwnerId_wrongOwner_returnsEmpty() {
        Card saved = cardRepository.save(
                buildCard(CardStatus.ACTIVE, "**** **** **** 4499", new BigDecimal("100.00")));
        UUID someoneElseId = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");

        Optional<Card> found = cardRepository.findByIdAndOwnerId(saved.getId(), someoneElseId);

        assertThat(found).isEmpty();
    }

    @Test
    void findByIdAndOwnerIdForUpdate_hasLockAnnotation() throws NoSuchMethodException {
        Method method = CardRepository.class.getMethod(
                "findByIdAndOwnerIdForUpdate", UUID.class, UUID.class);

        Lock lockAnnotation = method.getAnnotation(Lock.class);

        assertThat(lockAnnotation).isNotNull();
        assertThat(lockAnnotation.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void findByIdAndOwnerIdForUpdate_correctOwner_returnsCard() {
        Card saved = cardRepository.save(
                buildCard(CardStatus.ACTIVE, "**** **** **** 6655", new BigDecimal("1500.00")));

        Optional<Card> found = cardRepository.findByIdAndOwnerIdForUpdate(saved.getId(), owner.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getBalance()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void findByIdAndOwnerIdForUpdate_wrongOwner_returnsEmpty() {
        Card saved = cardRepository.save(
                buildCard(CardStatus.ACTIVE, "**** **** **** 7766", new BigDecimal("300.00")));
        UUID wrongOwnerId = UUID.fromString("ffffffff-0000-0000-0000-000000000002");

        Optional<Card> found = cardRepository.findByIdAndOwnerIdForUpdate(saved.getId(), wrongOwnerId);

        assertThat(found).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void findByIdAndOwnerIdForUpdate_concurrentUpdates_noLostUpdates() throws Exception {
        UUID[] ids = new UUID[2];
        txTemplate.executeWithoutResult(tx -> {
            User concurrentUser = userRepository.save(User.builder()
                    .email("concurrent.test@finbank.io")
                    .password("$2a$10$hash")
                    .role(Role.USER)
                    .build());
            Card card = cardRepository.save(Card.builder()
                    .encryptedNumber("AES256:lock-test")
                    .maskedNumber("**** **** **** 9999")
                    .owner(concurrentUser)
                    .expirationDate(LocalDate.of(2030, 1, 31))
                    .status(CardStatus.ACTIVE)
                    .balance(new BigDecimal("500.00"))
                    .build());
            ids[0] = concurrentUser.getId();
            ids[1] = card.getId();
        });

        UUID userId = ids[0];
        UUID cardId = ids[1];

        int threadCount = 5;
        BigDecimal increment = new BigDecimal("100.00");
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate  = new CountDownLatch(threadCount);
        AtomicInteger errors     = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                try {
                    startGate.await();
                    txTemplate.executeWithoutResult(tx -> {
                        Card c = cardRepository.findByIdAndOwnerIdForUpdate(cardId, userId)
                                .orElseThrow(() -> new RuntimeException("Card not found"));
                        c.setBalance(c.getBalance().add(increment));
                        cardRepository.save(c);
                    });
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        startGate.countDown();
        boolean allFinished = doneGate.await(15, TimeUnit.SECONDS);

        assertThat(allFinished).isTrue();
        assertThat(errors.get()).isZero();

        BigDecimal finalBalance = txTemplate.execute(tx ->
                cardRepository.findById(cardId).orElseThrow().getBalance());

        assertThat(finalBalance).isEqualByComparingTo(new BigDecimal("1000.00"));

        txTemplate.executeWithoutResult(tx -> {
            cardRepository.deleteById(cardId);
            userRepository.deleteById(userId);
            userRepository.deleteById(owner.getId()); // committed by @BeforeEach
        });
    }
}
