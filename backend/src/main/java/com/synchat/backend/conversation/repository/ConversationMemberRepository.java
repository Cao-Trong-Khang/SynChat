package com.synchat.backend.conversation.repository;

import com.synchat.backend.conversation.entity.ConversationMemberEntity;
import com.synchat.backend.conversation.entity.ConversationMemberId;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ConversationMemberRepository extends JpaRepository<ConversationMemberEntity, ConversationMemberId> {

    List<ConversationMemberEntity> findByUserId(UUID userId);

    List<ConversationMemberEntity> findByConversationId(UUID conversationId);

    Optional<ConversationMemberEntity> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m
            from ConversationMemberEntity m
            where m.conversationId = :conversationId
              and m.userId = :userId
            """)
    Optional<ConversationMemberEntity> findByConversationIdAndUserIdForUpdate(UUID conversationId, UUID userId);
}
