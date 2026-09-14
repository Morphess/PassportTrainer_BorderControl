# Архітектура Passport Trainer

## Мета

Passport Trainer будується як локальний JavaFX desktop-застосунок із чітким розділенням UI, прикладної логіки й доступу до SQLite. Цей документ фіксує межі компонентів; Stage 5 реалізував окремий read-only manifest pipeline, тоді як повна бізнес-логіка, SQL-схема та імпорт manifest у SQLite належать наступним етапам.

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
4. Майбутні SQLite-реалізації repository використовуватимуть тільки `DatabaseConnectionProvider` із шару `database`.
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

## Canonical manifest tooling

`ua.passporttrainer.manifest` є JDK-only tooling boundary між immutable source assets і майбутнім SQLite catalogue. `PassportManifestGenerator` читає source collection та versioned TSV corrections, створює deterministic normalized TSV catalogue і не записує нічого в source directory. `PassportManifestValidator` перевіряє stable IDs, source paths, enum/status contracts, references, duplicate relationships, ordering та quiz eligibility.

Generated catalogue нормалізовано за рівнями continent, entity, document та image. Exact duplicate files залишаються окремими records і посилаються на deterministic representative. `VERIFIED`, `AUTO_CLASSIFIED`, `NEEDS_REVIEW`, `EXCLUDED` є повним review-status contract; `quizEligible=true` дозволено лише для `VERIFIED` document/entity з відомим `DocumentType`.

Manifest зберігає primary та alternate continent IDs. Поточний `Country` record залишається без змін; перед SQLite integration потрібне окреме рішення щодо many-to-many geographic relation для transcontinental entities.

## Repository contracts

| Repository | Відповідальність |
|---|---|
| `CountryRepository` | Читання країн і пошук за ID/ISO-кодом. |
| `PassportRepository` | Читання документів країни та пов'язаних зображень. |
| `UserRepository` | Пошук і збереження локальних користувачів. |
| `ResultRepository` | Збереження та читання екзаменаційних результатів. |
| `SettingsRepository` | Читання й збереження локальних налаштувань. |

Контракти повертають domain-моделі. SQL, назви таблиць, `ResultSet` і JDBC-винятки не повинні просочуватися в контролери.

Реалізації на кшталт `SQLiteCountryRepository` будуть створені після затвердження схеми БД. Вони належатимуть підпакету `repository.sqlite` і залежатимуть від `DatabaseConnectionProvider`.

## Database layer

`DatabaseConnectionProvider` є мінімальною межею отримання JDBC-з'єднання. `SQLiteConnectionProvider` формує `jdbc:sqlite:` URL із нормалізованого абсолютного `Path` і повертає нове з'єднання; закривати його зобов'язаний repository через try-with-resources.

Database layer не знає про екрани, контролери або правила тестів. Міграції, транзакційна політика та створення схеми ще не реалізовані.

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

На наступних етапах залежності мають складатися в одному composition root:

1. визначити шлях до локальної SQLite БД;
2. створити `SQLiteConnectionProvider`;
3. створити SQLite repository-реалізації;
4. передати repository в сервіси;
5. передати потрібні сервіси й `NavigationService` конкретним контролерам через централізовану controller factory.

Service locator і глобальні mutable singleton-об'єкти не плануються. Один компонент може бути singleton за життєвим циклом, але його залежності мають залишатися явними.

## Тестові межі

- model і service тести не запускають JavaFX toolkit;
- repository integration-тести використовуватимуть тимчасову SQLite БД;
- controller тести підмінятимуть сервіси;
- navigation smoke-тест перевіряє наявність FXML, а повний UI-запуск перевіряється окремо;
- `ArchitectureTest` захищає точний склад enum та заборону JavaFX imports у model.
- manifest тести використовують synthetic temporary datasets, а checked-in manifest test перевіряє повне покриття immutable source paths;

## Що навмисно не реалізовано

- SQLite schema and migrations;
- SQLite repository implementations and ingestion of the canonical manifest;
- генератори навчальних та екзаменаційних запитань;
- конкретний password hashing algorithm;
- повні екрани й переходи між ними;
- збереження активної quiz-сесії.

Ці компоненти додаються окремими етапами без порушення зафіксованого напрямку залежностей.
