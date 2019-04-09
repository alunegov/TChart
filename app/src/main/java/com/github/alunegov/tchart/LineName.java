package com.github.alunegov.tchart;

import org.jetbrains.annotations.NotNull;

public class LineName {
    private @NotNull String name;
    private int color;

    public LineName(@NotNull String name, int color) {
        this.name = name;
        this.color = color;
    }

    public @NotNull String getName() {
        return name;
    }

    public int getColor() {
        return color;
    }
}
