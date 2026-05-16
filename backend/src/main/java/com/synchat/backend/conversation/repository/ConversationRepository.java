package com.synchat.backend.conversation.repository;

import com.synchat.backend.conversation.entity.ConversationEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    Optional<ConversationEntity> findByDirectKey(String directKey);

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
