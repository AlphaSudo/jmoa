package com.yourorg.jmoa.plugin.generated;

import java.util.List;

public record GeneratedClassClassification(
    GeneratedClassFamily family,
    List<String> indicators,
    boolean generatedLike
) {

    public GeneratedClassClassification {
        indicators = indicators == null ? List.of() : List.copyOf(indicators);
    }
}
