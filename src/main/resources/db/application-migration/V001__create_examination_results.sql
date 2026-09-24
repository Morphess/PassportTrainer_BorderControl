CREATE TABLE application_schema_migrations (
    version INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    checksum_sha256 TEXT NOT NULL CHECK (
        length(checksum_sha256) = 64
        AND checksum_sha256 NOT GLOB '*[^0-9a-f]*'
    )
) STRICT;

CREATE TABLE examination_results (
    id INTEGER PRIMARY KEY CHECK (id > 0),
    session_id INTEGER NOT NULL UNIQUE CHECK (session_id > 0),
    user_id INTEGER CHECK (user_id IS NULL OR user_id > 0),
    assessment_source TEXT NOT NULL CHECK (
        assessment_source IN ('GENERATED', 'AUTHORED', 'HYBRID')
    ),
    assessment_id TEXT,
    assessment_version TEXT,
    started_at TEXT NOT NULL CHECK (started_at <> ''),
    completed_at TEXT NOT NULL CHECK (completed_at <> ''),
    correct_count INTEGER NOT NULL CHECK (correct_count >= 0),
    total_count INTEGER NOT NULL CHECK (
        total_count > 0 AND correct_count <= total_count
    ),
    CHECK (
        (assessment_id IS NULL AND assessment_version IS NULL)
        OR (
            assessment_id IS NOT NULL AND assessment_id <> ''
            AND assessment_version IS NOT NULL AND assessment_version <> ''
        )
    ),
    CHECK (
        assessment_source = 'GENERATED'
        OR (assessment_id IS NOT NULL AND assessment_version IS NOT NULL)
    )
) STRICT;

CREATE INDEX examination_results_user_completed_idx
    ON examination_results(user_id, completed_at DESC, id DESC);
