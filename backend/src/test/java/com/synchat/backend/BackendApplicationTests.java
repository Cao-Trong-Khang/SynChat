package com.synchat.backend;

import com.synchat.backend.auth.service.AuthService;
import com.synchat.backend.conversation.service.ConversationMetadataMapper;
import com.synchat.backend.conversation.service.ConversationService;
import com.synchat.backend.message.service.MessageCreatedPayloadBuilder;
import com.synchat.backend.message.service.MessagePaginationService;
import com.synchat.backend.message.service.MessageSendService;
import com.synchat.backend.notification.service.PushCandidateResolver;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import com.synchat.backend.outbox.service.OutboxEventAppender;
import com.synchat.backend.receipt.service.ReceiptService;
import com.synchat.backend.realtime.presence.service.PresenceService;
import com.synchat.backend.sync.service.SyncService;
import com.synchat.backend.sync.service.UserEventAppender;
import com.synchat.backend.common.security.SessionValidator;
import com.synchat.backend.user.service.UserService;
import com.synchat.backend.device.service.DeviceService;
import com.synchat.backend.realtime.typing.service.TypingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "app.jwt.access-secret=01234567890123456789012345678901",
        "app.jwt.refresh-secret=01234567890123456789012345678901",
        "app.jwt.access-ttl-minutes=15",
        "app.jwt.refresh-ttl-days=30",
        "app.cors.allowed-origins[0]=http://localhost:3000"
})
@ActiveProfiles("test")
class BackendApplicationTests {

    @MockBean
    private TypingService typingService;

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
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private UserEventAppender userEventAppender;

    @MockBean
    private MessageCreatedPayloadBuilder messageCreatedPayloadBuilder;

    @MockBean
    private ReceiptService receiptService;

    @MockBean
    private DeviceService deviceService;

    @MockBean
    private PresenceService presenceService;

    @MockBean
    private PushCandidateResolver pushCandidateResolver;

    @Test
    void contextLoads() {
    }
}
