package com.foodloop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request/response bodies for authentication and the user profile. (Java records = immutable DTOs.) */
public final class AuthDtos {
    private AuthDtos() {}

    // Note: there is intentionally NO "role" field. Anything extra a client sends is ignored.
    public record RegisterRequest(
            @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters") String name,
            @NotBlank(message = "Email is required") @Email(message = "Email format is invalid") @Size(max = 150) String email,
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 64, message = "Password must be 8-64 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain at least one letter and one digit") String password,
            @Pattern(regexp = "^$|^[0-9+\\- ]{7,15}$", message = "Phone must be 7-15 digits (may include + - space)") String phone,
            @Size(max = 255, message = "Address must be at most 255 characters") String address) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required") @Email(message = "Email format is invalid") String email,
            @NotBlank(message = "Password is required") String password) {}

    public record UserResponse(Long id, String name, String email, String phone, String address, String role) {}

    public record AuthResponse(String token, String tokenType, long expiresInMs, UserResponse user) {}

    public record UpdateProfileRequest(
            @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters") String name,
            @Pattern(regexp = "^$|^[0-9+\\- ]{7,15}$", message = "Phone must be 7-15 digits (may include + - space)") String phone,
            @Size(max = 255, message = "Address must be at most 255 characters") String address) {}
}
