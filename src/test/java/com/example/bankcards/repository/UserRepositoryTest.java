package com.example.bankcards.repository;

import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_whenExists_returnsUser() {
        User user = User.builder()
                .email("test@bank.com")
                .password("encoded")
                .role(Role.USER)
                .build();
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("test@bank.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@bank.com");
    }

    @Test
    void findByEmail_whenNotExists_returnsEmpty() {
        Optional<User> found = userRepository.findByEmail("nobody@bank.com");
        assertThat(found).isEmpty();
    }

    @Test
    void existsByEmail_whenExists_returnsTrue() {
        User user = User.builder()
                .email("exists@bank.com")
                .password("encoded")
                .role(Role.USER)
                .build();
        userRepository.save(user);

        assertThat(userRepository.existsByEmail("exists@bank.com")).isTrue();
    }

    @Test
    void existsByEmail_whenNotExists_returnsFalse() {
        assertThat(userRepository.existsByEmail("ghost@bank.com")).isFalse();
    }
}
