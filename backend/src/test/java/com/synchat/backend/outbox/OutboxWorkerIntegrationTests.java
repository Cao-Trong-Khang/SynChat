package com.synchat.backend.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.synchat.backend.auth.service.AuthService;
import com.synchat.backend.common.security.SessionValidator;
import com.synchat.backend.conversation.service.ConversationMetadataMapper;
import com.synchat.backend.conversation.service.ConversationService;
import com.synchat.backend.message.service.MessageCreatedPayloadBuilder;
import com.synchat.backend.message.service.MessagePaginationService;
import com.synchat.backend.message.service.MessageSendService;
import com.synchat.backend.notification.NotificationDispatcher;
import com.synchat.backend.outbox.entity.OutboxEventEntity;
import com.synchat.backend.outbox.model.OutboxEventStatus;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import com.synchat.backend.outbox.service.ClaimedOutboxEvent;
import com.synchat.backend.outbox.service.OutboxClaimService;
import com.synchat.backend.outbox.service.OutboxEventAppender;
import com.synchat.backend.outbox.service.OutboxPublishService;
import com.synchat.backend.realtime.websocket.RealtimeEventPublisher;
import com.synchat.backend.sync.service.SyncService;
import com.synchat.backend.sync.service.UserEventAppender;
import com.synchat.backend.user.service.UserService;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "app.jwt.access-secret=01234567890123456789012345678901",
        "app.jwt.refresh-secret=01234567890123456789012345678901",
        "app.jwt.access-ttl-minutes=15",
        "app.jwt.refresh-ttl-days=30",
        "app.cors.allowed-origins[0]=http://localhost:3000"
})
@ActiveProfiles("test")
class OutboxWorkerIntegrationTests {

    @Autowired
    private OutboxClaimService outboxClaimService;

    @Autowired
    private OutboxPublishService outboxPublishService;

    @MockBean
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private RealtimeEventPublisher realtimeEventPublisher;

    @MockBean
    private NotificationDispatcher notificationDispatcher;

    @MockBean
    private AuthService authService;

    @MockBean
    private ConversationService conversationService;

    @MockBean
    private MessagePaginationService messagePaginationService;

    @MockBean
    private MessageSendService messageSendService;

    @MockBean
    private SyncService syncService;

    @MockBean
    private SessionValidator sessionValidator;

    @MockBean
    private ConversationMetadataMapper conversationMetadataMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private OutboxEventAppender outboxEventAppender;

    @MockBean
    private UserEventAppender userEventAppender;

    @MockBean
    private MessageCreatedPayloadBuilder messageCreatedPayloadBuilder;

    private final Map<UUID, OutboxEventEntity> eventsById = new HashMap<>();

    @BeforeEach
    void setUp() {
        eventsById.clear();

        when(outboxEventRepository.save(any())).thenAnswer(invocation -> {
            OutboxEventEntity event = invocation.getArgument(0);
            eventsById.put(event.getId(), event);
            return event;
        });
        when(outboxEventRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(eventsById.get(invocation.getArgument(0))));
        when(outboxEventRepository.findByStatusForUpdate(any(), any())).thenAnswer(invocation -> {
            String status = invocation.getArgument(0);
            Pageable pageable = invocation.getArgument(1);
            return eventsById.values()
                    .stream()
                    .filter(event -> event.getStatus().equals(status))
                    .sorted(Comparator.comparing(OutboxEventEntity::getCreatedAt))
                    .limit(pageable.getPageSize())
                    .toList();
        });
    }

    @Test
    void claimPendingChangesPendingEventsToProcessing() {
        OutboxEventEntity first = insertEvent(OutboxEventStatus.PENDING, validPayload(UUID.randomUUID(), 1L));
        OutboxEventEntity second = insertEvent(OutboxEventStatus.PENDING, validPayload(UUID.randomUUID(), 2L));

        List<ClaimedOutboxEvent> claimed = outboxClaimService.claimPending(10);

        assertThat(claimed).hasSize(2);
        assertThat(event(first.getId()).getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(event(second.getId()).getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
    }

    @Test
    void publishValidEventMarksPublished() {
        OutboxEventEntity event = insertEvent(OutboxEventStatus.PROCESSING, validPayload(UUID.randomUUID(), 1L));
        ClaimedOutboxEvent claimed = new ClaimedOutboxEvent(event.getId(), event.getEventType(), event.getPayload());

        outboxPublishService.publish(claimed);

        assertThat(event(event.getId()).getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    void publishInvalidPayloadMarksFailed() {
        OutboxEventEntity event = insertEvent(OutboxEventStatus.PROCESSING, """
                {
                  "eventSeq": 1,
                  "eventType": "message.created",
                  "payload": { "hello": "world" }
                }
                """);
        ClaimedOutboxEvent claimed = new ClaimedOutboxEvent(event.getId(), event.getEventType(), event.getPayload());

        outboxPublishService.publish(claimed);

        assertThat(event(event.getId()).getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    }

    @Test
    void failedEventsAreNotClaimedByClaimPending() {
        OutboxEventEntity failed = insertEvent(OutboxEventStatus.FAILED, validPayload(UUID.randomUUID(), 1L));

        List<ClaimedOutboxEvent> claimed = outboxClaimService.claimPending(10);

        assertThat(claimed).isEmpty();
        assertThat(event(failed.getId()).getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    }

    private OutboxEventEntity insertEvent(String status, String payload) {
        OutboxEventEntity event = OutboxEventEntity.create(
                UUID.randomUUID(),
                "message.created",
                payload,
                status,
                Instant.now().plusMillis(eventsById.size())
        );
        return outboxEventRepository.save(event);
    }

    private OutboxEventEntity event(UUID id) {
        return outboxEventRepository.findById(id).orElseThrow();
    }

    private String validPayload(UUID userId, Long eventSeq) {
        return """
                {
                  "userId": "%s",
                  "eventSeq": %d,
                  "eventType": "message.created",
                  "payload": {
                    "hello": "world"
                  }
                }
                """.formatted(userId, eventSeq);
    }

    @TestConfiguration
    static class TransactionTestConfiguration {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new PlatformTransactionManager() {
                @Override
                public TransactionStatus getTransaction(TransactionDefinition definition) {
                    return new SimpleTransactionStatus();
                }

                @Override
                public void commit(TransactionStatus status) {
                }

                @Override
                public void rollback(TransactionStatus status) {
                }
            };
        }
    }
}
