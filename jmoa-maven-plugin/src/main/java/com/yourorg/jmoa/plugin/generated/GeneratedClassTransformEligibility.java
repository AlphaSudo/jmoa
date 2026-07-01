package com.yourorg.jmoa.plugin.generated;

import java.util.List;

public record GeneratedClassTransformEligibility(
    String className,
    GeneratedClassFamily family,
    GeneratedClassSafetyCategory safetyCategory,
    List<String> reasons,
    List<String> allowedTransforms,
    List<String> forbiddenTransforms
) {

    public GeneratedClassTransformEligibility {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        allowedTransforms = allowedTransforms == null ? List.of() : List.copyOf(allowedTransforms);
        forbiddenTransforms = forbiddenTransforms == null ? List.of() : List.copyOf(forbiddenTransforms);
    }
}
