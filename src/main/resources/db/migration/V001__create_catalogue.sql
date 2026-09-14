CREATE TABLE schema_migrations (
    version INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    checksum_sha256 TEXT NOT NULL CHECK (
        length(checksum_sha256) = 64
        AND checksum_sha256 NOT GLOB '*[^0-9a-f]*'
    )
) STRICT;

CREATE TABLE catalogue_metadata (
    singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
    manifest_schema_version INTEGER NOT NULL CHECK (manifest_schema_version > 0),
    source_root TEXT NOT NULL CHECK (source_root <> ''),
    continent_count INTEGER NOT NULL CHECK (continent_count >= 0),
    entity_count INTEGER NOT NULL CHECK (entity_count >= 0),
    document_count INTEGER NOT NULL CHECK (document_count >= 0),
    image_count INTEGER NOT NULL CHECK (image_count >= 0),
    manifest_checksum_sha256 TEXT NOT NULL CHECK (
        length(manifest_checksum_sha256) = 64
        AND manifest_checksum_sha256 NOT GLOB '*[^0-9a-f]*'
    )
) STRICT;

CREATE TABLE continents (
    id TEXT PRIMARY KEY NOT NULL,
    code TEXT NOT NULL UNIQUE,
    display_name_uk TEXT NOT NULL CHECK (display_name_uk <> ''),
    source_name TEXT NOT NULL CHECK (source_name <> ''),
    sort_order INTEGER NOT NULL UNIQUE CHECK (sort_order > 0)
) STRICT;

CREATE TABLE entities (
    id TEXT PRIMARY KEY NOT NULL,
    canonical_key TEXT NOT NULL UNIQUE CHECK (canonical_key <> ''),
    display_name_uk TEXT NOT NULL CHECK (display_name_uk <> ''),
    entity_type TEXT NOT NULL CHECK (entity_type IN (
        'COUNTRY', 'TERRITORY', 'ORGANIZATION', 'DISPUTED_ENTITY', 'AMBIGUOUS_ENTITY'
    )),
    iso_alpha2 TEXT UNIQUE CHECK (iso_alpha2 IS NULL OR iso_alpha2 GLOB '[A-Z][A-Z]'),
    iso_alpha3 TEXT UNIQUE CHECK (iso_alpha3 IS NULL OR iso_alpha3 GLOB '[A-Z][A-Z][A-Z]'),
    review_status TEXT NOT NULL CHECK (review_status IN (
        'VERIFIED', 'AUTO_CLASSIFIED', 'NEEDS_REVIEW', 'EXCLUDED'
    )),
    notes TEXT NOT NULL
) STRICT;

CREATE TABLE entity_source_names (
    entity_id TEXT NOT NULL REFERENCES entities(id),
    source_name TEXT NOT NULL CHECK (source_name <> ''),
    sort_order INTEGER NOT NULL CHECK (sort_order >= 0),
    PRIMARY KEY (entity_id, source_name),
    UNIQUE (entity_id, sort_order)
) STRICT;

CREATE TABLE entity_continents (
    entity_id TEXT NOT NULL REFERENCES entities(id),
    continent_id TEXT NOT NULL REFERENCES continents(id),
    is_primary INTEGER NOT NULL CHECK (is_primary IN (0, 1)),
    sort_order INTEGER NOT NULL CHECK (sort_order >= 0),
    PRIMARY KEY (entity_id, continent_id),
    UNIQUE (entity_id, sort_order)
) STRICT;

CREATE UNIQUE INDEX entity_continents_one_primary
    ON entity_continents(entity_id)
    WHERE is_primary = 1;
CREATE INDEX entity_continents_by_continent
    ON entity_continents(continent_id, is_primary DESC, entity_id);

CREATE TABLE documents (
    id TEXT PRIMARY KEY NOT NULL,
    entity_id TEXT NOT NULL REFERENCES entities(id),
    document_type TEXT NOT NULL CHECK (document_type IN (
        'ORDINARY_PASSPORT', 'DIPLOMATIC_PASSPORT', 'SERVICE_PASSPORT', 'NATIONAL_ID', 'UNKNOWN'
    )),
    title_uk TEXT NOT NULL CHECK (title_uk <> ''),
    variant TEXT NOT NULL,
    review_status TEXT NOT NULL CHECK (review_status IN (
        'VERIFIED', 'AUTO_CLASSIFIED', 'NEEDS_REVIEW', 'EXCLUDED'
    )),
    quiz_eligible INTEGER NOT NULL CHECK (quiz_eligible IN (0, 1)),
    duplicate_of_document_id TEXT REFERENCES documents(id),
    notes TEXT NOT NULL,
    CHECK (duplicate_of_document_id IS NULL OR duplicate_of_document_id <> id)
) STRICT;

CREATE INDEX documents_by_entity
    ON documents(entity_id, document_type, id);
CREATE INDEX documents_quiz_pool
    ON documents(entity_id, document_type, id)
    WHERE quiz_eligible = 1;
CREATE INDEX documents_by_duplicate
    ON documents(duplicate_of_document_id)
    WHERE duplicate_of_document_id IS NOT NULL;

CREATE TABLE passport_images (
    id TEXT PRIMARY KEY NOT NULL,
    document_id TEXT NOT NULL REFERENCES documents(id),
    source_relative_path TEXT NOT NULL UNIQUE CHECK (
        source_relative_path <> ''
        AND substr(source_relative_path, 1, 1) <> '/'
        AND instr(source_relative_path, '\') = 0
    ),
    source_filename TEXT NOT NULL CHECK (
        source_filename <> ''
        AND instr(source_filename, '/') = 0
        AND instr(source_filename, '\') = 0
    ),
    mime_type TEXT NOT NULL CHECK (mime_type IN ('image/jpeg', 'image/png')),
    format TEXT NOT NULL CHECK (format IN ('JPEG', 'PNG')),
    width INTEGER NOT NULL CHECK (width > 0),
    height INTEGER NOT NULL CHECK (height > 0),
    file_size INTEGER NOT NULL CHECK (file_size > 0),
    sha256 TEXT NOT NULL CHECK (
        length(sha256) = 64
        AND sha256 NOT GLOB '*[^0-9a-f]*'
    ),
    review_status TEXT NOT NULL CHECK (review_status IN (
        'VERIFIED', 'AUTO_CLASSIFIED', 'NEEDS_REVIEW', 'EXCLUDED'
    )),
    duplicate_of_image_id TEXT REFERENCES passport_images(id),
    notes TEXT NOT NULL,
    CHECK (duplicate_of_image_id IS NULL OR duplicate_of_image_id <> id),
    CHECK (
        (format = 'JPEG' AND mime_type = 'image/jpeg')
        OR (format = 'PNG' AND mime_type = 'image/png')
    )
) STRICT;

CREATE INDEX passport_images_by_document
    ON passport_images(document_id, source_relative_path);
CREATE INDEX passport_images_by_sha256
    ON passport_images(sha256, source_relative_path);
CREATE INDEX passport_images_by_duplicate
    ON passport_images(duplicate_of_image_id)
    WHERE duplicate_of_image_id IS NOT NULL;

CREATE TABLE aliases (
    kind TEXT NOT NULL CHECK (kind IN ('CONTINENT', 'ENTITY', 'DOCUMENT_TYPE')),
    source_value TEXT NOT NULL CHECK (source_value <> ''),
    canonical_value TEXT NOT NULL CHECK (canonical_value <> ''),
    continent_id TEXT REFERENCES continents(id),
    entity_id TEXT REFERENCES entities(id),
    document_type TEXT CHECK (document_type IS NULL OR document_type IN (
        'ORDINARY_PASSPORT', 'DIPLOMATIC_PASSPORT', 'SERVICE_PASSPORT', 'NATIONAL_ID', 'UNKNOWN'
    )),
    PRIMARY KEY (kind, source_value),
    CHECK (
        (kind = 'CONTINENT' AND continent_id IS NOT NULL AND entity_id IS NULL AND document_type IS NULL)
        OR (kind = 'ENTITY' AND continent_id IS NULL AND entity_id IS NOT NULL AND document_type IS NULL)
        OR (kind = 'DOCUMENT_TYPE' AND continent_id IS NULL AND entity_id IS NULL AND document_type IS NOT NULL)
    )
) STRICT;
