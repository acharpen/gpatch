package com.yac.gpatch.util;

public interface Identifiable {

    String getName();

    default String getId() {
        return new StringBuilder().append(getClass().getSimpleName()).append(getName().replaceAll("[^A-Za-z0-9]", ""))
                .toString();
    }

}
