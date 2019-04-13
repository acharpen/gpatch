package com.yac.gpatch.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.yac.gpatch.util.logging.Log;
import com.yac.gpatch.util.logging.LogLevel;
import com.yac.gpatch.model.Hunk;
import com.yac.gpatch.model.Patch;
import com.yac.gpatch.model.Rule;

public class PatchParser {

    private static final Pattern RULE_HEADER_PATTERN = Pattern
            .compile("^\\s*@@\\s*([a-zA-Z0-9-_]+)(?:\\s+from\\s+([a-zA-Z0-9-_/.]+))?\\s*@@\\s*$");
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("^\\s*@@");

    public static Patch parse(Path filePath) throws IOException {
        try (Stream<String> linesStream = Files.lines(filePath)) {
            // Remove header lines
            List<String> relevantLines = linesStream.dropWhile(line -> !SEPARATOR_PATTERN.matcher(line).find())
                    .collect(Collectors.toList());

            // Create rules
            int indexOfRuleEnd;
            List<Rule> rules = new ArrayList<>();
            while ((indexOfRuleEnd = findIndexOfPattern(relevantLines, RULE_HEADER_PATTERN)) != -1) {
                createRule(relevantLines.subList(0, indexOfRuleEnd)).ifPresent(newRule -> {
                    rules.add(newRule);
                });
                relevantLines = relevantLines.subList(indexOfRuleEnd, relevantLines.size());
            }

            // Create hunks
            int indexOfHunk;
            List<Hunk> hunks = new ArrayList<>();
            while ((indexOfHunk = findIndexOfPattern(relevantLines, SEPARATOR_PATTERN)) != -1) {
                hunks.add(createHunk(String.valueOf(hunks.size() + 1), relevantLines.subList(1, indexOfHunk)));
                relevantLines = relevantLines.subList(indexOfHunk, relevantLines.size());
            }

            // Create patch
            return createPatch(rules, hunks);
        }
    }

    public static List<Log> validate(Patch patch) {
        List<Log> logs = new ArrayList<>();

        // Check for duplicated rules
        List<Rule> duplicatedRuleList = patch.getRules().stream().collect(Collectors.groupingBy(Rule::getId)).values()
                .stream().filter(duplicates -> duplicates.size() > 1).flatMap(Collection::stream)
                .collect(Collectors.toList());
        logs.addAll(duplicatedRuleList.stream().map(rule -> {
            String text = new StringBuilder().append("Duplicated rule: ").append(rule.getId()).toString();

            return new Log(LogLevel.ERROR, text);
        }).collect(Collectors.toList()));

        // Check for undefined rules inside hunks
        Set<String> ruleSet = patch.getRules().stream().map(Rule::getName).collect(Collectors.toSet());
        List<String> undefinedRuleList = new ArrayList<>();
        for (Hunk hunk : patch.getHunks()) {
            String text = hunk.getLines().stream().collect(Collectors.joining("\n"));
            Matcher matcher = Patch.CAPTURED_VARIABLE_REGEX.matcher(text);

            while (matcher.find()) {
                String rule = matcher.group(1);
                if (!ruleSet.contains(rule)) {
                    undefinedRuleList.add(rule);
                    break;
                }
            }
        }
        logs.addAll(undefinedRuleList.stream().map(rule -> {
            String text = new StringBuilder().append("Undefined rule: ").append(rule).toString();

            return new Log(LogLevel.ERROR, text);
        }).collect(Collectors.toList()));

        return logs;
    }

    private static Hunk createHunk(String id, List<String> lines) {
        return new Hunk(id, lines);
    }

    private static Patch createPatch(List<Rule> rules, List<Hunk> hunks) {
        return new Patch(rules, hunks);
    }

    private static Optional<Rule> createRule(List<String> lines) {
        Matcher ruleHeaderMatcher = RULE_HEADER_PATTERN.matcher(lines.get(0));
        if (ruleHeaderMatcher.find()) {
            String filename = ruleHeaderMatcher.group(2);
            String id = ruleHeaderMatcher.group(1);
            List<String> ruleLines = lines.subList(1, lines.size());

            return Optional.of(new Rule(id, filename, ruleLines));
        } else {
            return Optional.empty();
        }
    }

    private static int findIndexOfPattern(List<String> lines, Pattern pattern) {
        if (lines.isEmpty() || !pattern.matcher(lines.get(0)).find()) {
            return -1;
        } else {
            int i = 1;
            while (i < lines.size() && !SEPARATOR_PATTERN.matcher(lines.get(i)).find()) {
                i++;
            }

            return i;
        }
    }

}
