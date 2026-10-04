package com.smartpark.security.service;

import com.smartpark.entity.User;
import com.smartpark.exception.AuthenticationFailedException;
import com.smartpark.repository.UserRepository;
import com.smartpark.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<UserPrincipal> getCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return Optional.of(userPrincipal);
        }

        return Optional.empty();
    }

    public UserPrincipal getRequiredCurrentPrincipal() {
        return getCurrentPrincipal()
                .orElseThrow(() -> new AuthenticationFailedException("User is not authenticated"));
    }

    public Long getCurrentUserId() {
        return getRequiredCurrentPrincipal().getId();
    }

    public String getCurrentUserEmail() {
        return getRequiredCurrentPrincipal().getUsername();
    }

    public User getCurrentUserEntity() {
        Long userId = getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationFailedException("Authenticated user entity not found"));
    }
}
