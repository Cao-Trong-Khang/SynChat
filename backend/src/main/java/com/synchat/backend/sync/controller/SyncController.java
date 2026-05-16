package com.synchat.backend.sync.controller;

import com.synchat.backend.conversation.dto.SyncConversationsResponse;
import com.synchat.backend.conversation.service.ConversationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final ConversationService conversationService;

    public SyncController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/conversations")
    public SyncConversationsResponse syncConversations(
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        return conversationService.syncConversations(limit, cursor);
    }
}
