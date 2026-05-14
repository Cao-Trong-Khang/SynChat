# SynChat API

## Table of Contents

1. [Conventions](#1-conventions)
2. [Authentication](#2-authentication)
3. [REST APIs](#3-rest-apis)
4. [WebSocket Connection](#4-websocket-connection)
5. [WebSocket Client Commands](#5-websocket-client-commands)
6. [WebSocket Server Events](#6-websocket-server-events)
7. [Common Error Codes](#7-common-error-codes)

---

## 1. Conventions

### Base URL

```text
/api
```

### Authentication

Protected REST APIs must send the access token:

```http
Authorization: Bearer {accessToken}
```

WebSocket connection also uses the access token during connection.

### Naming

- REST JSON uses `camelCase`.
- Database fields use `snake_case`.
- API field names should map clearly to database fields.

Examples:

```text
clientMessageId → client_message_id
lastHandledEventSeq → last_handled_event_seq
oldestLoadedSeq → oldest_loaded_seq
```

### IDs

Most IDs are UUID strings.

```json
{
  "id": "8d091a9a-7c8e-4d32-9c21-1b8a4d7e2f90"
}
```

### Time

All timestamps are ISO-8601 strings in UTC.

```json
{
  "createdAt": "2026-05-13T10:30:00Z"
}
```

---

## 2. Authentication

The system supports multi-device login.

Each device has its own auth session. Logout from one device must not affect other devices.

The user only enters:

```text
email
password
```

The client creates or reads:

```text
deviceId
platform
```

from local secure storage and the current app environment.

Push token registration is handled separately by `devices.register`, because push token may not be available during login.

---

## 3. REST APIs

## 3.1 Register

```http
POST /auth/register
```

Creates a new user account.

Registration does not create an authenticated session. The client should show a success message and navigate back to the login screen.

### Request

```json
{
  "email": "user@example.com",
  "password": "plain-password",
  "displayName": "User Name"
}
```

### Response

```json
{
  "success": true
}
```

### Notes

Server creates:

```text
USERS
USER_CREDENTIALS
USER_EVENT_OFFSETS
```

The server does not return `accessToken` or `refreshToken`.

---

## 3.2 Login

```http
POST /auth/login
```

Creates a new auth session for the current device.

### Request

```json
{
  "email": "user@example.com",
  "password": "plain-password",
  "deviceId": "device-uuid",
  "platform": "android"
}
```

### Response

```json
{
  "accessToken": "access-token",
  "accessTokenExpiresAt": "2026-05-13T10:45:00Z",
  "refreshToken": "refresh-token",
  "refreshTokenExpiresAt": "2026-06-13T10:30:00Z",
  "user": {
    "id": "user-id",
    "email": "user@example.com",
    "displayName": "User Name",
    "avatarUrl": null
  }
}
```

### Notes

Server verifies credentials, upserts `DEVICES` for the current device with nullable push fields, then creates an `AUTH_SESSIONS` row for that device.

After login, the client should:

```text
1. Store tokens and deviceId securely.
2. Use accessTokenExpiresAt to know when the access token should be refreshed.
3. Run first app open sync.
4. Register or update push token later with devices.register.
```

---

## 3.3 Refresh Session

```http
POST /auth/refresh
```

Returns a new access token if the refresh session is still valid.

### Request

```json
{
  "refreshToken": "refresh-token",
  "deviceId": "device-uuid"
}
```

### Response

```json
{
  "accessToken": "new-access-token",
  "accessTokenExpiresAt": "2026-05-13T11:00:00Z",
  "refreshToken": "optional-new-refresh-token",
  "refreshTokenExpiresAt": "2026-06-13T10:30:00Z"
}
```

### Notes

The server checks the current device session in `AUTH_SESSIONS`.

If the server rotates the refresh token, it returns a new `refreshToken` and `refreshTokenExpiresAt`. The client should replace the old refresh token with the new one.

If refresh fails, the client should stop authenticated activity, disconnect WebSocket, and show the login screen.

---

## 3.4 Logout

```http
POST /auth/logout
```

Logs out only the current device/session.

### Request

```json
{
  "refreshToken": "refresh-token",
  "deviceId": "device-uuid"
}
```

### Response

```json
{
  "success": true
}
```

### Notes

Server revokes the current `AUTH_SESSIONS` row.

Client should:

```text
1. Disconnect WebSocket.
2. Stop local outbox processing.
3. Clear local chat cache.
4. Clear local tokens.
5. Show login screen.
```

Client clears:

```text
LOCAL_USERS
LOCAL_CONVERSATIONS
LOCAL_MESSAGES
LOCAL_MESSAGE_SYNC_STATE
LOCAL_SYNC_STATE
LOCAL_OUTBOX_COMMANDS
```

Other devices of the same user keep working.

---

## 3.5 Search Users

```http
GET /users/search?q={keyword}
```

Searches users by email or display name.

### Query Parameters

| Name | Required | Description     |
| ---- | -------: | --------------- |
| `q`  |      yes | Search keyword. |

### Response

```json
{
  "items": [
    {
      "id": "user-id",
      "email": "peer@example.com",
      "displayName": "Peer User",
      "avatarUrl": null
    }
  ]
}
```

### Notes

The client upserts returned users into `LOCAL_USERS`.

---

## 3.6 Get Direct Conversation

```http
GET /conversations/direct?peerUserId={userId}
```

Checks whether a direct conversation already exists between the current user and the selected peer.

### Query Parameters

| Name         | Required | Description       |
| ------------ | -------: | ----------------- |
| `peerUserId` |      yes | The peer user id. |

### Response: Existing Conversation

```json
{
  "conversation": {
    "conversationId": "conversation-id",
    "peer": {
      "id": "peer-user-id",
      "email": "peer@example.com",
      "displayName": "Peer User",
      "avatarUrl": null
    },
    "lastMessageSeq": 100,
    "lastMessageText": "hello",
    "lastMessageSenderId": "peer-user-id",
    "lastMessageAt": "2026-05-13T10:30:00Z",
    "unreadCount": 2,
    "peerReadSeq": 95,
    "peerDeliveredSeq": 100
  }
}
```

### Response: No Conversation

```json
{
  "conversation": null
}
```

### Notes

If the response is `null`, the client creates a virtual local conversation:

```text
LOCAL_CONVERSATIONS.server_id = null
LOCAL_CONVERSATIONS.local_id = direct:{peerUserId}
```

The server conversation is created only when the first message is sent.

---

## 3.7 Sync Conversations

```http
GET /sync/conversations?limit=20
GET /sync/conversations?limit=20&cursor={nextCursor}
```

Loads a page of conversation metadata.

### Query Parameters

| Name     | Required | Description                                                 |
| -------- | -------: | ----------------------------------------------------------- |
| `limit`  |      yes | Page size. Usually `20`.                                    |
| `cursor` |       no | Cursor from the previous response. Omit for the first page. |

### Response: First Page

Used when `cursor` is omitted.

```json
{
  "items": [
    {
      "conversationId": "conversation-id",
      "peer": {
        "id": "peer-user-id",
        "email": "peer@example.com",
        "displayName": "Peer User",
        "avatarUrl": null
      },
      "lastMessageSeq": 100,
      "lastMessageText": "hello",
      "lastMessageSenderId": "peer-user-id",
      "lastMessageAt": "2026-05-13T10:30:00Z",
      "unreadCount": 2,
      "peerReadSeq": 95,
      "peerDeliveredSeq": 100
    }
  ],
  "nextCursor": "encoded-cursor",
  "hasMore": true,
  "serverEventSeq": 1050
}
```

### Response: Load More

Used when `cursor` is present.

```json
{
  "items": [
    {
      "conversationId": "conversation-id",
      "peer": {
        "id": "peer-user-id",
        "email": "peer@example.com",
        "displayName": "Peer User",
        "avatarUrl": null
      },
      "lastMessageSeq": 80,
      "lastMessageText": "older conversation",
      "lastMessageSenderId": "peer-user-id",
      "lastMessageAt": "2026-05-10T10:30:00Z",
      "unreadCount": 0,
      "peerReadSeq": 80,
      "peerDeliveredSeq": 80
    }
  ],
  "nextCursor": "encoded-cursor",
  "hasMore": true
}
```

### Notes

- First sync has no `cursor`.
- Load more requests include `cursor`.
- The endpoint returns conversation metadata only.
- It does not return full message history.
- `serverEventSeq` is returned only for the first page.

For the first page, the client stores:

```text
LOCAL_SYNC_STATE["last_handled_event_seq"] = serverEventSeq
LOCAL_SYNC_STATE["next_conversation_cursor"] = nextCursor
LOCAL_SYNC_STATE["has_more_conversations"] = hasMore
```

For load more requests, the client stores only:

```text
LOCAL_SYNC_STATE["next_conversation_cursor"] = nextCursor
LOCAL_SYNC_STATE["has_more_conversations"] = hasMore
```

The client must not update `LOCAL_SYNC_STATE["last_handled_event_seq"]` when loading more conversations.

For `LOCAL_MESSAGE_SYNC_STATE`, the client only creates it if it does not exist yet. It must not reset an existing message range.

---

## 3.8 Sync Events

```http
GET /sync/events?afterSeq={lastHandledEventSeq}&limit=500
```

Replays missed domain events.

### Query Parameters

| Name       | Required | Description                                          |
| ---------- | -------: | ---------------------------------------------------- |
| `afterSeq` |      yes | Last event sequence that the client already applied. |
| `limit`    |      yes | Max number of events to return. Usually `500`.       |

### Response

```json
{
  "events": [
    {
      "eventSeq": 1051,
      "eventType": "message.created",
      "payload": {}
    }
  ],
  "hasMore": true,
  "nextAfterSeq": 1051
}
```

### Cursor Expired Response

```json
{
  "error": {
    "code": "EVENT_CURSOR_EXPIRED",
    "message": "Event cursor is too old."
  }
}
```

### Notes

Events are sorted by `eventSeq` ascending.

The client must apply events in order and update `LOCAL_SYNC_STATE["last_handled_event_seq"]` only after the event is saved locally.

---

## 3.9 Load Latest Messages

```http
GET /conversations/{conversationId}/messages?limit=20
```

Loads the latest messages in one conversation.

### Path Parameters

| Name             | Description             |
| ---------------- | ----------------------- |
| `conversationId` | Server conversation id. |

### Query Parameters

| Name    | Required | Description              |
| ------- | -------: | ------------------------ |
| `limit` |      yes | Page size. Usually `20`. |

### Response

```json
{
  "conversationId": "conversation-id",
  "messages": [
    {
      "seq": 100,
      "senderId": "user-id",
      "clientMessageId": "optional-client-message-id",
      "body": "hello",
      "createdAtServer": "2026-05-13T10:30:00Z"
    }
  ],
  "oldestSeq": 81,
  "newestSeq": 100,
  "hasMoreBefore": true
}
```

### Notes

The server returns `conversationId`, which is the server id of the conversation. Before saving messages, the client finds the matching local conversation by `LOCAL_CONVERSATIONS.server_id = conversationId`. Then it saves messages with `LOCAL_MESSAGES.conversation_local_id = LOCAL_CONVERSATIONS.local_id`.

This is needed because local conversations can be virtual before the server creates the real conversation.

---

## 3.10 Load Older Messages

```http
GET /conversations/{conversationId}/messages?beforeSeq={oldestLoadedSeq}&limit=20
```

Loads messages older than the local oldest message.

### Query Parameters

| Name        | Required | Description                           |
| ----------- | -------: | ------------------------------------- |
| `beforeSeq` |      yes | Load messages with `seq < beforeSeq`. |
| `limit`     |      yes | Page size. Usually `20`.              |

### Response

```json
{
  "conversationId": "conversation-id",
  "messages": [
    {
      "seq": 80,
      "senderId": "user-id",
      "clientMessageId": "optional-client-message-id",
      "body": "older message",
      "createdAtServer": "2026-05-12T10:30:00Z"
    }
  ],
  "oldestSeq": 61,
  "hasMoreBefore": true
}
```

### Notes

The client updates:

```text
LOCAL_MESSAGE_SYNC_STATE.oldest_loaded_seq
LOCAL_MESSAGE_SYNC_STATE.has_more_before
```

---

## 4. WebSocket Connection

### Connect

The client connects with an access token.

```text
wss://server/ws?accessToken={accessToken}&deviceId={deviceId}
```

Alternative header-based auth is also valid if the client platform supports it.

### Connection Rules

- The server validates the access token only when the WebSocket connection is opened.
- If the token is invalid or already expired at connect time, the server rejects the connection.
- After the socket is accepted, the server does not close it only because the access token later expires.
- If the socket reconnects, the client must use a valid access token.
- If the session is revoked, for example after logout, the server closes sockets for that session.

### WebSocket Message Envelope

Client command:

```json
{
  "type": "messages.send",
  "requestId": "client-request-id",
  "payload": {}
}
```

Server error:

```json
{
  "type": "error",
  "requestId": "client-request-id",
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid payload."
  }
}
```

Persisted realtime event:

```json
{
  "type": "event",
  "eventSeq": 1051,
  "eventType": "message.created",
  "payload": {}
}
```

Ephemeral event:

```json
{
  "type": "typing.started",
  "payload": {}
}
```

---

## 5. WebSocket Client Commands

Client commands are messages sent from client to server. They ask the server to do something.

## 5.1 Send Message

```text
messages.send
```

Sends a new message.

### Command: Existing Conversation

```json
{
  "type": "messages.send",
  "requestId": "client-request-id",
  "payload": {
    "conversationId": "conversation-id",
    "clientMessageId": "client-message-uuid",
    "body": "hello"
  }
}
```

### Command: Virtual Conversation

```json
{
  "type": "messages.send",
  "requestId": "client-request-id",
  "payload": {
    "recipientId": "peer-user-id",
    "clientMessageId": "client-message-uuid",
    "body": "hello"
  }
}
```

### Notes

Exactly one of these fields is required:

```text
conversationId
recipientId
```

If the message already exists, the server does not insert a duplicate. The existing message is delivered back to the sender through the normal `message.created` event.

---

## 5.2 Delivered Up To

```text
delivered.up_to
```

Marks messages as delivered up to a sequence.

### Command

```json
{
  "type": "delivered.up_to",
  "requestId": "client-request-id",
  "payload": {
    "conversationId": "conversation-id",
    "deliveredSeq": 101
  }
}
```

### Notes

The server updates:

```text
CONVERSATION_MEMBERS.delivered_seq
```

for the current user.

---

## 5.3 Read Up To

```text
read.up_to
```

Marks messages as read up to a sequence.

### Command

```json
{
  "type": "read.up_to",
  "requestId": "client-request-id",
  "payload": {
    "conversationId": "conversation-id",
    "readSeq": 101
  }
}
```

### Notes

The server updates:

```text
CONVERSATION_MEMBERS.read_seq
CONVERSATION_MEMBERS.unread_count = 0
```

for the current user.

---

## 5.4 Device Register

```text
devices.register
```

Registers or updates the current device and push token.

### Command: Push Token Available

```json
{
  "type": "devices.register",
  "requestId": "client-request-id",
  "payload": {
    "deviceId": "device-uuid",
    "platform": "android",
    "pushToken": "fcm-token",
    "pushProvider": "fcm"
  }
}
```

### Command: Push Token Not Available

```json
{
  "type": "devices.register",
  "requestId": "client-request-id",
  "payload": {
    "deviceId": "device-uuid",
    "platform": "android",
    "pushToken": null,
    "pushProvider": null
  }
}
```

### Notes

The server upserts:

```text
DEVICES
```

If push token is not available yet, `pushToken` and `pushProvider` may be `null`.

The client should send this command after login, when the app opens, and whenever the push token changes. For Android, FCM can rotate the token, so the client must send the new token to the server.

---

## 5.5 Presence Subscribe

```text
presence.subscribe
```

Subscribes to presence updates for visible users.

### Command

```json
{
  "type": "presence.subscribe",
  "requestId": "client-request-id",
  "payload": {
    "userIds": ["user-a", "user-b"]
  }
}
```

### Response Event

The server returns a snapshot:

```json
{
  "type": "presence.snapshot",
  "payload": {
    "items": [
      {
        "userId": "user-a",
        "online": true,
        "lastSeenAt": null
      },
      {
        "userId": "user-b",
        "online": false,
        "lastSeenAt": "2026-05-13T10:30:00Z"
      }
    ]
  }
}
```

### Notes

Presence is stored in Redis and client memory, not in local DB.

---

## 5.6 Presence Unsubscribe

```text
presence.unsubscribe
```

Stops receiving presence updates for selected users.

### Command

```json
{
  "type": "presence.unsubscribe",
  "requestId": "client-request-id",
  "payload": {
    "userIds": ["user-a", "user-b"]
  }
}
```

### Notes

This command is optional for MVP. Server should also clean up subscriptions when the WebSocket disconnects.

---

## 5.7 Typing Subscribe

```text
typing.subscribe
```

Subscribes the current socket session to typing events of one conversation.

### Command

```json
{
  "type": "typing.subscribe",
  "requestId": "client-request-id",
  "payload": {
    "conversationId": "conversation-id"
  }
}
```

### Notes

The server validates that the current user is a member of the conversation, then stores the subscription in Redis or memory.

---

## 5.8 Typing Unsubscribe

```text
typing.unsubscribe
```

Stops receiving typing events of one conversation.

### Command

```json
{
  "type": "typing.unsubscribe",
  "requestId": "client-request-id",
  "payload": {
    "conversationId": "conversation-id"
  }
}
```

### Notes

The server should also clean up typing subscriptions when the WebSocket disconnects.

---

## 5.9 Typing Start

```text
typing.start
```

Tells the peer that the user is typing.

### Command

```json
{
  "type": "typing.start",
  "requestId": "client-request-id",
  "payload": {
    "conversationId": "conversation-id"
  }
}
```

### Notes

The server stores a short TTL key in Redis:

```text
typing:{conversationId}:{userId}
```

The client should throttle this command.

---

## 5.10 Typing Stop

```text
typing.stop
```

Tells the peer that the user stopped typing.

### Command

```json
{
  "type": "typing.stop",
  "requestId": "client-request-id",
  "payload": {
    "conversationId": "conversation-id"
  }
}
```

### Notes

This command is optional. If the client does not send it, the Redis TTL will expire and the server will send `typing.stopped`.

---

## 5.11 Device State

```text
device.state
```

Updates the current device runtime state.

### Command

```json
{
  "type": "device.state",
  "requestId": "client-request-id",
  "payload": {
    "state": "foreground"
  }
}
```

Allowed values:

```text
foreground
background
```

### Notes

The server stores this state in Redis. This state is temporary and is used by the notification worker to decide whether a device can receive push notification.

---

## 6. WebSocket Server Events

Server events are messages sent from server to client. They notify the client that something happened.

## 6.1 Message Created

Persisted domain event.

```json
{
  "type": "event",
  "eventSeq": 1051,
  "eventType": "message.created",
  "payload": {
    "conversation": {
      "conversationId": "conversation-id",
      "peer": {
        "id": "peer-user-id",
        "email": "peer@example.com",
        "displayName": "Peer User",
        "avatarUrl": null
      },
      "lastMessageSeq": 101,
      "lastMessageText": "hello",
      "lastMessageSenderId": "sender-user-id",
      "lastMessageAt": "2026-05-13T10:30:00Z",
      "unreadCount": 1,
      "peerReadSeq": 95,
      "peerDeliveredSeq": 100
    },
    "message": {
      "seq": 101,
      "senderId": "sender-user-id",
      "clientMessageId": "client-message-uuid",
      "body": "hello",
      "createdAtServer": "2026-05-13T10:30:00Z"
    }
  }
}
```

### Client Handling

Client should:

```text
1. Upsert LOCAL_USERS.
2. Upsert LOCAL_CONVERSATIONS.
3. Ensure LOCAL_MESSAGE_SYNC_STATE exists.
4. Upsert LOCAL_MESSAGES.
5. Update message sync range.
6. Update last_handled_event_seq after local commit.
```

### Notes

The `conversation` object is personalized for the receiving user.

For the current client:

- `peer` is the other user in the conversation.
- `unreadCount` is the current user's unread count.
- `peerReadSeq` and `peerDeliveredSeq` are the peer member state relative to the current user.

When applying `message.created`:

- If `newest_loaded_seq` is `null`, set it to `message.seq`.
- If `newest_loaded_seq` is not `null`, set it to `max(current_newest_loaded_seq, message.seq)`.
- If `oldest_loaded_seq` is `null`, set it to `message.seq`.
- If `oldest_loaded_seq` is not `null`, keep it unchanged.
- If this is the first message of a new virtual conversation, `has_more_before` can be `false`.
- Otherwise, if only this new message is known locally and `message.seq > 1`, `has_more_before` should be `true`.

---

## 6.2 Message Delivered

Persisted domain event.

```json
{
  "type": "event",
  "eventSeq": 1052,
  "eventType": "message.delivered",
  "payload": {
    "conversationId": "conversation-id",
    "userId": "peer-user-id",
    "deliveredSeq": 101
  }
}
```

### Client Handling

If `userId` is the peer, update:

```text
LOCAL_CONVERSATIONS.peer_delivered_seq
```

If `userId` is the current user, this may be used to sync current user's other devices.

---

## 6.3 Message Read

Persisted domain event.

```json
{
  "type": "event",
  "eventSeq": 1053,
  "eventType": "message.read",
  "payload": {
    "conversationId": "conversation-id",
    "userId": "peer-user-id",
    "readSeq": 101
  }
}
```

### Client Handling

If `userId` is the peer, update:

```text
LOCAL_CONVERSATIONS.peer_read_seq
```

If `userId` is the current user, update:

```text
LOCAL_CONVERSATIONS.unread_count = 0
```

---

## 6.4 Presence Snapshot

Ephemeral event.

```json
{
  "type": "presence.snapshot",
  "requestId": "client-request-id",
  "payload": {
    "items": [
      {
        "userId": "user-id",
        "online": true,
        "lastSeenAt": null
      }
    ]
  }
}
```

### Client Handling

Store in memory:

```text
presenceStore
```

Do not write to local DB.

---

## 6.5 Presence Changed

Ephemeral event.

```json
{
  "type": "presence.changed",
  "payload": {
    "userId": "user-id",
    "online": false,
    "lastSeenAt": "2026-05-13T10:30:00Z"
  }
}
```

### Client Handling

Update in-memory `presenceStore`.

---

## 6.6 Typing Started

Ephemeral event.

```json
{
  "type": "typing.started",
  "payload": {
    "conversationId": "conversation-id",
    "userId": "peer-user-id"
  }
}
```

### Client Handling

Show typing animation if the user is viewing that conversation.

---

## 6.7 Typing Stopped

Ephemeral event.

```json
{
  "type": "typing.stopped",
  "payload": {
    "conversationId": "conversation-id",
    "userId": "peer-user-id"
  }
}
```

### Client Handling

Hide typing animation.

---

## 6.8 Device Registered

Ephemeral event.

```json
{
  "type": "device.registered",
  "requestId": "client-request-id",
  "payload": {
    "deviceId": "device-uuid"
  }
}
```

### Client Handling

Mark the local `RegisterDevice` outbox command as done.

---

## 7. Common Error Codes

| Code                   | Meaning                                                          |
| ---------------------- | ---------------------------------------------------------------- |
| `UNAUTHORIZED`         | Missing, invalid, expired, or revoked authentication.            |
| `FORBIDDEN`            | User is authenticated but not allowed to do this action.         |
| `VALIDATION_ERROR`     | Request or command payload is invalid.                           |
| `EMAIL_ALREADY_EXISTS` | Email is already used during registration.                       |
| `INVALID_CREDENTIALS`  | Login email or password is incorrect.                            |
| `NOT_FOUND`            | Requested user, device, conversation, or resource not found.     |
| `EVENT_CURSOR_EXPIRED` | Server can no longer replay events after the requested sequence. |
| `MESSAGE_TOO_LARGE`    | Message body exceeds allowed size.                               |
| `RATE_LIMITED`         | Client sends too many requests or commands.                      |
| `INTERNAL_ERROR`       | Unexpected server error.                                         |
