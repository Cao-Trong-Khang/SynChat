package com.synchat.backend.sync.controller;

import com.synchat.backend.conversation.dto.SyncConversationsResponse;
import com.synchat.backend.conversation.service.ConversationService;
import com.synchat.backend.sync.dto.SyncEventsResponse;
import com.synchat.backend.sync.service.SyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final ConversationService conversationService;
    private final SyncService syncService;

    public SyncController(ConversationService conversationService, SyncService syncService) {
        this.conversationService = conversationService;
        this.syncService = syncService;
    }

    @GetMapping("/conversations")
    public SyncConversationsResponse syncConversations(
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        return conversationService.syncConversations(limit, cursor);
    }

    @GetMapping("/events")
    public SyncEventsResponse syncEvents(
            @RequestParam(value = "afterSeq", required = false) Long afterSeq,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return syncService.syncEvents(afterSeq, limit);
    }
}
