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

## 6. Interactive world map — Stage 8

- **Status:** Completed in Stage 8.

- **Goal:** support continent and country selection through an interactive map without replacing catalogue navigation.
- **Primary result:** START → interactive world map or fallback list → existing document list → image details, using the same canonical entity IDs and navigation stack as Stage 7.
- **Delivered:** reproducibly generated Natural Earth 5.1.1 Ukraine-POV Robinson SVG with validated atomic replacement; 249 stable `ADM0_A3` regions; explicit versioned mappings for 135 linked and 114 intentionally unlinked regions; explicit list-only coverage for the remaining 4 of 139 canonical entities; strict SVG/TSV integrity validation; six presentation groups over seven canonical continents, including cyclic dateline focus for Australia/Oceania; native JavaFX hover, selection, bounded zoom/pan/reset, keyboard access, Unicode fallback search, accessible review-status labels, document counts, and graceful map-unavailable fallback to the Stage 7 catalogue.
- **Dependencies:** completed Stage 7 catalogue navigation and the approved Stage 8 map asset and interaction design.

## 7. Quiz generation engine — Stage 9

- **Status:** Completed in Stage 9.

- **Goal:** generate valid country-identification and document-type questions with multiple answer options and controlled randomization.
- **Primary result:** a UI-independent typed question generator operating only on eligible canonical passport records.
- **Delivered:** an approved clean cutover from numeric/string questions to stable typed identities; four question strategies; a prepared canonical candidate query; verified/known-type/eligibility and duplicate defenses; canonical continent/entity/document-type scopes; semantic distractor selection; one injectable seeded random source; complete-session-or-structured-failure behavior; explicit positive fixtures; and clean refusal of the intentionally empty production pool.
- **Dependencies:** Stage 4C invariants, Stage 5 eligibility data, Stage 6 catalogue access, and the Stage 9 architecture audit.

## 8. Review, verification and quiz eligibility activation — Stage 10

- **Status:** Completed in Stage 10.

- **Goal:** preserve Stage 5 classification provenance while requiring explicit, auditable human verification and separate quiz activation.
- **Primary result:** a version-controlled stable-ID review overlay, strict per-target drift fingerprints, an editorial CLI, and deterministic materialization into the read-only schema-version-2 catalogue.
- **Delivered:** canonical UTF-8/LF review TSV with atomic replacement; independent entity/document/image decisions; explicit document activation/deactivation; fail-closed stale, duplicate, unresolved, excluded, and noncanonical validation; V1-to-V2 migration without inferred approvals; review provenance in SQLite; and unchanged Stage 9 repository/service APIs.
- **Stage 10A pilot:** 4 verified entities, 12 verified documents, 12 verified images, and 12 explicit activations produce a deliberately bounded 12-record pool across 4 countries. The remainder of the catalogue is not implied to be reviewed or eligible.
- **Stage 10B verification:** the real production pool contains 4 ordinary, 3 diplomatic, 3 service-passport, and 2 national-ID records; all four Stage 9 strategies, deterministic replay, valid randomized variation, answer validation, and structured failure behavior pass. Current logical catalogue SHA-256: `577043d6202c3666eed15ed986173a1df9e28fa566d57e5594b406ccd8a4f936`.
- **Dependencies:** Stage 5 stable identities and classification, Stage 6 deterministic catalogue rebuilds, and Stage 9 fail-closed eligibility.

## 9. Learning and training modes — Stage 11

- **Status:** Learning and Training completed in Stage 11D; Examination readiness hardened in Stage 11E.
- **Goal:** provide the required learning and training experiences using the shared quiz engine.
- **Primary result:** distinct learning and training flows with behavior appropriate to each existing product mode.
- **Delivered in Stage 11B:** JavaFX-independent accepted configuration and `CREATED → ACTIVE → COMPLETED` coordination; lifecycle/result-persistence separation; complete-answer completion invariant; injected anonymous identity/session ID/seed boundaries; mode-safe feedback policy; `NavigationService.replace`/`resetTo`; and the single renamed `ApplicationControllerFactory`. No mode UI or persistence was introduced.
- **Delivered in Stage 11C:** a non-scored Learning flow from mode selection through an eligible-country list into a safe passport browser; the JavaFX-independent `LearningService` reuses the authoritative quiz-candidate query and presents only canonical verified eligible material.
- **Delivered in Stage 11D:** a complete non-persistent Training flow with global/exact-country configuration, all four generated question types, safe image handling, coordinator-authoritative immediate feedback, in-place progression, in-memory results, repeat/new/main-menu actions, confirmed abandonment, and canonical Learning-to-Training country handoff. Examination remains Stage 12.
- **Delivered in Stage 11E:** focused Examination mode-safety, completion-invariant, shared-presentation, result-boundary and duration verification; the unchanged production pool deterministically supports a 10-question global Examination request with 4 options and all four allowed question types. Examination UI remains disabled and writable result persistence remains a Stage 12/13 policy decision.
- **Dependencies:** the completed Stage 9 quiz engine, Stage 10 reviewed-catalogue infrastructure, and Stage 7 catalogue UI foundations.

## 10. Examination mode — Stage 12

- **Status:** Generated Examination runtime/UI and completed-result persistence completed through Stage 12C.
- **Goal:** provide a controlled examination flow and reliable result calculation.
- **Primary result:** examination lifecycle, completion rules, scoring, and result persistence consistent with established contracts.
- **Delivered in Stage 12A:** fixed generated policy and flow, shared-controller reuse, `QuestionSetProvider` source boundary, separate writable application-data boundary, temporary anonymous identity, and retained shared `ResultRepository` write/read contract.
- **Delivered in Stage 12B:** enabled Examination entry and localized confirmation; fixed global 10-question/4-option generation across all four types; shared answer-key-free question UI with no immediate feedback/running score; confirmed abandonment; completion-only in-memory counts/percentage/duration; New Examination/Main Menu navigation; and generated provider wiring with coordinator-owned lifecycle.
- **Delivered in Stage 12C:** separate per-user writable SQLite application database and checksummed migration; transactional `SQLiteResultRepository`; nullable persisted anonymous user; generated/authored/hybrid source columns and optional immutable assessment provenance; one-result-per-session idempotency; completion-only save through `ExamResultService`; localized retry that retains completed state; and unchanged read-only passport catalogue.
- **Stage 12D:** not currently defined; future work continues in Stage 13 and later stages.
- **Dependencies:** the completed Stage 9 quiz engine, Stage 11 shared mode infrastructure, and Stage 10 reviewed read-only catalogue.

## 11. Users, ownership and statistics — Stage 13

- **Status:** Stage 13 completed through the Stage 13E integration, regression, migration, security-boundary, navigation, and release-baseline pass.
- **Goal:** complete local users, authentication/session state, Examination result ownership, personal result history, and derived statistics through service/repository boundaries.
- **Primary result:** persisted users and completed Examination results with isolated user-facing history/statistics; settings remain separately scoped future work.
- **Delivered in Stage 13A:** minimum local-user/authentication/session/role/statistics architecture preserving existing quiz and result boundaries while keeping future Admin/Question Bank compatible.
- **Delivered in Stage 13B:** checksummed application V2 users/result-ownership migration; database-generated local users and soft deactivation; canonical username handling; Argon2id hashing and rehash-on-login; generic active-account authentication; password-free current-user session; active-admin authorization boundary; and exactly-once quiz owner capture with anonymous compatibility.
- **Delivered in Stage 13C:** localized first-run creation of one initial `ADMIN`; ordinary `USER` registration; generic local login or anonymous continuation; compact current-user presentation; logout/switch-user with confirmed active-quiz abandonment; and unchanged exactly-once Examination ownership capture.
- **Delivered in Stage 13D:** current-active-user-only newest-first Examination history; anonymous guest and authenticated-empty states; persisted-active authorization; derived completed/average/best/answered/correct statistics without aggregate persistence; localized presentation-safe rows; START navigation; and JavaFX coverage for ADMIN/USER empty history plus a persisted owned USER result.
- **Delivered in Stage 13E:** clean V001/V002 and V1-upgrade verification; complete first-run/login/anonymous/logout/switch integration; authenticated/anonymous ownership and My Results isolation; Learning/Training/Examination regression; password/principal, inactive-account, repository, navigation, and controller-factory boundary checks; full-suite/package verification; and a clean baseline for separately scoped Admin UI/Question Bank work.
- **Dependencies:** Stage 4 persistence foundation and stable behavior from Stages 8–12.

## 12. Windows release — Stage 14

- **Goal:** deliver and verify the application on its target Windows platform.
- **Primary result:** a self-contained Windows runtime/package with verified startup, writable user-data placement, resources, and upgrade/install behavior.
- **Dependencies:** completion of required application stages and an explicit release/packaging decision.
