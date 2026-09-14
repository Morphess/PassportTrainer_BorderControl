# Passport Trainer roadmap

The roadmap preserves the established architecture and product scope. Details remain intentionally limited where a later architectural or data decision is required.

## 1. Project context and agent rules — Stage 4B

- **Status:** Completed in Stage 4B.

- **Goal:** establish persistent technical context, hard project rules, current-state documentation, and a shared delivery sequence.
- **Primary result:** `.omp/AGENTS.md`, `.omp/RULES.md`, `docs/PROJECT_STATE.md`, and this roadmap are present and mutually consistent.
- **Dependencies:** completed repository audit and Stage 4A build/runtime stabilization.

## 2. Domain contracts and QuizService invariants — Stage 4C

- **Status:** Completed in Stage 4C.

- **Goal:** make existing domain and quiz lifecycle contracts internally consistent before data and UI layers depend on them.
- **Primary result:** explicitly decided domain contracts and enforced quiz-session invariants with focused behavioral verification.
- **Dependencies:** Stage 4B rules and the current architecture baseline.

## 3. Canonical passport manifest — Stage 5

- **Status:** Completed in Stage 5.

- **Goal:** create a reviewed, machine-readable mapping from immutable source paths to canonical countries/entities and document types.
- **Primary result:** a versioned manifest that preserves source traceability and separates accepted records from ambiguous data.
- **Dependencies:** stable domain terminology from Stage 2 and decisions based on `docs/database-analysis.md`.

## 4. SQLite catalogue and repository implementations

- **Status:** Completed in Stage 6.

- **Goal:** provide persistent catalogue access behind existing repository boundaries.
- **Primary result:** an approved SQLite catalogue, migrations, and working country/passport repository implementations populated from the canonical manifest.
- **Dependencies:** completed Stage 5 manifest and a separate accepted database-design decision.
- **Delivered:** stable Stage 5 text identities, normalized schema version 1, checksummed migration runner, transactional manifest-only importer, database integrity validation, read-only catalogue composition, and working `CountryRepository`/`PassportRepository` SQLite implementations.

## 5. Country/passport catalogue UI — Stage 7

- **Status:** Completed in Stage 7.

- **Goal:** let users browse canonical countries and passport documents through the existing JavaFX architecture.
- **Primary result:** START → continent/entity catalogue → document list → image details, backed by read-only services/repositories with localized status and error presentation.
- **Delivered:** explicit constructor-injected controller creation, instance-local back navigation, seven canonical continents with membership counts, Unicode search, entity/review classification, complete document listings, real image loading with portable path containment, loading/error states, and multi-image controls.
- **Dependencies:** completed Stage 6 catalogue access and established navigation conventions.

## 6. Interactive world map

- **Status:** Next.

- **Goal:** support continent and country selection through an interactive map without replacing catalogue navigation.
- **Primary result:** a clickable world-map flow connected to canonical country data and the implemented passport catalogue screens.
- **Dependencies:** Stage 7 catalogue navigation plus an approved map asset and interaction design.

## 7. Quiz generation engine

- **Goal:** generate valid country-identification and document-type questions with multiple answer options and controlled randomization.
- **Primary result:** a domain-level question generator operating only on eligible canonical passport records.
- **Dependencies:** Stage 4C invariants, Stage 5 eligibility data, and Stage 6 catalogue access.

## 8. Learning and training modes

- **Goal:** provide the required learning and training experiences using the shared quiz engine.
- **Primary result:** distinct learning and training flows with behavior appropriate to each existing product mode.
- **Dependencies:** Stage 7 question generation and catalogue UI foundations.

## 9. Examination mode

- **Goal:** provide a controlled examination flow and reliable result calculation.
- **Primary result:** examination lifecycle, completion rules, scoring, and result persistence consistent with established contracts.
- **Dependencies:** Stage 7 quiz engine, Stage 8 shared mode infrastructure, and persistence from Stage 4.

## 10. Users, settings and statistics

- **Goal:** complete local user, configuration, progress, result-history, and statistics capabilities already represented by service/repository boundaries.
- **Primary result:** persisted users, settings, results, and user-facing statistics without bypassing repository contracts.
- **Dependencies:** Stage 4 persistence foundation and stable behavior from Stages 8–9.

## 11. Windows release

- **Goal:** deliver and verify the application on its target Windows platform.
- **Primary result:** a self-contained Windows runtime/package with verified startup, writable user-data placement, resources, and upgrade/install behavior.
- **Dependencies:** completion of required application stages and an explicit release/packaging decision.
