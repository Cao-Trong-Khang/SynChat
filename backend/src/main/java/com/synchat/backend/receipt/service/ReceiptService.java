package com.synchat.backend.receipt.service;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.common.security.AuthenticatedUser;
import com.synchat.backend.common.security.CurrentUserProvider;
import com.synchat.backend.conversation.entity.ConversationEntity;
import com.synchat.backend.conversation.entity.ConversationMemberEntity;
import com.synchat.backend.conversation.repository.ConversationMemberRepository;
import com.synchat.backend.conversation.repository.ConversationRepository;
import com.synchat.backend.receipt.dto.DeliveredUpToCommandPayload;
import com.synchat.backend.receipt.dto.MessageDeliveredPayload;
import com.synchat.backend.receipt.dto.MessageReadPayload;
import com.synchat.backend.receipt.dto.ReadUpToCommandPayload;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiptService {

    private final CurrentUserProvider currentUserProvider;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ReceiptEventAppender receiptEventAppender;

    public ReceiptService(
            CurrentUserProvider currentUserProvider,
            ConversationRepository conversationRepository,
            ConversationMemberRepository conversationMemberRepository,
            ReceiptEventAppender receiptEventAppender
    ) {
        this.currentUserProvider = currentUserProvider;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.receiptEventAppender = receiptEventAppender;
    }

    @Transactional
    public void deliveredUpTo(DeliveredUpToCommandPayload payload) {
        validateDeliveredPayload(payload);
        ReceiptContext context = loadContext(payload.conversationId());
        Long normalizedSeq = normalizeSeq(payload.deliveredSeq(), context.conversation());
        Instant now = Instant.now();

        if (!context.currentMember().updateDeliveredSeq(normalizedSeq, now)) {
            return;
        }

        conversationMemberRepository.save(context.currentMember());
        MessageDeliveredPayload eventPayload = new MessageDeliveredPayload(
                context.conversation().getId(),
                context.currentUserId(),
                normalizedSeq
        );
        receiptEventAppender.appendDeliveredEvent(context.currentUserId(), eventPayload, now);
        receiptEventAppender.appendDeliveredEvent(context.peerMember().getUserId(), eventPayload, now);
    }

    @Transactional
    public void readUpTo(ReadUpToCommandPayload payload) {
        validateReadPayload(payload);
        ReceiptContext context = loadContext(payload.conversationId());
        Long normalizedSeq = normalizeSeq(payload.readSeq(), context.conversation());
        Instant now = Instant.now();

        if (!context.currentMember().updateReadSeqAndClearUnread(normalizedSeq, now)) {
            return;
        }

        conversationMemberRepository.save(context.currentMember());
        MessageReadPayload eventPayload = new MessageReadPayload(
                context.conversation().getId(),
                context.currentUserId(),
                normalizedSeq
        );
        receiptEventAppender.appendReadEvent(context.currentUserId(), eventPayload, now);
        receiptEventAppender.appendReadEvent(context.peerMember().getUserId(), eventPayload, now);
    }

    private ReceiptContext loadContext(UUID conversationId) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        UUID currentUserId = currentUser.userId();

        ConversationEntity conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(this::notFound);

        ConversationMemberEntity currentMember = conversationMemberRepository
                .findByConversationIdAndUserIdForUpdate(conversationId, currentUserId)
                .orElseThrow(this::notFound);

        List<ConversationMemberEntity> members = conversationMemberRepository.findByConversationId(conversationId);
        if (members.size() != 2) {
            throw notFound();
        }

        ConversationMemberEntity peerMember = members.stream()
                .filter(member -> !member.getUserId().equals(currentUserId))
                .findFirst()
                .orElseThrow(this::notFound);

        return new ReceiptContext(conversation, currentMember, peerMember, currentUserId);
    }

    private Long normalizeSeq(Long seq, ConversationEntity conversation) {
        Long lastMessageSeq = conversation.getLastMessageSeq() == null ? 0L : conversation.getLastMessageSeq();
        return Math.min(seq, lastMessageSeq);
    }

    private void validateDeliveredPayload(DeliveredUpToCommandPayload payload) {
        if (payload == null) {
            throw validation("Delivered receipt payload is required.");
        }
        validateConversationId(payload.conversationId());
        validateSeq(payload.deliveredSeq(), "deliveredSeq is required.");
    }

    private void validateReadPayload(ReadUpToCommandPayload payload) {
        if (payload == null) {
            throw validation("Read receipt payload is required.");
        }
        validateConversationId(payload.conversationId());
        validateSeq(payload.readSeq(), "readSeq is required.");
    }

    private void validateConversationId(UUID conversationId) {
        if (conversationId == null) {
            throw validation("conversationId is required.");
        }
    }

    private void validateSeq(Long seq, String requiredMessage) {
        if (seq == null) {
            throw validation(requiredMessage);
        }
        if (seq < 0) {
            throw validation("Receipt sequence must be greater than or equal to 0.");
        }
    }

    private AppException validation(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message);
    }

    private AppException notFound() {
        return new AppException(ErrorCode.NOT_FOUND, "Requested resource not found.");
    }

    private record ReceiptContext(
            ConversationEntity conversation,
            ConversationMemberEntity currentMember,
            ConversationMemberEntity peerMember,
            UUID currentUserId
    ) {
    }
}
