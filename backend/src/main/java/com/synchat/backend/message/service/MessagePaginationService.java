package com.synchat.backend.message.service;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.common.security.AuthenticatedUser;
import com.synchat.backend.common.security.CurrentUserProvider;
import com.synchat.backend.conversation.repository.ConversationMemberRepository;
import com.synchat.backend.message.dto.MessageItemResponse;
import com.synchat.backend.message.dto.MessagePageResponse;
import com.synchat.backend.message.entity.MessageEntity;
import com.synchat.backend.message.repository.MessageRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessagePaginationService {

    private final CurrentUserProvider currentUserProvider;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;

    public MessagePaginationService(
            CurrentUserProvider currentUserProvider,
            ConversationMemberRepository conversationMemberRepository,
            MessageRepository messageRepository
    ) {
        this.currentUserProvider = currentUserProvider;
        this.conversationMemberRepository = conversationMemberRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public MessagePageResponse loadMessages(UUID conversationId, Long beforeSeq, Integer limit) {
        if (conversationId == null) {
            throw notFound();
        }

        int pageSize = validateLimit(limit);
        validateMembership(conversationId);

        if (beforeSeq != null && beforeSeq <= 1) {
            return new MessagePageResponse(conversationId, List.of(), null, null, false);
        }

        return beforeSeq == null
                ? loadLatestMessages(conversationId, pageSize)
                : loadOlderMessages(conversationId, beforeSeq, pageSize);
    }

    private int validateLimit(Integer limit) {
        int value = limit == null ? 20 : limit;
        if (value < 1 || value > 100) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid limit.");
        }
        return value;
    }

    private void validateMembership(UUID conversationId) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        conversationMemberRepository.findByConversationIdAndUserId(conversationId, currentUser.userId())
                .orElseThrow(this::notFound);
    }

    private MessagePageResponse loadLatestMessages(UUID conversationId, int limit) {
        List<MessageEntity> fetched = messageRepository.findLatestByConversationId(
                conversationId,
                PageRequest.of(0, limit + 1)
        );

        return toPageResponse(conversationId, fetched, limit, true);
    }

    private MessagePageResponse loadOlderMessages(UUID conversationId, Long beforeSeq, int limit) {
        List<MessageEntity> fetched = messageRepository.findOlderByConversationId(
                conversationId,
                beforeSeq,
                PageRequest.of(0, limit + 1)
        );

        return toPageResponse(conversationId, fetched, limit, false);
    }

    private MessagePageResponse toPageResponse(
            UUID conversationId,
            List<MessageEntity> fetched,
            int limit,
            boolean includeNewestSeq
    ) {
        boolean hasMoreBefore = fetched.size() > limit;
        List<MessageEntity> returned = hasMoreBefore ? fetched.subList(0, limit) : fetched;
        List<MessageEntity> ascending = new ArrayList<>(returned);
        Collections.reverse(ascending);

        List<MessageItemResponse> messages = ascending.stream()
                .map(this::toResponse)
                .toList();
        Long oldestSeq = ascending.isEmpty() ? null : ascending.get(0).getSeq();
        Long newestSeq = includeNewestSeq && !ascending.isEmpty()
                ? ascending.get(ascending.size() - 1).getSeq()
                : null;

        return new MessagePageResponse(conversationId, messages, oldestSeq, newestSeq, hasMoreBefore);
    }

    private MessageItemResponse toResponse(MessageEntity message) {
        return new MessageItemResponse(
                message.getSeq(),
                message.getSenderId(),
                message.getClientMessageId(),
                message.getBody(),
                message.getCreatedAtServer()
        );
    }

    private AppException notFound() {
        return new AppException(ErrorCode.NOT_FOUND, "Requested resource not found.");
    }
}
