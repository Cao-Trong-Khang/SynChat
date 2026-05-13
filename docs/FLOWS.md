# Chat System Flows

## Table of Contents

1. [First App Open on a New Platform](#1-first-app-open-on-a-new-platform)
2. [App Reopens After Offline Time](#2-app-reopens-after-offline-time)
3. [Load More Conversations](#3-load-more-conversations)
4. [Search User and Open Virtual Conversation](#4-search-user-and-open-virtual-conversation)
5. [Open Conversation Detail](#5-open-conversation-detail)
6. [Load Older Messages](#6-load-older-messages)
7. [Send Message](#7-send-message)
8. [Delivered Receipt](#8-delivered-receipt)
9. [Read Receipt and Multi-Device Consistency](#9-read-receipt-and-multi-device-consistency)
10. [Push Notification for Offline or Background Recipient](#10-push-notification-for-offline-or-background-recipient)
11. [WebSocket Event Gap Handling](#11-websocket-event-gap-handling)
12. [Send Failure and Retry](#12-send-failure-and-retry)
13. [Device Registration / Push Token Refresh](#13-device-registration--push-token-refresh)
14. [Presence and Last Seen](#14-presence-and-last-seen)
15. [Typing Indicator](#15-typing-indicator)

---

## 1. First App Open on a New Platform

### Diagram

![First App Open on a New Platform](diagrams/flows/first-open.svg)

### Description

- This flow runs when the user opens the app on a platform that has no local data yet. The client does not download all old data. It only asks the server for the latest conversation metadata page, for example 20 conversations.

- The server returns conversation metadata, a `serverEventSeq`, a `nextCursor`, and a `hasMore` flag. The client stores them in `LOCAL_USERS`, `LOCAL_CONVERSATIONS`, `LOCAL_MESSAGE_SYNC_STATE`, and `LOCAL_SYNC_STATE`.

- Messages are not loaded yet. They are loaded later when the user opens a conversation.

---

## 2. App Reopens After Offline Time

### Diagram

![App Reopens After Offline Time](diagrams/flows/reopen-offline.svg)

### Description

- This flow is used when the client already has local data but may have missed events while offline.

- The client sends `last_handled_event_seq` from `LOCAL_SYNC_STATE`. If the server still has the events, it returns them sorted by `event_seq`. The client applies each event in order and updates the local checkpoint only after the event is saved locally.

- If the event checkpoint is too old, the server returns `EVENT_CURSOR_EXPIRED`. The client clears cache tables and starts again with the first open sync.

---

## 3. Load More Conversations

### Diagram

![Load More Conversations](diagrams/flows/load-conversations.svg)

### Description

- This flow loads older conversations when the user scrolls down the conversation list.

- The client uses `next_conversation_cursor` and `has_more_conversations` from `LOCAL_SYNC_STATE`. The server uses keyset pagination, not offset pagination, because conversations can move to the top when new messages arrive.

- The response updates `LOCAL_USERS`, `LOCAL_CONVERSATIONS`, `LOCAL_MESSAGE_SYNC_STATE`, and `LOCAL_SYNC_STATE`.

---

## 4. Search User and Open Virtual Conversation

### Diagram

![Search User and Open Virtual Conversation](diagrams/flows/search-virtual.svg)

### Description

- The app has no friend system, so search can return users from the database.

- If the selected user already has a conversation, the client opens it. If not, the client creates a virtual local conversation with `LOCAL_CONVERSATIONS.server_id = null`. The server conversation is created only when the first message is sent.

---

## 5. Open Conversation Detail

### Diagram

![Open Conversation Detail](diagrams/flows/open-chat.svg)

### Description

- The UI always renders from the local database first.

- If the conversation has `server_id = null`, it is virtual, so there is no server message history to load. If `LOCAL_MESSAGE_SYNC_STATE.newest_loaded_seq` is null, the client loads the latest messages from the server and updates the message range.

---

## 6. Load Older Messages

### Diagram

![Load Older Messages](diagrams/flows/load-messages.svg)

### Description

- This flow loads old messages only when needed. This flow only runs when `oldest_loaded_seq` is not null. If it is null, the client must load the latest messages first in the Open Conversation Detail flow.

- The client uses `LOCAL_MESSAGE_SYNC_STATE.oldest_loaded_seq` as `beforeSeq`. If `has_more_before` is false, the client does not call the server again.

---

## 7. Send Message

### Diagram

![Send Message](diagrams/flows/send-message.svg)

### Description

- The client first inserts a local message with `local_status = sending`. This gives instant UI feedback.

- If the conversation already exists, the client sends `conversationId`. If it is virtual, the client sends `recipientId`. The server creates or finds the direct conversation, inserts the message into `MESSAGES`, updates `CONVERSATIONS`, updates `CONVERSATION_MEMBERS`, inserts `USER_EVENTS`, and inserts `OUTBOX_EVENTS`.

- The ACK contains the server conversation id, message sequence, server time, and `client_message_id`. If the ACK is lost and the client retries with the same client_message_id, the server must return the existing message instead of inserting a duplicate.

---

## 8. Delivered Receipt

### Diagram

![Delivered Receipt](diagrams/flows/delivered-receipt.svg)

### Description

- Delivered means that at least one recipient device received and saved the message locally.

- If the conversation is not cached yet, Client B creates the local conversation and its message sync state from the `MessageCreated` event before saving the message.

- After Client B saves the message, it sends `DeliveredUpTo` with `delivered_seq`. The server updates `CONVERSATION_MEMBERS.delivered_seq` for User B and emits an event so User A can update `LOCAL_CONVERSATIONS.peer_delivered_seq`.

---

## 9. Read Receipt and Multi-Device Consistency

### Diagram

![Read Receipt and Multi-Device Consistency](diagrams/flows/read-receipt.svg)

### Description

- Read means the user actually viewed the conversation in foreground.

- The server updates `CONVERSATION_MEMBERS.read_seq` and sets `unread_count = 0` for the reader. The read event is sent to all devices of the same user, so all devices clear unread state. It is also sent to the peer, so the peer can show `seen` when `peer_read_seq` reaches the sent message sequence.

---

## 10. Push Notification for Offline or Background Recipient

### Diagram

![Push Notification for Offline or Background Recipient](diagrams/flows/push-notification.svg)

### Description

- Push notification is decided per device, not only per user.

- Device state such as foreground, background, offline, and active conversation is stored in Redis. Durable push data is stored in `DEVICES`. If the recipient device is background or offline, the worker sends a push notification using `push_token` and `push_provider`.

## 11. WebSocket Event Gap Handling

### Diagram

![WebSocket Event Gap Handling](diagrams/flows/ws-event-gap.svg)

### Description

- This flow runs when the client receives realtime events from WebSocket.

- The client checks `event_seq` before applying an event. If the event is old or duplicated, it is ignored. If the client detects a gap, for example it expects `6` but receives `7`, it stops applying realtime events and calls `/sync/events?afterSeq=last_handled_event_seq`.

- The checkpoint is stored in `LOCAL_SYNC_STATE`. It should only be updated after the event is saved into the local database.

---

## 12. Send Failure and Retry

### Diagram

![Send Failure and Retry](diagrams/flows/send-retry.svg)

### Description

- This flow handles send timeout, network error, and retry.

- When the user sends a message, the client first inserts a local message with `LOCAL_MESSAGES.local_status = sending`. If sending fails, the client changes it to `failed`.

- Retry must use the same `client_message_id`. The server checks `MESSAGES` by `sender_id` and `client_message_id`. If the message already exists, the server returns the existing message instead of inserting a duplicate.

- Only messages with `LOCAL_MESSAGES.local_status = failed` can be retried or deleted locally.

---

## 13. Device Registration / Push Token Refresh

### Diagram

![Device Registration / Push Token Refresh](diagrams/flows/device-register.svg)

### Description

- This flow runs after login, app open, or push token refresh.

- The client sends `deviceId`, `platform`, `push_token`, and `push_provider` to the server. The server upserts this data into `DEVICES`.

- This data is needed for push notification when a device is offline or in background. If WebSocket is not available, the client stores a `RegisterDevice` command in `LOCAL_OUTBOX_COMMANDS` and sends it later.

---

## 14. Presence and Last Seen

### Diagram

![Presence and Last Seen](diagrams/flows/presence-last-seen.svg)

### Description

- Presence state is temporary, so it is stored in Redis and kept in client memory, not in PostgreSQL or local DB.

- When a device connects, the server marks it online in Redis. When the last device of a user disconnects, the server updates `USERS.last_seen_at`.

- The client subscribes to presence for users that are currently visible, such as peers in the loaded conversation list or the opened chat detail. The server returns a presence snapshot and later sends `PresenceChanged` events only to subscribed clients.

- The UI uses `LOCAL_USERS` for avatar, name, and email. It uses the in-memory presence state to show the green online dot or last seen text.

---

## 15. Typing Indicator

### Diagram

![Typing Indicator](diagrams/flows/typing-indicator.svg)

### Description

- Typing is temporary and should not be stored in PostgreSQL or local DB.

- When a user is typing, the client sends `typing.start` through WebSocket. The server stores a typing key in Redis with a short TTL and sends `TypingStarted` to the peer.

- The client should throttle `typing.start`, for example send it every few seconds while the user is typing. When the TTL expires or the user stops typing, the peer receives `TypingStopped`.
