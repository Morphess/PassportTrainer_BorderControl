# Passport Trainer

Passport Trainer — локальний JavaFX-застосунок для вивчення паспортних документів країн світу.

На поточному етапі проєкт містить мінімальний стартовий екран і базову Maven-конфігурацію. Оригінальна колекція зображень у каталозі `Зразки паспортних документів/` не змінюється та ще не імпортується застосунком.

## Вимоги

- Java Development Kit 25;
- Maven 3.9 або новіший;
- Linux із графічним середовищем для локальної розробки;
- цільова платформа майбутнього релізу — Windows 10/11 x86_64.

Перевірка активного JDK:

```bash
java --version
mvn --version
```

У Fedora, якщо Java 25 встановлена в `/usr/lib/jvm/java-25-openjdk`, але не вибрана системно:

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
```

## Запуск

```bash
mvn clean javafx:run
```

## Перевірка

```bash
mvn clean test
mvn clean package
```

## Каталоги

- `src/main/java/ua/passporttrainer/` — Java-код;
- `src/main/resources/` — FXML, CSS, шрифти, іконки та карти;
- `src/test/java/` — автоматичні тести;
- `docs/` — документація та аудит бази;
- `data/` — майбутні структуровані дані й схема SQLite;
- `passports/` — майбутній підготовлений read-only каталог ресурсів;
- `scripts/` — допоміжні сценарії;
- `release/` — конфігурація та результати підготовки релізів;
- `Зразки паспортних документів/` — незмінене джерело паспортних зображень.

Документація:

- [аудит джерельної бази](docs/database-analysis.md);
- [архітектура застосунку](docs/architecture.md).
