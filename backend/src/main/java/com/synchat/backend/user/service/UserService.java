package com.synchat.backend.user.service;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.common.security.AuthenticatedUser;
import com.synchat.backend.common.security.CurrentUserProvider;
import com.synchat.backend.user.dto.UserSearchItemResponse;
import com.synchat.backend.user.dto.UserSearchResponse;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final int SEARCH_LIMIT = 20;

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    public UserService(CurrentUserProvider currentUserProvider, UserRepository userRepository) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
    }

    public UserSearchResponse searchUsers(String q) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        String keyword = validateKeyword(q);
        String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";

        List<UserSearchItemResponse> items = userRepository
                .searchUsers(currentUser.userId(), pattern, PageRequest.of(0, SEARCH_LIMIT))
                .stream()
                .map(this::toSearchItem)
                .toList();

        return new UserSearchResponse(items);
    }

    private String validateKeyword(String q) {
        if (q == null || q.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Search keyword is required.");
        }

        String trimmed = q.trim();
        if (trimmed.length() < 2) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Search keyword must be at least 2 characters.");
        }

        if (trimmed.length() > 100) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Search keyword is too long.");
        }

        return trimmed;
    }

    private UserSearchItemResponse toSearchItem(UserEntity user) {
        return new UserSearchItemResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl()
        );
    }
}
