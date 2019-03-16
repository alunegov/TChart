package com.github.alunegov.tchart;

import org.jetbrains.annotations.NotNull;

public class LineName {
    private String name;
    private int color;

    public LineName(@NotNull String name, int color) {
        this.name = name;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public int getColor() {
        return color;
    }
}
