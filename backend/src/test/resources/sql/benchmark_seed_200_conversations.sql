-- ==============================================================================
-- SynChat: Benchmark Seed Data Script (200 Conversations, ~3,000 Messages)
-- Target API: GET /api/sync/conversations?limit=50 (N+1 Query Benchmark)
-- ==============================================================================
-- Safety Guard: Only run on local/dev/test databases
-- ==============================================================================

DO $$
BEGIN
    IF current_database() NOT IN ('synchat', 'synchat_test', 'synchat_dev') THEN
        RAISE EXCEPTION 'SAFETY CHECK FAILED: Refusing to run on database "%". Only synchat, synchat_test, synchat_dev are allowed.', current_database();
    END IF;
END $$;

BEGIN;

-- ------------------------------------------------------------------------------
-- 1. Constants & Identifier Scheme
-- ------------------------------------------------------------------------------
-- User A (Primary User):
--   ID:    a0000000-0000-0000-0000-000000000001
--   Email: benchmark.user.a@synchat.test
--   Pass:  Password123!
--   BCrypt Hash (verified with Spring Security BCryptPasswordEncoder):
--          $2a$10$GSniRDjpDgEUSvDg6S1Hv.B8qRHKTAvDYhZuYrFe0aCBTkltOPRsy
--
-- Peer Users (200 peers, i from 1 to 200):
--   ID:    b0000000-0000-0000-0000-<12-digit zero-padded index>
--   Email: benchmark.peer.<001..200>@synchat.test
--
-- Conversations (200 direct conversations):
--   ID:    c0000000-0000-0000-0000-<12-digit zero-padded index>
--   Direct Key: direct:a0000000-0000-0000-0000-000000000001:b0000000-0000-0000-0000-...
--   (Since 'a000...' < 'b000...', lexicographical sorting rule is strictly obeyed)
-- ------------------------------------------------------------------------------

-- 2. Seed Primary User (User A)
INSERT INTO users (
    id,
    email,
    display_name,
    avatar_url,
    last_seen_at,
    created_at,
    updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'benchmark.user.a@synchat.test',
    'Benchmark User A',
    null,
    now(),
    now() - interval '30 days',
    now()
)
ON CONFLICT (id) DO UPDATE
SET email = EXCLUDED.email,
    display_name = EXCLUDED.display_name,
    updated_at = now();

-- Seed User A Credentials (Password123!)
INSERT INTO user_credentials (
    user_id,
    password_hash,
    created_at,
    updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000001'::uuid,
    '$2a$10$GSniRDjpDgEUSvDg6S1Hv.B8qRHKTAvDYhZuYrFe0aCBTkltOPRsy',
    now() - interval '30 days',
    now()
)
ON CONFLICT (user_id) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    updated_at = now();

-- Seed User A Event Offset
INSERT INTO user_event_offsets (
    user_id,
    last_event_seq,
    updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000001'::uuid,
    0,
    now()
)
ON CONFLICT (user_id) DO UPDATE
SET updated_at = now();

-- ------------------------------------------------------------------------------
-- 3. Seed 200 Peer Users
-- ------------------------------------------------------------------------------
INSERT INTO users (
    id,
    email,
    display_name,
    avatar_url,
    last_seen_at,
    created_at,
    updated_at
)
SELECT
    ('b0000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid AS id,
    'benchmark.peer.' || lpad(i::text, 3, '0') || '@synchat.test' AS email,
    'Benchmark Peer ' || lpad(i::text, 3, '0') AS display_name,
    null AS avatar_url,
    now() - (i || ' minutes')::interval AS last_seen_at,
    now() - interval '30 days',
    now()
FROM generate_series(1, 200) AS i
ON CONFLICT (id) DO UPDATE
SET email = EXCLUDED.email,
    display_name = EXCLUDED.display_name,
    updated_at = now();

-- ------------------------------------------------------------------------------
-- 4. Seed 200 Conversations
-- Messages per conversation: 10 + (i % 11) -> 10 to 20 messages.
-- last_message_at distributed from (now - 200 minutes) to (now - 1 minute).
-- ------------------------------------------------------------------------------
INSERT INTO conversations (
    id,
    direct_key,
    last_message_seq,
    last_message_at,
    created_at,
    updated_at
)
SELECT
    ('c0000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid AS id,
    'direct:a0000000-0000-0000-0000-000000000001:' || ('b0000000-0000-0000-0000-' || lpad(i::text, 12, '0')) AS direct_key,
    (10 + (i % 11))::bigint AS last_message_seq,
    now() - ((201 - i) || ' minutes')::interval AS last_message_at,
    now() - interval '30 days',
    now()
FROM generate_series(1, 200) AS i
ON CONFLICT (id) DO UPDATE
SET direct_key = EXCLUDED.direct_key,
    last_message_seq = EXCLUDED.last_message_seq,
    last_message_at = EXCLUDED.last_message_at,
    updated_at = now();

-- ------------------------------------------------------------------------------
-- 5. Seed Conversation Members (2 members per conversation = 400 rows)
-- Required by ConversationMetadataMapper: members.size() == 2
-- ------------------------------------------------------------------------------
-- 5.1 Members for User A
INSERT INTO conversation_members (
    conversation_id,
    user_id,
    read_seq,
    delivered_seq,
    unread_count,
    created_at,
    updated_at
)
SELECT
    ('c0000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid AS conversation_id,
    'a0000000-0000-0000-0000-000000000001'::uuid AS user_id,
    ((10 + (i % 11)) - (i % 4))::bigint AS read_seq,
    (10 + (i % 11))::bigint AS delivered_seq,
    (i % 4) AS unread_count,
    now() - interval '30 days',
    now()
FROM generate_series(1, 200) AS i
ON CONFLICT (conversation_id, user_id) DO UPDATE
SET read_seq = EXCLUDED.read_seq,
    delivered_seq = EXCLUDED.delivered_seq,
    unread_count = EXCLUDED.unread_count,
    updated_at = now();

-- 5.2 Members for Peer Users
INSERT INTO conversation_members (
    conversation_id,
    user_id,
    read_seq,
    delivered_seq,
    unread_count,
    created_at,
    updated_at
)
SELECT
    ('c0000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid AS conversation_id,
    ('b0000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid AS user_id,
    (10 + (i % 11))::bigint AS read_seq,
    (10 + (i % 11))::bigint AS delivered_seq,
    0 AS unread_count,
    now() - interval '30 days',
    now()
FROM generate_series(1, 200) AS i
ON CONFLICT (conversation_id, user_id) DO UPDATE
SET read_seq = EXCLUDED.read_seq,
    delivered_seq = EXCLUDED.delivered_seq,
    unread_count = EXCLUDED.unread_count,
    updated_at = now();

-- ------------------------------------------------------------------------------
-- 6. Seed Messages (10 to 20 messages per conversation, ~3,000 total)
-- Deterministic message ID: md5('benchmark:msg:' || i || ':' || j)::uuid
-- Deterministic client_message_id: md5('benchmark:client:' || i || ':' || j)::uuid
-- Seq: 1 to (10 + (i % 11))
-- Last message (j = seq_max) matches conversation.last_message_at
-- ------------------------------------------------------------------------------
WITH conversation_msg_specs AS (
    SELECT
        i AS conv_index,
        ('c0000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid AS conv_id,
        ('b0000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid AS peer_id,
        (10 + (i % 11)) AS msg_count,
        now() - ((201 - i) || ' minutes')::interval AS last_msg_at
    FROM generate_series(1, 200) AS i
),
expanded_messages AS (
    SELECT
        spec.conv_id,
        spec.peer_id,
        spec.msg_count,
        j AS seq,
        md5('benchmark:msg:' || spec.conv_index || ':' || j)::uuid AS msg_id,
        md5('benchmark:client:' || spec.conv_index || ':' || j)::uuid AS client_msg_id,
        CASE
            WHEN j % 2 = 1 THEN 'a0000000-0000-0000-0000-000000000001'::uuid
            ELSE spec.peer_id
        END AS sender_id,
        '[BENCHMARK] Conversation ' || spec.conv_index || ' Message #' || j || ' - Payload for N+1 benchmark verification.' AS body,
        spec.last_msg_at - ((spec.msg_count - j) * 20 || ' seconds')::interval AS msg_created_at
    FROM conversation_msg_specs spec
    CROSS JOIN LATERAL generate_series(1, spec.msg_count) AS j
)
INSERT INTO messages (
    id,
    conversation_id,
    seq,
    sender_id,
    client_message_id,
    body,
    created_at_server
)
SELECT
    msg_id,
    conv_id,
    seq,
    sender_id,
    client_msg_id,
    body,
    msg_created_at
FROM expanded_messages
ON CONFLICT (conversation_id, seq) DO UPDATE
SET body = EXCLUDED.body,
    created_at_server = EXCLUDED.created_at_server;

COMMIT;

-- ==============================================================================
-- End of Seed Script
-- ==============================================================================
