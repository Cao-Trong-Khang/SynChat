package com.synchat.backend.conversation.repository;

import com.synchat.backend.conversation.entity.ConversationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    Optional<ConversationEntity> findByDirectKey(String directKey);
}
