package com.yourorg.jmoa.plugin.framework;

import com.yourorg.jmoa.plugin.ClassRootKind;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class FrameworkAdmissionReportAnalyzer {

    public Map<String, Object> analyze(LambdaFilterResult filterResult) {
        List<LambdaFilterDecision> frameworkDecisions = filterResult.decisions().stream()
            .filter(decision -> decision.frameworkSafetyDecision() != null)
            .filter(decision -> decision.frameworkSafetyLevel() != FrameworkSafetyLevel.APPLICATION)
            .toList();
        List<LambdaFilterDecision> deniedFrameworkDecisions = frameworkDecisions.stream()
            .filter(decision -> !decision.frameworkSafetyDecision().allowed())
            .toList();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("frameworkTotal", frameworkDecisions.size());
        report.put("frameworkAllowed", frameworkDecisions.stream().filter(decision -> decision.frameworkSafetyDecision().allowed()).count());
        report.put("frameworkDenied", deniedFrameworkDecisions.size());
        report.put("denialBreakdown", denialBreakdown(deniedFrameworkDecisions));
        report.put("rootKindBreakdown", rootKindBreakdown(frameworkDecisions));
        report.put("topDeniedOwnerClasses", topCounts(deniedFrameworkDecisions, decision -> decision.meta().ownerInternalName(), 50));
        report.put("topDeniedSamInterfaces", topCounts(deniedFrameworkDecisions, decision -> decision.meta().samInterfaceInternalName(), 50));
        report.put("topDeniedPackages", topCounts(deniedFrameworkDecisions, decision -> decision.meta().packageInternalName(), 50));
        return report;
    }

    private Map<String, Long> denialBreakdown(List<LambdaFilterDecision> deniedFrameworkDecisions) {
        Map<FrameworkAdmissionDenialReason, Long> counts = new EnumMap<>(FrameworkAdmissionDenialReason.class);
        for (FrameworkAdmissionDenialReason reason : FrameworkAdmissionDenialReason.values()) {
            counts.put(reason, 0L);
        }
        for (LambdaFilterDecision decision : deniedFrameworkDecisions) {
            FrameworkAdmissionDenialReason reason = primaryDenialReason(decision.frameworkSafetyDecision());
            counts.put(reason, counts.get(reason) + 1);
        }
        Map<String, Long> ordered = new LinkedHashMap<>();
        for (FrameworkAdmissionDenialReason reason : FrameworkAdmissionDenialReason.values()) {
            ordered.put(reason.name(), counts.get(reason));
        }
        return ordered;
    }

    private Map<String, Object> rootKindBreakdown(List<LambdaFilterDecision> frameworkDecisions) {
        Map<String, Object> breakdown = new LinkedHashMap<>();
        for (ClassRootKind rootKind : ClassRootKind.values()) {
            long total = frameworkDecisions.stream()
                .filter(decision -> decision.frameworkSafetyDecision().rootKind() == rootKind)
                .count();
            long allowed = frameworkDecisions.stream()
                .filter(decision -> decision.frameworkSafetyDecision().rootKind() == rootKind)
                .filter(decision -> decision.frameworkSafetyDecision().allowed())
                .count();
            long denied = total - allowed;

            Map<String, Object> rootSummary = new LinkedHashMap<>();
            rootSummary.put("total", total);
            rootSummary.put("allowed", allowed);
            rootSummary.put("denied", denied);
            breakdown.put(rootKind.name(), rootSummary);
        }
        return breakdown;
    }

    private List<Map<String, Object>> topCounts(
        List<LambdaFilterDecision> decisions,
        Function<LambdaFilterDecision, String> classifier,
        int limit
    ) {
        return decisions.stream()
            .collect(Collectors.groupingBy(classifier, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry::getKey))
            .limit(limit)
            .map(entry -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("value", entry.getKey());
                row.put("count", entry.getValue());
                return row;
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private FrameworkAdmissionDenialReason primaryDenialReason(FrameworkSafetyDecision decision) {
        List<FrameworkSafetyReason> reasons = decision.reasons();
        if (reasons.contains(FrameworkSafetyReason.MISSING_PROFILE)) {
            return FrameworkAdmissionDenialReason.MISSING_PROFILE;
        }
        if (reasons.contains(FrameworkSafetyReason.UNSUPPORTED_SAM_KIND)) {
            return FrameworkAdmissionDenialReason.UNSUPPORTED_SAM_KIND;
        }
        if (reasons.contains(FrameworkSafetyReason.HOT_SITE)) {
            return FrameworkAdmissionDenialReason.HOT_SITE;
        }
        if (reasons.contains(FrameworkSafetyReason.CAPTURING_LAMBDA)) {
            return FrameworkAdmissionDenialReason.CAPTURING_LAMBDA;
        }
        if (reasons.contains(FrameworkSafetyReason.SERIALIZABLE_LAMBDA)) {
            return FrameworkAdmissionDenialReason.SERIALIZABLE_LAMBDA;
        }
        if (reasons.contains(FrameworkSafetyReason.UNKNOWN_FRAMEWORK_PACKAGE)) {
            return FrameworkAdmissionDenialReason.UNKNOWN_FRAMEWORK_PACKAGE;
        }
        return FrameworkAdmissionDenialReason.DENIED_PACKAGE;
    }
}
