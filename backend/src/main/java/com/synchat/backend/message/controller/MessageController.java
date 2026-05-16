package com.synchat.backend.message.controller;

import com.synchat.backend.message.dto.MessagePageResponse;
import com.synchat.backend.message.service.MessagePaginationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

    private final MessagePaginationService messagePaginationService;

    public MessageController(MessagePaginationService messagePaginationService) {
        this.messagePaginationService = messagePaginationService;
    }

    @GetMapping
    public MessagePageResponse loadMessages(
            @PathVariable UUID conversationId,
            @RequestParam(value = "beforeSeq", required = false) Long beforeSeq,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return messagePaginationService.loadMessages(conversationId, beforeSeq, limit);
    }
}
