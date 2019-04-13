package com.yac.gpatch.model;

import java.util.List;
import java.util.stream.Collectors;

import com.yac.gpatch.util.Identifiable;

public class Hunk implements Identifiable {

    private static final String LINE_TO_ADD_MARKER = "+";
    private static final String LINE_TO_REMOVE_MARKER = "-";

    private final String name;
    private final List<String> lines;

    private final String _id;

    public Hunk(String name, List<String> lines) {
        this.name = name;
        this.lines = lines;

        this._id = Identifiable.super.getId();
    }

    public List<String> getFinalLines() {
        return lines.stream().filter(line -> !line.startsWith(LINE_TO_REMOVE_MARKER))
                .map(line -> line.startsWith(LINE_TO_ADD_MARKER) ? line.substring(1) : line)
                .collect(Collectors.toList());
    }

    public List<String> getInitialLines() {
        return lines.stream().filter(line -> !line.startsWith(LINE_TO_ADD_MARKER))
                .map(line -> line.startsWith(LINE_TO_REMOVE_MARKER) ? line.substring(1) : line)
                .collect(Collectors.toList());
    }

    public List<String> getLines() {
        return lines;
    }

    ///////////////////////////////////////////////////////////////////////////////

    public String getId() {
        return _id;
    }

    public String getName() {
        return name;
    }

    ///////////////////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return new StringBuilder().append(lines.stream().collect(Collectors.joining("\n")))
                .append(lines.size() <= 1 ? "\n" : "").toString();
    }

}
