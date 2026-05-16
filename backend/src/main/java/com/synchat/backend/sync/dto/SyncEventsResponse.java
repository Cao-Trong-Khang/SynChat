package com.synchat.backend.sync.dto;

import java.util.List;

public record SyncEventsResponse(List<SyncEventItemResponse> events, boolean hasMore, Long nextAfterSeq) {
}
