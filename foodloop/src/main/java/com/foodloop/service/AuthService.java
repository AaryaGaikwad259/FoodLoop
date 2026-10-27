
package com.foodloop.service;

import com.foodloop.dto.AuthDtos.*;
import com.foodloop.entity.User;
import com.foodloop.enums.Role;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.UnauthorizedException;
import com.foodloop.repository.UserRepository;
import com.foodloop.security.JwtService;
import com.foodloop.util.Mapper;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // Register a new user
    @Transactional
    public UserResponse register(RegisterRequest request) {

        String email = request.email().trim().toLowerCase();

        // Prevent duplicate accounts
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException(
                    "An account with this email already exists"
            );
        }

        User user = new User();

        user.setName(request.name().trim());
        user.setEmail(email);

        // Store a hashed password, never the plain password
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );

        user.setPhone(blankToNull(request.phone()));
        user.setAddress(blankToNull(request.address()));

        // Every newly registered account gets USER role
        // The client cannot register itself as ADMIN
        user.setRole(Role.USER);

        User savedUser = userRepository.save(user);

        return Mapper.toUser(savedUser);
    }

    // Log in an existing user
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        String email = request.email().trim().toLowerCase();

        // Find the account and verify its password
        User user = userRepository.findByEmail(email)
                .filter(existingUser ->
                        passwordEncoder.matches(
                                request.password(),
                                existingUser.getPasswordHash()
                        )
                )
                .orElseThrow(() ->
                        new UnauthorizedException(
                                "Invalid email or password"
                        )
                );

        // Generate a JWT only after successful verification
        String token = jwtService.generateToken(
                user.getEmail(),
                user.getRole().name()
        );

        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationMs(),
                Mapper.toUser(user)
        );
    }

    // Convert empty optional fields to null
    static String blankToNull(String value) {
        return (value == null || value.isBlank())
                ? null
                : value.trim();
    }
}
