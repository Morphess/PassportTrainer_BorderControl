# Архітектура Passport Trainer

## Мета

Passport Trainer є локальним JavaFX desktop-застосунком із розділеними UI, application logic і SQLite boundaries. Read-only manifest/catalogue/review pipelines, catalogue/map/Learning/Training UI, generated Examination runtime, completed-result persistence, local users та Stage 14B.2 Admin database/domain/repository/service foundation реалізовані. Admin workflows/UI, authored/hybrid runtime providers, active-session persistence та Windows packaging залишаються майбутніми етапами.

## Шари та дозволені залежності

```text
JavaFX View (FXML/CSS)
          │
          ▼
Controller ───────► NavigationService
    │
    ▼
Service ──────────► security contracts
    │
    ▼
Repository interfaces
    ▲
    │ implemented by
SQLite repositories ─────► DatabaseConnectionProvider
                              │
                              ▼
                          SQLite JDBC

Model ◄── Controller / Service / Repository
Model ──► only Java/JDK types (never JavaFX)
```

Правила залежностей:

1. Контролер обробляє UI-події, викликає сервіси та просить `NavigationService` змінити екран. Він не виконує SQL і не завантажує FXML самостійно.
2. Сервіс координує сценарій використання та залежить від repository-контрактів через constructor injection.
3. Repository приховує спосіб зберігання. Інтерфейси не знають про JavaFX або конкретний SQLite JDBC URL.
4. SQLite-реалізації repository використовують тільки `DatabaseConnectionProvider` із шару `database`.
5. Модель не залежить від JavaFX, FXML, JDBC або конкретної БД.
6. Верхній шар може залежати від нижнього, але нижній не залежить від верхнього.

`navigation` є UI-інфраструктурою поруч із контролерами, а не бізнес-шаром. Він може використовувати JavaFX, але не повинен містити навчальну, екзаменаційну чи авторизаційну логіку.

## Компоненти запуску й UI

### `Launcher`

Звичайна Java-точка входу для Maven і майбутнього native packaging. Делегує запуск `PassportTrainerApplication`.

### `PassportTrainerApplication`

JavaFX composition root. Він визначає runtime locations, відкриває й перевіряє read-only `CatalogueApplicationContext`, створює `PassportImageLocator`, `GeneratedQuestionSetProvider`, один application-scoped `QuizSessionCoordinator`, explicit `ApplicationControllerFactory` та `NavigationService`, задає параметри головного `Stage` і відкриває START. Catalogue context створює `LearningService` і `QuizGenerationService` з того самого `PassportRepository`; окремого eligibility query або repository немає. Coordinator отримує питання через provider, а `QuizSessionLifecycleService` і application-owned suppliers залишають coordinator власником non-persisted session identity, timestamps, answers, progression і completion для Training та Examination; Learning залишається passive. Factory окремо завантажує та перевіряє map resources; помилка карти вимикає лише map action, а catalogue fallback залишається доступним. Якщо catalogue не відкривається або не проходить integrity validation, START залишається працездатним і вимикає catalogue, map і mode entry.

### `NavigationService`

Єдина точка завантаження FXML і зміни екранів:

- приймає логічний екран із реєстру `View`;
- знаходить FXML та shared CSS у classpath;
- створює `FXMLLoader` із ресурсами локалізації;
- делегує створення controller до `NavigationControllerFactory`;
- дозволяє typed one-shot initialization controller після FXML injection і до показу root;
- встановлює кореневий вузол однієї сцени;
- веде instance-local back stack із попередніх roots;
- підтримує `replace` без додавання поточного root до history та `resetTo` з очищенням history;
- перетворює відсутні ресурси й помилки FXML на `NavigationException` із контекстом.

Контролери не створюють `FXMLLoader`, `Scene` або `Stage`. Усі переходи й повернення виконуються через injected `NavigationService`; глобального navigation state немає.

### Контролери

Кожен FXML-екран має власний вузький controller:

- `StartController` показує незалежну availability catalogue/map, вимикає mode entry разом із недоступним catalogue і відкриває відповідний flow;
- `CatalogueController` відображає canonical continents/entities, membership counts, review/type labels і Unicode-normalized search;
- `WorldMapController` адаптує validated map definition і `WorldMapCatalogue` до native JavaFX map, continent shortcuts, fallback list та наявного переходу до паспортів;
- `PassportListController` відображає всі документи одного entity;
- `PassportDetailsController` показує metadata, image loading/error state та керування послідовністю зображень.
- `ModeSelectionController` відкриває реалізовані Learning, Training і generated Examination flows;
- `LearningEntitySelectionController` показує окремий deterministic список лише entities з eligible Learning material;
- `LearningBrowserController` показує локалізовані metadata та safe images, керує лише Previous/Next view state, не створює session/answers/score і передає canonical entity ID до Training configuration;
- `TrainingConfigurationController` керує лише accepted controls, делегує immutable configuration `TrainingConfigurationFactory`, запускає session через application-scoped coordinator і показує localized typed generation failures;
- `ExaminationConfirmationController` показує fixed 10-question/4-option/global policy, запускає рівно одну generated Examination session і залишає екран на typed failure;
- shared `QuestionController` відображає JavaFX-independent answer-key-free presentation, безпечно завантажує images, надсилає stable option ID coordinator-у, показує `Feedback` лише для Training і не розкриває correctness для Examination;
- `TrainingResultController` відображає completed in-memory Training summary і делегує restart, new-configuration та main-menu actions;
- `ExaminationResultController` вимагає completed Examination, показує in-memory correct/incorrect/total/percentage/duration і делегує New Examination/Main Menu без persistence.

`ApplicationControllerFactory` явно створює лише підтримувані controller types і передає `NavigationService`, вузькі сервіси, image locator, validated map definition та `ResourceBundle` через constructors. Це єдиний FXML controller factory; DI framework, service locator, controller SQL/TSV/filesystem scanning і загальний `MainController` не використовуються.

### Межа інтерактивної карти

`tools/map/generate-world-map.sh` відтворює checked-in `world-map.svg` із pinned Natural Earth 5.1.1 Ukraine-POV dataset через `mapshaper@0.7.61`, Robinson projection і topology-preserving 15% simplification. Source/output SHA-256, 249 unique `ADM0_A3` IDs, provenance та public-domain notice зафіксовані в `src/main/resources/map/`.

Map metadata використовує окремий stable `mapRegionId` namespace і explicit `mapRegionId -> canonical entityId` links. Runtime не виконує fuzzy/name/ISO matching. `WorldMapResourceLoader` блокує DTD/external entities, перевіряє asset hash і вимагає точної рівності SVG/TSV region sets. `WorldMapValidator` вимагає 249 regions = 135 linked + 114 intentionally unlinked та повне покриття 139 canonical entities через 135 links + 4 explicit list-only exclusions.

Шість presentation groups не змінюють сім canonical continent records; `AUSTRALIA_OCEANIA` об'єднує `continent:australia` і `continent:oceania` лише у presentation model. `WorldMapCatalogue` будує deduplicated fallback lists через `CountryService`. `MapViewportState` і `MapInteractionState` є pure JDK state models; `WorldMapPane` є native JavaFX renderer/input surface. Map controller не читає SVG/TSV, не зіставляє names і не виконує catalogue rules.

## Domain model

Усі класи розташовані в `ua.passporttrainer.model` і використовують лише типи JDK.

| Модель | Призначення |
|---|---|
| `Continent` | Канонічний континент, його source name і порядок відображення. |
| `Country` | Країна або інша сутність-емітент із primary continent, `EntityType`, `ReviewStatus` та aggregate document count. |
| `PassportDocument` | Логічний документ або окремий його варіант із `DocumentType`, `ReviewStatus` та image count. |
| `PassportImage` | Шлях, формат, розмір і SHA-256 конкретного зображення. |
| `DocumentType` | Канонічний тип документа. |
| `EntityType` | `COUNTRY`, `TERRITORY`, `ORGANIZATION`, `DISPUTED_ENTITY` або `AMBIGUOUS_ENTITY`. |
| `ReviewStatus` | `VERIFIED`, `AUTO_CLASSIFIED`, `NEEDS_REVIEW` або `EXCLUDED`. |
| `User` | Persisted local account із DB-generated positive ID, canonical username, mutable display name, encoded password hash, `USER`/`ADMIN`, active flag і UTC timestamps; physical delete API відсутній. |
| `UserRole` | Роль `USER` або `ADMIN`. |
| `NewUser` | Валідовані account facts без ID до database-generated insert. |
| `Question` / `QuestionLifecycleState` | Stable authored aggregate identity, `DRAFT`/`ACTIVE`/`ARCHIVED`, ADMIN author reference та UTC timestamps; це не runtime `QuizQuestion`. |
| `QuestionVersion` / `QuestionOption` | Immutable authored version із typed content shape, prompt/optional metadata, deterministic ordered options і рівно одним correct option. |
| `MediaMetadata` / `MediaReferenceType` | Metadata foundation для application-owned file або read-only passport-catalogue reference без file-upload behavior. |
| `AssessmentDefinition` / `AssessmentState` | Мінімальна assessment identity/title/lifecycle foundation; builder, immutable publication versions і execution policy ще не реалізовані. |
| `QuizQuestionType` | Точний контракт чотирьох підтримуваних стратегій генерації. |
| `QuizContent` | Sealed typed subject/option contract для country, document і document-type значень зі stable identity та окремим presentation label. |
| `QuizCandidate` | Повна canonical eligibility projection документа, image, entity, continent memberships, review state, duplicate identity та SHA-256. |
| `QuizGenerationRequest` | Immutable mode/count/options/type/scope/seed configuration однієї генерації. |
| `QuizSessionConfiguration` | Immutable accepted mode/count/options/type/scope configuration до додавання technical session identity та seed. |
| `QuizSessionLifecycle` | Explicit application state `CREATED`, `ACTIVE` або `COMPLETED`. |
| `QuizGenerationFailureReason` | Машинно-читані причини invalid request, недостатнього pool або distractors. |
| `QuizQuestion` | Typed subject, typed options, stable correct-answer ID і семантичні invariants конкретного question type. |
| `QuizSession` | Стан навчальної, тренувальної або екзаменаційної сесії. |
| `QuizAnswer` | Stable selected-answer ID, час відповіді та результат її оцінювання. |
| `ExamResult` | Підсумок завершеного екзамену. |

Допоміжний `QuizMode` відображає три режими: `LEARNING`, `TRAINING`, `EXAMINATION`.

`DocumentType` має один authoritative contract із рівно такими значеннями:

```text
ORDINARY_PASSPORT
DIPLOMATIC_PASSPORT
SERVICE_PASSPORT
NATIONAL_ID
UNKNOWN
```

Ці значення описують чотири підтверджені dataset-класи та невизначений тип. Початкові назви й псевдоніми на кшталт `ззп`, `ДП`, `СП`, `id` не є enum-значеннями. Окремі `TEMPORARY` або `OTHER` не вводяться без підтверджених продуктових чи dataset-вимог; неоднозначний запис залишається `UNKNOWN` до ручної перевірки.

Catalogue records використовують stable Stage 5 `String` IDs end-to-end. Це усуває окремий synthetic numeric identity space; `Continent`, `Country`, `PassportDocument`, `PassportImage` та catalogue repository/service lookup methods узгоджені з SQLite `TEXT` keys.

## Canonical manifest tooling

`ua.passporttrainer.manifest` є JDK-only tooling boundary між immutable source assets і майбутнім SQLite catalogue. `PassportManifestGenerator` читає source collection та versioned TSV corrections, створює deterministic normalized TSV catalogue і не записує нічого в source directory. `PassportManifestValidator` перевіряє stable IDs, source paths, enum/status contracts, references, duplicate relationships, ordering та quiz eligibility.

Generated catalogue нормалізовано за рівнями continent, entity, document та image. Exact duplicate files залишаються окремими records і посилаються на deterministic representative. `VERIFIED`, `AUTO_CLASSIFIED`, `NEEDS_REVIEW`, `EXCLUDED` є повним review-status contract; `quizEligible=true` дозволено лише для `VERIFIED` document/entity з відомим `DocumentType`.

Manifest і SQLite schema зберігають primary та alternate continent IDs через `entity_continents`. `Country` повертає primary `continentId`, а `CountryRepository.findByContinentId` шукає через усі membership rows, тому transcontinental entities з'являються в кожному відповідному canonical continent без дублювання domain identity.

## Human review and quiz activation

`data/passport-review/review-decisions.tsv` є єдиним authoritative джерелом human verification та quiz activation. Stage 5 manifest/metadata залишаються canonical classification sources і не переписуються review tooling. Runtime не читає review TSV: build-time importer валідує його проти exact manifest і materialize-ить effective state у read-only SQLite.

Review TSV має strict UTF-8/LF canonical representation, exact header і deterministic ordering за `(recordType, targetType, targetId)`. `REVIEW` підтримує лише `VERIFIED`, `NEEDS_REVIEW`, `EXCLUDED` для `ENTITY`, `DOCUMENT`, `IMAGE`; `QUIZ_ACTIVATION` підтримує лише `ENABLED`, `DISABLED` для `DOCUMENT`. Відсутній row завжди означає відсутність human approval/activation.

Entity, document та image review є незалежними. Document отримує effective `quiz_eligible=1` лише за explicit `ENABLED`, effective `VERIFIED` для entity/document/image, known `DocumentType` і canonical document/image duplicate identity. Enabled dependency не можна перевести з `VERIFIED` без попередньої explicit `DISABLED` mutation.

Per-target fingerprint schema version 1 використовує namespace `passport-trainer-review-target`, big-endian length-prefixed UTF-8 text, list counts, fixed-width integer/long та boolean encodings перед SHA-256. Entity fingerprint включає ID, canonical key, entity type, semantic display name, primary continent, sorted alternate memberships, sorted source names і manifest status. Document fingerprint включає ID, entity association, document type, title, variant, manifest status/eligibility та duplicate relationship. Image fingerprint включає ID, document ID, validated source path/filename, MIME/format, dimensions, size, content SHA-256, manifest status і duplicate relationship. Free-form notes виключено навмисно: їх редагування не змінює перевірену identity/content.

`PassportReviewTool` є editorial CLI, не JavaFX admin UI. Він list/inspect-ить canonical context, записує лише одну stable-ID mutation за операцію, повторно валідовує current source, перевіряє expected checksum, force-ить same-directory temporary file і вимагає atomic replacement. Bulk approval commands відсутні.

## Repository contracts

| Repository | Відповідальність |
|---|---|
| `ContinentRepository` | Упорядковане читання canonical continents. |
| `CountryRepository` | Читання entities, continent memberships і пошук за ID/ISO-кодом. |
| `PassportRepository` | Читання всіх документів entity, пов'язаних зображень і bounded projection канонічних quiz candidates. |
| `UserRepository` | Перевірка наявності users, пошук за ID/canonical username, DB-generated create, atomic create-if-empty для initial administrator та update локальних користувачів; delete відсутній. |
| `ResultRepository` | Збереження та читання екзаменаційних результатів. |
| `SettingsRepository` | Читання й збереження локальних налаштувань. |
| `QuestionRepository` | Create/update/read authored question aggregate identity й lifecycle; authorization не виконує. |
| `QuestionVersionRepository` | Append/read immutable authored question versions. |
| `MediaRepository` | Create/update/read media metadata без filesystem або catalogue mutation. |
| `AssessmentRepository` | Create/update/read minimal assessment definitions. |

Контракти повертають domain-моделі. SQL, назви таблиць, `ResultSet` і JDBC-винятки не просочуються в services або controllers.

`SQLiteContinentRepository`, `SQLiteCountryRepository` і `SQLitePassportRepository` розташовані в `repository.sqlite`, залежать від `DatabaseConnectionProvider`, використовують prepared statements і перетворюють `SQLException` на `RepositoryAccessException`. Вони не знають про TSV, source filesystem або JavaFX. Quiz-candidate statement виконує один bounded read із join лише потрібних entity/document/image/geography rows та вимагає `quiz_eligible=1`, три `VERIFIED` statuses, відомий `DocumentType` і відсутність document/image duplicate links.

`SQLiteResultRepository` працює лише з writable application DB, transactionally inserts canonical completed-result facts і deterministic reads за result/user ID. `UNIQUE(session_id)` плюс conflict comparison гарантують at-most-one result per session: identical retry повертає existing row, а different facts fail. Repository не залежить від passport catalogue.

`SQLiteUserRepository` працює лише з writable application DB. `UserAccountService` один раз canonicalize-ить login identity через Unicode NFKC, trim і `Locale.ROOT` lowercase, atomically створює лише одного initial `ADMIN`, створює ordinary accounts лише як `USER`, хешує password через Argon2id та виконує profile update/soft deactivation. Canonical username immutable і має database `UNIQUE`; display name non-unique/mutable.

`SQLiteQuestionRepository`, `SQLiteQuestionVersionRepository`, `SQLiteMediaRepository` і `SQLiteAssessmentRepository` працюють лише з writable application DB та не виконують authorization. Question-version append вставляє version і весь ordered option set в одній transaction; будь-яка помилка відкочує обидві таблиці. Усі reads reconstruct-ять validated domain values у deterministic order, nullable metadata зберігається без sentinel values, archive state є durable, а JDBC failures перетворюються на `RepositoryAccessException`.

## Database layer

`DatabaseConnectionProvider` є мінімальною межею отримання JDBC-з'єднання. `SQLiteConnectionProvider` формує `jdbc:sqlite:` URL із нормалізованого абсолютного `Path`, вмикає foreign keys для кожного connection і підтримує explicit read-only mode для runtime catalogue. Закривати connection зобов'язаний caller через try-with-resources.

`SQLiteMigrationRunner` застосовує ordered SQL resources, перевіряє name і SHA-256 уже застосованої migration та відмовляється від unknown/newer schema. V1 створює normalized canonical tables. V2 додає manifest/base provenance columns, strict `review_decisions`, review-source metadata та materialized effective state; V1 не змінено. Stable manifest IDs залишаються `TEXT PRIMARY KEY`; duplicate references — self foreign keys; exact source path — portable POSIX relative text.

`ApplicationDatabaseMigrationRunner` незалежно застосовує checksummed resources із `db/application-migration`, створює application schema при першому запуску та відмовляється від unknown/newer version або checksum mismatch без destructive migration. Application V1 створює `application_schema_migrations` і `examination_results`; V2 додає strict users та nullable result ownership; V3 додає лише strict `questions`, `question_versions`, `question_options`, `media_metadata` і `assessments` foundation tables. V003 references application users/content with restrictive foreign keys, preserves V1/V2 data, and rolls back as one migration transaction. Catalogue migration resources не використовуються й не змінюються.

`PassportCatalogueImporter` окремо валідовує `ManifestCatalogue`, читає canonical review source, перевіряє stable targets/fingerprints/activation до будь-якої database mutation, застосовує migrations і перебудовує catalogue у transaction. Manifest status/eligibility записуються в `manifest_review_status`/`manifest_quiz_eligible`; existing `review_status`/`quiz_eligible` містять effective materialized values. Decisions, review schema/count/checksum також копіюються у SQLite. Importer не сканує assets, не класифікує filenames і не змінює manifest/review source.

`PassportCatalogueValidator` reconstruct-ить base review inputs із SQLite, повторно обчислює fingerprints/materialization, звіряє effective statuses/eligibility та review-source checksum і лише після цього допускає runtime. `PassportCatalogueFingerprint` включає V2 provenance, decisions і effective data, тому determinism не залежить від SQLite page layout.

Reference catalogue генерується під час build/release та встановлюється як strictly read-only application data. Versioned development artifact знаходиться в `data/passport-catalogue/passport-catalogue.sqlite`; його можна перевизначити через `passporttrainer.catalogue.path`. Окрема writable application DB створюється `ApplicationDatabaseContext` за `passporttrainer.application.database.path`; default — `%LOCALAPPDATA%\PassportTrainer\passport-trainer.sqlite` на Windows і XDG user-data `passport-trainer/passport-trainer.sqlite` на Linux. Вона не має catalogue dependency.

Catalogue CLI після compilation:

```bash
java -cp <runtime-classpath> ua.passporttrainer.catalogue.PassportCatalogueTool \
  rebuild data/passport-manifest data/passport-review/review-decisions.tsv \
  <output>/passport-catalogue.sqlite
java -cp <runtime-classpath> ua.passporttrainer.catalogue.PassportCatalogueTool \
  validate <output>/passport-catalogue.sqlite
```

Review CLI:

```bash
java -cp <runtime-classpath> ua.passporttrainer.catalogue.PassportReviewTool \
  validate data/passport-manifest data/passport-review/review-decisions.tsv
```

## Passport image location

SQLite зберігає тільки canonical POSIX-style `source_relative_path`. `PassportImageLocator` є межею між presentation і deployment layout; `FileSystemPassportImageLocator`:

- отримує один configured read-only root (`passporttrainer.assets.root`; development default — `Зразки паспортних документів`);
- відхиляє absolute, Windows-drive, backslash, empty-component і traversal paths;
- resolve-ить real paths і забороняє symlink escape за межі root;
- перевіряє regular/readable file та повертає `URI`.

Controller не будує filesystem paths і не сканує source collection. Stage 7 не копіює та не змінює passport assets. Майбутній Windows package мусить розмістити read-only catalogue й image tree зі збереженою relative-path structure та передати їх locations через deployment configuration; конкретний installer layout у Stage 7 не визначений.

## Services

| Сервіс | Залежності | Межа відповідальності |
|---|---|---|
| `ContinentService` | `ContinentRepository` | Канонічний список континентів. |
| `CountryService` | `CountryRepository` | Каталог entities, continent membership і lookup. |
| `PassportService` | `PassportRepository` | Усі документи entity та їх зображення. |
| `LearningService` | `PassportRepository` | Deterministic passive Learning projections лише з `findQuizEligibleCandidates()`, додаткова перевірка `QuizCandidate.usable()`, canonical-document deduplication та entity filtering без session/score/persistence. |
| `TrainingConfigurationFactory` | немає infrastructure dependencies | Перетворення прийнятих Training controls на immutable `QuizSessionConfiguration` з fixed option count 4 без дублювання generation rules. |
| `ExaminationConfigurationFactory` | немає infrastructure dependencies | Створює fixed generated Examination configuration: 10 questions, 4 options, all four types, global scope. |
| `QuestionSetProvider` / `GeneratedQuestionSetProvider` | `QuizGenerationService` у generated implementation | Повертає validated ordered `QuestionSet` з optional assessment provenance без створення runtime session; future authored/hybrid providers залишаються окремими implementations. |
| `QuizSessionLifecycleService` | `Clock` | Одноразове приймання stable answer ID, authoritative correctness, заборона duplicate/post-completion answer та завершення лише повністю answered session. |
| `QuizSessionCoordinator` | `QuestionSetProvider`, `QuizSessionLifecycleService`, current-user/session-ID/seed suppliers | Application-scoped `CREATED → ACTIVE → COMPLETED` state; exactly-once owner capture at start, coordinator-owned session identity/timestamps/answers/progression/completion, mode-safe feedback/score, retained optional provenance, restart та non-persisted abandonment. |
| `ExamResultService` | `ResultRepository` | Єдина межа запису result: валідовує fully answered completed `EXAMINATION`, перетворює temporary anonymous `0L` на nullable persisted user і передає canonical summary repository-ю. |
| `QuizGenerationService` | `PassportRepository`, `Clock`, injectable seeded `RandomGenerator` factory | Незмінні eligibility/scope/question/distractor/randomization/uniqueness rules; один algorithm path повертає questions для generated provider або full session для direct Stage 9 callers. |
| `AuthenticationService` | `UserRepository`, `PasswordHasher`, `Clock` | Canonical username lookup, generic failure for unknown/wrong/inactive credentials, Argon2id verification та transparent rehash-on-login; повертає password-free principal. |
| `UserAccountService` | `UserRepository`, `PasswordHasher`, `Clock` | First-run detection, atomic initial `ADMIN`, ordinary `USER`, password-confirmation/account validation, canonical username ownership, DB-generated create, mutable display name та soft deactivation. |
| `AccountSessionService` | `UserAccountService`, `AuthenticationService`, `CurrentUserSession` | UI-safe orchestration first-run/registration/login/anonymous/logout/switch; повертає або зберігає лише password-free principals. |
| `AuthorizationService` | `CurrentUserSession`, `UserRepository` | Повторно вимагає authenticated persisted-active account для personal-data reads і додатково роль `ADMIN` для privileged commands; controller/repository authorization відсутня. |
| `AdminQuestionService` | `AuthorizationService`, `QuestionRepository`, `QuestionVersionRepository`, `Clock`, ID supplier | Require-ить persisted-active `ADMIN` перед кожним read/mutation; створює `DRAFT`, append-ить immutable versions, читає/list-ить і archive-ить questions; actor ID надходить лише з principal. |
| `AdminAssessmentService` | `AuthorizationService`, `AssessmentRepository`, `Clock`, ID supplier | Require-ить `ADMIN`; створює/read/list/archive-ить minimal assessment foundations без builder/publication policy. |
| `AdminMediaService` | `AuthorizationService`, `MediaRepository`, `Clock`, ID supplier | Require-ить `ADMIN`; створює/read/list/archive-ить metadata без upload/copy/delete або passport-catalogue mutation. |
| `StatisticsService` | `ResultRepository` | Виконує один bounded `findByUserId` read, повертає newest-first history і в пам'яті обчислює count/average/best/answered/correct без aggregate persistence. |
| `CurrentUserResultsService` | `CurrentUserSession`, `AuthorizationService`, `StatisticsService` | Current-account facade: anonymous повертає explicit empty optional без user-ID query, active account отримує лише власний snapshot, inactive account відхиляється до result read. |
| `SettingsService` | `SettingsRepository` | Читання/збереження параметрів без знання способу зберігання. |

`Clock` передається в lifecycle/generation/account/authentication/Admin services, щоб час детерміновано контролювався в тестах. `ApplicationDatabaseContext` owns one JavaFX-independent in-memory `CurrentUserSession`, concrete writable Admin repositories і authorization-aware Admin services; вони не inject-яться в JavaFX controllers до появи окремого Admin workflow. `AuthenticatedUser` містить лише ID/username/display name/role, без password hash. `AccountSessionService` координує existing account/authentication/session boundaries, а `CurrentUserResultsService` — authorized current-account history/statistics без repository access із JavaFX controllers. Anonymous state не перетворюється на query key `0`; persisted-active account повторно перевіряється перед `ResultRepository.findByUserId`. `QuizSessionCoordinator` викликає injected current-user supplier рівно один раз під час `start`, використовує real positive user ID або runtime anonymous `0L`; зміна current principal після start не змінює session owner, а `ExamResultService` перетворює лише `0L` на persisted `NULL`. Controllers не створюють repositories/SQLite, не зберігають global user IDs, не будують `QuizGenerationRequest` і не визначають correctness. Learning не використовує coordinator.

Stage 14B.2 реалізує Admin service/repository foundation: кожна public privileged operation викликає `AuthorizationService.requireAdmin()` до protected repository read/mutation і бере creator ID з поверненого password-free principal. Repositories залишаються role-agnostic. Повні Question Bank/media workflows, Assessment Builder/publication та authored/hybrid execution не входять до цієї межі.

`QuizQuestion` вимагає щонайменше два options з унікальними stable IDs, рівно одну correct option та type-specific shape. `QuizSession` перевіряє question/answer identity й correctness; lifecycle забороняє completion до повної відповіді. `ExamResult` зберігає canonical IDs, nullable user, assessment source/provenance, start/completion timestamps і correct/total counts; incorrect count, percentage та duration обчислюються. `AUTHORED`/`HYBRID` results вимагають immutable assessment ID/version.

Для `EXAMINATION` coordinator повертає `QuizAnswerSubmissionResult.Accepted` без correctness/correct option і не показує score до `COMPLETED`; для `TRAINING` повертається authoritative `Feedback` і доступний active score. Lifecycle invariants спільні для режимів: duplicate/post-completion answers та early completion відхиляються, а `completedAt` з'являється лише після повного набору відповідей.

## Quiz generation

Generation не використовує JavaFX, FXML, localized prompts або concrete SQLite API. `QuizGenerationService` один раз читає `QuizCandidate` records через `PassportRepository`, повторно перевіряє eligibility/status/type/canonical identity, застосовує canonical continent-membership, entity та document-type scopes, сортує candidates за stable IDs і прибирає повтори за canonical document та exact image SHA-256.

Підтримуються рівно чотири `QuizQuestionType`:

- `PASSPORT_TO_COUNTRY`: document subject, country options; distinct entities того самого canonical continent мають пріоритет, а global pool є fallback;
- `COUNTRY_TO_PASSPORT`: country subject, document options від distinct entities; документи того самого type мають пріоритет;
- `DOCUMENT_TYPE`: document subject і options лише з чотирьох відомих `DocumentType`, без `UNKNOWN`;
- `FIND_ORDINARY_PASSPORT`: ordinary-type subject marker та document options із рівно одним `ORDINARY_PASSPORT`.

Один Fisher–Yates path перемішує blueprint order, distractor pools і final options через той самий seeded random source. У сесії не повторюються semantic question key, canonical correct document або correct image SHA-256. Distractor IDs у межах питання унікальні; document options походять із уже deduplicated candidate pool.

Generation є all-or-nothing. Некоректний request повертає `INVALID_REQUEST`; недостатній eligible pool — `INSUFFICIENT_ELIGIBLE_DATA`; неможливий semantic distractor set — `INSUFFICIENT_DISTRACTORS` із question type, requested count і observed pool size. Partial session, fallback на unverified records і synthetic production content заборонені. Історично Stage 9 production catalogue мав нуль eligible records і чисто повертав structured insufficiency failure. Після explicit Stage 10 review/activation поточний production pool містить 12 eligible documents для 4 verified entities; усі чотири strategies пройшли Stage 10B production smoke.

Passive Learning використовує той самий `PassportRepository.findQuizEligibleCandidates()` boundary та `QuizCandidate.usable()` contract. `LearningService` не читає всі 521 records, не дублює Stage 9 SQL і не розширює eligibility. Для Stage 11C entity selection є окремим компактним eligible-only list: це уникає клонування або зміни Stage 8 map behavior; general catalogue/map продовжують показувати свій повний canonical scope.

Training presentation відокремлена від JavaFX через `QuestionPresentationMapper`, `QuestionPresentation`, typed subject/option projections і `QuestionLayoutKind`. Stable option IDs залишаються internal control identity; `correctAnswerId` не входить до presentation contract. Image-card options мають лише нейтральні labels, а localized document-type names створюються presentation mapper-ом. Required image loading проходить тільки через `PassportImageLocator`; помилка залишає question observable, але блокує submission.

Цей presentation contract спільний для Training та Examination: він не містить `correctAnswerId`, а neutral image labels не залежать від mode. Generic authored question content може пізніше розширити mapper/layout contract без зміни session lifecycle або controller-source boundary.

## Типовий потік виклику

Приклад відкриття паспортів entity:

```text
Catalogue View ── selected continentId/entityId ──► CatalogueController
       │                                                   │
       │                                                   ├──► ContinentService
       │                                                   └──► CountryService
       │
World Map View ── explicit mapRegionId/entityId ──► WorldMapController
       │                                                   │
       │                                                   └──► WorldMapCatalogue
       │                                                           │
       │                                                           └──► CountryService
       ▼
NavigationService ──► Passport List View
                           │ selected documentId
                           ▼
                    PassportListController ──► PassportService ──► PassportRepository
                           │
                           └──► NavigationService ──► PassportDetailsController
                                                              │
                                                              ├──► PassportService
                                                              └──► PassportImageLocator
```

Після отримання даних controllers лише перетворюють результат на view state. Вони не кешують глобальний стан програми, не застосовують quiz eligibility і не беруть на себе відповідальність інших screens.

## Створення залежностей

Runtime composition:

1. `ApplicationLocations` нормалізує configured catalogue/image locations і per-user writable application DB location;
2. `ApplicationDatabaseContext.open` створює parent directory/SQLite file, застосовує application migrations through V003 та composes result/user/authentication services разом із concrete Admin repositories/services; Admin services не передаються controller factory, бо Admin UI ще відсутній;
3. `CatalogueApplicationContext.openReadOnly` окремо створює read-only `SQLiteConnectionProvider` і перевіряє catalogue migration version/integrity;
4. catalogue context створює три read-only SQLite repositories та catalogue/Learning/generation services;
5. `PassportTrainerApplication` створює image locator, lifecycle service, application-scoped coordinator і controller factory, потім обирає first-run setup для empty user store або login;
6. composition root обгортає generator у `GeneratedQuestionSetProvider` та inject-ить coordinator-у current-user supplier із `ApplicationDatabaseContext`; coordinator, а не provider, створює runtime session;
7. factory окремо валідовує map resources, constructor-injects account/session/result services та presentation mappers у supported controllers, а `NavigationService` передає factory кожному `FXMLLoader`;
8. first-run, login, registration, START, My Results, question logout/switch та всі mode transitions використовують лише `NavigationService`; controllers не створюють concrete persistence.

Failure to open/migrate the application DB or open/validate the catalogue creates an unavailable factory: START remains loadable and disables catalogue, map and mode actions. Failure to load or validate only map resources disables map action but preserves catalogue and Learning/Training/Examination flows. Service locator і global mutable singleton-об'єкти відсутні.

## Тестові межі

- model, service і pure presentation тести не запускають JavaFX toolkit;
- repository integration-тести використовують лише temporary SQLite DB;
- map loader/validator тести перевіряють XML hardening, asset integrity, exact region/entity coverage, explicit list-only records і high-risk mappings;
- JavaFX smoke завантажує real FXML через production navigation/controller factory і перевіряє first-run `ADMIN`, ordinary `USER`, login/anonymous/current identity, logout/switch, active-session exit, authenticated-empty та anonymous My Results states, persisted own-result presentation/statistics, Learning, Training, complete user-owned Examination, disclosure, persistence failure/retry та navigation behavior;
- UI тести не порівнюють pixel-perfect screenshots;
- `ArchitectureTest` захищає model від JavaFX imports;
- manifest тести використовують synthetic temporary datasets, а checked-in manifest test перевіряє immutable source paths;
- application SQLite integration перевіряє fresh creation, version rejection, transactional V1→V2 and V2→V3 upgrades, V3 conflict rollback, Admin repository round trips, deterministic ordering, version/option rollback, archive durability, restrictive foreign keys, typed JDBC errors, ADMIN/USER/anonymous/inactive authorization matrix, unchanged passport-catalogue bytes, user/result preservation, ownership, idempotency and statistics; production Examination integration записує лише temporary application DB.

Stage 13E integration повторно підтвердила цю композицію на clean V001/V002 DB, V1→V2 upgrade, USER/ADMIN/anonymous account flows, exactly-once Examination ownership, own-user history/statistics isolation та production JavaFX navigation. Learning не отримав user dependency; Training не отримав persistence; Examination не розкриває correctness/score до completion. Full suite і existing Maven package залишаються release baseline без нової migration або architecture path.

Stage 14B.2 зберігає ізоляцію editable/versioned Admin content від runtime: generated questions не переносяться до Question Bank, Admin services не inject-яться в existing controllers, а `QuizQuestion`, `QuizContent`, `QuizSession`, `QuestionSetProvider`, `QuizSessionCoordinator`, `ExamResult` і `ResultRepository` не залежать від нових repository implementations.

## Що навмисно не реалізовано

- persistent settings та active quiz-session persistence;
- Question Bank/media workflows, authored/hybrid providers, Assessment Builder/version publication і JavaFX Admin UI;
- explicit permissions matrix, global/Admin analytics dashboard та rankings/comparisons.

Ці компоненти додаються окремими етапами без порушення зафіксованого напрямку залежностей.
