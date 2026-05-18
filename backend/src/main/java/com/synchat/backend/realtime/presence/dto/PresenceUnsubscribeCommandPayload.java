package com.synchat.backend.realtime.presence.dto;

import java.util.List;
import java.util.UUID;

public record PresenceUnsubscribeCommandPayload(List<UUID> userIds) {
}
