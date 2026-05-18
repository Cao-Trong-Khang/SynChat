package com.synchat.backend.notification.fcm;

import com.synchat.backend.notification.model.PushNotificationRequest;

public interface FcmClient {

    void send(PushNotificationRequest request);
}
