# Passport Trainer project state

This document records the repository state after Stage 4A. It distinguishes implemented behavior from architectural contracts and planned work.

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
- Repository interfaces for countries, passports, users, results, and settings.
- Service-layer classes using constructor injection.
- `DatabaseConnectionProvider` and a basic `SQLiteConnectionProvider`.
- Basic in-memory quiz-session lifecycle when a prepared question list is supplied by a caller.
- Six JUnit tests: three architecture tests and three application/resource smoke tests.
- Source passport dataset audit in `docs/database-analysis.md`.

### Partially implemented

- Navigation infrastructure exists, but the only registered screen is the start view.
- Passport models, repository contract, and service exist, but no repository implementation or application integration loads passport records.
- SQLite connection creation exists, but schema, migrations, and SQLite repository implementations do not.
- Quiz session creation, answer submission, completion, and result construction exist, but question generation and mode-specific rules do not.
- Learning, training, and examination are represented by `QuizMode`, but their behavior is not differentiated.
- Authentication, settings, and statistics have service and repository boundaries but no persistence implementations or UI flows.
- Localization infrastructure exists as one default resource bundle with minimal text.

### Not implemented

- Canonical passport manifest and importer.
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

### Known technical debt

- `QuizService` does not yet enforce one answer per question, immutable completion, examination-only result persistence, or score bounds under duplicate answers.
- Domain records provide limited validation of identifiers, nullability, and value ranges.
- The canonical `DocumentType` vocabulary must be reconciled with the terminology proposed by the dataset audit before persistence schema work.
- Missing resources can escape `NavigationService` as null-related failures rather than a uniform navigation error.
- Existing tests cover architecture and startup resources but not service behavior or persistence integration.
- The produced JAR is not a complete Windows distribution; native packaging remains absent.
- Maven 3.9.11 emits a non-fatal Guice `sun.misc.Unsafe` deprecation warning on Java 25.

### Passport dataset state

`Зразки паспортних документів/` is the unchanged source dataset and is not imported by the application.

The completed audit records:

- 521 readable passport-document images;
- 173 physical second-level country/entity directories;
- 141 normalized unique names, not all of which represent sovereign states;
- 431 unique binary images and 87 exact-duplicate groups;
- inconsistent geography, naming, document types, case, and Unicode normalization;
- ambiguous records that require manual canonical decisions.

Source files must remain unchanged. Future normalization belongs in a separate canonical manifest and SQLite catalogue while retaining original relative paths.

### Current Git/development workflow

- Inspect the working tree and relevant documentation before changes.
- Use Maven Wrapper commands rather than relying on global Maven.
- Keep JDK 25 active for Maven Wrapper.
- For significant work: inspect, plan, identify affected files, implement, test, package, self-review, inspect the diff, and update project documentation.
- Do not create Git commits unless directly requested by the user.
- Stage 4A changed build/runtime infrastructure only; it did not change application behavior.

### Immediate next step

Stage 4B establishes persistent agent context and project rules. The next implementation stage is to review domain contracts and correct `QuizService` invariants before canonical manifest and persistence work. Scope and contract changes must be justified before implementation.
