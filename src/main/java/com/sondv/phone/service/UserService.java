package com.sondv.phone.service;

import com.sondv.phone.dto.UserResponseDTO;
import com.sondv.phone.entity.User;
import com.sondv.phone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Cacheable(value = "currentUser", key = "#principal.name", unless = "#result == null")
    public UserResponseDTO getCurrentUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new RuntimeException("Unauthorized access!");
        }

        String email = principal.getName();
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Người dùng không tồn tại!"));

        return new UserResponseDTO(
        );
    }
}
