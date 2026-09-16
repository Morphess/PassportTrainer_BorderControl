package ua.passporttrainer.catalogue;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;

/** Command-line editorial review workflow; never used by the packaged runtime application. */
public final class PassportReviewTool {

    private PassportReviewTool() {
    }

    public static void main(String[] arguments) throws Exception {
        run(arguments, System.out, Clock.systemUTC());
    }

    static void run(String[] arguments, PrintStream output, Clock clock) throws Exception {
        if (arguments.length < 3) {
            throw usage();
        }
        String command = arguments[0];
        PassportReviewService service = PassportReviewService.open(
                Path.of(arguments[1]),
                Path.of(arguments[2]),
                clock
        );
        switch (command) {
            case "list-pending" -> {
                requireLength(arguments, 3, 4);
                var filter = arguments.length == 4 ? targetType(arguments[3]) : null;
                service.listPending(filter).forEach(view -> output.println(format(view)));
            }
            case "list-reviewed" -> {
                requireLength(arguments, 3, 4);
                var filter = arguments.length == 4 ? targetType(arguments[3]) : null;
                service.listReviewed(filter).forEach(view -> output.println(format(view)));
            }
            case "inspect" -> {
                requireLength(arguments, 5, 5);
                output.println(format(service.inspect(targetType(arguments[3]), arguments[4])));
            }
            case "decide" -> {
                requireLength(arguments, 7, 8);
                String note = arguments.length == 8 ? arguments[7] : "";
                output.println(format(service.decide(
                        targetType(arguments[3]),
                        arguments[4],
                        decision(arguments[5]),
                        arguments[6],
                        note
                )));
            }
            case "activate" -> {
                requireLength(arguments, 5, 6);
                String note = arguments.length == 6 ? arguments[5] : "";
                output.println(format(service.activate(arguments[3], arguments[4], note)));
            }
            case "deactivate" -> {
                requireLength(arguments, 6, 6);
                output.println(format(service.deactivate(arguments[3], arguments[4], arguments[5])));
            }
            case "validate" -> {
                requireLength(arguments, 3, 3);
                output.println(format(service.validate()));
            }
            case "report" -> {
                requireLength(arguments, 3, 3);
                PassportReviewService.Report report = service.report();
                output.printf(
                        "targets=%d reviewed=%d pending=%d decisions=%s checksum=%s%n",
                        report.totalTargets(),
                        report.reviewedTargets(),
                        report.pendingTargets(),
                        report.decisionCounts(),
                        report.sourceChecksum()
                );
            }
            default -> throw usage();
        }
    }

    private static String format(PassportReviewService.ValidationSummary summary) {
        return "decisions=" + summary.decisionCount()
                + " enabled=" + summary.enabledCount()
                + " checksum=" + summary.sourceChecksum();
    }

    private static String format(PassportReviewService.TargetView view) {
        StringBuilder output = new StringBuilder();
        line(output, "targetType", view.targetType().name());
        line(output, "targetId", view.targetId());
        line(output, "entityId", view.entity().id());
        line(output, "entityName", view.entity().displayNameUk());
        line(output, "entityType", view.entity().entityType().name());
        line(output, "entityContinents", String.join(",", joinedContinents(view)));
        line(output, "entityManifestStatus", view.entity().reviewStatus().name());
        if (view.document() != null) {
            line(output, "documentId", view.document().id());
            line(output, "documentTitle", view.document().titleUk());
            line(output, "documentType", view.document().documentType().name());
            line(output, "documentManifestStatus", view.document().reviewStatus().name());
            line(output, "documentDuplicateOf", view.document().duplicateOfDocumentId());
        }
        if (view.image() != null) {
            line(output, "imageId", view.image().id());
            line(output, "imageSourcePath", view.image().sourcePath());
            line(output, "imageFormat", view.image().format());
            line(output, "imageDimensions", view.image().width() + "x" + view.image().height());
            line(output, "imageFileSize", Long.toString(view.image().fileSize()));
            line(output, "imageSha256", view.image().sha256());
            line(output, "imageManifestStatus", view.image().reviewStatus().name());
            line(output, "imageDuplicateOf", view.image().duplicateOfImageId());
        }
        line(output, "reviewDecision", view.review() == null ? "PENDING" : view.review().decision().name());
        line(output, "reviewNote", view.review() == null ? "" : view.review().note());
        line(output, "quizActivation", view.activation() == null ? "NOT_SET" : view.activation().decision().name());
        return output.toString();
    }

    private static java.util.List<String> joinedContinents(PassportReviewService.TargetView view) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        ids.add(view.entity().primaryContinentId());
        ids.addAll(view.entity().alternateContinentIds());
        return java.util.List.copyOf(ids);
    }

    private static void line(StringBuilder output, String name, String value) {
        if (!output.isEmpty()) {
            output.append(' ');
        }
        output.append(name).append('=').append(value);
    }

    private static ReviewDecisionSource.TargetType targetType(String value) {
        try {
            return ReviewDecisionSource.TargetType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid target type: " + value, exception);
        }
    }

    private static ReviewDecisionSource.Decision decision(String value) {
        try {
            return ReviewDecisionSource.Decision.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid review decision: " + value, exception);
        }
    }

    private static void requireLength(String[] arguments, int minimum, int maximum) {
        if (arguments.length < minimum || arguments.length > maximum) {
            throw new IllegalArgumentException("Invalid arguments: " + Arrays.toString(arguments));
        }
    }

    private static IllegalArgumentException usage() {
        return new IllegalArgumentException(
                "Usage: <command> <manifest-directory> <review-source> ...; commands: "
                        + "list-pending [target-type], list-reviewed [target-type], "
                        + "inspect <target-type> <target-id>, "
                        + "decide <target-type> <target-id> <decision> <reviewer> [note], "
                        + "activate <document-id> <reviewer> [note], "
                        + "deactivate <document-id> <reviewer> <note>, validate, report"
        );
    }
}
