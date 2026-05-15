package com.synchat.backend.conversation.repository;

import com.synchat.backend.conversation.entity.ConversationMemberEntity;
import com.synchat.backend.conversation.entity.ConversationMemberId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMemberRepository extends JpaRepository<ConversationMemberEntity, ConversationMemberId> {

    List<ConversationMemberEntity> findByUserId(UUID userId);

    List<ConversationMemberEntity> findByConversationId(UUID conversationId);
}
