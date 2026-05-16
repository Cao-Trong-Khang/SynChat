-- Manual seed example for testing conversation APIs before message sending exists.
-- This file is not executed automatically and is not part of Flyway migrations.
--
-- Replace these placeholder UUIDs before running:
--   CURRENT_USER_ID
--   PEER_USER_ID
--   CONVERSATION_ID
--   MESSAGE_ID
--   CLIENT_MESSAGE_ID
--
-- Assumes users already exist in users:
--   CURRENT_USER_ID
--   PEER_USER_ID

begin;

insert into conversations (
    id,
    direct_key,
    last_message_seq,
    last_message_at,
    created_at,
    updated_at
) values (
    'CONVERSATION_ID',
    'direct:CURRENT_USER_ID:PEER_USER_ID',
    0,
    null,
    now(),
    now()
);

insert into conversation_members (
    conversation_id,
    user_id,
    read_seq,
    delivered_seq,
    unread_count,
    created_at,
    updated_at
) values
    (
        'CONVERSATION_ID',
        'CURRENT_USER_ID',
        1,
        1,
        0,
        now(),
        now()
    ),
    (
        'CONVERSATION_ID',
        'PEER_USER_ID',
        0,
        0,
        1,
        now(),
        now()
    );

insert into messages (
    id,
    conversation_id,
    seq,
    sender_id,
    client_message_id,
    body,
    created_at_server
) values (
    'MESSAGE_ID',
    'CONVERSATION_ID',
    1,
    'CURRENT_USER_ID',
    'CLIENT_MESSAGE_ID',
    'Hello from the manual seed script.',
    now()
);

update conversations
set last_message_seq = 1,
    last_message_at = (
        select created_at_server
        from messages
        where id = 'MESSAGE_ID'
    ),
    updated_at = now()
where id = 'CONVERSATION_ID';

commit;

-- Verification queries

select *
from conversations
where id = 'CONVERSATION_ID';

select *
from conversation_members
where conversation_id = 'CONVERSATION_ID'
order by user_id;

select *
from messages
where conversation_id = 'CONVERSATION_ID'
order by seq;
