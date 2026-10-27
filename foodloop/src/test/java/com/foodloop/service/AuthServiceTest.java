package com.foodloop.service;

import com.foodloop.dto.AuthDtos.*;
import com.foodloop.entity.User;
import com.foodloop.enums.Role;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.UnauthorizedException;
import com.foodloop.repository.UserRepository;
import com.foodloop.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserRepository userRepository;

    PasswordEncoder encoder = new BCryptPasswordEncoder();
    JwtService jwtService = new JwtService("0123456789abcdef0123456789abcdef", 60_000);
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, encoder, jwtService);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void register_alwaysCreatesUserRole_andHashesPassword() {
        UserResponse r = service.register(new RegisterRequest("Asha", "  Asha@Example.com ", "Passw0rd1", null, null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(Role.USER, saved.getRole());
        assertEquals("asha@example.com", saved.getEmail());
        assertNotEquals("Passw0rd1", saved.getPasswordHash());
        assertTrue(encoder.matches("Passw0rd1", saved.getPasswordHash()));
        assertEquals("USER", r.role());
    }

    @Test
    void register_duplicateEmail_isConflict() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(true);
        assertThrows(ConflictException.class,
                () -> service.register(new RegisterRequest("Asha", "asha@example.com", "Passw0rd1", null, null)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_withCorrectPassword_returnsJwt() {
        User u = new User();
        u.setEmail("asha@example.com");
        u.setName("Asha");
        u.setRole(Role.USER);
        u.setPasswordHash(encoder.encode("Passw0rd1"));
        when(userRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(u));

        AuthResponse r = service.login(new LoginRequest("asha@example.com", "Passw0rd1"));

        assertFalse(r.token().isBlank());
        assertEquals("asha@example.com", jwtService.extractEmail(r.token()));
    }

    @Test
    void login_wrongPasswordOrUnknownEmail_isUnauthorized() {
        User u = new User();
        u.setEmail("asha@example.com");
        u.setRole(Role.USER);
        u.setPasswordHash(encoder.encode("Passw0rd1"));
        when(userRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(u));

        assertThrows(UnauthorizedException.class, () -> service.login(new LoginRequest("asha@example.com", "wrong")));
        assertThrows(UnauthorizedException.class, () -> service.login(new LoginRequest("nobody@example.com", "Passw0rd1")));
    }
}
