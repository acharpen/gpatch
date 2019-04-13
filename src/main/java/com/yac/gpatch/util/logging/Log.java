package com.yac.gpatch.util.logging;

public class Log {

    private final LogLevel level;
    private final String text;

    public Log(LogLevel level, String text) {
        this.level = level;
        this.text = text;
    }

    public void print() {
        switch (level) {
        case ERROR:
            System.err.println(new StringBuilder().append("Error: ").append(text).toString());
            break;
        case INFO:
            System.out.println(text);
            break;
        }
    }

}
