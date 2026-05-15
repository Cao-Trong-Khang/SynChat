package com.synchat.backend.message.repository;

import com.synchat.backend.message.entity.MessageEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    Optional<MessageEntity> findBySenderIdAndClientMessageId(UUID senderId, UUID clientMessageId);

    Optional<MessageEntity> findByConversationIdAndSeq(UUID conversationId, Long seq);

    List<MessageEntity> findByConversationIdOrderBySeqAsc(UUID conversationId);
}
