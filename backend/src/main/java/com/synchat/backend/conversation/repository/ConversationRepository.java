package com.synchat.backend.conversation.repository;

import com.synchat.backend.conversation.dto.ConversationMetadataRow;
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
import org.springframework.data.repository.query.Param;

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

    @Query("""
            select new com.synchat.backend.conversation.dto.ConversationMetadataRow(
                c.id,
                uPeer.id,
                uPeer.email,
                uPeer.displayName,
                uPeer.avatarUrl,
                c.lastMessageSeq,
                m.body,
                m.senderId,
                c.lastMessageAt,
                cmCurr.unreadCount,
                cmPeer.readSeq,
                cmPeer.deliveredSeq
            )
            from ConversationEntity c
            join ConversationMemberEntity cmCurr on cmCurr.conversationId = c.id and cmCurr.userId = :userId
            join ConversationMemberEntity cmPeer on cmPeer.conversationId = c.id and cmPeer.userId <> :userId
            join UserEntity uPeer on uPeer.id = cmPeer.userId
            left join MessageEntity m on m.conversationId = c.id and m.seq = c.lastMessageSeq
            where c.lastMessageAt is not null
            order by c.lastMessageAt desc, c.id desc
            """)
    List<ConversationMetadataRow> findMetadataLatestForUser(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @Query("""
            select new com.synchat.backend.conversation.dto.ConversationMetadataRow(
                c.id,
                uPeer.id,
                uPeer.email,
                uPeer.displayName,
                uPeer.avatarUrl,
                c.lastMessageSeq,
                m.body,
                m.senderId,
                c.lastMessageAt,
                cmCurr.unreadCount,
                cmPeer.readSeq,
                cmPeer.deliveredSeq
            )
            from ConversationEntity c
            join ConversationMemberEntity cmCurr on cmCurr.conversationId = c.id and cmCurr.userId = :userId
            join ConversationMemberEntity cmPeer on cmPeer.conversationId = c.id and cmPeer.userId <> :userId
            join UserEntity uPeer on uPeer.id = cmPeer.userId
            left join MessageEntity m on m.conversationId = c.id and m.seq = c.lastMessageSeq
            where c.lastMessageAt is not null
              and (
                  c.lastMessageAt < :cursorLastMessageAt
                  or (c.lastMessageAt = :cursorLastMessageAt and c.id < :cursorConversationId)
              )
            order by c.lastMessageAt desc, c.id desc
            """)
    List<ConversationMetadataRow> findMetadataAfterCursorForUser(
            @Param("userId") UUID userId,
            @Param("cursorLastMessageAt") Instant cursorLastMessageAt,
            @Param("cursorConversationId") UUID cursorConversationId,
            Pageable pageable
    );
}
