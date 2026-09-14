package ua.passporttrainer.manifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class TsvTable {
    private final List<String> header;
    private final List<Map<String, String>> rows;

    private TsvTable(List<String> header, List<Map<String, String>> rows) {
        this.header = List.copyOf(header);
        this.rows = List.copyOf(rows);
    }

    static TsvTable read(Path path, List<String> expectedHeader) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("TSV file is empty: " + path);
        }
        List<String> actualHeader = List.of(lines.getFirst().split("\\t", -1));
        if (!actualHeader.equals(expectedHeader)) {
            throw new IllegalArgumentException(
                    "Unexpected TSV header in " + path + ": " + actualHeader + "; expected " + expectedHeader
            );
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int lineNumber = 2; lineNumber <= lines.size(); lineNumber++) {
            String line = lines.get(lineNumber - 1);
            if (line.isEmpty()) {
                throw new IllegalArgumentException("Blank TSV row at " + path + ":" + lineNumber);
            }
            String[] values = line.split("\\t", -1);
            if (values.length != expectedHeader.size()) {
                throw new IllegalArgumentException(
                        "Wrong TSV column count at " + path + ":" + lineNumber
                );
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int index = 0; index < values.length; index++) {
                row.put(expectedHeader.get(index), values[index]);
            }
            rows.add(Map.copyOf(row));
        }
        return new TsvTable(expectedHeader, rows);
    }

    static void write(Path path, List<String> header, List<List<String>> rows) throws IOException {
        Files.createDirectories(path.getParent());
        StringBuilder output = new StringBuilder();
        appendRow(output, header);
        for (List<String> row : rows) {
            if (row.size() != header.size()) {
                throw new IllegalArgumentException("Wrong TSV column count for " + path);
            }
            appendRow(output, row);
        }
        Files.writeString(path, output, StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder output, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            String value = Objects.requireNonNull(values.get(index), "TSV value");
            if (value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("TSV values must not contain tabs or line breaks: " + value);
            }
            if (index > 0) {
                output.append('\t');
            }
            output.append(value);
        }
        output.append('\n');
    }

    List<Map<String, String>> rows() {
        return rows;
    }

    static String required(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required TSV value is blank: " + column);
        }
        return value;
    }
}
