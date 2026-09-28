CREATE TABLE users (
    id INTEGER PRIMARY KEY CHECK (id > 0),
    username TEXT NOT NULL UNIQUE CHECK (username <> ''),
    display_name TEXT NOT NULL CHECK (display_name <> ''),
    password_hash TEXT NOT NULL CHECK (password_hash <> ''),
    role TEXT NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    active INTEGER NOT NULL CHECK (active IN (0, 1)),
    created_at TEXT NOT NULL CHECK (created_at <> ''),
    updated_at TEXT NOT NULL CHECK (updated_at <> '')
) STRICT;

CREATE TABLE examination_results_v2 (
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
    ),
    FOREIGN KEY (user_id) REFERENCES users(id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
) STRICT;

INSERT INTO examination_results_v2 (
    id,
    session_id,
    user_id,
    assessment_source,
    assessment_id,
    assessment_version,
    started_at,
    completed_at,
    correct_count,
    total_count
)
SELECT
    id,
    session_id,
    user_id,
    assessment_source,
    assessment_id,
    assessment_version,
    started_at,
    completed_at,
    correct_count,
    total_count
FROM examination_results;

DROP TABLE examination_results;

ALTER TABLE examination_results_v2 RENAME TO examination_results;

CREATE INDEX examination_results_user_completed_idx
    ON examination_results(user_id, completed_at DESC, id DESC);
