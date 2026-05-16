package com.synchat.backend.message.repository;

import com.synchat.backend.message.entity.MessageEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    Optional<MessageEntity> findBySenderIdAndClientMessageId(UUID senderId, UUID clientMessageId);

    Optional<MessageEntity> findByConversationIdAndSeq(UUID conversationId, Long seq);

    List<MessageEntity> findByConversationIdOrderBySeqAsc(UUID conversationId);

    @Query("""
            select m
            from MessageEntity m
            where m.conversationId = :conversationId
            order by m.seq desc
            """)
    List<MessageEntity> findLatestByConversationId(UUID conversationId, Pageable pageable);

    @Query("""
            select m
            from MessageEntity m
            where m.conversationId = :conversationId
              and m.seq < :beforeSeq
            order by m.seq desc
            """)
    List<MessageEntity> findOlderByConversationId(UUID conversationId, Long beforeSeq, Pageable pageable);
}
