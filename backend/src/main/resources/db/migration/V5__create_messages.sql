create table messages (
    id uuid primary key,
    conversation_id uuid not null references conversations(id) on delete cascade,
    seq bigint not null,
    sender_id uuid not null references users(id),
    client_message_id uuid not null,
    body text not null,
    created_at_server timestamptz not null default now(),
    check (seq > 0),
    unique (conversation_id, seq),
    unique (sender_id, client_message_id)
);

create index idx_messages_conversation_seq on messages(conversation_id, seq);

create index idx_messages_sender_id on messages(sender_id);
