package com.yac.gpatch.matcher;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.yac.gpatch.model.Hunk;
import com.yac.gpatch.model.Patch;
import com.yac.gpatch.model.Rule;
import com.yac.gpatch.model.Snippet;
import com.yac.gpatch.util.logging.Log;
import com.yac.gpatch.util.logging.LogLevel;

public class PatchMatcher implements Callable<List<Log>> {

    private static final String REGEX_ALTERNATIVE_SYMBOL = "|";

    private final Patch patch;
    private final List<Path> paths;

    private List<Log> logs;

    public PatchMatcher(Patch patch, List<Path> paths) {
        this.patch = patch;
        this.paths = paths;
        this.logs = new ArrayList<>();
    }

    public void match() {
        paths.forEach(path -> {
            try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
                MappedByteBuffer mappedByteBuffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0,
                        file.length());

                if (mappedByteBuffer != null) {
                    CharSequence mappedCharBuffer = Charset.defaultCharset().decode(mappedByteBuffer);

                    Map<String, List<Snippet>> snippetMap = populateSnippetMap(mappedCharBuffer);

                    boolean rulesValid = true;
                    for (Rule rule : patch.getRules()) {
                        if (snippetMap.containsKey(rule.getId()) && snippetMap.get(rule.getId()).size() > 1) {
                            logMultipleVariableDefinitions(path, rule.getName());
                        }
                    }

                    if (rulesValid) {
                        List<Snippet> replacingSnippets = new ArrayList<>();
                        patch.getHunks().stream().filter(hunk -> snippetMap.containsKey(hunk.getId())).forEach(hunk -> {
                            List<Snippet> capturedVariables = new ArrayList<>();
                            String finalText = hunk.getFinalLines().stream().collect(Collectors.joining("\n"));
                            String initialText = hunk.getInitialLines().stream().collect(Collectors.joining("\n"));

                            boolean hunksValid = true;

                            // Check hunk final lines
                            Matcher finalTextMatcher = Patch.CAPTURED_VARIABLE_REGEX.matcher(finalText);
                            while (finalTextMatcher.find()) {
                                Rule matchingRule = patch.getRules().stream()
                                        .filter(rule -> rule.getName().equals(finalTextMatcher.group(1))).findFirst()
                                        .get();
                                if (snippetMap.get(matchingRule.getId()).isEmpty()) {
                                    hunksValid = false;
                                    logMissingVariable(path, matchingRule.getName());
                                }
                            }

                            // Check hunk initial lines
                            Matcher initialTextMatcher = Patch.CAPTURED_VARIABLE_REGEX.matcher(initialText);
                            while (initialTextMatcher.find()) {
                                Rule matchingRule = patch.getRules().stream()
                                        .filter(rule -> rule.getName().equals(initialTextMatcher.group(1))).findFirst()
                                        .get();
                                if (snippetMap.get(matchingRule.getId()).isEmpty()) {
                                    hunksValid = false;
                                    logMissingVariable(path, matchingRule.getName());
                                } else {
                                    capturedVariables
                                            .add(new Snippet(snippetMap.get(matchingRule.getId()).get(0).getText(),
                                                    initialTextMatcher.start(1), initialTextMatcher.end(1)));
                                }
                            }

                            if (hunksValid) {
                                if (capturedVariables.isEmpty()) {
                                    snippetMap.get(hunk.getId()).stream().forEach(snippet -> {
                                        replacingSnippets.add(generateReplacingSnippet(finalText, initialText,
                                                snippet.getText(), snippet.getStart(), snippet.getEnd(), snippetMap));
                                    });
                                } else {
                                    capturedVariables.sort(Comparator.comparing(Snippet::getStart).reversed());

                                    String capturedVariableText = Patch.CAPTURED_VARIABLE_REGEX.pattern();
                                    int prefixLengthOfCapturedVariableText = capturedVariableText.indexOf("(");
                                    int suffixLengthOfCapturedVariableText = capturedVariableText.length()
                                            - capturedVariableText.lastIndexOf(")") - 1;
                                    String initialTextWithCapturedVariables = initialText;
                                    for (Snippet capturedVariable : capturedVariables) {
                                        initialTextWithCapturedVariables = new StringBuilder()
                                                .append(initialTextWithCapturedVariables.substring(0,
                                                        capturedVariable.getStart()
                                                                - prefixLengthOfCapturedVariableText))
                                                .append(capturedVariable.getText())
                                                .append(initialTextWithCapturedVariables.substring(
                                                        capturedVariable.getEnd() + suffixLengthOfCapturedVariableText))
                                                .toString();
                                    }

                                    Pattern initialTextWithCapturedVariablesRegex = Pattern.compile(escapeText(
                                            initialTextWithCapturedVariables.replaceAll(Patch.ELLIPSIS_MACRO, ".*?")));

                                    snippetMap.get(hunk.getId()).stream().filter(snippet -> {
                                        return initialTextWithCapturedVariablesRegex.matcher(snippet.getText())
                                                .matches();
                                    }).forEach(snippet -> {
                                        replacingSnippets.add(generateReplacingSnippet(finalText, initialText,
                                                snippet.getText(), snippet.getStart(), snippet.getEnd(), snippetMap));
                                    });
                                }
                            }
                        });

                        if (!replacingSnippets.isEmpty()) {
                            StringBuilder newFileContent = generateNewTextFromReplacingSnippets(replacingSnippets,
                                    mappedCharBuffer);
                            writeNewFileContent(newFileContent, mappedByteBuffer, file);
                        }
                    }

                }
            } catch (Exception e) {
                String message = new StringBuilder().append("Unable to handle ").append(path.getFileName().toString())
                        .toString();
                logs.add(new Log(LogLevel.ERROR, message));
            }
        });
    }

    private String addPrefixIfNotPresent(String str, String prefix) {
        if (str.startsWith(prefix)) {
            return str;
        } else {
            return new StringBuilder().append(prefix).append(str).toString();
        }
    }

    private String addSuffixIfNotPresent(String str, String prefix) {
        if (str.endsWith(prefix)) {
            return str;
        } else {
            return new StringBuilder().append(str).append(prefix).toString();
        }
    }

    private Snippet generateReplacingSnippet(String finalText, String initialText, String matchingText,
            int startPosition, int endPosition, Map<String, List<Snippet>> snippetMap) {
        List<Snippet> innerReplacingSnippets = new ArrayList<>();

        // Replace captured variables
        Matcher capturedVariablesMatcher = Patch.CAPTURED_VARIABLE_REGEX.matcher(finalText);
        while (capturedVariablesMatcher.find()) {
            String capturedVariable = capturedVariablesMatcher.group(1);
            Rule matchingRule = patch.getRules().stream().filter(rule -> rule.getName().equals(capturedVariable))
                    .findFirst().get();

            innerReplacingSnippets.add(new Snippet(snippetMap.get(matchingRule.getId()).get(0).getText(),
                    capturedVariablesMatcher.start(1) - Patch.CAPTURING_VARIABLE_PREFIX.length(),
                    capturedVariablesMatcher.end(1) + Patch.CAPTURING_VARIABLE_SUFFIX.length()));
        }

        String replacingText = innerReplacingSnippets.isEmpty() ? ""
                : generateNewTextFromReplacingSnippets(innerReplacingSnippets, finalText).toString();

        // Replace ellipsis
        if (finalText.contains(Patch.ELLIPSIS_MACRO)) {
            Matcher ellipsisMatcher = Pattern.compile(escapeText(initialText).replaceAll(Patch.ELLIPSIS_MACRO, "(.*?)"))
                    .matcher(matchingText);
            List<String> ellipisReplacements = new ArrayList<>();
            while (ellipsisMatcher.find()) {
                String capturedContent = ellipsisMatcher.group(1);
                if (capturedContent != null) {
                    ellipisReplacements.add(capturedContent);
                }
            }
            for (String replacement : ellipisReplacements) {
                replacingText = replacingText.replace(Patch.ELLIPSIS_MACRO, replacement);
            }
        }

        replacingText = addPrefixIfNotPresent(replacingText, "\n");
        replacingText = addSuffixIfNotPresent(replacingText, "\n");

        return new Snippet(replacingText, startPosition, endPosition);
    }

    private String escapeText(String text) {
        String escapedText = text.replaceAll("\\s+", "\\\\s*") // whitespaces
                .replaceAll("\\(", "\\\\(") // left parenthesis
                .replaceAll("\\)", "\\\\)") // right parenthesis
                .replaceAll("\\{", "\\\\{") // left brace
                .replaceAll("\\}", "\\\\}") // right brace
        ;
        escapedText = addPrefixIfNotPresent(escapedText, "\\s*");
        escapedText = addSuffixIfNotPresent(escapedText, "\\s*");

        return escapedText;
    }

    private StringBuilder generateNewTextFromReplacingSnippets(List<Snippet> replacingSnippets,
            CharSequence initialText) {
        replacingSnippets.sort(Comparator.comparing(Snippet::getStart));

        StringBuilder newFileContent = new StringBuilder()
                .append(initialText.subSequence(0, replacingSnippets.get(0).getStart()));
        int i = 0;
        do {
            newFileContent = newFileContent.append(replacingSnippets.get(i).getText());
            if (i + 1 < replacingSnippets.size()) {
                newFileContent = newFileContent.append(initialText.subSequence(replacingSnippets.get(i).getEnd(),
                        replacingSnippets.get(i + 1).getStart()));
            }
            i++;
        } while (i < replacingSnippets.size());

        return newFileContent.append(initialText
                .subSequence(replacingSnippets.get(replacingSnippets.size() - 1).getEnd(), initialText.length()));
    }

    private void logMissingVariable(Path file, String variable) {
        String log = new StringBuilder().append("File ").append(file.toString()).append(": Missing variable '")
                .append(variable).append("'").toString();
        logs.add(new Log(LogLevel.INFO, log));
    }

    private void logMultipleVariableDefinitions(Path file, String variable) {
        String log = new StringBuilder().append("File ").append(file.toString()).append(": Variable '").append(variable)
                .append("' has multiple definitions").toString();
        logs.add(new Log(LogLevel.INFO, log));
    }

    private Map<String, List<Snippet>> populateSnippetMap(CharSequence mappedCharBuffer) {
        Map<String, List<Snippet>> snippetMap = Stream
                .concat(patch.getRules().stream().map(Rule::getId), patch.getHunks().stream().map(Hunk::getId))
                .collect(Collectors.toMap(id -> id, id -> new ArrayList<>()));

        patch.getRules().forEach(rule -> {
            String str = new StringBuilder().append("(?<").append(rule.getId()).append(">.+)").toString();
            Pattern regex = Pattern.compile(escapeText(rule.getLines().stream().collect(Collectors.joining("\n")))
                    .replaceAll(Patch.ELLIPSIS_MACRO, ".*?").replaceAll(Patch.CAPTURING_VARIABLE_MACRO, str));
            Matcher ruleMatcher = regex.matcher(mappedCharBuffer);
            while (ruleMatcher.find()) {
                String ruleText = ruleMatcher.group(rule.getId());
                if (ruleText != null) {
                    int groupStart = ruleMatcher.start(rule.getId());
                    int groupEnd = ruleMatcher.end(rule.getId());
                    snippetMap.get(rule.getId()).add(new Snippet(ruleText, groupStart, groupEnd));
                }
            }
        });

        patch.getHunks().forEach(hunk -> {
            String str = escapeText(hunk.getInitialLines().stream().collect(Collectors.joining("\n")))
                    .replaceAll(Patch.ELLIPSIS_MACRO, ".*?").replaceAll(Patch.CAPTURED_VARIABLE_REGEX.pattern(), ".+");

            Pattern regex = Pattern.compile(new StringBuilder().append("(?<").append(hunk.getId()).append(">")
                    .append(str).append(")").toString());
            Matcher hunkMatcher = regex.matcher(mappedCharBuffer);
            while (hunkMatcher.find()) {
                String hunkText = hunkMatcher.group(hunk.getId());
                if (hunkText != null) {
                    int groupStart = hunkMatcher.start(hunk.getId());
                    int groupEnd = hunkMatcher.end(hunk.getId());
                    snippetMap.get(hunk.getId()).add(new Snippet(hunkText, groupStart, groupEnd));
                }
            }
        });

        return snippetMap;
    }

    private void writeNewFileContent(StringBuilder newFileContent, MappedByteBuffer mappedByteBuffer,
            RandomAccessFile file) throws IOException {
        file.setLength(newFileContent.length());
        mappedByteBuffer = file.getChannel().map(MapMode.READ_WRITE, 0, file.length());
        mappedByteBuffer.clear();
        mappedByteBuffer.limit(newFileContent.length());
        mappedByteBuffer.put(newFileContent.toString().getBytes());
    }

    ///////////////////////////////////////////////////////////////////////////////

    @Override
    public List<Log> call() {
        match();

        return logs;
    }

}
