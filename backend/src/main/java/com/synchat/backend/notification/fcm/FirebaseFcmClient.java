package com.synchat.backend.notification.fcm;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.synchat.backend.common.config.AppProperties;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.notification.model.PushNotificationRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.notification.fcm",
        name = "enabled",
        havingValue = "true"
)
public class FirebaseFcmClient implements FcmClient {

    private static final Logger log = LoggerFactory.getLogger(FirebaseFcmClient.class);

    private final FirebaseMessaging firebaseMessaging;
    private final AppProperties appProperties;

    public FirebaseFcmClient(FirebaseMessaging firebaseMessaging, AppProperties appProperties) {
        this.firebaseMessaging = firebaseMessaging;
        this.appProperties = appProperties;
    }

    @Override
    public void send(PushNotificationRequest request) {
        Message.Builder messageBuilder = Message.builder()
                .setToken(request.pushToken())
                .setNotification(Notification.builder()
                        .setTitle(request.title())
                        .setBody(request.body())
                        .build());

        Map<String, String> data = request.data();
        if (data != null && !data.isEmpty()) {
            messageBuilder.putAllData(data);
        }

        Message message = messageBuilder.build();
        try {
            String messageId;
            if (appProperties.notification().fcm().dryRun()) {
                messageId = firebaseMessaging.send(message, true);
            } else {
                messageId = firebaseMessaging.send(message);
            }
            log.debug("FCM notification sent with messageId={}.", messageId);
        } catch (FirebaseMessagingException exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Failed to send push notification.");
        }
    }
}
