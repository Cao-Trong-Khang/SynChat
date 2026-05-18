package com.synchat.backend.realtime.presence.dto;

import java.util.List;

public record PresenceSnapshotPayload(List<PresenceItemResponse> items) {
}
