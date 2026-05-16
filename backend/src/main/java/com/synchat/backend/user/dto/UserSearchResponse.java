package com.synchat.backend.user.dto;

import java.util.List;

public record UserSearchResponse(List<UserSearchItemResponse> items) {
}
