# Passport Trainer project state

This document records the repository state after Stage 5. It distinguishes implemented behavior from architectural contracts and planned work.

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
- One minimal JavaFX start screen loaded from FXML with CSS and a resource bundle.
- Central FXML loading and scene-root replacement through `NavigationService`.
- JDK-only domain records and enums for countries, passports, users, quiz sessions, answers, and results.
- The authoritative `DocumentType` contract is `ORDINARY_PASSPORT`, `DIPLOMATIC_PASSPORT`, `SERVICE_PASSPORT`, `NATIONAL_ID`, and `UNKNOWN`.
- Repository interfaces for countries, passports, users, results, and settings.
- Service-layer classes using constructor injection.
- `DatabaseConnectionProvider` and a basic `SQLiteConnectionProvider`.
- In-memory quiz-session lifecycle with validated questions, unique question IDs and answer options, one answer per question, immutable completion, bounded scores, and examination-only result-save delegation.
- Thirty-nine JUnit tests: the original twenty-four tests and fifteen manifest normalization, generation, validation, and source-traceability tests.
- A versioned TSV metadata layer for continent mappings, entity corrections, document-type aliases, exact source overrides, and service-file exclusions.
- A JDK-only read-only scanner, deterministic manifest generator, strict validator, and command-line entry point in `ua.passporttrainer.manifest`.
- A generated canonical TSV catalogue with 7 continents, 139 entities, 521 provisional document records, 521 source-image records, and 166 normalization aliases.
- The verified source passport dataset audit and Stage 5 manifest decisions in `docs/database-analysis.md`.

### Partially implemented

- Navigation infrastructure exists, but the only registered screen is the start view.
- Passport models, repository contract, and service exist, and the canonical manifest is generated, but no repository implementation or application integration loads those records.
- SQLite connection creation exists, but schema, migrations, and SQLite repository implementations do not.
- Quiz session creation, answer submission, completion, and result construction enforce domain invariants, but question generation and mode-specific rules do not exist.
- Learning, training, and examination are represented by `QuizMode`, but their behavior is not differentiated.
- Authentication, settings, and statistics have service and repository boundaries but no persistence implementations or UI flows.
- Localization infrastructure exists as one default resource bundle with minimal text.

### Not implemented

- SQLite catalogue ingestion from the canonical manifest.
- SQLite catalogue schema, migrations, and repository implementations.
- Country and passport catalogue screens.
- Interactive world map and country selection.
- Passport image loading in the application UI.
- Country-identification and document-type question generation.
- Random question and answer-option generation.
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
- `database` contains JDBC connection infrastructure.
- `navigation` is UI infrastructure; `NavigationService` is the centralized FXML loader.
- The current composition root wires only startup navigation. Persistence and application services are not yet connected to controllers.

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

### Known technical debt

- Domain records outside the Stage 4C quiz scope still provide limited validation of identifiers, nullability, and value ranges.
- Missing resources can escape `NavigationService` as null-related failures rather than a uniform navigation error.
- Tests cover quiz lifecycle and the manifest pipeline, but SQLite integration and full JavaFX behavior remain untested.
- The produced JAR is not a complete Windows distribution; native packaging remains absent.
- Maven 3.9.11 emits a non-fatal Guice `sun.misc.Unsafe` deprecation warning on Java 25.

### Passport dataset state

`Зразки паспортних документів/` remains the unchanged source dataset. The Stage 5 tool scans it read-only; the application does not load it or the generated manifest yet.

The repeated audit and generated manifest record:

- 521 readable passport-document images and 4 excluded `.DS_Store` service files;
- 173 physical second-level country/entity directories and 141 normalized source entity names;
- 139 canonical entity records after explicit alias mergers;
- 431 unique binary images and 87 exact-duplicate groups, represented by 90 duplicate relationships;
- 462 `AUTO_CLASSIFIED` and 59 `NEEDS_REVIEW` document records;
- 133 ordinary, 162 diplomatic, 79 service, 146 national-ID, and 1 `UNKNOWN` document-type classifications;
- inconsistent geography, naming, case, and Unicode retained as source traceability plus versioned correction metadata.

Source files must remain unchanged. Future SQLite catalogue ingestion must consume the canonical manifest rather than infer records directly from directory and filename conventions.

### Current Git/development workflow

- Inspect the working tree and relevant documentation before changes.
- Use Maven Wrapper commands rather than relying on global Maven.
- Keep JDK 25 active for Maven Wrapper.
- For significant work: inspect, plan, identify affected files, implement, test, package, self-review, inspect the diff, and update project documentation.
- Do not create Git commits unless directly requested by the user.
- Stages 4A and 4C established build and domain behavior; Stage 5 adds only the read-only manifest tool, metadata, generated catalogue, tests, and factual documentation.

### Immediate next step

Stage 5 completed the canonical passport manifest. The immediate next stage is a separately approved SQLite catalogue schema and repository implementation that imports the manifest. UI, map, quiz generation, authentication, statistics, and Windows packaging remain later stages.
