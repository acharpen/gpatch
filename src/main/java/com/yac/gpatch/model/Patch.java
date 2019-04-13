package com.yac.gpatch.model;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Patch {

    public static final String CAPTURING_VARIABLE_MACRO = "@<>@";
    public static final String CAPTURING_VARIABLE_PREFIX = "@<";
    public static final String CAPTURING_VARIABLE_SUFFIX = ">@";
    public static final String ELLIPSIS_MACRO = "@...@";
    public static final Pattern CAPTURED_VARIABLE_REGEX = Pattern
            .compile(CAPTURING_VARIABLE_PREFIX + "(.+?)" + CAPTURING_VARIABLE_SUFFIX);

    private final List<Rule> rules;
    private final List<Hunk> hunks;

    public Patch(List<Rule> rules, List<Hunk> hunks) {
        this.rules = rules;
        this.hunks = hunks;
    }

    public List<Hunk> getHunks() {
        return hunks;
    }

    public List<Rule> getRules() {
        return rules;
    }

    ///////////////////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return new StringBuilder().append(rules.stream().map(Rule::toString).collect(Collectors.joining("\n")))
                .append(rules.size() <= 1 ? "\n" : "")
                .append(hunks.stream().map(Hunk::toString).collect(Collectors.joining("\n")))
                .append(hunks.size() <= 1 ? "\n" : "").toString();
    }
}
