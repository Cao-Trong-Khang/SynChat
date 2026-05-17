package com.synchat.backend.message.service;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.conversation.dto.ConversationMetadataResponse;
import com.synchat.backend.conversation.dto.PeerResponse;
import com.synchat.backend.conversation.entity.ConversationEntity;
import com.synchat.backend.conversation.entity.ConversationMemberEntity;
import com.synchat.backend.message.dto.MessageCreatedMessagePayload;
import com.synchat.backend.message.dto.MessageCreatedPayload;
import com.synchat.backend.message.entity.MessageEntity;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class MessageCreatedPayloadBuilder {

    private final UserRepository userRepository;

    public MessageCreatedPayloadBuilder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public MessageCreatedPayload build(
            ConversationEntity conversation,
            MessageEntity message,
            ConversationMemberEntity currentMember,
            ConversationMemberEntity peerMember
    ) {
        UserEntity peerUser = userRepository.findById(peerMember.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Requested resource not found."));

        ConversationMetadataResponse conversationPayload = new ConversationMetadataResponse(
                conversation.getId(),
                new PeerResponse(
                        peerUser.getId(),
                        peerUser.getEmail(),
                        peerUser.getDisplayName(),
                        peerUser.getAvatarUrl()
                ),
                conversation.getLastMessageSeq(),
                message.getBody(),
                message.getSenderId(),
                conversation.getLastMessageAt(),
                currentMember.getUnreadCount(),
                peerMember.getReadSeq(),
                peerMember.getDeliveredSeq()
        );

        MessageCreatedMessagePayload messagePayload = new MessageCreatedMessagePayload(
                message.getSeq(),
                message.getSenderId(),
                message.getClientMessageId(),
                message.getBody(),
                message.getCreatedAtServer()
        );

        return new MessageCreatedPayload(conversationPayload, messagePayload);
    }
}
