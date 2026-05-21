package com.synchat.backend.notification;

import com.synchat.backend.device.model.PushProviders;
import com.synchat.backend.notification.fcm.FcmClient;
import com.synchat.backend.notification.model.DeviceRuntimeStates;
import com.synchat.backend.notification.model.MessageCreatedNotificationData;
import com.synchat.backend.notification.model.PushCandidate;
import com.synchat.backend.notification.model.PushNotificationRequest;
import com.synchat.backend.notification.service.MessageCreatedNotificationParser;
import com.synchat.backend.notification.service.PushCandidateResolver;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultNotificationDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultNotificationDispatcher.class);
    private static final String MESSAGE_CREATED_EVENT_TYPE = "message.created";

    private final PushCandidateResolver pushCandidateResolver;
    private final MessageCreatedNotificationParser messageCreatedNotificationParser;
    private final FcmClient fcmClient;

    public DefaultNotificationDispatcher(
            PushCandidateResolver pushCandidateResolver,
            MessageCreatedNotificationParser messageCreatedNotificationParser,
            FcmClient fcmClient
    ) {
        this.pushCandidateResolver = pushCandidateResolver;
        this.messageCreatedNotificationParser = messageCreatedNotificationParser;
        this.fcmClient = fcmClient;
    }

    @Override
    public void dispatchForOutboxEvent(UUID userId, String eventType, Object payload) {
        if (userId == null || !MESSAGE_CREATED_EVENT_TYPE.equals(eventType)) {
            return;
        }

        try {
            Optional<MessageCreatedNotificationData> notificationData =
                    messageCreatedNotificationParser.parse(userId, payload);
            if (notificationData.isEmpty()) {
                return;
            }

            for (PushCandidate candidate : pushCandidateResolver.resolveCandidates(userId)) {
                dispatchToCandidate(notificationData.get(), candidate);
            }
        } catch (Exception exception) {
            log.warn("Failed to dispatch push notifications for eventType={} userId={}.", eventType, userId, exception);
        }
    }

    private void dispatchToCandidate(MessageCreatedNotificationData notificationData, PushCandidate candidate) {
        if (!shouldPush(candidate)) {
            return;
        }

        try {
            fcmClient.send(new PushNotificationRequest(
                    notificationData.recipientUserId(),
                    candidate.deviceId(),
                    candidate.pushToken(),
                    notificationData.senderDisplayName(),
                    notificationData.messagePreview(),
                    Map.of(
                            "type", MESSAGE_CREATED_EVENT_TYPE,
                            "eventType", MESSAGE_CREATED_EVENT_TYPE,
                            "conversationId", notificationData.conversationId().toString(),
                            "senderId", notificationData.senderId().toString(),
                            "messageSeq", notificationData.messageSeq().toString(),
                            "clientMessageId", notificationData.clientMessageId().toString(),
                            "clickAction", "/conversations/" + notificationData.conversationId()
                    )
            ));
        } catch (Exception exception) {
            log.warn(
                    "Failed to send push notification for userId={} deviceId={}.",
                    notificationData.recipientUserId(),
                    candidate.deviceId(),
                    exception
            );
        }
    }

    private boolean shouldPush(PushCandidate candidate) {
        if (candidate.pushToken() == null || candidate.pushToken().isBlank()) {
            return false;
        }
        if (!PushProviders.FCM.equals(candidate.pushProvider())
                && !PushProviders.FCM_WEB.equals(candidate.pushProvider())) {
            return false;
        }
        if (!candidate.online()) {
            return true;
        }
        return DeviceRuntimeStates.BACKGROUND.equals(candidate.runtimeState());
    }
}
