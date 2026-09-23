# Passport Trainer project state

This document records the repository state after Stage 11C. It distinguishes implemented behavior from architectural contracts and planned work.

### Current technical baseline

- Local JavaFX educational application for passport-document training.
- Development platform: Fedora Linux.
- Target release platform: Windows 10/11 x86_64.
- Target Java release: Java 25.
- Build system: Maven Wrapper 3.3.4, pinned to Maven 3.9.11.
- UI stack: JavaFX 25.0.4, FXML, and CSS.
- Persistence direction: SQLite through JDBC and repository boundaries.
- Main entry point: `ua.passporttrainer.Launcher`.
- JavaFX application class: `ua.passporttrainer.PassportTrainerApplication`.

### Implemented

- Maven project structure and dependency configuration.
- Maven Wrapper scripts for Unix and Windows.
- Early Maven Enforcer guard requiring JDK 25 or newer.
- JavaFX development launch through `exec-maven-plugin` and the existing `Launcher`.
- JavaFX catalogue flow from START through continent/entity selection, document selection, and passport-image details, with back navigation between every screen.
- JavaFX interactive-map flow from START through map or fallback entity selection into the existing document and passport-image screens, with the same instance-local back navigation.
- A reproducibly generated Natural Earth 5.1.1 Ukraine-POV Robinson SVG with 249 unique `ADM0_A3` region IDs, pinned source/output hashes, retained public-domain notice, and a pinned `mapshaper@0.7.61` generation tool that validates staged output before atomic replacement.
- Strict map resource loading and validation covering 249 geometry records, 135 explicit canonical links, 114 intentionally unlinked geographic regions, and 4 list-only entities so that all 139 canonical entities remain reachable without runtime name matching.
- Native JavaFX map interaction with six presentation groups over the seven canonical continents, dateline-aware Australia/Oceania focus, hover/review/no-data/selection states, bounded zoom and pan, reset, keyboard activation, Unicode fallback search, and localized accessible labels and tooltips.
- Central FXML loading, constructor-injected controller creation, scene-root replacement, shared CSS, instance-local navigation history, root replacement without history growth, and history-reset navigation through `NavigationService`.
- JDK-only domain records and enums for countries, passports, users, quiz sessions, accepted session configuration, explicit session lifecycle, answers, results, typed quiz content, quiz-generation requests, and structured generation failures; catalogue and quiz identities use stable Stage 5 text IDs, while presentation labels remain separate.
- The authoritative `DocumentType` contract is `ORDINARY_PASSPORT`, `DIPLOMATIC_PASSPORT`, `SERVICE_PASSPORT`, `NATIONAL_ID`, and `UNKNOWN`.
- The authoritative `QuizQuestionType` contract is `PASSPORT_TO_COUNTRY`, `COUNTRY_TO_PASSPORT`, `DOCUMENT_TYPE`, and `FIND_ORDINARY_PASSPORT`.
- Repository interfaces for continents, countries, passports, users, results, and settings; `PassportRepository` includes one bounded quiz-candidate read.
- Service-layer classes using constructor injection, including catalogue browsing by canonical continent membership.
- `DatabaseConnectionProvider` and `SQLiteConnectionProvider` with per-connection foreign-key enforcement and explicit read-only catalogue mode.
- Separate in-memory quiz-session lifecycle and examination-result boundaries: `QuizSessionLifecycleService` depends only on `Clock`, determines correctness, rejects duplicate/post-completion answers and early completion, while `ExamResultService` alone depends on `ResultRepository`.
- `QuizGenerationService`, which filters canonical verified eligible candidates, applies canonical continent/entity/document-type scopes, builds all four supported question types, selects semantic distractors, shuffles through one injectable seeded random source, prevents repeated correct documents/images, and returns only fully populated `QuizSession` instances.
- An application-scoped, JavaFX-independent `QuizSessionCoordinator` with immutable accepted configuration, explicit `CREATED → ACTIVE → COMPLETED` state, one generation call per start, current-question progression, derived progress/score, mode-safe answer feedback, typed generation-failure preservation, deterministic injected ID/seed suppliers, restart, and non-persisted abandonment.
- A minimal `QuizModePolicy` that exposes immediate correctness and active score only for Training while allowing future Examination submission results to omit correctness.
- A complete non-scored Learning Mode UI: START mode entry, explicit mode selection with unavailable Training/Examination controls, reviewed-entity selection, and a passive document browser with localized metadata, safe image loading, navigation progress, Previous/Next, and back navigation.
- A JavaFX-independent `LearningService` that reuses `PassportRepository.findQuizEligibleCandidates()`, defensively applies the canonical `QuizCandidate.usable()` contract, deduplicates by canonical document, and returns stable presentation-safe entity/document models without creating a `QuizSession`.
- One hundred sixty-nine JUnit tests, including isolated SQLite integration, review-source canonicalization and atomic-write safety, target-fingerprint sensitivity and drift rejection, disallowed activation targeting, V1-to-V2 migration safety, review materialization, unchanged Stage 9 integration, reviewed production candidate-pool invariants, quiz-generation determinism/invariants/failures, session lifecycle/coordinator/domain invariants, deterministic Learning-service filtering and production counts, vertical Learning navigation/image/paging behavior, navigation replacement/reset behavior, isolated zero-pool failure behavior, Unicode search, map integrity/completeness, high-risk geopolitical mappings, dateline-wrapped viewport and interaction behavior, independent map-failure fallback, FXML loading, complete map/catalogue navigation, portable image resolution, real image decoding, and multi-image paging.
- A versioned TSV metadata layer for continent mappings, entity corrections, document-type aliases, exact source overrides, and service-file exclusions.
- A JDK-only read-only scanner, deterministic manifest generator, strict validator, and command-line entry point in `ua.passporttrainer.manifest`.
- A generated canonical TSV catalogue with 7 continents, 139 entities, 521 provisional document records, 521 source-image records, and 166 normalization aliases.
- The verified source passport dataset audit and Stage 5 manifest decisions in `docs/database-analysis.md`.
- Two checksummed SQLite migrations: V1 for normalized canonical catalogue data and V2 for separate manifest provenance, human review decisions, and materialized effective review/eligibility state.
- A deterministic transactional importer that consumes the independently validated Stage 5 manifest plus the validated Stage 10 review source, fails before mutation on stale review data, and produces a post-import validated logical catalogue fingerprint.
- Working `SQLiteContinentRepository`, `SQLiteCountryRepository`, and `SQLitePassportRepository` implementations behind repository boundaries, with canonical `String` IDs, alternate geography, review status, aggregate entity document counts, image counts, and a prepared quiz-candidate query restricted to fully verified, known-type, quiz-eligible, non-duplicate records.
- A non-JavaFX `CatalogueApplicationContext` that validates a read-only catalogue and wires all three SQLite repositories into `ContinentService`, `CountryService`, `PassportService`, and `QuizGenerationService`.
- A deterministic generated schema-version-2 development catalogue at `data/passport-catalogue/passport-catalogue.sqlite`; its current ordered logical SHA-256 is `577043d6202c3666eed15ed986173a1df9e28fa566d57e5594b406ccd8a4f936`.
- A canonical version-controlled `data/passport-review/review-decisions.tsv` source with stable entity/document/image IDs, separate review and activation records, per-target schema-version-1 SHA-256 fingerprints, strict drift validation, and a deliberately bounded Stage 10A pilot containing 4 verified entities, 12 verified documents, 12 verified images, and 12 enabled activations.
- A non-JavaFX editorial `PassportReviewTool` supporting pending/reviewed listing, complete target inspection, single-target review decisions, explicit activation/deactivation, validation, reporting, and atomic compare-and-replace mutation without bulk approval commands.
- Portable runtime location configuration through `passporttrainer.catalogue.path` and `passporttrainer.assets.root`, with repository-relative development defaults and image containment checks.

### Partially implemented

- The catalogue UI browses every current record and displays effective review state; human review editing and quiz activation are intentionally confined to developer/editorial CLI tooling rather than the student-facing JavaFX application.
- Versioned SQLite schema creation and deterministic manifest ingestion are implemented; Windows packaging of the read-only catalogue and passport assets remains a later release concern.
- Learning is implemented as passive browsing over reviewed eligible material and never starts a quiz session; generated Training and Examination sessions still have no user-facing screens.
- Training and Examination disclosure behavior is represented by `QuizModePolicy`, but their complete mode-specific UI workflows are not implemented.
- Authentication, settings, and statistics have service and repository boundaries but no persistence implementations or UI flows.
- Localization infrastructure covers the implemented catalogue, interactive-map, mode-selection, and Learning flows in the default resource bundles; additional locales are not present.

### Not implemented

- Complete training and examination behavior.
- Persistent users, settings, statistics, results, or active quiz sessions.
- Windows runtime image, installer, and Windows execution verification.

### Current architecture

The intended dependency direction is:

```text
JavaFX View (FXML/CSS)
    -> Controller
    -> Service
    -> Repository interface
    -> Persistence implementation
```

- `model` uses only JDK types and does not depend on JavaFX or JDBC.
- Controllers are scoped to individual FXML views.
- Services coordinate use cases and receive dependencies through constructors.
- Repository interfaces isolate services from persistence technology.
- `database` contains JDBC connection infrastructure and the lightweight migration runner.
- `catalogue` contains canonical import, validation, logical fingerprinting, CLI tooling, and the non-UI catalogue composition root.
- `navigation` is UI infrastructure; `NavigationService` is the centralized FXML loader, typed controller initializer, back-stack owner, history-preserving root replacer, and history-reset boundary.
- The read-only catalogue composition context creates `LearningService` from the same `PassportRepository` used by quiz generation. The JavaFX composition root creates the image locator, lifecycle service, application-scoped session coordinator and single `ApplicationControllerFactory`; Learning controllers receive only `LearningService`, `PassportImageLocator`, navigation and resources, while existing catalogue controllers retain their prior dependencies.

### Build and runtime

JDK 25 or newer must be the active Java used by Maven Wrapper.

Tests:

```bash
./mvnw clean test
```

Package:

```bash
./mvnw package
```

Development run:

```bash
./mvnw compile exec:exec
```

Stage 4A verification established that:

- Maven Wrapper can bootstrap Maven 3.9.11 without a global Maven installation;
- Java below 25 fails during `validate` with an explicit required/actual version message;
- `./mvnw clean test` passes all six tests;
- `./mvnw package` succeeds;
- the documented development command starts the JavaFX process;
- application code and passport assets were not changed during Stage 4A.

Stage 4C verification established that:

- the code and relevant documentation use one canonical `DocumentType` contract;
- quiz questions, sessions, answers, and examination results enforce the documented invariants;
- duplicate answers and operations after completion are rejected;
- all original tests and eighteen new behavioral tests pass;
- no persistence, importer, UI, map, or passport-asset functionality was added.

Stage 5 verification established that:

- the read-only scanner reproduces the verified 521-image dataset audit without changing source assets;
- metadata plus source assets generate a validated, deterministically ordered TSV catalogue;
- 90 additional exact-copy records reference deterministic representatives instead of being removed;
- 59 unresolved documents are marked `NEEDS_REVIEW`, and all 521 documents remain quiz-ineligible until explicit verification;
- fifteen new manifest tests cover normalization, aliases, stable IDs, duplicate handling, validation, eligibility, source traceability, and deterministic output.

Stage 6 verification established that:

- schema version 1 is reproducible from a checksummed plain SQL migration;
- every provided SQLite connection enables foreign keys, and runtime catalogue composition uses read-only mode;
- the canonical manifest imports as 7 continents, 139 entities, 521 documents, 521 images, and 166 aliases;
- primary and alternate continent relations, review status, zero quiz-eligible documents, exact source-relative paths, and 90 duplicate relationships survive import;
- invalid manifests fail before mutation, transactional failures roll back, and independent rebuilds produce the same ordered logical SHA-256 fingerprint;
- SQLite repositories and both application services read the real imported catalogue from isolated temporary databases.

Stage 7 verification established that:

- the generated development catalogue retains the Stage 6 logical fingerprint and opens in explicit read-only mode;
- the UI presents 7 canonical continents, all continent memberships, 139 entities, every document record, review status, entity type, and image count without applying quiz-eligibility filtering;
- case-insensitive Unicode-normalized filtering, missing records, unavailable catalogue data, missing images, real image decoding, multi-image paging, FXML loading, and complete back navigation are covered;
- the actual JavaFX surface rendered canonical catalogue data and a source passport image with preserved aspect ratio;
- all 73 tests pass and the application development command reaches a live JavaFX window.

Stage 8 verification established that:

- the checked-in Natural Earth asset reproduces from the pinned Ukraine-POV source and `mapshaper@0.7.61` to the expected SHA-256; generation validates staged output before atomic replacement, and a controlled failure leaves an existing destination unchanged;
- strict XML security, asset integrity, SVG/metadata identity equality, exact canonical coverage, list-only exclusions, and high-risk mappings fail closed;
- the native JavaFX surface builds 249 canonical paths, supports bounded viewport and interaction state, and retains list access for unlinked or geometry-less catalogue entities;
- Australia/Oceania focus presents the map cyclically across the dateline so Australia, New Zealand, Tonga, Samoa, and representative Pacific islands remain visible through resize, zoom, pan, selection synchronization, document navigation, back navigation, and reset;
- START → world map → map/list entity selection → existing passport documents → back navigation is exercised on a graphical JavaFX toolkit, including keyboard activation, Unicode-normalized search, and accessible review-status labeling;
- the early performance gate measured 109 ms resource loading, 271 ms path construction, 44 ms first layout, and a 12 MiB heap delta on the development workstation;
- all 95 tests pass, `./mvnw package` succeeds, and the standard development command reaches a live JavaFX process.

Stage 9 verification established that:

- the pre-implementation domain audit identified the old numeric/string question representation as incompatible with canonical text IDs and all four required strategies; the approved clean cutover replaced it with typed subjects/options and stable answer IDs;
- generation supports passport-to-country, country-to-passport, document-type, and ordinary-passport identification without JavaFX or localized text in the domain;
- the repository query and service defense checks require `quiz_eligible=1`, `VERIFIED` entity/document/image status, a known document type, canonical document/image records, and deduplication by exact image SHA-256;
- one seeded injectable `RandomGenerator` controls blueprint and option shuffling; equal seeds are reproducible after canonical sorting, while different seeds vary output and correct-answer positions;
- requests apply canonical continent membership, entity, and document-type scopes and fail with structured reasons instead of returning partial or fabricated sessions when candidates or distractors are insufficient;
- the production catalogue remains intentionally at zero eligible records and cleanly returns `INSUFFICIENT_ELIGIBLE_DATA`; positive behavior is covered only by explicit controlled fixtures;
- all 118 tests pass, including 128-seed invariant coverage, SQLite eligibility filtering, existing quiz lifecycle invariants, and the production zero-pool path.

Stage 10 verification established that:

- the Stage 5 manifest and metadata remain canonical classification sources, while the canonical Stage 10 review TSV is the sole authoritative human-decision source;
- entity, document, and image verification are independent, document quiz activation is a separate explicit decision, and an enabled record becomes effective only when all Stage 9 predicates hold;
- target fingerprints use versioned length-prefixed canonical serialization and reject changes to identity, association, document type, image path/content/technical metadata, manifest state, or duplicate representative;
- review mutations revalidate the current source, compare its checksum, write and force a same-directory temporary file, and require atomic replacement; an enabled dependency cannot be revoked until activation is explicitly disabled;
- V2 preserves manifest/base status and eligibility separately from effective values, stores decisions and review-source provenance, and migrates V1 without creating implicit approvals;
- the current reviewed production rebuild has logical SHA-256 `577043d6202c3666eed15ed986173a1df9e28fa566d57e5594b406ccd8a4f936`;
- the Stage 10A pilot contains 40 explicit decisions: 4 verified entities, 12 verified documents, 12 verified images, and 12 enabled document activations, producing 12 eligible records across 4 countries;
- the pilot pool contains 4 ordinary, 3 diplomatic, 3 service-passport, and 2 national-ID records; all remaining catalogue records retain their manifest/base provenance and are not implied to be human verified;
- the Stage 10B real-production smoke passed all four Stage 9 strategies, same-seed determinism, valid different-seed variation, answer validation, and the structured impossible-request failure path;
- all 145 tests pass without adding a JavaFX review UI or changing the Stage 9 repository/service API.

Stage 11B verification established that:

- session lifecycle no longer depends on result persistence, and examination result persistence remains a separate boundary without a production repository implementation;
- normal completion requires exactly one answer for every generated question, while duplicate, early and repeated completion attempts fail deterministically;
- the application-scoped coordinator preserves typed generation failures, never retains partial generation, derives progress/score, gates correctness by mode policy, restarts with injected new IDs/seeds and abandons without persistence;
- `NavigationService.replace` preserves existing history without pushing the replaced root, while `resetTo` clears history; existing `show`/`goBack` behavior remains intact;
- `ApplicationControllerFactory` remains the only FXML controller factory, and no Learning, Training or Examination UI entry was added;
- all 163 tests pass and `./mvnw package` succeeds.

Stage 11C verification established that:

- Learning lists only canonical reviewed quiz-eligible documents returned by the existing bounded repository query and never falls back to the 509 ineligible catalogue records;
- the checked-in production catalogue exposes 12 Learning documents across Україна (4), Франція (3), Німеччина (3), and Ірландія (2);
- passive Learning creates no `QuizSession`, answer, score, progress persistence, or result record;
- Training and Examination remain visibly unavailable on Mode Selection and have no dead navigation routes;
- the dedicated eligible-country list avoids duplicating or weakening the Stage 8 map architecture, while the general catalogue continues to display all canonical records;
- Learning reuses `PassportImageLocator`, keeps paging available after missing-image failures, and exposes localized document types rather than raw enum names;
- the real application was launched and visually exercised through START, Mode Selection, Learning entity selection, Learning Browser, Next, Previous, Back, existing Catalogue/details, and existing World Map;
- all 169 tests pass and `./mvnw package` succeeds.

### Known technical debt

- Domain records outside the Stage 4C quiz scope still provide limited validation of identifiers, nullability, and value ranges.
- Human verification currently covers only the bounded 12-document Stage 10A pilot; the remainder of the 521-record catalogue is not quiz-eligible and must not be described as reviewed.
- Duplicate suppression uses explicit canonical links and exact SHA-256 equality; visually equivalent re-encodings require a future reviewed data decision.
- The current read-only catalogue and source assets use configurable filesystem locations; their final Windows installation layout is intentionally deferred to the Windows-release stage.
- JavaFX smoke tests require a graphical display and validate behavior rather than pixel-perfect screenshots.
- The produced JAR is not a complete Windows distribution; native packaging remains absent.
- Maven 3.9.11 emits a non-fatal Guice `sun.misc.Unsafe` deprecation warning on Java 25.

### Passport dataset state

`Зразки паспортних документів/` remains the unchanged source dataset. Stage 5 tooling scans it, Stage 6/10 imports the canonical manifest and separate review overlay without reading or classifying source assets, Stages 7–8 only resolve catalogue-provided relative image paths below a configured read-only root, and Stage 9 reads only materialized effective catalogue state and retained relative paths.

The repeated audit and generated manifest record:

- 521 readable passport-document images and 4 excluded `.DS_Store` service files;
- 173 physical second-level country/entity directories and 141 normalized source entity names;
- 139 canonical entity records after explicit alias mergers;
- 431 unique binary images and 87 exact-duplicate groups, represented by 90 duplicate relationships;
- Stage 5 manifest/base state remains 462 `AUTO_CLASSIFIED` and 59 `NEEDS_REVIEW` document records; Stage 10 effective state separately promotes only the reviewed 12-document pilot;
- 133 ordinary, 162 diplomatic, 79 service, 146 national-ID, and 1 `UNKNOWN` document-type classifications;
- inconsistent geography, naming, case, and Unicode retained as source traceability plus versioned correction metadata.

Source files must remain unchanged. SQLite catalogue ingestion consumes the canonical manifest and never infers records from directory or filename conventions.

### Current Git/development workflow

- Inspect the working tree and relevant documentation before changes.
- Use Maven Wrapper commands rather than relying on global Maven.
- Keep JDK 25 active for Maven Wrapper.
- For significant work: inspect, plan, identify affected files, implement, test, package, self-review, inspect the diff, and update project documentation.
- Do not create Git commits unless directly requested by the user.
- Stages 4A and 4C established build and domain behavior; Stage 5 added the canonical manifest; Stage 6 added catalogue persistence; Stage 7 added catalogue presentation; Stage 8 added the reproducible map and its runtime flow; Stage 9 added the UI-independent quiz-generation engine; Stage 10 added editorial review/activation tooling and deterministic read-only catalogue materialization; Stage 11B added shared non-UI session/navigation infrastructure; Stage 11C added non-scored reviewed Learning browsing.

### Immediate next step

Stage 11C Learning Mode is complete. Stage 11D Training Mode is the next bounded implementation pass. Learning remains non-scored and uses the unchanged 12-document reviewed pool across 4 countries. Examination behavior, authentication, statistics, mutable user persistence, and Windows packaging remain later stages.
