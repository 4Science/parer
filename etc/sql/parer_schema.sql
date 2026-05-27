-- ============================================================================
-- SCHEMA DB BRIDGE PARER
-- PostgreSQL 13+ / Amazon RDS Aurora
-- ============================================================================

-- ============================================================================
-- sip_package: un job di versamento = un pacchetto SIP
-- ============================================================================

CREATE TABLE IF NOT EXISTS sip_package (
    id                    SERIAL PRIMARY KEY,
    sip_id                VARCHAR(255) NOT NULL UNIQUE,
    parer_id              VARCHAR(255) UNIQUE,

    -- Stato (gestito lato Java)
    status                VARCHAR(50)  NOT NULL,
    status_reason         TEXT,

    -- S3: tutti gli artefatti sotto un prefix comune
    s3_uri                TEXT,

    -- Timestamp
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    submitted_at          TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_sip_package_status     ON sip_package (status);
CREATE INDEX IF NOT EXISTS idx_sip_package_created_at ON sip_package (created_at);

-- ============================================================================
-- sip_file: file associati al pacchetto (original, derivati, annotazioni)
-- ============================================================================

CREATE TABLE IF NOT EXISTS sip_file (
    id                   SERIAL PRIMARY KEY,
    sip_package_id       INTEGER NOT NULL REFERENCES sip_package (id) ON DELETE CASCADE,

    -- Riferimento S3 master
    s3_uri               TEXT,

    -- Metadati file
    file_name            VARCHAR(255) NOT NULL,
    file_size_bytes      BIGINT       NOT NULL,
    mime_type            VARCHAR(100) NOT NULL,

    -- Upload FTP verso ParER (stati gestiti lato Java)
    upload_status        VARCHAR(50)  NOT NULL,

    -- Timestamp
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at              TIMESTAMPTZ,

    remote_file_name     VARCHAR(255),
    checksum             VARCHAR(255),

    CONSTRAINT uk_sip_file_key UNIQUE (sip_package_id, s3_uri)
);

CREATE INDEX IF NOT EXISTS idx_sip_file_sip_package_id ON sip_file (sip_package_id);
CREATE INDEX IF NOT EXISTS idx_sip_file_upload_status  ON sip_file (upload_status);
