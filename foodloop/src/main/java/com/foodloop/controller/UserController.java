package com.foodloop.controller;

import com.foodloop.dto.AuthDtos.UpdateProfileRequest;
import com.foodloop.dto.AuthDtos.UserResponse;
import com.foodloop.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(Authentication auth) {
        return userService.getProfile(auth.getName());
    }

    @PutMapping("/me")
    public UserResponse update(Authentication auth, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(auth.getName(), request);
    }
}
