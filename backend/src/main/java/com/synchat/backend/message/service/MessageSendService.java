package com.synchat.backend.message.service;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.common.security.AuthenticatedUser;
import com.synchat.backend.common.security.CurrentUserProvider;
import com.synchat.backend.conversation.entity.ConversationEntity;
import com.synchat.backend.conversation.entity.ConversationMemberEntity;
import com.synchat.backend.conversation.repository.ConversationMemberRepository;
import com.synchat.backend.conversation.repository.ConversationRepository;
import com.synchat.backend.conversation.util.DirectConversationKeyUtil;
import com.synchat.backend.message.dto.MessageCreatedPayload;
import com.synchat.backend.message.dto.SendMessageCommandPayload;
import com.synchat.backend.message.entity.MessageEntity;
import com.synchat.backend.message.repository.MessageRepository;
import com.synchat.backend.outbox.service.OutboxEventAppender;
import com.synchat.backend.sync.service.UserEventAppender;
import com.synchat.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageSendService {

    private static final String MESSAGE_CREATED = "message.created";
    private static final int MAX_BODY_LENGTH = 4000;

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final UserEventAppender userEventAppender;
    private final OutboxEventAppender outboxEventAppender;
    private final MessageCreatedPayloadBuilder messageCreatedPayloadBuilder;

    public MessageSendService(
            CurrentUserProvider currentUserProvider,
            UserRepository userRepository,
            ConversationRepository conversationRepository,
            ConversationMemberRepository conversationMemberRepository,
            MessageRepository messageRepository,
            UserEventAppender userEventAppender,
            OutboxEventAppender outboxEventAppender,
            MessageCreatedPayloadBuilder messageCreatedPayloadBuilder
    ) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.messageRepository = messageRepository;
        this.userEventAppender = userEventAppender;
        this.outboxEventAppender = outboxEventAppender;
        this.messageCreatedPayloadBuilder = messageCreatedPayloadBuilder;
    }

    @Transactional
    public void send(SendMessageCommandPayload payload) {
        ValidatedPayload validated = validate(payload);
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        UUID senderId = currentUser.userId();

        Optional<MessageEntity> existingMessage = messageRepository.findBySenderIdAndClientMessageId(
                senderId,
                validated.clientMessageId()
        );
        if (existingMessage.isPresent()) {
            reemitSenderConfirmation(senderId, existingMessage.get(), Instant.now());
            return;
        }

        ConversationContext context = validated.conversationId() != null
                ? resolveExistingConversation(validated.conversationId(), senderId)
                : resolveDirectConversation(validated.recipientId(), senderId);

        Instant now = Instant.now();
        Long nextSeq = context.conversation().getLastMessageSeq() + 1;
        MessageEntity message = MessageEntity.create(
                UUID.randomUUID(),
                context.conversation().getId(),
                nextSeq,
                senderId,
                validated.clientMessageId(),
                validated.body(),
                now
        );

        messageRepository.save(message);
        context.conversation().applyLastMessage(nextSeq, now);

        context.senderMember().clearUnread(now);
        context.recipientMember().incrementUnread(now);
        conversationMemberRepository.save(context.senderMember());
        conversationMemberRepository.save(context.recipientMember());

        appendMessageCreatedEvents(context, message, now);
    }

    private ValidatedPayload validate(SendMessageCommandPayload payload) {
        if (payload == null) {
            throw validation("Message payload is required.");
        }

        boolean hasConversationId = payload.conversationId() != null;
        boolean hasRecipientId = payload.recipientId() != null;
        if (hasConversationId == hasRecipientId) {
            throw validation("Exactly one conversation target is required.");
        }

        if (payload.clientMessageId() == null) {
            throw validation("clientMessageId is required.");
        }

        if (payload.body() == null || payload.body().isBlank()) {
            throw validation("Message body is required.");
        }

        String body = payload.body().trim();
        if (body.length() > MAX_BODY_LENGTH) {
            throw new AppException(ErrorCode.MESSAGE_TOO_LARGE, "Message body exceeds allowed size.");
        }

        return new ValidatedPayload(
                payload.conversationId(),
                payload.recipientId(),
                payload.clientMessageId(),
                body
        );
    }

    private ConversationContext resolveExistingConversation(UUID conversationId, UUID senderId) {
        ConversationEntity conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(this::notFound);

        ConversationMemberEntity senderMember = conversationMemberRepository
                .findByConversationIdAndUserId(conversation.getId(), senderId)
                .orElseThrow(this::notFound);

        List<ConversationMemberEntity> members = conversationMemberRepository.findByConversationId(conversation.getId());
        if (members.size() != 2) {
            throw notFound();
        }

        ConversationMemberEntity recipientMember = members.stream()
                .filter(member -> !member.getUserId().equals(senderId))
                .findFirst()
                .orElseThrow(this::notFound);

        return new ConversationContext(conversation, senderMember, recipientMember);
    }

    private ConversationContext resolveDirectConversation(UUID recipientId, UUID senderId) {
        if (recipientId.equals(senderId)) {
            throw validation("Peer user must be different from current user.");
        }

        if (!userRepository.existsById(recipientId)) {
            throw notFound();
        }

        String directKey = DirectConversationKeyUtil.build(senderId, recipientId);
        ConversationEntity conversation = conversationRepository.findByDirectKeyForUpdate(directKey)
                .orElseGet(() -> createDirectConversation(directKey));

        Instant now = Instant.now();
        ConversationMemberEntity senderMember = conversationMemberRepository
                .findByConversationIdAndUserId(conversation.getId(), senderId)
                .orElseGet(() -> conversationMemberRepository.save(
                        ConversationMemberEntity.create(conversation.getId(), senderId, now)
                ));
        ConversationMemberEntity recipientMember = conversationMemberRepository
                .findByConversationIdAndUserId(conversation.getId(), recipientId)
                .orElseGet(() -> conversationMemberRepository.save(
                        ConversationMemberEntity.create(conversation.getId(), recipientId, now)
                ));

        return new ConversationContext(conversation, senderMember, recipientMember);
    }

    private ConversationEntity createDirectConversation(String directKey) {
        try {
            Instant now = Instant.now();
            ConversationEntity conversation = conversationRepository.saveAndFlush(
                    ConversationEntity.create(UUID.randomUUID(), directKey, now)
            );
            return conversationRepository.findByIdForUpdate(conversation.getId())
                    .orElseThrow(this::notFound);
        } catch (DataIntegrityViolationException exception) {
            return conversationRepository.findByDirectKeyForUpdate(directKey)
                    .orElseThrow(() -> exception);
        }
    }

    private void appendMessageCreatedEvents(ConversationContext context, MessageEntity message, Instant now) {
        MessageCreatedPayload senderPayload = messageCreatedPayloadBuilder.build(
                context.conversation(),
                message,
                context.senderMember(),
                context.recipientMember()
        );
        Long senderEventSeq = userEventAppender.append(
                context.senderMember().getUserId(),
                MESSAGE_CREATED,
                senderPayload,
                now
        );
        appendOutboxEvent(context.senderMember().getUserId(), senderEventSeq, senderPayload, now);

        MessageCreatedPayload recipientPayload = messageCreatedPayloadBuilder.build(
                context.conversation(),
                message,
                context.recipientMember(),
                context.senderMember()
        );
        Long recipientEventSeq = userEventAppender.append(
                context.recipientMember().getUserId(),
                MESSAGE_CREATED,
                recipientPayload,
                now
        );
        appendOutboxEvent(context.recipientMember().getUserId(), recipientEventSeq, recipientPayload, now);
    }

    private void reemitSenderConfirmation(UUID senderId, MessageEntity message, Instant now) {
        ConversationEntity conversation = conversationRepository.findByIdForUpdate(message.getConversationId())
                .orElseThrow(this::notFound);
        List<ConversationMemberEntity> members = conversationMemberRepository.findByConversationId(conversation.getId());
        if (members.size() != 2) {
            throw notFound();
        }

        ConversationMemberEntity senderMember = members.stream()
                .filter(member -> member.getUserId().equals(senderId))
                .findFirst()
                .orElseThrow(this::notFound);
        ConversationMemberEntity peerMember = members.stream()
                .filter(member -> !member.getUserId().equals(senderId))
                .findFirst()
                .orElseThrow(this::notFound);

        MessageCreatedPayload senderPayload = messageCreatedPayloadBuilder.build(
                conversation,
                message,
                senderMember,
                peerMember
        );
        Long eventSeq = userEventAppender.append(senderId, MESSAGE_CREATED, senderPayload, now);
        appendOutboxEvent(senderId, eventSeq, senderPayload, now);
    }

    private void appendOutboxEvent(UUID userId, Long eventSeq, MessageCreatedPayload payload, Instant now) {
        outboxEventAppender.append(
                MESSAGE_CREATED,
                new OutboxMessageCreatedPayload(userId, eventSeq, MESSAGE_CREATED, payload),
                now
        );
    }

    private AppException validation(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message);
    }

    private AppException notFound() {
        return new AppException(ErrorCode.NOT_FOUND, "Requested resource not found.");
    }

    private record ValidatedPayload(
            UUID conversationId,
            UUID recipientId,
            UUID clientMessageId,
            String body
    ) {
    }

    private record ConversationContext(
            ConversationEntity conversation,
            ConversationMemberEntity senderMember,
            ConversationMemberEntity recipientMember
    ) {
    }

    private record OutboxMessageCreatedPayload(
            UUID userId,
            Long eventSeq,
            String eventType,
            MessageCreatedPayload payload
    ) {
    }
}
