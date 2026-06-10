CREATE TABLE openapi_credentials (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    access_key TEXT NOT NULL UNIQUE,
    secret_encrypted TEXT NOT NULL,
    secret_fingerprint TEXT NOT NULL,
    scopes_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    expires_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    last_used_at DATETIME,
    last_used_ip TEXT,
    last_used_user_agent TEXT
);

CREATE TABLE openapi_request_nonces (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    access_key TEXT NOT NULL,
    nonce TEXT NOT NULL,
    request_timestamp DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    UNIQUE(access_key, nonce)
);

CREATE INDEX idx_openapi_request_nonces_expires_at
    ON openapi_request_nonces(expires_at);
