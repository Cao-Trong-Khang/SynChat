package com.synchat.backend.realtime.presence.dto;

import java.util.List;
import java.util.UUID;

public record PresenceSubscribeCommandPayload(List<UUID> userIds) {
}
