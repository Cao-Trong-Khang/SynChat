create table users (
    id uuid primary key,
    email varchar(255) not null,
    display_name varchar(120) not null,
    avatar_url text null,
    last_seen_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (email)
);

create table user_credentials (
    user_id uuid primary key references users(id) on delete cascade,
    password_hash varchar(255) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
