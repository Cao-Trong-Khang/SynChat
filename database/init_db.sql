-- 1. Enable extension for fuzzy text search (Full-text search) on usernames
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. Create users table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Optimization: Create GIN index to make LIKE '%name%' searches very fast (FR-06)
CREATE INDEX idx_users_display_name_trgm ON users USING GIN (display_name gin_trgm_ops);

-- 3. Create chat_rooms table
CREATE TABLE chat_rooms (
    id UUID PRIMARY KEY,
    type VARCHAR(50) NOT NULL, -- 'PRIVATE' or 'GROUP'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- Optimization: Denormalization to push latest conversations to the top (FR-05)
    last_message_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_message_preview VARCHAR(255)
);

-- Optimization: Index last_message_at for fast sorting of chat list
CREATE INDEX idx_chat_rooms_last_message_at ON chat_rooms (last_message_at DESC);

-- 4. Create participants table (Many-to-Many)
CREATE TABLE participants (
    chat_room_id UUID REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (chat_room_id, user_id)
);

-- Optimization: Frequently need to get all chat rooms of a user
CREATE INDEX idx_participants_user_id ON participants (user_id);

-- 5. Create messages table
CREATE TABLE messages (
    id UUID PRIMARY KEY,
    chat_room_id UUID REFERENCES chat_rooms(id) ON DELETE CASCADE NOT NULL,
    sender_id UUID REFERENCES users(id) ON DELETE SET NULL, -- Keep messages if user is deleted
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Optimization: Required index to fetch messages of a specific chat room (FR-04)
CREATE INDEX idx_messages_chat_room_id ON messages (chat_room_id);

-- Optimization: Index on time to sort messages by old/new order
CREATE INDEX idx_messages_created_at ON messages (created_at ASC);