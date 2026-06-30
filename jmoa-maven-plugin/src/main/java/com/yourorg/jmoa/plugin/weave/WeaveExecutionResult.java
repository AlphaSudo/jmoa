package com.yourorg.jmoa.plugin.weave;

import java.util.List;

public record WeaveExecutionResult(
    boolean failFast,
    int plannedSites,
    int targetedClasses,
    int rewrittenClasses,
    int failedClasses,
    List<String> failedClassNames,
    LambdaWeaveSanitySummary sanitySummary,
    List<RewrittenClassDelta> rewrittenClassDeltas
) {

    public WeaveExecutionResult {
        failedClassNames = List.copyOf(failedClassNames);
        rewrittenClassDeltas = List.copyOf(rewrittenClassDeltas);
    }
}
