package com.foodloop.service;

import com.foodloop.dto.AuthDtos.UpdateProfileRequest;
import com.foodloop.dto.AuthDtos.UserResponse;
import com.foodloop.entity.User;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.UserRepository;
import com.foodloop.util.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(String email) {
        return Mapper.toUser(find(email));
    }

    /** Only name, phone and address can be changed. Email, role and password are not editable here. */
    @Transactional
    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = find(email);
        user.setName(request.name().trim());
        user.setPhone(AuthService.blankToNull(request.phone()));
        user.setAddress(AuthService.blankToNull(request.address()));
        return Mapper.toUser(userRepository.save(user));
    }

    private User find(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
