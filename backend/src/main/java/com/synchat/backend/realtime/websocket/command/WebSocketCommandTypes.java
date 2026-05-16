package com.synchat.backend.realtime.websocket.command;

public final class WebSocketCommandTypes {

    public static final String MESSAGES_SEND = "messages.send";
    public static final String DELIVERED_UP_TO = "delivered.up_to";
    public static final String READ_UP_TO = "read.up_to";
    public static final String DEVICES_REGISTER = "devices.register";
    public static final String PRESENCE_SUBSCRIBE = "presence.subscribe";
    public static final String PRESENCE_UNSUBSCRIBE = "presence.unsubscribe";
    public static final String TYPING_SUBSCRIBE = "typing.subscribe";
    public static final String TYPING_UNSUBSCRIBE = "typing.unsubscribe";
    public static final String TYPING_START = "typing.start";
    public static final String TYPING_STOP = "typing.stop";
    public static final String DEVICE_STATE = "device.state";

    private WebSocketCommandTypes() {
    }
}
