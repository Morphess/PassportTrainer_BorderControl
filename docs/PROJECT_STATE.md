# Passport Trainer project state

This document records the repository state after Stage 9. It distinguishes implemented behavior from architectural contracts and planned work.

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
- Central FXML loading, constructor-injected controller creation, scene-root replacement, shared CSS, and instance-local navigation history through `NavigationService`.
- JDK-only domain records and enums for countries, passports, users, quiz sessions, answers, results, typed quiz content, quiz-generation requests, and structured generation failures; catalogue and quiz identities use stable Stage 5 text IDs, while presentation labels remain separate.
- The authoritative `DocumentType` contract is `ORDINARY_PASSPORT`, `DIPLOMATIC_PASSPORT`, `SERVICE_PASSPORT`, `NATIONAL_ID`, and `UNKNOWN`.
- The authoritative `QuizQuestionType` contract is `PASSPORT_TO_COUNTRY`, `COUNTRY_TO_PASSPORT`, `DOCUMENT_TYPE`, and `FIND_ORDINARY_PASSPORT`.
- Repository interfaces for continents, countries, passports, users, results, and settings; `PassportRepository` includes one bounded quiz-candidate read.
- Service-layer classes using constructor injection, including catalogue browsing by canonical continent membership.
- `DatabaseConnectionProvider` and `SQLiteConnectionProvider` with per-connection foreign-key enforcement and explicit read-only catalogue mode.
- In-memory quiz-session lifecycle with typed validated questions, unique question IDs and answer-option IDs, one answer per question, immutable completion, bounded scores, and examination-only result-save delegation.
- `QuizGenerationService`, which filters canonical verified eligible candidates, applies canonical continent/entity/document-type scopes, builds all four supported question types, selects semantic distractors, shuffles through one injectable seeded random source, prevents repeated correct documents/images, and returns only fully populated `QuizSession` instances.
- One hundred eighteen JUnit tests, including isolated SQLite integration, quiz-generation determinism/invariants/failures, production zero-pool refusal, Unicode search, map integrity/completeness, high-risk geopolitical mappings, dateline-wrapped viewport and interaction behavior, independent map-failure fallback, FXML loading, complete map/catalogue navigation, portable image resolution, real image decoding, and multi-image paging.
- A versioned TSV metadata layer for continent mappings, entity corrections, document-type aliases, exact source overrides, and service-file exclusions.
- A JDK-only read-only scanner, deterministic manifest generator, strict validator, and command-line entry point in `ua.passporttrainer.manifest`.
- A generated canonical TSV catalogue with 7 continents, 139 entities, 521 provisional document records, 521 source-image records, and 166 normalization aliases.
- The verified source passport dataset audit and Stage 5 manifest decisions in `docs/database-analysis.md`.
- A checksummed version-1 SQLite migration for normalized catalogue metadata, continents, entities, geography, documents, images, aliases, review state, eligibility, and duplicate relationships.
- A deterministic transactional importer that consumes only the validated Stage 5 manifest, plus post-import database validation and an ordered logical catalogue fingerprint.
- Working `SQLiteContinentRepository`, `SQLiteCountryRepository`, and `SQLitePassportRepository` implementations behind repository boundaries, with canonical `String` IDs, alternate geography, review status, aggregate entity document counts, image counts, and a prepared quiz-candidate query restricted to fully verified, known-type, quiz-eligible, non-duplicate records.
- A non-JavaFX `CatalogueApplicationContext` that validates a read-only catalogue and wires all three SQLite repositories into `ContinentService`, `CountryService`, `PassportService`, and `QuizGenerationService`.
- A deterministic generated development catalogue at `data/passport-catalogue/passport-catalogue.sqlite`; its ordered logical SHA-256 is `279f2ecb5f1d09fe66e266560e0c323246a4b44909b00412cf8336eb2c2a7093`.
- Portable runtime location configuration through `passporttrainer.catalogue.path` and `passporttrainer.assets.root`, with repository-relative development defaults and image containment checks.

### Partially implemented

- The catalogue UI browses every current record and displays provisional review state; it does not yet provide manual review editing or quiz-eligibility decisions.
- Versioned SQLite schema creation and deterministic manifest ingestion are implemented; Windows packaging of the read-only catalogue and passport assets remains a later release concern.
- Quiz generation is implemented independently of JavaFX, but no screen starts or presents a generated session and no mode-specific learning, training, or examination rules exist.
- Learning, training, and examination are represented by `QuizMode`; generation preserves the requested mode without differentiating its behavior.
- Authentication, settings, and statistics have service and repository boundaries but no persistence implementations or UI flows.
- Localization infrastructure covers the implemented catalogue and interactive-map flows in the default resource bundles; additional locales are not present.

### Not implemented

- Complete learning, training, and examination behavior.
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
- `navigation` is UI infrastructure; `NavigationService` is the centralized FXML loader, typed controller initializer, and back-stack owner.
- The JavaFX composition root validates the configured read-only catalogue, creates the image locator and explicit controller factory, and injects catalogue services into screen-specific controllers.

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

### Known technical debt

- Domain records outside the Stage 4C quiz scope still provide limited validation of identifiers, nullability, and value ranges.
- The production catalogue has zero quiz-eligible documents until explicit review decisions are made; the generator intentionally refuses to fabricate fallback content.
- Duplicate suppression uses explicit canonical links and exact SHA-256 equality; visually equivalent re-encodings require a future reviewed data decision.
- The current read-only catalogue and source assets use configurable filesystem locations; their final Windows installation layout is intentionally deferred to the Windows-release stage.
- JavaFX smoke tests require a graphical display and validate behavior rather than pixel-perfect screenshots.
- The produced JAR is not a complete Windows distribution; native packaging remains absent.
- Maven 3.9.11 emits a non-fatal Guice `sun.misc.Unsafe` deprecation warning on Java 25.

### Passport dataset state

`Зразки паспортних документів/` remains the unchanged source dataset. Stage 5 tooling scans it, Stage 6 imports the canonical manifest without reading or classifying source assets, Stages 7–8 only resolve catalogue-provided relative image paths below a configured read-only root, and Stage 9 reads only catalogue metadata and retained relative paths.

The repeated audit and generated manifest record:

- 521 readable passport-document images and 4 excluded `.DS_Store` service files;
- 173 physical second-level country/entity directories and 141 normalized source entity names;
- 139 canonical entity records after explicit alias mergers;
- 431 unique binary images and 87 exact-duplicate groups, represented by 90 duplicate relationships;
- 462 `AUTO_CLASSIFIED` and 59 `NEEDS_REVIEW` document records;
- 133 ordinary, 162 diplomatic, 79 service, 146 national-ID, and 1 `UNKNOWN` document-type classifications;
- inconsistent geography, naming, case, and Unicode retained as source traceability plus versioned correction metadata.

Source files must remain unchanged. SQLite catalogue ingestion consumes the canonical manifest and never infers records from directory or filename conventions.

### Current Git/development workflow

- Inspect the working tree and relevant documentation before changes.
- Use Maven Wrapper commands rather than relying on global Maven.
- Keep JDK 25 active for Maven Wrapper.
- For significant work: inspect, plan, identify affected files, implement, test, package, self-review, inspect the diff, and update project documentation.
- Do not create Git commits unless directly requested by the user.
- Stages 4A and 4C established build and domain behavior; Stage 5 added the canonical manifest; Stage 6 added catalogue persistence; Stage 7 added catalogue presentation; Stage 8 added the reproducible map and its runtime flow; Stage 9 added only the UI-independent quiz-generation model, eligible candidate query, generator service, tests, and factual documentation.

### Immediate next step

Stage 9 completed the UI-independent quiz generation engine. Stage 10 learning/training flows are the next planned implementation stage; meaningful production sessions additionally require explicit manual verification and quiz-eligibility decisions, because the production pool intentionally remains empty. Examination behavior, authentication, statistics, mutable user persistence, review editing, and Windows packaging remain later stages.
