package com.synchat.backend.conversation.service;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.common.security.AuthenticatedUser;
import com.synchat.backend.common.security.CurrentUserProvider;
import com.synchat.backend.conversation.dto.ConversationMetadataResponse;
import com.synchat.backend.conversation.dto.DirectConversationResponse;
import com.synchat.backend.conversation.entity.ConversationEntity;
import com.synchat.backend.conversation.repository.ConversationRepository;
import com.synchat.backend.conversation.util.DirectConversationKeyUtil;
import com.synchat.backend.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private final CurrentUserProvider currentUserProvider;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final ConversationMetadataMapper conversationMetadataMapper;

    public ConversationService(
            CurrentUserProvider currentUserProvider,
            ConversationRepository conversationRepository,
            UserRepository userRepository,
            ConversationMetadataMapper conversationMetadataMapper
    ) {
        this.currentUserProvider = currentUserProvider;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.conversationMetadataMapper = conversationMetadataMapper;
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

    private DirectConversationResponse toResponse(ConversationEntity conversation, UUID currentUserId) {
        ConversationMetadataResponse metadata = conversationMetadataMapper.toMetadata(conversation, currentUserId);
        return new DirectConversationResponse(metadata);
    }
}
