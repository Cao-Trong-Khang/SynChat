create table auth_sessions (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    device_id uuid not null references devices(id) on delete cascade,
    refresh_token_hash text not null,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    revoked_at timestamptz null
);

create index idx_auth_sessions_user_id on auth_sessions(user_id);

create index idx_auth_sessions_device_id on auth_sessions(device_id);
