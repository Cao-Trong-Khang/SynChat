create table conversations (
    id uuid primary key,
    direct_key varchar(255) not null,
    last_message_seq bigint not null default 0,
    last_message_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (direct_key)
);

create table conversation_members (
    conversation_id uuid not null references conversations(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    read_seq bigint not null default 0,
    delivered_seq bigint not null default 0,
    unread_count int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (conversation_id, user_id),
    check (read_seq >= 0),
    check (delivered_seq >= 0),
    check (unread_count >= 0)
);

create index idx_conversation_members_user_id on conversation_members(user_id);
