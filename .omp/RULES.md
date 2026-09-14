# Project rules

- Never delete, rename, move, normalize, or modify original passport assets without direct user permission.
- Do not change working architecture without concrete technical justification.
- Never place business or game logic directly in JavaFX controllers.
- The domain layer must not depend on JavaFX, FXML, JDBC, or a concrete database.
- Implement persistence through repository boundaries.
- Analyze dependencies and affected call sites before large changes.
- Run relevant tests and the required build after changes.
- Inspect `git diff` before completing a task.
- Never create a Git commit without a direct user request.
- Never store credentials, tokens, private keys, or other secrets in the repository.
- Never hide, suppress, or misreport failing tests.
- Do not change the Java 25 baseline without a separate, explicit architectural decision.
