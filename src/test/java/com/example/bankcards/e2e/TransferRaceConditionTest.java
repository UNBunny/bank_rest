package com.example.bankcards.e2e;

import com.example.bankcards.dto.request.AuthLoginRequest;
import com.example.bankcards.dto.request.CardCreateRequest;
import com.example.bankcards.dto.request.CardTransferRequest;
import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.Role;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferRaceConditionTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userToken;
    private UUID userId;
    private UUID fromCardId;
    private UUID toCardId;

    private static final BigDecimal INITIAL_BALANCE = BigDecimal.valueOf(1000);
    private static final BigDecimal TRANSFER_AMOUNT = BigDecimal.valueOf(100);
    private static final int CONCURRENT_TRANSFERS = 10;

    @BeforeEach
    void setUp() throws Exception {
        cardRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(User.builder()
                .email("admin@race.com")
                .password(passwordEncoder.encode("Admin1234!"))
                .role(Role.ADMIN)
                .build());

        User user = userRepository.save(User.builder()
                .email("user@race.com")
                .password(passwordEncoder.encode("User1234!"))
                .role(Role.USER)
                .build());
        userId = user.getId();

        String adminToken = loginAndGetToken("admin@race.com", "Admin1234!");
        userToken = loginAndGetToken("user@race.com", "User1234!");

        fromCardId = createCardForUser(admin.getId(), adminToken, userId, INITIAL_BALANCE);
        toCardId = createCardForUser(admin.getId(), adminToken, userId, BigDecimal.ZERO);
    }

    @Test
    void concurrentTransfers_balanceIsConserved() throws Exception {
        CardTransferRequest transfer = new CardTransferRequest(fromCardId, toCardId, TRANSFER_AMOUNT);
        String transferJson = objectMapper.writeValueAsString(transfer);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TRANSFERS);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_TRANSFERS; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                MvcResult result = mockMvc.perform(post("/api/cards/transfer")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transferJson))
                        .andReturn();
                return result.getResponse().getStatus();
            }));
        }

        startLatch.countDown();
        executor.shutdown();

        long successCount = futures.stream()
                .mapToInt(f -> {
                    try { return f.get(); } catch (Exception e) { return 500; }
                })
                .filter(status -> status == 204)
                .count();

        BigDecimal fromBalance = cardRepository.findById(fromCardId).orElseThrow().getBalance();
        BigDecimal toBalance = cardRepository.findById(toCardId).orElseThrow().getBalance();
        BigDecimal totalBalance = fromBalance.add(toBalance);

        assertThat(totalBalance).isEqualByComparingTo(INITIAL_BALANCE);

        BigDecimal expectedFromBalance = INITIAL_BALANCE.subtract(
                TRANSFER_AMOUNT.multiply(BigDecimal.valueOf(successCount)));
        BigDecimal expectedToBalance = TRANSFER_AMOUNT.multiply(BigDecimal.valueOf(successCount));

        assertThat(fromBalance).isEqualByComparingTo(expectedFromBalance);
        assertThat(toBalance).isEqualByComparingTo(expectedToBalance);
    }

    @Test
    void bidirectionalTransfers_noDeadlock() throws Exception {
        CardTransferRequest forwardTransfer = new CardTransferRequest(fromCardId, toCardId, TRANSFER_AMOUNT);
        CardTransferRequest reverseTransfer = new CardTransferRequest(toCardId, fromCardId, TRANSFER_AMOUNT);

        cardRepository.findById(toCardId).ifPresent(c -> {
            c.setBalance(INITIAL_BALANCE);
            cardRepository.save(c);
        });

        String forwardJson = objectMapper.writeValueAsString(forwardTransfer);
        String reverseJson = objectMapper.writeValueAsString(reverseTransfer);

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String json = (i % 2 == 0) ? forwardJson : reverseJson;
            futures.add(executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/cards/transfer")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                        .andReturn().getResponse().getStatus();
            }));
        }

        startLatch.countDown();
        executor.shutdown();

        futures.forEach(f -> {
            try { f.get(); } catch (Exception e) { throw new RuntimeException(e); }
        });

        BigDecimal fromBalance = cardRepository.findById(fromCardId).orElseThrow().getBalance();
        BigDecimal toBalance = cardRepository.findById(toCardId).orElseThrow().getBalance();

        assertThat(fromBalance.add(toBalance)).isEqualByComparingTo(INITIAL_BALANCE.add(INITIAL_BALANCE));
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        AuthLoginRequest login = new AuthLoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private UUID createCardForUser(UUID adminId, String adminToken, UUID ownerId, BigDecimal balance) throws Exception {
        CardCreateRequest request = new CardCreateRequest(ownerId, LocalDate.now().plusYears(2), balance);
        MvcResult result = mockMvc.perform(post("/api/admin/cards")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}
