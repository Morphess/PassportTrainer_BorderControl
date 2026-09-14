# Passport Trainer agent context

## Project

Passport Trainer is a local JavaFX educational game for learning passport documents from countries and other issuing entities. Development is performed on Fedora Linux; the target release platform is Windows 10/11 x86_64.

The current product is an architectural baseline, not a complete game. Do not describe planned screens, persistence, passport import, map interaction, or game modes as implemented until repository evidence confirms them.

## Technical baseline

- Target JDK: Java 25. JDK 25 or newer must be the active Java used by Maven Wrapper.
- Build system: Maven Wrapper, pinned to Maven 3.9.11.
- UI: JavaFX with FXML and CSS.
- Persistence direction: SQLite.
- Standard tests: `./mvnw clean test`.
- Standard package: `./mvnw package`.
- Standard development run: `./mvnw compile exec:exec`.

A global Maven installation is not required. Do not replace wrapper commands with assumptions about a system `mvn` installation.

## Architecture

The application follows this dependency direction:

```text
Controller -> Service -> Repository -> Persistence
```

- Controllers handle UI events and view state. Business and game logic belongs in services or domain components, not JavaFX controllers.
- Repository interfaces are the persistence boundary. Services depend on repository contracts, not SQLite implementations.
- The domain model must not depend on JavaFX, FXML, JDBC, or a concrete database.
- Dependencies are explicit and use constructor injection.
- FXML navigation is centralized through `NavigationService`; controllers must not create independent navigation mechanisms or load FXML directly.
- Persistence implementations may depend on SQLite/JDBC behind repository interfaces.
- Preserve working architectural decisions from completed stages. Rewrite them only when repository evidence demonstrates a concrete technical need and the change is explicitly justified.

## Passport source data

`Зразки паспортних документів/` contains source passport images. Treat this directory as immutable input data unless the user directly authorizes a specific change.

- Never automatically rename, move, delete, normalize, deduplicate, or rewrite source images.
- Canonical names, corrections, aliases, review status, and normalized paths belong in a separate manifest or persistence layer.
- Import and analysis processes must retain traceability to the original relative path.
- Do not silently include ambiguous or unreviewed source records in training or examination data.

## Workflow for large changes

1. Inspect the current repository state and applicable documentation.
2. Plan the change from verified facts and existing conventions.
3. Identify affected files, call sites, contracts, and risks.
4. Implement the smallest complete change without unrelated refactoring.
5. Run relevant tests.
6. Run the required build or package command.
7. Perform a self-review for correctness, scope, and architectural consistency.
8. Inspect `git diff` and confirm protected files are unchanged.
9. Update project-state and roadmap documentation when the delivered state changes.

Do not create a Git commit unless the user explicitly requests it.
