package com.synchat.backend.notification.fcm;

import com.synchat.backend.notification.model.PushNotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.notification.fcm",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoopFcmClient implements FcmClient {

    private static final Logger log = LoggerFactory.getLogger(NoopFcmClient.class);

    @Override
    public void send(PushNotificationRequest request) {
        log.debug(
                "FCM send skipped for userId={} deviceId={}.",
                request.userId(),
                request.deviceId()
        );
    }
}
