package com.yourorg.jmoa.plugin.size;

public record BytecodeSizeConfig(
    int warnMethodBytes,
    int dangerMethodBytes,
    int failMethodBytes,
    boolean failOnNear64k
) {

    public static BytecodeSizeConfig defaults() {
        return new BytecodeSizeConfig(32_768, 49_152, 65_535, false);
    }
}
