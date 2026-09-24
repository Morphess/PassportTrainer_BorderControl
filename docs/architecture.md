# Архітектура Passport Trainer

## Мета

Passport Trainer будується як локальний JavaFX desktop-застосунок із чітким розділенням UI, прикладної логіки й доступу до SQLite. Stage 5 реалізував read-only manifest pipeline, Stage 6 — versioned SQLite catalogue pipeline та repository implementations, Stage 7 — browse-only catalogue UI, Stage 8 — інтерактивну карту поверх тих самих canonical IDs, Stage 9 — UI-independent quiz generation engine, а Stage 10 — version-controlled human-review overlay та materialized effective catalogue state. Режими гри, quiz UI та writable user persistence належать наступним етапам.

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
| `User` | Локальний користувач; містить тільки password hash, не відкритий пароль. |
| `UserRole` | Роль `USER` або `ADMIN`. |
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
| `UserRepository` | Пошук і збереження локальних користувачів. |
| `ResultRepository` | Збереження та читання екзаменаційних результатів. |
| `SettingsRepository` | Читання й збереження локальних налаштувань. |

Контракти повертають domain-моделі. SQL, назви таблиць, `ResultSet` і JDBC-винятки не просочуються в services або controllers.

`SQLiteContinentRepository`, `SQLiteCountryRepository` і `SQLitePassportRepository` розташовані в `repository.sqlite`, залежать від `DatabaseConnectionProvider`, використовують prepared statements і перетворюють `SQLException` на `RepositoryAccessException`. Вони не знають про TSV, source filesystem або JavaFX. Quiz-candidate statement виконує один bounded read із join лише потрібних entity/document/image/geography rows та вимагає `quiz_eligible=1`, три `VERIFIED` statuses, відомий `DocumentType` і відсутність document/image duplicate links.

## Database layer

`DatabaseConnectionProvider` є мінімальною межею отримання JDBC-з'єднання. `SQLiteConnectionProvider` формує `jdbc:sqlite:` URL із нормалізованого абсолютного `Path`, вмикає foreign keys для кожного connection і підтримує explicit read-only mode для runtime catalogue. Закривати connection зобов'язаний caller через try-with-resources.

`SQLiteMigrationRunner` застосовує ordered SQL resources, перевіряє name і SHA-256 уже застосованої migration та відмовляється від unknown/newer schema. V1 створює normalized canonical tables. V2 додає manifest/base provenance columns, strict `review_decisions`, review-source metadata та materialized effective state; V1 не змінено. Stable manifest IDs залишаються `TEXT PRIMARY KEY`; duplicate references — self foreign keys; exact source path — portable POSIX relative text.

`PassportCatalogueImporter` окремо валідовує `ManifestCatalogue`, читає canonical review source, перевіряє stable targets/fingerprints/activation до будь-якої database mutation, застосовує migrations і перебудовує catalogue у transaction. Manifest status/eligibility записуються в `manifest_review_status`/`manifest_quiz_eligible`; existing `review_status`/`quiz_eligible` містять effective materialized values. Decisions, review schema/count/checksum також копіюються у SQLite. Importer не сканує assets, не класифікує filenames і не змінює manifest/review source.

`PassportCatalogueValidator` reconstruct-ить base review inputs із SQLite, повторно обчислює fingerprints/materialization, звіряє effective statuses/eligibility та review-source checksum і лише після цього допускає runtime. `PassportCatalogueFingerprint` включає V2 provenance, decisions і effective data, тому determinism не залежить від SQLite page layout.

Reference catalogue генерується під час build/release та встановлюється як read-only application data. Versioned development artifact знаходиться в `data/passport-catalogue/passport-catalogue.sqlite`; його можна перевизначити через `passporttrainer.catalogue.path`. Review TSV і editorial tooling не потрібні packaged runtime. Майбутня writable DB для users, settings, results і statistics є окремим persistence concern: `%LOCALAPPDATA%\\PassportTrainer` на Windows і XDG user-data location на Linux.

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
| `QuizSessionCoordinator` | `QuestionSetProvider`, `QuizSessionLifecycleService`, ID/seed suppliers | Application-scoped `CREATED → ACTIVE → COMPLETED` state; coordinator-owned session identity/timestamps/answers/progression/completion, mode-safe feedback/score, retained optional provenance, restart та non-persisted abandonment. |
| `ExamResultService` | `ResultRepository` | Єдина межа запису result: створення/збереження лише для fully answered completed `EXAMINATION`; concrete writable persistence залишається Stage 12C concern. |
| `QuizGenerationService` | `PassportRepository`, `Clock`, injectable seeded `RandomGenerator` factory | Незмінні eligibility/scope/question/distractor/randomization/uniqueness rules; один algorithm path повертає questions для generated provider або full session для direct Stage 9 callers. |
| `AuthenticationService` | `UserRepository`, `PasswordHasher` | Пошук активного користувача й делегування безпечної перевірки пароля. |
| `StatisticsService` | `ResultRepository` | Read-only result-history aggregation; Stage 12A retained the same repository contract for `ExamResultService` writes and statistics reads. |
| `SettingsService` | `SettingsRepository` | Читання/збереження параметрів без знання способу зберігання. |

`Clock` передається в `QuizSessionLifecycleService` і `QuizGenerationService`, щоб час можна було детерміновано контролювати в тестах. `QuizGenerationService` створює рівно один random source із request seed на одну генерацію; `GeneratedQuestionSetProvider` делегує його question-only seam і не створює temporary `QuizSession`. `QuizSessionCoordinator` створює runtime session через lifecycle service, використовує injected `LongSupplier` для session ID/seed та тимчасовий anonymous user ID `0L`, який не зберігається. Controllers не будують `QuizGenerationRequest`, не визначають correctness і не знають concrete question source. Learning не використовує coordinator. Конкретний алгоритм хешування паролів навмисно не закодований в `AuthenticationService`: він буде окремою реалізацією `PasswordHasher`.

`QuizQuestion` вимагає щонайменше два options з унікальними stable IDs, рівно одну option з `correctAnswerId` та type-specific subject/option shape. `QuizSession` вимагає непорожній набір запитань з унікальними ID, не більше однієї узгодженої відповіді на запитання, належність selected answer до options і відсутність відповідей на сторонні запитання. `QuizSessionLifecycleService` додатково не дозволяє normal completion, доки кожне питання не має відповіді. `ExamResult` гарантує `0 <= correctAnswers <= totalQuestions`, додатну кількість запитань і невід'ємну тривалість.

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

1. `ApplicationLocations` нормалізує configured catalogue/image locations;
2. `CatalogueApplicationContext.openReadOnly` створює read-only `SQLiteConnectionProvider` і перевіряє migration version та catalogue integrity;
3. context створює три SQLite repositories та `ContinentService`, `CountryService`, `PassportService`, `LearningService` і `QuizGenerationService`;
4. `PassportTrainerApplication` створює `PassportImageLocator`, `QuizSessionLifecycleService`, application-scoped `QuizSessionCoordinator` та `ApplicationControllerFactory`;
5. composition root обгортає `QuizGenerationService` у `GeneratedQuestionSetProvider` і передає provider coordinator-у; coordinator, а не provider, створює session через lifecycle service;
6. factory окремо завантажує map resources, перевіряє hash/mappings/catalogue coverage і зберігає незалежний map availability state;
7. `NavigationService` передає factory кожному `FXMLLoader`;
8. factory constructor-injects лише залежності конкретного controller; Stage 12B додає Examination confirmation/result controllers і використовує shared `QuestionController` та mode-aware abandonment boundary.

Failure to open or validate catalogue creates an unavailable factory: START remains loadable and disables catalogue, map and mode actions. Failure to load or validate only map resources disables map action but preserves catalogue and Learning/Training/Examination flows. Service locator і global mutable singleton-об'єкти відсутні.

## Тестові межі

- model, service і pure presentation тести не запускають JavaFX toolkit;
- repository integration-тести використовують лише temporary SQLite DB;
- map loader/validator тести перевіряють XML hardening, asset integrity, exact region/entity coverage, explicit list-only records і high-risk mappings;
- catalogue UI smoke-тести завантажують real FXML через production navigation/controller factory, перевіряють Learning, Training і complete 10-question Examination flows; mode-safe disclosure; typed start failure; result counts/percentage/duration; New Examination/Main Menu; abandonment cancel/confirm; question layouts/image safety; map/catalogue navigation; `replace` history semantics; і `resetTo` history clearing на graphical JavaFX toolkit;
- UI тести не порівнюють pixel-perfect screenshots;
- `ArchitectureTest` захищає точний склад enum та заборону JavaFX imports у model;
- manifest тести використовують synthetic temporary datasets, а checked-in manifest test перевіряє повне покриття immutable source paths;
- SQLite integration tests перевіряють migrations, V1-to-V2 fail-closed behavior, foreign keys, import/review/determinism та read-only composition; generator tests використовують explicit controlled candidates; production Training smoke перевіряє readable real images і normal completion; Stage 12B production Examination smoke перевіряє provider/coordinator-owned deterministic 10-question global runtime, 4 options, all four allowed types, hidden active score і normal completion без запису catalogue.

## Що навмисно не реалізовано

- writable Examination result persistence і concrete production `ResultRepository`;
- конкретний password hashing algorithm;
- authored/hybrid Question Bank sources and JavaFX Admin editor; Stage 10 review remains a separate editorial CLI.
- збереження активної quiz-сесії.

Ці компоненти додаються окремими етапами без порушення зафіксованого напрямку залежностей.
