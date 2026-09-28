package ua.passporttrainer.presentation;

import java.text.MessageFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.ResourceBundle;

import ua.passporttrainer.model.ExamResult;
import ua.passporttrainer.service.UserResultStatistics;

/** Maps result-domain values to localized text without exposing technical identifiers. */
public final class UserResultsPresentationMapper {

    private static final Locale DEFAULT_BUNDLE_LOCALE = Locale.forLanguageTag("uk");

    private final ResourceBundle resources;
    private final ZoneId zoneId;
    private final Locale locale;
    private final DateTimeFormatter dateTimeFormatter;

    public UserResultsPresentationMapper(ResourceBundle resources) {
        this(resources, ZoneId.systemDefault());
    }

    public UserResultsPresentationMapper(ResourceBundle resources, ZoneId zoneId) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        Locale bundleLocale = resources.getLocale();
        locale = bundleLocale == null || bundleLocale.equals(Locale.ROOT)
                ? DEFAULT_BUNDLE_LOCALE
                : bundleLocale;
        dateTimeFormatter = DateTimeFormatter.ofPattern(
                resources.getString("results.dateTimePattern"), locale
        );
    }

    public ResultHistoryItemPresentation mapResult(ExamResult result) {
        Objects.requireNonNull(result, "result");
        String source = resources.getString(
                "results.source." + result.assessmentSource().name()
        );
        return new ResultHistoryItemPresentation(
                dateTimeFormatter.format(result.completedAt().atZone(zoneId)),
                format("results.assessmentValue", source),
                Integer.toString(result.totalQuestions()),
                Integer.toString(result.correctAnswers()),
                formatPercentage(result.scorePercent()),
                format("results.durationValue", result.durationSeconds())
        );
    }

    public UserStatisticsPresentation mapStatistics(UserResultStatistics statistics) {
        Objects.requireNonNull(statistics, "statistics");
        return new UserStatisticsPresentation(
                Long.toString(statistics.completedExaminations()),
                formatOptionalPercentage(statistics.averagePercentage()),
                formatOptionalPercentage(statistics.bestPercentage()),
                Long.toString(statistics.totalAnswered()),
                Long.toString(statistics.totalCorrect())
        );
    }

    private String formatOptionalPercentage(OptionalDouble percentage) {
        return percentage.isPresent()
                ? formatPercentage(percentage.orElseThrow())
                : resources.getString("results.valueUnavailable");
    }

    private String formatPercentage(double percentage) {
        return format("results.percentageValue", percentage);
    }

    private String format(String key, Object... arguments) {
        return new MessageFormat(resources.getString(key), locale).format(arguments);
    }
}
