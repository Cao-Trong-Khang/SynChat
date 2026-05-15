create table user_event_offsets (
    user_id uuid primary key references users(id) on delete cascade,
    last_event_seq bigint not null default 0,
    updated_at timestamptz not null default now(),
    check (last_event_seq >= 0)
);

create table user_events (
    user_id uuid not null references users(id) on delete cascade,
    event_seq bigint not null,
    event_type varchar(100) not null,
    payload jsonb not null,
    created_at timestamptz not null default now(),
    primary key (user_id, event_seq),
    check (event_seq > 0)
);

create index idx_user_events_user_seq on user_events(user_id, event_seq);
