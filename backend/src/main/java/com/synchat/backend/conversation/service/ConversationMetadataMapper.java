package com.synchat.backend.conversation.service;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.conversation.dto.ConversationMetadataResponse;
import com.synchat.backend.conversation.dto.PeerResponse;
import com.synchat.backend.conversation.entity.ConversationEntity;
import com.synchat.backend.conversation.entity.ConversationMemberEntity;
import com.synchat.backend.conversation.repository.ConversationMemberRepository;
import com.synchat.backend.message.entity.MessageEntity;
import com.synchat.backend.message.repository.MessageRepository;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConversationMetadataMapper {

    private final UserRepository userRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;

    public ConversationMetadataMapper(
            UserRepository userRepository,
            ConversationMemberRepository conversationMemberRepository,
            MessageRepository messageRepository
    ) {
        this.userRepository = userRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.messageRepository = messageRepository;
    }

    public ConversationMetadataResponse toMetadata(ConversationEntity conversation, UUID currentUserId) {
        List<ConversationMemberEntity> members = conversationMemberRepository.findByConversationId(conversation.getId());
        if (members.size() != 2) {
            throw notFound();
        }

        ConversationMemberEntity currentMember = members.stream()
                .filter(member -> member.getUserId().equals(currentUserId))
                .findFirst()
                .orElseThrow(this::forbidden);

        ConversationMemberEntity peerMember = members.stream()
                .filter(member -> !member.getUserId().equals(currentUserId))
                .findFirst()
                .orElseThrow(this::notFound);

        UserEntity peer = userRepository.findById(peerMember.getUserId())
                .orElseThrow(this::notFound);

        Optional<MessageEntity> lastMessage = findLastMessage(conversation);

        return new ConversationMetadataResponse(
                conversation.getId(),
                new PeerResponse(peer.getId(), peer.getEmail(), peer.getDisplayName(), peer.getAvatarUrl()),
                conversation.getLastMessageSeq(),
                lastMessage.map(MessageEntity::getBody).orElse(null),
                lastMessage.map(MessageEntity::getSenderId).orElse(null),
                conversation.getLastMessageAt(),
                currentMember.getUnreadCount(),
                peerMember.getReadSeq(),
                peerMember.getDeliveredSeq()
        );
    }

    private Optional<MessageEntity> findLastMessage(ConversationEntity conversation) {
        if (conversation.getLastMessageSeq() == null || conversation.getLastMessageSeq() == 0) {
            return Optional.empty();
        }

        return messageRepository.findByConversationIdAndSeq(conversation.getId(), conversation.getLastMessageSeq());
    }

    private AppException forbidden() {
        return new AppException(ErrorCode.FORBIDDEN, "Forbidden.");
    }

    private AppException notFound() {
        return new AppException(ErrorCode.NOT_FOUND, "Requested resource not found.");
    }
}
