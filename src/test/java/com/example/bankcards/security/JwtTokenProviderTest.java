package com.example.bankcards.security;

import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.Role;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION = 86400000L;

    private UUID userId;
    private CustomUserDetails userDetails;
    private CustomUserDetails otherUserDetails;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, EXPIRATION);
        userId = UUID.randomUUID();
        userDetails = buildCustomUser(userId, "user@bank.com", Role.USER);
        otherUserDetails = buildCustomUser(UUID.randomUUID(), "other@bank.com", Role.USER);
    }

    @Test
    void generateToken_extractUsername_returnsCorrectEmail() {
        String token = jwtTokenProvider.generateToken(userDetails);
        assertThat(jwtTokenProvider.extractUsername(token)).isEqualTo("user@bank.com");
    }

    @Test
    void generateToken_extractUserId_returnsCorrectUUID() {
        String token = jwtTokenProvider.generateToken(userDetails,
                Map.of("userId", userId.toString(), "role", "USER"));
        assertThat(jwtTokenProvider.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    void generateToken_extractRole_returnsCorrectRole() {
        String token = jwtTokenProvider.generateToken(userDetails,
                Map.of("userId", userId.toString(), "role", "USER"));
        assertThat(jwtTokenProvider.extractRole(token)).isEqualTo("USER");
    }

    @Test
    void isTokenValid_withValidToken_returnsTrue() {
        String token = jwtTokenProvider.generateToken(userDetails);
        assertThat(jwtTokenProvider.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void isTokenValid_withWrongUser_returnsFalse() {
        String token = jwtTokenProvider.generateToken(userDetails);
        assertThat(jwtTokenProvider.isTokenValid(token, otherUserDetails)).isFalse();
    }

    @Test
    void isTokenValid_withExpiredToken_throwsExpiredJwtException() {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, -1000L);
        String token = shortLived.generateToken(userDetails);
        assertThatThrownBy(() -> shortLived.isTokenValid(token, userDetails))
                .isInstanceOf(ExpiredJwtException.class);
    }

    private CustomUserDetails buildCustomUser(UUID id, String email, Role role) {
        User user = User.builder()
                .id(id)
                .email(email)
                .password("encoded")
                .role(role)
                .build();
        return new CustomUserDetails(user);
    }
}
