package com.synchat.backend.notification.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.synchat.backend.common.config.AppProperties;
import com.synchat.backend.device.model.PushProviders;
import com.synchat.backend.notification.model.PushNotificationRequest;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class FirebaseFcmClientTests {

    @Test
    void fcmWebMessageIsDataOnlyAndContainsTitleAndBody() throws Exception {
        FirebaseMessaging firebaseMessaging = Mockito.mock(FirebaseMessaging.class);
        FirebaseFcmClient client = new FirebaseFcmClient(firebaseMessaging, appProperties());
        when(firebaseMessaging.send(any(Message.class), eq(true))).thenReturn("fcm-message-id");

        client.send(new PushNotificationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "firebase-web-token",
                PushProviders.FCM_WEB,
                "Alex",
                "Hello from Alex",
                Map.of(
                        "type", "message.created",
                        "conversationId", UUID.randomUUID().toString()
                )
        ));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging).send(messageCaptor.capture(), eq(true));

        Message message = messageCaptor.getValue();
        assertThat(data(message)).containsEntry("title", "Alex");
        assertThat(data(message)).containsEntry("body", "Hello from Alex");
        assertThat(notification(message)).isNull();
    }

    @Test
    void nativeFcmMessageKeepsNotificationPayload() throws Exception {
        FirebaseMessaging firebaseMessaging = Mockito.mock(FirebaseMessaging.class);
        FirebaseFcmClient client = new FirebaseFcmClient(firebaseMessaging, appProperties());
        when(firebaseMessaging.send(any(Message.class), eq(true))).thenReturn("fcm-message-id");

        client.send(new PushNotificationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "native-token",
                PushProviders.FCM,
                "Alex",
                "Hello from Alex",
                Map.of("type", "message.created")
        ));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging).send(messageCaptor.capture(), eq(true));

        assertThat(notification(messageCaptor.getValue())).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> data(Message message) throws Exception {
        Method method = Message.class.getDeclaredMethod("getData");
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(message);
    }

    private Notification notification(Message message) throws Exception {
        Method method = Message.class.getDeclaredMethod("getNotification");
        method.setAccessible(true);
        return (Notification) method.invoke(message);
    }

    private AppProperties appProperties() {
        return new AppProperties(
                null,
                null,
                null,
                new AppProperties.Notification(new AppProperties.Notification.Fcm(true, "synchat-test", true))
        );
    }
}
