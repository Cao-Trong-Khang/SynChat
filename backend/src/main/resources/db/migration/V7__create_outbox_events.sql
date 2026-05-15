create table outbox_events (
    id uuid primary key,
    event_type varchar(100) not null,
    payload jsonb not null,
    status varchar(50) not null,
    created_at timestamptz not null default now()
);

create index idx_outbox_events_status_created_at on outbox_events(status, created_at);
