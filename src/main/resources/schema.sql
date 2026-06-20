CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS tenant (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    config TEXT
);

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
