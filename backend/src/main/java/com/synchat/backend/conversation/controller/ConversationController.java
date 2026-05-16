package com.synchat.backend.conversation.controller;

import com.synchat.backend.conversation.dto.DirectConversationResponse;
import com.synchat.backend.conversation.service.ConversationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/direct")
    public DirectConversationResponse getDirectConversation(@RequestParam("peerUserId") UUID peerUserId) {
        return conversationService.getDirectConversation(peerUserId);
    }
}
