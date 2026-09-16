ALTER TABLE catalogue_metadata ADD COLUMN review_schema_version INTEGER NOT NULL DEFAULT 1 CHECK (
    review_schema_version = 1
);
ALTER TABLE catalogue_metadata ADD COLUMN review_decision_count INTEGER NOT NULL DEFAULT 0 CHECK (
    review_decision_count >= 0
);
ALTER TABLE catalogue_metadata ADD COLUMN review_source_checksum_sha256 TEXT NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000' CHECK (
    length(review_source_checksum_sha256) = 64
    AND review_source_checksum_sha256 NOT GLOB '*[^0-9a-f]*'
);

ALTER TABLE entities ADD COLUMN manifest_review_status TEXT NOT NULL DEFAULT 'NEEDS_REVIEW' CHECK (
    manifest_review_status IN ('VERIFIED', 'AUTO_CLASSIFIED', 'NEEDS_REVIEW', 'EXCLUDED')
);
UPDATE entities SET manifest_review_status = review_status;
UPDATE entities SET review_status = CASE manifest_review_status
    WHEN 'EXCLUDED' THEN 'EXCLUDED'
    WHEN 'AUTO_CLASSIFIED' THEN 'AUTO_CLASSIFIED'
    ELSE 'NEEDS_REVIEW'
END;

ALTER TABLE documents ADD COLUMN manifest_review_status TEXT NOT NULL DEFAULT 'NEEDS_REVIEW' CHECK (
    manifest_review_status IN ('VERIFIED', 'AUTO_CLASSIFIED', 'NEEDS_REVIEW', 'EXCLUDED')
);
ALTER TABLE documents ADD COLUMN manifest_quiz_eligible INTEGER NOT NULL DEFAULT 0 CHECK (
    manifest_quiz_eligible IN (0, 1)
);
UPDATE documents SET manifest_review_status = review_status, manifest_quiz_eligible = quiz_eligible;
UPDATE documents SET review_status = CASE manifest_review_status
    WHEN 'EXCLUDED' THEN 'EXCLUDED'
    WHEN 'AUTO_CLASSIFIED' THEN 'AUTO_CLASSIFIED'
    ELSE 'NEEDS_REVIEW'
END, quiz_eligible = 0;

ALTER TABLE passport_images ADD COLUMN manifest_review_status TEXT NOT NULL DEFAULT 'NEEDS_REVIEW' CHECK (
    manifest_review_status IN ('VERIFIED', 'AUTO_CLASSIFIED', 'NEEDS_REVIEW', 'EXCLUDED')
);
UPDATE passport_images SET manifest_review_status = review_status;
UPDATE passport_images SET review_status = CASE manifest_review_status
    WHEN 'EXCLUDED' THEN 'EXCLUDED'
    WHEN 'AUTO_CLASSIFIED' THEN 'AUTO_CLASSIFIED'
    ELSE 'NEEDS_REVIEW'
END;

CREATE TABLE review_decisions (
    record_type TEXT NOT NULL CHECK (record_type IN ('REVIEW', 'QUIZ_ACTIVATION')),
    target_type TEXT NOT NULL CHECK (target_type IN ('ENTITY', 'DOCUMENT', 'IMAGE')),
    target_id TEXT NOT NULL CHECK (target_id <> ''),
    target_fingerprint_sha256 TEXT NOT NULL CHECK (
        length(target_fingerprint_sha256) = 64
        AND target_fingerprint_sha256 NOT GLOB '*[^0-9a-f]*'
    ),
    decision TEXT NOT NULL CHECK (decision IN (
        'VERIFIED', 'NEEDS_REVIEW', 'EXCLUDED', 'ENABLED', 'DISABLED'
    )),
    reviewer TEXT NOT NULL CHECK (reviewer <> ''),
    decided_at TEXT NOT NULL CHECK (decided_at <> ''),
    note TEXT NOT NULL,
    PRIMARY KEY (record_type, target_type, target_id),
    CHECK (
        (record_type = 'REVIEW' AND decision IN ('VERIFIED', 'NEEDS_REVIEW', 'EXCLUDED'))
        OR (record_type = 'QUIZ_ACTIVATION' AND target_type = 'DOCUMENT'
            AND decision IN ('ENABLED', 'DISABLED'))
    )
) STRICT;

CREATE INDEX review_decisions_by_target
    ON review_decisions(target_type, target_id, record_type);
