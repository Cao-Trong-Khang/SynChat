create table devices (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    platform varchar(50) not null,
    push_token text null,
    push_provider varchar(50) null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_devices_user_id on devices(user_id);
