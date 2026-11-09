package com.synchat.backend.conversation.service;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.common.security.AuthenticatedUser;
import com.synchat.backend.common.security.CurrentUserProvider;
import com.synchat.backend.conversation.dto.ConversationMetadataResponse;
import com.synchat.backend.conversation.dto.ConversationMetadataRow;
import com.synchat.backend.conversation.dto.DirectConversationResponse;
import com.synchat.backend.conversation.dto.SyncConversationsResponse;
import com.synchat.backend.conversation.entity.ConversationEntity;
import com.synchat.backend.conversation.repository.ConversationRepository;
import com.synchat.backend.conversation.util.ConversationCursor;
import com.synchat.backend.conversation.util.ConversationCursorCodec;
import com.synchat.backend.conversation.util.DirectConversationKeyUtil;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import com.synchat.backend.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private final CurrentUserProvider currentUserProvider;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final ConversationMetadataMapper conversationMetadataMapper;
    private final ConversationCursorCodec conversationCursorCodec;
    private final UserEventOffsetRepository userEventOffsetRepository;

    public ConversationService(
            CurrentUserProvider currentUserProvider,
            ConversationRepository conversationRepository,
            UserRepository userRepository,
            ConversationMetadataMapper conversationMetadataMapper,
            ConversationCursorCodec conversationCursorCodec,
            UserEventOffsetRepository userEventOffsetRepository
    ) {
        this.currentUserProvider = currentUserProvider;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.conversationMetadataMapper = conversationMetadataMapper;
        this.conversationCursorCodec = conversationCursorCodec;
        this.userEventOffsetRepository = userEventOffsetRepository;
    }

    @Transactional(readOnly = true)
    public DirectConversationResponse getDirectConversation(UUID peerUserId) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();

        if (peerUserId == null || peerUserId.equals(currentUser.userId())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Peer user must be different from current user.");
        }

        if (!userRepository.existsById(peerUserId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Requested resource not found.");
        }

        String directKey = DirectConversationKeyUtil.build(currentUser.userId(), peerUserId);
        return conversationRepository.findByDirectKey(directKey)
                .map(conversation -> toResponse(conversation, currentUser.userId()))
                .orElseGet(() -> new DirectConversationResponse(null));
    }

    @Transactional(readOnly = true)
    public SyncConversationsResponse syncConversations(Integer limit, String cursor) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        int pageSize = validateLimit(limit);
        boolean firstPage = cursor == null || cursor.isBlank();

        List<ConversationMetadataRow> fetched = firstPage
                ? conversationRepository.findMetadataLatestForUser(
                        currentUser.userId(),
                        PageRequest.of(0, pageSize + 1)
                )
                : findAfterCursor(currentUser.userId(), cursor, pageSize);

        boolean hasMore = fetched.size() > pageSize;
        List<ConversationMetadataRow> page = hasMore ? fetched.subList(0, pageSize) : fetched;
        String nextCursor = hasMore ? nextCursor(page) : null;
        Long serverEventSeq = firstPage ? serverEventSeq(currentUser.userId()) : null;

        List<ConversationMetadataResponse> items = page.stream()
                .map(ConversationMetadataRow::toResponse)
                .toList();

        return new SyncConversationsResponse(items, nextCursor, hasMore, serverEventSeq);
    }

    private DirectConversationResponse toResponse(ConversationEntity conversation, UUID currentUserId) {
        ConversationMetadataResponse metadata = conversationMetadataMapper.toMetadata(conversation, currentUserId);
        return new DirectConversationResponse(metadata);
    }

    private List<ConversationMetadataRow> findAfterCursor(UUID currentUserId, String cursor, int pageSize) {
        ConversationCursor decodedCursor = conversationCursorCodec.decode(cursor);
        return conversationRepository.findMetadataAfterCursorForUser(
                currentUserId,
                decodedCursor.lastMessageAt(),
                decodedCursor.conversationId(),
                PageRequest.of(0, pageSize + 1)
        );
    }

    private int validateLimit(Integer limit) {
        int value = limit == null ? 20 : limit;
        if (value < 1 || value > 50) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid limit.");
        }
        return value;
    }

    private String nextCursor(List<ConversationMetadataRow> page) {
        ConversationMetadataRow last = page.get(page.size() - 1);
        return conversationCursorCodec.encode(new ConversationCursor(last.lastMessageAt(), last.conversationId()));
    }

    private Long serverEventSeq(UUID currentUserId) {
        return userEventOffsetRepository.findById(currentUserId)
                .map(UserEventOffsetEntity::getLastEventSeq)
                .orElse(0L);
    }
}
