package com.yac.gpatch.model;

public class Snippet {

    private final String text;
    private final int start;
    private final int end;

    public Snippet(String text, int start, int end) {
        this.text = text;
        this.start = start;
        this.end = end;
    }

    public int getEnd() {
        return end;
    }

    public int getStart() {
        return start;
    }

    public String getText() {
        return text;
    }

}
