# Архітектура Passport Trainer

## Мета

Passport Trainer будується як локальний JavaFX desktop-застосунок із чітким розділенням UI, прикладної логіки й доступу до SQLite. Stage 5 реалізував read-only manifest pipeline, Stage 6 — versioned SQLite catalogue pipeline та repository implementations, а Stage 7 — browse-only catalogue UI поверх цих меж. Інтерактивна карта, quiz generation, режими гри та writable persistence належать наступним етапам.

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

JavaFX composition root. Він визначає runtime locations, відкриває й перевіряє read-only `CatalogueApplicationContext`, створює `PassportImageLocator`, explicit controller factory та `NavigationService`, задає параметри головного `Stage` і відкриває START. Якщо catalogue не відкривається або не проходить integrity validation, START залишається працездатним і показує локалізовану помилку. Composition root не містить бізнес-правил.

### `NavigationService`

Єдина точка завантаження FXML і зміни екранів:

- приймає логічний екран із реєстру `View`;
- знаходить FXML та shared CSS у classpath;
- створює `FXMLLoader` із ресурсами локалізації;
- делегує створення controller до `NavigationControllerFactory`;
- дозволяє typed one-shot initialization controller після FXML injection і до показу root;
- встановлює кореневий вузол однієї сцени;
- веде instance-local back stack із попередніх roots;
- перетворює відсутні ресурси й помилки FXML на `NavigationException` із контекстом.

Контролери не створюють `FXMLLoader`, `Scene` або `Stage`. Усі переходи й повернення виконуються через injected `NavigationService`; глобального navigation state немає.

### Контролери

Кожен FXML-екран має власний вузький controller:

- `StartController` показує availability catalogue і відкриває catalogue flow;
- `CatalogueController` відображає canonical continents/entities, membership counts, review/type labels і Unicode-normalized search;
- `PassportListController` відображає всі документи одного entity;
- `PassportDetailsController` показує metadata, image loading/error state та керування послідовністю зображень.

`CatalogueControllerFactory` явно створює лише підтримувані controller types і передає `NavigationService`, сервіси, image locator та `ResourceBundle` через constructors. DI framework, service locator, controller SQL/TSV/filesystem scanning і загальний `MainController` не використовуються.

## Domain model

Усі класи розташовані в `ua.passporttrainer.model` і використовують лише типи JDK.

| Модель | Призначення |
|---|---|
| `Continent` | Канонічний континент, його source name і порядок відображення. |
| `Country` | Країна або інша сутність-емітент із primary continent, `EntityType` і `ReviewStatus`. |
| `PassportDocument` | Логічний документ або окремий його варіант із `DocumentType`, `ReviewStatus` та image count. |
| `PassportImage` | Шлях, формат, розмір і SHA-256 конкретного зображення. |
| `DocumentType` | Канонічний тип документа. |
| `EntityType` | `COUNTRY`, `TERRITORY`, `ORGANIZATION`, `DISPUTED_ENTITY` або `AMBIGUOUS_ENTITY`. |
| `ReviewStatus` | `VERIFIED`, `AUTO_CLASSIFIED`, `NEEDS_REVIEW` або `EXCLUDED`. |
| `User` | Локальний користувач; містить тільки password hash, не відкритий пароль. |
| `UserRole` | Роль `USER` або `ADMIN`. |
| `QuizQuestion` | Запитання, варіанти й правильна відповідь. |
| `QuizSession` | Стан навчальної, тренувальної або екзаменаційної сесії. |
| `QuizAnswer` | Відповідь користувача та результат її оцінювання. |
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

## Repository contracts

| Repository | Відповідальність |
|---|---|
| `ContinentRepository` | Упорядковане читання canonical continents. |
| `CountryRepository` | Читання entities, continent memberships і пошук за ID/ISO-кодом. |
| `PassportRepository` | Читання всіх документів entity та пов'язаних зображень. |
| `UserRepository` | Пошук і збереження локальних користувачів. |
| `ResultRepository` | Збереження та читання екзаменаційних результатів. |
| `SettingsRepository` | Читання й збереження локальних налаштувань. |

Контракти повертають domain-моделі. SQL, назви таблиць, `ResultSet` і JDBC-винятки не просочуються в controllers.

`SQLiteContinentRepository`, `SQLiteCountryRepository` і `SQLitePassportRepository` розташовані в `repository.sqlite`, залежать від `DatabaseConnectionProvider`, використовують prepared statements для параметризованих lookup і перетворюють `SQLException` на `RepositoryAccessException`. Вони не знають про TSV, source filesystem або JavaFX.

## Database layer

`DatabaseConnectionProvider` є мінімальною межею отримання JDBC-з'єднання. `SQLiteConnectionProvider` формує `jdbc:sqlite:` URL із нормалізованого абсолютного `Path`, вмикає foreign keys для кожного connection і підтримує explicit read-only mode для runtime catalogue. Закривати connection зобов'язаний caller через try-with-resources.

`SQLiteMigrationRunner` застосовує ordered SQL resources, перевіряє name і SHA-256 уже застосованої migration та відмовляється від unknown/newer schema. Version 1 містить `schema_migrations`, `catalogue_metadata`, `continents`, `entities`, `entity_source_names`, `entity_continents`, `documents`, `passport_images` і `aliases`. Stable manifest IDs є `TEXT PRIMARY KEY`; duplicate references — self foreign keys; exact source path — portable POSIX relative text.

`PassportCatalogueImporter` читає тільки `ManifestCatalogue`, виконує source-independent Stage 5 contract validation, застосовує migration, перебудовує canonical rows у transaction і запускає `PassportCatalogueValidator` до commit. Importer не сканує passport assets, не класифікує filenames і не змінює manifest. `PassportCatalogueFingerprint` обчислює SHA-256 ordered logical rows, тому determinism не залежить від SQLite page layout.

Reference catalogue генерується під час build/release та встановлюється як read-only application data. Stage 7 versioned development artifact знаходиться в `data/passport-catalogue/passport-catalogue.sqlite`; його можна перевизначити через `passporttrainer.catalogue.path`. Майбутня writable DB для users, settings, results і statistics є окремим persistence concern: `%LOCALAPPDATA%\\PassportTrainer` на Windows і XDG user-data location на Linux. Writable state не розміщується в installation directory.

CLI після compilation:

```bash
java -cp <runtime-classpath> ua.passporttrainer.catalogue.PassportCatalogueTool \
  rebuild data/passport-manifest <output>/passport-catalogue.sqlite
java -cp <runtime-classpath> ua.passporttrainer.catalogue.PassportCatalogueTool \
  validate <output>/passport-catalogue.sqlite
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
| `QuizService` | `ResultRepository`, `Clock` | Життєвий цикл валідної сесії, одноразове приймання відповіді, одноразове завершення та збереження результату лише для завершеного `EXAMINATION`. Генерація запитань ще не реалізована. |
| `AuthenticationService` | `UserRepository`, `PasswordHasher` | Пошук активного користувача й делегування безпечної перевірки пароля. |
| `StatisticsService` | `ResultRepository` | Читання результатів і базове агрегування балів. |
| `SettingsService` | `SettingsRepository` | Читання/збереження параметрів без знання способу зберігання. |

`Clock` передається в `QuizService`, щоб час можна було детерміновано контролювати в тестах. Конкретний алгоритм хешування паролів навмисно не закодований в `AuthenticationService`: він буде окремою реалізацією `PasswordHasher`.

`QuizQuestion` вимагає непорожній prompt, щонайменше два унікальні options і належність correct answer до options. `QuizSession` вимагає непорожній набір запитань з унікальними ID, не більше однієї узгодженої відповіді на запитання та відсутність відповідей на сторонні запитання. `ExamResult` гарантує `0 <= correctAnswers <= totalQuestions`, додатну кількість запитань і невід'ємну тривалість.

## Типовий потік виклику

Приклад відкриття паспортів entity:

```text
Catalogue View
    │ selected continentId / entityId
    ▼
CatalogueController
    │
    ├──► ContinentService ──► ContinentRepository
    └──► CountryService   ──► CountryRepository
                                  │
                                  ▼
                             SQLite catalogue

Passport List View
    │ selected documentId
    ▼
PassportListController ──► PassportService ──► PassportRepository
    │
    └──► NavigationService ──► PassportDetailsController
                                      │
                                      ├──► PassportService
                                      └──► PassportImageLocator ──► read-only image URI
```

Після отримання даних controllers лише перетворюють результат на view state. Вони не кешують глобальний стан програми, не застосовують quiz eligibility і не беруть на себе відповідальність інших screens.

## Створення залежностей

Runtime composition:

1. `ApplicationLocations` нормалізує configured catalogue/image locations;
2. `CatalogueApplicationContext.openReadOnly` створює read-only `SQLiteConnectionProvider` і перевіряє migration version та catalogue integrity;
3. context створює три SQLite repositories та відповідні services;
4. `PassportTrainerApplication` створює `PassportImageLocator` і `CatalogueControllerFactory`;
5. `NavigationService` передає factory кожному `FXMLLoader`;
6. factory constructor-injects лише залежності конкретного controller.

Failure to open or validate catalogue creates an unavailable factory: START remains loadable, показує одну локалізовану помилку й disables catalogue action. Service locator і global mutable singleton-об'єкти відсутні.

## Тестові межі

- model, service і pure presentation тести не запускають JavaFX toolkit;
- repository integration-тести використовують лише temporary SQLite DB;
- catalogue UI smoke-тести завантажують real FXML через production navigation/controller factory, перевіряють user-observable state, real image decode, multi-image controls і back navigation на graphical JavaFX toolkit;
- UI тести не порівнюють pixel-perfect screenshots;
- `ArchitectureTest` захищає точний склад enum та заборону JavaFX imports у model;
- manifest тести використовують synthetic temporary datasets, а checked-in manifest test перевіряє повне покриття immutable source paths;
- SQLite integration tests перевіряють migration, foreign keys, full import, rollback, canonical counts/content, repositories, read-only composition та logical determinism.

## Що навмисно не реалізовано

- інтерактивна карта;
- генератори навчальних та екзаменаційних запитань;
- конкретний password hashing algorithm;
- learning, training та examination screens;
- review editor;
- збереження активної quiz-сесії.

Ці компоненти додаються окремими етапами без порушення зафіксованого напрямку залежностей.
