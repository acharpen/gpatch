package com.yac.gpatch.model;

import java.util.List;
import java.util.stream.Collectors;

import com.yac.gpatch.util.Identifiable;

public class Rule implements Identifiable {

    private final String filename;
    private final String name;
    private final List<String> lines;

    private final String _id;

    public Rule(String name, String filename, List<String> lines) {
        this.name = name;
        this.filename = filename;
        this.lines = lines;

        this._id = Identifiable.super.getId();
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
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (filename != null) {
            sb.append(" from ");
            sb.append(filename);
        }
        sb.append("\n");
        sb.append(lines.stream().collect(Collectors.joining("\n")));
        sb.append(lines.size() <= 1 ? "\n" : "");

        return sb.toString();
    }

}
