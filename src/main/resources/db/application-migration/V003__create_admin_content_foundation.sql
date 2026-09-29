CREATE TABLE questions (
    question_id TEXT NOT NULL PRIMARY KEY CHECK (question_id <> ''),
    lifecycle_state TEXT NOT NULL CHECK (
        lifecycle_state IN ('DRAFT', 'ACTIVE', 'ARCHIVED')
    ),
    created_by INTEGER NOT NULL CHECK (created_by > 0),
    created_at TEXT NOT NULL CHECK (created_at <> ''),
    updated_at TEXT NOT NULL CHECK (updated_at <> ''),
    FOREIGN KEY (created_by) REFERENCES users(id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
) STRICT;

CREATE TABLE media_metadata (
    media_id TEXT NOT NULL PRIMARY KEY CHECK (media_id <> ''),
    reference_type TEXT NOT NULL CHECK (
        reference_type IN ('UPLOADED_FILE', 'PASSPORT_CATALOGUE')
    ),
    reference_value TEXT NOT NULL CHECK (reference_value <> ''),
    mime_type TEXT NOT NULL CHECK (mime_type <> ''),
    size_bytes INTEGER CHECK (size_bytes IS NULL OR size_bytes >= 0),
    sha256 TEXT CHECK (
        sha256 IS NULL
        OR (
            length(sha256) = 64
            AND sha256 NOT GLOB '*[^0-9a-f]*'
        )
    ),
    created_by INTEGER NOT NULL CHECK (created_by > 0),
    created_at TEXT NOT NULL CHECK (created_at <> ''),
    updated_at TEXT NOT NULL CHECK (updated_at <> ''),
    archived INTEGER NOT NULL CHECK (archived IN (0, 1)),
    FOREIGN KEY (created_by) REFERENCES users(id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
) STRICT;

CREATE TABLE assessments (
    assessment_id TEXT NOT NULL PRIMARY KEY CHECK (assessment_id <> ''),
    state TEXT NOT NULL CHECK (
        state IN ('DRAFT', 'PUBLISHED', 'UNPUBLISHED', 'ARCHIVED')
    ),
    title TEXT NOT NULL CHECK (title <> ''),
    description TEXT CHECK (description IS NULL OR description <> ''),
    created_by INTEGER NOT NULL CHECK (created_by > 0),
    created_at TEXT NOT NULL CHECK (created_at <> ''),
    updated_at TEXT NOT NULL CHECK (updated_at <> ''),
    FOREIGN KEY (created_by) REFERENCES users(id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
) STRICT;

CREATE TABLE question_versions (
    version_id TEXT NOT NULL PRIMARY KEY CHECK (version_id <> ''),
    question_id TEXT NOT NULL CHECK (question_id <> ''),
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    question_type TEXT NOT NULL CHECK (
        question_type IN ('PASSPORT_LINKED', 'THEORETICAL', 'IMAGE_BASED')
    ),
    prompt TEXT NOT NULL CHECK (prompt <> ''),
    instructions TEXT CHECK (instructions IS NULL OR instructions <> ''),
    explanation TEXT CHECK (explanation IS NULL OR explanation <> ''),
    difficulty TEXT CHECK (difficulty IS NULL OR difficulty <> ''),
    created_by INTEGER NOT NULL CHECK (created_by > 0),
    created_at TEXT NOT NULL CHECK (created_at <> ''),
    UNIQUE (question_id, version_number),
    FOREIGN KEY (question_id) REFERENCES questions(question_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    FOREIGN KEY (created_by) REFERENCES users(id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
) STRICT;

CREATE TABLE question_options (
    option_id TEXT NOT NULL CHECK (option_id <> ''),
    version_id TEXT NOT NULL CHECK (version_id <> ''),
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    option_text TEXT NOT NULL CHECK (option_text <> ''),
    correct INTEGER NOT NULL CHECK (correct IN (0, 1)),
    PRIMARY KEY (version_id, option_id),
    UNIQUE (version_id, display_order),
    FOREIGN KEY (version_id) REFERENCES question_versions(version_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
) STRICT;

CREATE UNIQUE INDEX question_options_one_correct_idx
    ON question_options(version_id)
    WHERE correct = 1;
