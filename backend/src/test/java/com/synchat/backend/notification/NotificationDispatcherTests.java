package com.synchat.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.synchat.backend.device.model.PushProviders;
import com.synchat.backend.notification.fcm.FcmClient;
import com.synchat.backend.notification.model.DeviceRuntimeStates;
import com.synchat.backend.notification.model.MessageCreatedNotificationData;
import com.synchat.backend.notification.model.PushCandidate;
import com.synchat.backend.notification.model.PushNotificationRequest;
import com.synchat.backend.notification.service.MessageCreatedNotificationParser;
import com.synchat.backend.notification.service.PushCandidateResolver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class NotificationDispatcherTests {

    private PushCandidateResolver pushCandidateResolver;
    private MessageCreatedNotificationParser messageCreatedNotificationParser;
    private FcmClient fcmClient;
    private DefaultNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        pushCandidateResolver = Mockito.mock(PushCandidateResolver.class);
        messageCreatedNotificationParser = Mockito.mock(MessageCreatedNotificationParser.class);
        fcmClient = Mockito.mock(FcmClient.class);
        dispatcher = new DefaultNotificationDispatcher(
                pushCandidateResolver,
                messageCreatedNotificationParser,
                fcmClient
        );
    }

    @Test
    void nonMessageEventDoesNotPush() {
        dispatcher.dispatchForOutboxEvent(UUID.randomUUID(), "message.read", Map.of());

        verifyNoInteractions(messageCreatedNotificationParser, pushCandidateResolver, fcmClient);
    }

    @Test
    void messageCreatedForSenderDoesNotPush() {
        UUID senderId = UUID.randomUUID();
        when(messageCreatedNotificationParser.parse(senderId, Map.of())).thenReturn(Optional.empty());

        dispatcher.dispatchForOutboxEvent(senderId, "message.created", Map.of());

        verify(messageCreatedNotificationParser).parse(senderId, Map.of());
        verifyNoInteractions(pushCandidateResolver, fcmClient);
    }

    @Test
    void offlineRecipientDeviceSendsPush() {
        UUID recipientId = UUID.randomUUID();
        MessageCreatedNotificationData notificationData = notificationData(recipientId);
        when(messageCreatedNotificationParser.parse(recipientId, Map.of())).thenReturn(Optional.of(notificationData));
        when(pushCandidateResolver.resolveCandidates(recipientId))
                .thenReturn(List.of(candidate(recipientId, "token-1", PushProviders.FCM, false, null)));

        dispatcher.dispatchForOutboxEvent(recipientId, "message.created", Map.of());

        ArgumentCaptor<PushNotificationRequest> requestCaptor =
                ArgumentCaptor.forClass(PushNotificationRequest.class);
        verify(fcmClient).send(requestCaptor.capture());

        PushNotificationRequest request = requestCaptor.getValue();
        assertThat(request.userId()).isEqualTo(recipientId);
        assertThat(request.pushToken()).isEqualTo("token-1");
        assertThat(request.pushProvider()).isEqualTo(PushProviders.FCM);
        assertThat(request.title()).isEqualTo(notificationData.senderDisplayName());
        assertThat(request.body()).isEqualTo(notificationData.messagePreview());
        assertThat(request.data()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "type", "message.created",
                "eventType", "message.created",
                "conversationId", notificationData.conversationId().toString(),
                "senderId", notificationData.senderId().toString(),
                "messageSeq", notificationData.messageSeq().toString(),
                "clientMessageId", notificationData.clientMessageId().toString(),
                "clickAction", "/conversations/" + notificationData.conversationId()
        ));
    }

    @Test
    void onlineForegroundDeviceDoesNotPush() {
        UUID recipientId = UUID.randomUUID();
        when(messageCreatedNotificationParser.parse(recipientId, Map.of()))
                .thenReturn(Optional.of(notificationData(recipientId)));
        when(pushCandidateResolver.resolveCandidates(recipientId))
                .thenReturn(List.of(candidate(
                        recipientId,
                        "token-1",
                        PushProviders.FCM,
                        true,
                        DeviceRuntimeStates.FOREGROUND
                )));

        dispatcher.dispatchForOutboxEvent(recipientId, "message.created", Map.of());

        verify(fcmClient, never()).send(any());
    }

    @Test
    void onlineBackgroundDeviceSendsPush() {
        UUID recipientId = UUID.randomUUID();
        when(messageCreatedNotificationParser.parse(recipientId, Map.of()))
                .thenReturn(Optional.of(notificationData(recipientId)));
        when(pushCandidateResolver.resolveCandidates(recipientId))
                .thenReturn(List.of(candidate(
                        recipientId,
                        "token-1",
                        PushProviders.FCM,
                        true,
                        DeviceRuntimeStates.BACKGROUND
                )));

        dispatcher.dispatchForOutboxEvent(recipientId, "message.created", Map.of());

        verify(fcmClient).send(any());
    }

    @Test
    void fcmWebProviderSendsPush() {
        UUID recipientId = UUID.randomUUID();
        MessageCreatedNotificationData notificationData = notificationData(recipientId);
        when(messageCreatedNotificationParser.parse(recipientId, Map.of()))
                .thenReturn(Optional.of(notificationData));
        when(pushCandidateResolver.resolveCandidates(recipientId))
                .thenReturn(List.of(candidate(recipientId, "firebase-web-token-1", PushProviders.FCM_WEB, false, null)));

        dispatcher.dispatchForOutboxEvent(recipientId, "message.created", Map.of());

        ArgumentCaptor<PushNotificationRequest> requestCaptor =
                ArgumentCaptor.forClass(PushNotificationRequest.class);
        verify(fcmClient).send(requestCaptor.capture());

        PushNotificationRequest request = requestCaptor.getValue();
        assertThat(request.pushProvider()).isEqualTo(PushProviders.FCM_WEB);
        assertThat(request.data()).containsEntry("title", notificationData.senderDisplayName());
        assertThat(request.data()).containsEntry("body", notificationData.messagePreview());
        assertThat(request.data()).containsEntry("type", "message.created");
        assertThat(request.data()).containsEntry("eventType", "message.created");
        assertThat(request.data()).containsEntry("conversationId", notificationData.conversationId().toString());
        assertThat(request.data()).containsEntry("senderId", notificationData.senderId().toString());
        assertThat(request.data()).containsEntry("messageSeq", notificationData.messageSeq().toString());
        assertThat(request.data()).containsEntry("clientMessageId", notificationData.clientMessageId().toString());
        assertThat(request.data()).containsEntry("clickAction", "/conversations/" + notificationData.conversationId());
    }

    @Test
    void deviceWithoutPushTokenIsSkipped() {
        UUID recipientId = UUID.randomUUID();
        when(messageCreatedNotificationParser.parse(recipientId, Map.of()))
                .thenReturn(Optional.of(notificationData(recipientId)));
        when(pushCandidateResolver.resolveCandidates(recipientId))
                .thenReturn(List.of(candidate(recipientId, " ", PushProviders.FCM, false, null)));

        dispatcher.dispatchForOutboxEvent(recipientId, "message.created", Map.of());

        verify(fcmClient, never()).send(any());
    }

    @Test
    void unsupportedProviderIsSkipped() {
        UUID recipientId = UUID.randomUUID();
        when(messageCreatedNotificationParser.parse(recipientId, Map.of()))
                .thenReturn(Optional.of(notificationData(recipientId)));
        when(pushCandidateResolver.resolveCandidates(recipientId))
                .thenReturn(List.of(candidate(recipientId, "token-1", "web_push", false, null)));

        dispatcher.dispatchForOutboxEvent(recipientId, "message.created", Map.of());

        verify(fcmClient, never()).send(any());
    }

    @Test
    void oneDeviceSendFailureDoesNotStopOtherDevices() {
        UUID recipientId = UUID.randomUUID();
        PushCandidate firstCandidate = candidate(recipientId, "token-1", PushProviders.FCM, false, null);
        PushCandidate secondCandidate = candidate(recipientId, "token-2", PushProviders.FCM, false, null);
        when(messageCreatedNotificationParser.parse(recipientId, Map.of()))
                .thenReturn(Optional.of(notificationData(recipientId)));
        when(pushCandidateResolver.resolveCandidates(recipientId))
                .thenReturn(List.of(firstCandidate, secondCandidate));
        doThrow(new RuntimeException("send failed"))
                .doNothing()
                .when(fcmClient).send(any(PushNotificationRequest.class));

        assertThatCode(() -> dispatcher.dispatchForOutboxEvent(recipientId, "message.created", Map.of()))
                .doesNotThrowAnyException();

        ArgumentCaptor<PushNotificationRequest> requestCaptor =
                ArgumentCaptor.forClass(PushNotificationRequest.class);
        verify(fcmClient, Mockito.times(2)).send(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(PushNotificationRequest::pushToken)
                .containsExactly("token-1", "token-2");
    }

    private MessageCreatedNotificationData notificationData(UUID recipientId) {
        return new MessageCreatedNotificationData(
                UUID.randomUUID(),
                UUID.randomUUID(),
                recipientId,
                "Alex",
                "Hello there",
                42L,
                UUID.randomUUID()
        );
    }

    private PushCandidate candidate(
            UUID userId,
            String pushToken,
            String pushProvider,
            boolean online,
            String runtimeState
    ) {
        return new PushCandidate(
                userId,
                UUID.randomUUID(),
                "ios",
                pushToken,
                pushProvider,
                online,
                runtimeState
        );
    }
}
