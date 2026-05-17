package com.synchat.backend.conversation.repository;

import com.synchat.backend.conversation.entity.ConversationEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    Optional<ConversationEntity> findByDirectKey(String directKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConversationEntity c where c.id = :id")
    Optional<ConversationEntity> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConversationEntity c where c.directKey = :directKey")
    Optional<ConversationEntity> findByDirectKeyForUpdate(String directKey);

    @Query("""
            select c
            from ConversationEntity c
            where c.lastMessageAt is not null
              and exists (
                  select 1
                  from ConversationMemberEntity member
                  where member.conversationId = c.id
                    and member.userId = :userId
              )
            order by c.lastMessageAt desc, c.id desc
            """)
    List<ConversationEntity> findLatestConversationsForUser(UUID userId, Pageable pageable);

    @Query("""
            select c
            from ConversationEntity c
            where c.lastMessageAt is not null
              and exists (
                  select 1
                  from ConversationMemberEntity member
                  where member.conversationId = c.id
                    and member.userId = :userId
              )
              and (
                  c.lastMessageAt < :cursorLastMessageAt
                  or (c.lastMessageAt = :cursorLastMessageAt and c.id < :cursorConversationId)
              )
            order by c.lastMessageAt desc, c.id desc
            """)
    List<ConversationEntity> findConversationsForUserAfterCursor(
            UUID userId,
            Instant cursorLastMessageAt,
            UUID cursorConversationId,
            Pageable pageable
    );
}
