# Архітектура Passport Trainer

## Мета

Passport Trainer будується як локальний JavaFX desktop-застосунок із чітким розділенням UI, прикладної логіки й доступу до SQLite. Цей документ фіксує межі компонентів; Stage 5 реалізував read-only manifest pipeline, а Stage 6 — окремий versioned SQLite catalogue pipeline та repository implementations. Повна бізнес-логіка й catalogue UI належать наступним етапам.

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

JavaFX composition root на поточному етапі. Він створює `NavigationService`, задає параметри головного `Stage` і відкриває перший екран. У міру появи реалізацій repository тут або в окремому малому `ApplicationContext` будуть зв'язуватися залежності. Composition root не повинен перетворюватися на сервіс або містити бізнес-правила.

### `NavigationService`

Єдина точка завантаження FXML:

- приймає логічний екран із реєстру `View`;
- знаходить FXML у classpath;
- створює `FXMLLoader` із ресурсами локалізації;
- встановлює кореневий вузол сцени;
- один раз підключає CSS;
- перетворює помилки завантаження на `NavigationException` із контекстом.

Контролери не повинні створювати `FXMLLoader`. Для переходу вони отримуватимуть `NavigationService` як залежність. Коли контролерам знадобиться constructor injection, фабрика контролерів буде налаштована централізовано всередині навігаційної інфраструктури/composition root.

### Контролери

Кожен FXML-екран має власний вузький контролер: наприклад, `StartController`, у майбутньому `LoginController`, `CountryListController`, `PassportDetailsController`, `QuizController`. Загальний `MainController`, який керує всією програмою, не використовується.

Поточний `StartController` відповідає лише за стартовий екран і перевіряє коректність FXML-injection.

## Domain model

Усі класи розташовані в `ua.passporttrainer.model` і використовують лише типи JDK.

| Модель | Призначення |
|---|---|
| `Continent` | Континент у каталозі та його джерельна назва. |
| `Country` | Країна або інша сутність-емітент із посиланням на континент. |
| `PassportDocument` | Логічний документ або окремий його варіант. |
| `PassportImage` | Шлях, формат, розмір і SHA-256 конкретного зображення. |
| `DocumentType` | Канонічний тип документа. |
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

Manifest і SQLite schema зберігають primary та alternate continent IDs через `entity_continents`. `Country` повертає primary `continentId` для поточного application API; many-to-many geography не дублюється в domain record лише заради storage schema.

## Repository contracts

| Repository | Відповідальність |
|---|---|
| `CountryRepository` | Читання країн і пошук за ID/ISO-кодом. |
| `PassportRepository` | Читання документів країни та пов'язаних зображень. |
| `UserRepository` | Пошук і збереження локальних користувачів. |
| `ResultRepository` | Збереження та читання екзаменаційних результатів. |
| `SettingsRepository` | Читання й збереження локальних налаштувань. |

Контракти повертають domain-моделі. SQL, назви таблиць, `ResultSet` і JDBC-винятки не повинні просочуватися в контролери.

`SQLiteCountryRepository` і `SQLitePassportRepository` розташовані в `repository.sqlite`, залежать від `DatabaseConnectionProvider`, використовують prepared statements і перетворюють `SQLException` на `RepositoryAccessException`. Вони не знають про TSV, source filesystem або JavaFX.

## Database layer

`DatabaseConnectionProvider` є мінімальною межею отримання JDBC-з'єднання. `SQLiteConnectionProvider` формує `jdbc:sqlite:` URL із нормалізованого абсолютного `Path`, вмикає foreign keys для кожного connection і підтримує explicit read-only mode для runtime catalogue. Закривати connection зобов'язаний caller через try-with-resources.

`SQLiteMigrationRunner` застосовує ordered SQL resources, перевіряє name і SHA-256 уже застосованої migration та відмовляється від unknown/newer schema. Version 1 містить `schema_migrations`, `catalogue_metadata`, `continents`, `entities`, `entity_source_names`, `entity_continents`, `documents`, `passport_images` і `aliases`. Stable manifest IDs є `TEXT PRIMARY KEY`; duplicate references — self foreign keys; exact source path — portable POSIX relative text.

`PassportCatalogueImporter` читає тільки `ManifestCatalogue`, виконує source-independent Stage 5 contract validation, застосовує migration, перебудовує canonical rows у transaction і запускає `PassportCatalogueValidator` до commit. Importer не сканує passport assets, не класифікує filenames і не змінює manifest. `PassportCatalogueFingerprint` обчислює SHA-256 ordered logical rows, тому determinism не залежить від SQLite page layout.

Reference catalogue генерується під час build/release та встановлюється як read-only application data. Майбутня writable DB для users, settings, results і statistics є окремим persistence concern: `%LOCALAPPDATA%\\PassportTrainer` на Windows і XDG user-data location на Linux. Writable state не розміщується в installation directory.

CLI після compilation:

```bash
java -cp <runtime-classpath> ua.passporttrainer.catalogue.PassportCatalogueTool \
  rebuild data/passport-manifest <output>/passport-catalogue.sqlite
java -cp <runtime-classpath> ua.passporttrainer.catalogue.PassportCatalogueTool \
  validate <output>/passport-catalogue.sqlite
```

## Services

| Сервіс | Залежності | Межа відповідальності |
|---|---|---|
| `CountryService` | `CountryRepository` | Каталог і пошук країн. |
| `PassportService` | `PassportRepository` | Документи країни та їх зображення. |
| `QuizService` | `ResultRepository`, `Clock` | Життєвий цикл валідної сесії, одноразове приймання відповіді, одноразове завершення та збереження результату лише для завершеного `EXAMINATION`. Генерація запитань ще не реалізована. |
| `AuthenticationService` | `UserRepository`, `PasswordHasher` | Пошук активного користувача й делегування безпечної перевірки пароля. |
| `StatisticsService` | `ResultRepository` | Читання результатів і базове агрегування балів. |
| `SettingsService` | `SettingsRepository` | Читання/збереження параметрів без знання способу зберігання. |

`Clock` передається в `QuizService`, щоб час можна було детерміновано контролювати в тестах. Конкретний алгоритм хешування паролів навмисно не закодований в `AuthenticationService`: він буде окремою реалізацією `PasswordHasher`.

`QuizQuestion` вимагає непорожній prompt, щонайменше два унікальні options і належність correct answer до options. `QuizSession` вимагає непорожній набір запитань з унікальними ID, не більше однієї узгодженої відповіді на запитання та відсутність відповідей на сторонні запитання. `ExamResult` гарантує `0 <= correctAnswers <= totalQuestions`, додатну кількість запитань і невід'ємну тривалість.

## Типовий потік виклику

Приклад відкриття паспортів країни:

```text
Country screen
    │ UI event
    ▼
CountryController
    │ countryId
    ▼
PassportService
    │ findByCountryId(countryId)
    ▼
PassportRepository
    │ prepared SQL
    ▼
DatabaseConnectionProvider → SQLite JDBC
```

Після отримання даних контролер лише перетворює результат на стан свого view. Він не кешує глобальний стан програми й не бере на себе відповідальність інших екранів.

## Створення залежностей

Catalogue dependencies already compose outside JavaFX through `CatalogueApplicationContext`:

1. отримати path до packaged read-only SQLite catalogue;
2. створити read-only `SQLiteConnectionProvider`;
3. перевірити migration version та catalogue integrity;
4. створити `SQLiteCountryRepository` і `SQLitePassportRepository`;
5. передати repositories у `CountryService` і `PassportService`.

Наступний UI stage передасть потрібні сервіси й `NavigationService` конкретним controllers через централізовану controller factory. `PassportTrainerApplication` на Stage 6 навмисно не відкриває нових screens.

Service locator і глобальні mutable singleton-об'єкти не плануються. Один компонент може бути singleton за життєвим циклом, але його залежності мають залишатися явними.

## Тестові межі

- model і service тести не запускають JavaFX toolkit;
- repository integration-тести використовують лише тимчасові SQLite DB;
- controller тести підмінятимуть сервіси;
- navigation smoke-тест перевіряє наявність FXML, а повний UI-запуск перевіряється окремо;
- `ArchitectureTest` захищає точний склад enum та заборону JavaFX imports у model.
- manifest тести використовують synthetic temporary datasets, а checked-in manifest test перевіряє повне покриття immutable source paths;
- SQLite integration tests перевіряють migration, foreign keys, full import, rollback, canonical counts/content, repositories, read-only composition та logical determinism;

## Що навмисно не реалізовано

- генератори навчальних та екзаменаційних запитань;
- конкретний password hashing algorithm;
- повні екрани й переходи між ними;
- збереження активної quiz-сесії.

Ці компоненти додаються окремими етапами без порушення зафіксованого напрямку залежностей.
