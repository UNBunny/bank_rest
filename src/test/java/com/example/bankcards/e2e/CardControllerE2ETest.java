package com.example.bankcards.e2e;

import com.example.bankcards.dto.request.AuthLoginRequest;
import com.example.bankcards.dto.request.AuthRegisterRequest;
import com.example.bankcards.dto.request.CardCreateRequest;
import com.example.bankcards.dto.request.CardTransferRequest;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.CardStatus;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CardControllerE2ETest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userToken;
    private String adminToken;
    private UUID userId;
    private UUID adminId;

    @BeforeEach
    void setUp() throws Exception {
        cardRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(User.builder()
                .email("admin@bank.com")
                .password(passwordEncoder.encode("Admin1234!"))
                .role(Role.ADMIN)
                .build());
        adminId = admin.getId();

        User user = userRepository.save(User.builder()
                .email("user@bank.com")
                .password(passwordEncoder.encode("User1234!"))
                .role(Role.USER)
                .build());
        userId = user.getId();

        adminToken = loginAndGetToken("admin@bank.com", "Admin1234!");
        userToken = loginAndGetToken("user@bank.com", "User1234!");
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

    private UUID createCardForUser(UUID ownerId, BigDecimal balance) throws Exception {
        CardCreateRequest request = new CardCreateRequest(ownerId, LocalDate.now().plusYears(2), balance);
        MvcResult result = mockMvc.perform(post("/api/admin/cards")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String idStr = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        return UUID.fromString(idStr);
    }

    @Test
    void getMyCards_success() throws Exception {
        createCardForUser(userId, BigDecimal.valueOf(500));

        mockMvc.perform(get("/api/cards")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getMyCards_fail_unauthorized() throws Exception {
        mockMvc.perform(get("/api/cards"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCard_asOwner_returnsCard() throws Exception {
        UUID cardId = createCardForUser(userId, BigDecimal.valueOf(500));

        mockMvc.perform(get("/api/cards/" + cardId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cardId.toString()))
                .andExpect(jsonPath("$.maskedNumber").isNotEmpty());
    }

    @Test
    void getBalance_asOwner_returnsBalance() throws Exception {
        UUID cardId = createCardForUser(userId, BigDecimal.valueOf(750));

        mockMvc.perform(get("/api/cards/" + cardId + "/balance")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(content().string("750.0000"));
    }

    @Test
    void requestBlock_asOwner_blocksCard() throws Exception {
        UUID cardId = createCardForUser(userId, BigDecimal.valueOf(500));

        mockMvc.perform(patch("/api/cards/" + cardId + "/block")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void transfer_betweenOwnCards_updatesBalances() throws Exception {
        UUID fromId = createCardForUser(userId, BigDecimal.valueOf(1000));
        UUID toId = createCardForUser(userId, BigDecimal.valueOf(200));

        CardTransferRequest transfer = new CardTransferRequest(fromId, toId, BigDecimal.valueOf(300));

        mockMvc.perform(post("/api/cards/transfer")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cards/" + fromId + "/balance")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(content().string("700.0000"));

        mockMvc.perform(get("/api/cards/" + toId + "/balance")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(content().string("500.0000"));
    }

    @Test
    void transfer_withInsufficientFunds_returns422() throws Exception {
        UUID fromId = createCardForUser(userId, BigDecimal.valueOf(100));
        UUID toId = createCardForUser(userId, BigDecimal.valueOf(200));

        CardTransferRequest transfer = new CardTransferRequest(fromId, toId, BigDecimal.valueOf(9999));

        mockMvc.perform(post("/api/cards/transfer")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void adminCreateCard_asUser_returns403() throws Exception {
        CardCreateRequest request = new CardCreateRequest(userId, LocalDate.now().plusYears(2), BigDecimal.valueOf(100));

        mockMvc.perform(post("/api/admin/cards")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDeleteCard_asAdmin_returns204() throws Exception {
        UUID cardId = createCardForUser(userId, BigDecimal.valueOf(500));

        mockMvc.perform(delete("/api/admin/cards/" + cardId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminActivateCard_afterBlock_returnsActive() throws Exception {
        UUID cardId = createCardForUser(userId, BigDecimal.valueOf(500));

        mockMvc.perform(patch("/api/admin/cards/" + cardId + "/block")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        mockMvc.perform(patch("/api/admin/cards/" + cardId + "/activate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void adminGetAllCards_withStatusFilter_returnsOnlyMatchingCards() throws Exception {
        UUID cardId = createCardForUser(userId, BigDecimal.valueOf(500));
        mockMvc.perform(patch("/api/admin/cards/" + cardId + "/block")
                .header("Authorization", "Bearer " + adminToken));
        createCardForUser(userId, BigDecimal.valueOf(200));

        mockMvc.perform(get("/api/admin/cards")
                        .param("status", "BLOCKED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("BLOCKED"));
    }

    @Test
    void getMyCards_withStatusFilter_returnsOnlyMatchingCards() throws Exception {
        UUID cardId = createCardForUser(userId, BigDecimal.valueOf(500));
        mockMvc.perform(patch("/api/admin/cards/" + cardId + "/block")
                .header("Authorization", "Bearer " + adminToken));
        createCardForUser(userId, BigDecimal.valueOf(200));

        mockMvc.perform(get("/api/cards")
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void transfer_fromExpiredCard_returns409() throws Exception {
        UUID fromId = createCardForUser(userId, BigDecimal.valueOf(1000));
        UUID toId = createCardForUser(userId, BigDecimal.valueOf(200));

        Card fromCard = cardRepository.findById(fromId).orElseThrow();
        fromCard.setStatus(CardStatus.EXPIRED);
        cardRepository.save(fromCard);

        CardTransferRequest transfer = new CardTransferRequest(fromId, toId, BigDecimal.valueOf(100));
        mockMvc.perform(post("/api/cards/transfer")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isConflict());
    }

    @Test
    void transfer_toSameCard_returns400() throws Exception {
        UUID cardId = createCardForUser(userId, BigDecimal.valueOf(1000));

        CardTransferRequest transfer = new CardTransferRequest(cardId, cardId, BigDecimal.valueOf(100));
        mockMvc.perform(post("/api/cards/transfer")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminActivateCard_withExpiredDate_returns422() throws Exception {
        UUID cardId = createCardForUser(userId, BigDecimal.valueOf(500));

        Card card = cardRepository.findById(cardId).orElseThrow();
        card.setExpirationDate(LocalDate.now().minusDays(1));
        card.setStatus(CardStatus.BLOCKED);
        cardRepository.save(card);

        mockMvc.perform(patch("/api/admin/cards/" + cardId + "/activate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void getCard_asOtherUser_returns403() throws Exception {
        UUID cardId = createCardForUser(userId, BigDecimal.valueOf(500));

        User otherUser = userRepository.save(User.builder()
                .email("other@bank.com")
                .password(passwordEncoder.encode("Other1234!"))
                .role(Role.USER)
                .build());
        String otherToken = loginAndGetToken("other@bank.com", "Other1234!");

        mockMvc.perform(get("/api/cards/" + cardId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }
}
