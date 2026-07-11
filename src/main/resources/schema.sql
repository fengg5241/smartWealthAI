CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS tenant (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    config TEXT,
    tenant_group VARCHAR(20) NOT NULL DEFAULT 'enterprise'
);
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS tenant_group VARCHAR(20) NOT NULL DEFAULT 'enterprise';
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS subscription_plan VARCHAR(20);
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(100);
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS stripe_subscription_id VARCHAR(100);
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS subscription_expiry DATE;
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS trial_ends_at DATE;
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS reset_token VARCHAR(100);
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS reset_token_expiry TIMESTAMP;

CREATE TABLE IF NOT EXISTS enterprise_document (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    file_name VARCHAR(255),
    file_type VARCHAR(20),
    chunk_text TEXT,
    chunk_index INTEGER,
    chunk_metadata TEXT,
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ed_tenant_id ON enterprise_document(tenant_id);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50),
    question TEXT,
    answer TEXT,
    sources TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_al_tenant_id ON audit_log(tenant_id);

CREATE TABLE IF NOT EXISTS product_image (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    category VARCHAR(100),
    description TEXT,
    image_path VARCHAR(500),
    image_content_type VARCHAR(50),
    vector_id VARCHAR(100),
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pi_tenant_id ON product_image(tenant_id);
CREATE INDEX IF NOT EXISTS idx_pi_category ON product_image(tenant_id, category);

CREATE TABLE IF NOT EXISTS vector_store_image (
    id VARCHAR(100) PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding vector(1024)
);

CREATE INDEX IF NOT EXISTS idx_vsi_embedding ON vector_store_image USING hnsw (embedding vector_cosine_ops);

-- IM bot tenant mapping (Slack / WeCom → tenant)
CREATE TABLE IF NOT EXISTS im_tenant_mapping (
    id BIGSERIAL PRIMARY KEY,
    platform VARCHAR(20) NOT NULL,
    platform_team_id VARCHAR(200) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    bot_token VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(platform, platform_team_id)
);

-- OAuth tokens for document sync (Google Drive / OneDrive)
CREATE TABLE IF NOT EXISTS sync_auth_token (
    id BIGSERIAL PRIMARY KEY,
    platform VARCHAR(20) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    access_token TEXT,
    refresh_token TEXT,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(platform, tenant_id)
);

-- ============================================================
-- Learning Assistant tables (错题库 + 好词好句库 + 复习系统)
-- ============================================================

-- Notebook (错题本 / 好句分组)
CREATE TABLE IF NOT EXISTS notebook (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    notebook_type VARCHAR(20) DEFAULT 'mistake',  -- mistake / phrase
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_nb_tenant ON notebook(tenant_id);
CREATE INDEX IF NOT EXISTS idx_nb_tenant_type ON notebook(tenant_id, notebook_type);

-- Mistake question (错题)
CREATE TABLE IF NOT EXISTS mistake_question (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    notebook_id BIGINT,
    subject VARCHAR(30),
    question_type VARCHAR(60),
    grade_level VARCHAR(20),
    content TEXT,
    correct_answer TEXT,
    error_reason VARCHAR(100),
    source VARCHAR(255),
    mastery_level VARCHAR(20) DEFAULT '不熟悉',
    orig_image VARCHAR(500),
    clean_image VARCHAR(500),
    handwrite_removed BOOLEAN DEFAULT FALSE,
    vector_id VARCHAR(100),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_mq_tenant ON mistake_question(tenant_id);
CREATE INDEX IF NOT EXISTS idx_mq_notebook ON mistake_question(tenant_id, notebook_id);
CREATE INDEX IF NOT EXISTS idx_mq_subject_type ON mistake_question(tenant_id, subject, question_type);
CREATE INDEX IF NOT EXISTS idx_mq_mastery ON mistake_question(tenant_id, mastery_level);
CREATE INDEX IF NOT EXISTS idx_mq_grade ON mistake_question(tenant_id, grade_level);
CREATE INDEX IF NOT EXISTS idx_mq_source ON mistake_question(tenant_id, source);

-- Good phrase (好词好句)
CREATE TABLE IF NOT EXISTS good_phrase (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    notebook_id BIGINT,
    content TEXT NOT NULL,
    source VARCHAR(255),
    theme VARCHAR(50),
    emotion VARCHAR(30),
    usage_type VARCHAR(50),
    tags VARCHAR(255),
    mastery_level VARCHAR(20) DEFAULT '不熟悉',
    entry_method VARCHAR(20) DEFAULT 'photo',
    image_path VARCHAR(500),
    vector_id VARCHAR(100),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gp_tenant ON good_phrase(tenant_id);
CREATE INDEX IF NOT EXISTS idx_gp_notebook ON good_phrase(tenant_id, notebook_id);
CREATE INDEX IF NOT EXISTS idx_gp_theme ON good_phrase(tenant_id, theme);
CREATE INDEX IF NOT EXISTS idx_gp_mastery ON good_phrase(tenant_id, mastery_level);
ALTER TABLE good_phrase ADD COLUMN IF NOT EXISTS language VARCHAR(10) NOT NULL DEFAULT 'zh';
CREATE INDEX IF NOT EXISTS idx_gp_language ON good_phrase(tenant_id, language);

-- Review schedule (SM-2 algorithm)
CREATE TABLE IF NOT EXISTS review_schedule (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    mistake_id BIGINT REFERENCES mistake_question(id) ON DELETE CASCADE,
    phrase_id BIGINT,
    review_stage INTEGER DEFAULT 1,
    ease_factor DOUBLE PRECISION DEFAULT 2.5,
    interval_days INTEGER DEFAULT 1,
    next_review_date DATE NOT NULL,
    last_reviewed TIMESTAMP,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_rs_tenant ON review_schedule(tenant_id);
CREATE INDEX IF NOT EXISTS idx_rs_next_date ON review_schedule(tenant_id, next_review_date);
CREATE INDEX IF NOT EXISTS idx_rs_mistake ON review_schedule(mistake_id);
ALTER TABLE review_schedule ADD COLUMN IF NOT EXISTS phrase_id BIGINT;

-- Sync status for individual files
CREATE TABLE IF NOT EXISTS sync_file_status (
    id BIGSERIAL PRIMARY KEY,
    platform VARCHAR(20) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    file_id VARCHAR(255) NOT NULL,
    file_name VARCHAR(500),
    last_modified TIMESTAMP,
    sync_status VARCHAR(20) DEFAULT 'pending',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(platform, tenant_id, file_id)
);

-- ============================================================
-- WhatsApp AI Chatbot tables
-- ============================================================

-- WhatsApp customer profiles
CREATE TABLE IF NOT EXISTS wa_customers (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    wa_phone VARCHAR(30) NOT NULL,
    display_name VARCHAR(200),
    tags VARCHAR(500),
    budget VARCHAR(100),
    requirement TEXT,
    source VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, wa_phone)
);
CREATE INDEX IF NOT EXISTS idx_wc_tenant ON wa_customers(tenant_id);

-- WhatsApp conversations (state machine: ai_active / pending_human / human_assigned / closed)
CREATE TABLE IF NOT EXISTS wa_conversations (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES wa_customers(id),
    status VARCHAR(20) DEFAULT 'ai_active',
    assigned_agent VARCHAR(100),
    last_customer_message_at TIMESTAMP,
    unread_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE wa_conversations ADD COLUMN IF NOT EXISTS last_customer_message_at TIMESTAMP;
ALTER TABLE wa_conversations ALTER COLUMN status SET DEFAULT 'ai_active';
CREATE INDEX IF NOT EXISTS idx_wconv_tenant ON wa_conversations(tenant_id);
CREATE INDEX IF NOT EXISTS idx_wconv_customer ON wa_conversations(customer_id);
CREATE INDEX IF NOT EXISTS idx_wconv_status ON wa_conversations(tenant_id, status);

-- WhatsApp messages (with idempotency via provider_message_id)
CREATE TABLE IF NOT EXISTS wa_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES wa_conversations(id),
    direction VARCHAR(10) NOT NULL,
    sender_type VARCHAR(20),
    message_type VARCHAR(20) DEFAULT 'session',
    provider_message_id VARCHAR(100),
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE wa_messages ADD COLUMN IF NOT EXISTS message_type VARCHAR(20) DEFAULT 'session';
ALTER TABLE wa_messages ADD COLUMN IF NOT EXISTS provider_message_id VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_wm_conv ON wa_messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_wm_created ON wa_messages(conversation_id, created_at);
CREATE UNIQUE INDEX IF NOT EXISTS idx_wm_provider_msg ON wa_messages(provider_message_id) WHERE provider_message_id IS NOT NULL;

-- Usage log (for billing, rate limiting, reconciliation)
CREATE TABLE IF NOT EXISTS wa_usage_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    conversation_id BIGINT,
    direction VARCHAR(10) NOT NULL,
    message_type VARCHAR(20) DEFAULT 'session',
    twilio_message_sid VARCHAR(100),
    provider_message_id VARCHAR(100),
    cost DECIMAL(10, 4) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_wul_tenant_date ON wa_usage_log(tenant_id, created_at);

-- im_tenant_mapping: add is_active for soft-disable
ALTER TABLE im_tenant_mapping ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;
