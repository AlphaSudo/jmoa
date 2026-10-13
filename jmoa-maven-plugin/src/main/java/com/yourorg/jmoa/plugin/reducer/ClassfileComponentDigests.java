package com.yourorg.jmoa.plugin.reducer;

public record ClassfileComponentDigests(
    String constantPoolSha256,
    String bytecodeSha256,
    String stackMapTableSha256,
    String bootstrapMethodsSha256,
    String annotationsSha256
) {
}
