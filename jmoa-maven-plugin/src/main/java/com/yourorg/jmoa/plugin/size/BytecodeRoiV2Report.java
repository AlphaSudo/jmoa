package com.yourorg.jmoa.plugin.size;

import java.util.List;

public record BytecodeRoiV2Report(
    String metadataVersion,
    String generatedAt,
    List<BytecodeRoiV2Record> candidates
) {

    public BytecodeRoiV2Report {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
