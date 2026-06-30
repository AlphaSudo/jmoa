package com.yourorg.jmoa.plugin.framework;

import com.yourorg.jmoa.plugin.ClassRootKind;
import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.filter.ExclusionReason;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;
import com.yourorg.jmoa.plugin.filter.LambdaTier;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameworkAdmissionReportAnalyzerTest {

    @Test
    void summarizesDeniedFrameworkReasonsAndRootKinds() {
        LambdaMeta springContext = meta("org/springframework/context/SafeThing", "java/util/function/Supplier");
        LambdaMeta springCore = meta("org/springframework/core/CoreThing", "java/util/function/Function");
        LambdaMeta unknown = meta("org/acme/framework/UnknownThing", "java/util/function/Supplier");

        LambdaFilterResult result = new LambdaFilterResult(List.of(
            LambdaFilterDecision.eligible(
                springContext,
                true,
                false,
                3L,
                LambdaTier.TIER1,
                AccessResolver.Visibility.PUBLIC,
                FrameworkSafetyDecision.allow(
                    springContext.siteKey(),
                    springContext.ownerInternalName(),
                    ClassRootKind.EXPANDED_DEPENDENCY,
                    FrameworkSafetyLevel.FRAMEWORK_SAFE,
                    List.of(FrameworkSafetyReason.SAFE_EXPANDED_DEPENDENCY_PREFIX, FrameworkSafetyReason.COLD_PROFILED_SITE, FrameworkSafetyReason.SAFE_SAM_KIND)
                )
            ),
            LambdaFilterDecision.excluded(
                springCore,
                false,
                false,
                0L,
                ExclusionReason.FRAMEWORK_SAFETY_DENIED,
                AccessResolver.Visibility.UNKNOWN,
                FrameworkSafetyDecision.deny(
                    springCore.siteKey(),
                    springCore.ownerInternalName(),
                    ClassRootKind.ADDITIONAL_DIRECTORY,
                    FrameworkSafetyLevel.FRAMEWORK_SAFE,
                    List.of(FrameworkSafetyReason.SAFE_ADDITIONAL_DIRECTORY, FrameworkSafetyReason.MISSING_PROFILE)
                )
            ),
            LambdaFilterDecision.excluded(
                unknown,
                true,
                false,
                1L,
                ExclusionReason.FRAMEWORK_SAFETY_DENIED,
                AccessResolver.Visibility.UNKNOWN,
                FrameworkSafetyDecision.deny(
                    unknown.siteKey(),
                    unknown.ownerInternalName(),
                    ClassRootKind.EXPANDED_DEPENDENCY,
                    FrameworkSafetyLevel.FRAMEWORK_UNKNOWN,
                    List.of(FrameworkSafetyReason.UNKNOWN_FRAMEWORK_PACKAGE)
                )
            )
        ));

        Map<String, Object> report = new FrameworkAdmissionReportAnalyzer().analyze(result);

        assertEquals(3, report.get("frameworkTotal"));
        assertEquals(1L, report.get("frameworkAllowed"));
        assertEquals(2, report.get("frameworkDenied"));

        @SuppressWarnings("unchecked")
        Map<String, Long> denialBreakdown = (Map<String, Long>) report.get("denialBreakdown");
        assertEquals(1L, denialBreakdown.get("MISSING_PROFILE"));
        assertEquals(1L, denialBreakdown.get("UNKNOWN_FRAMEWORK_PACKAGE"));

        @SuppressWarnings("unchecked")
        Map<String, Object> rootKindBreakdown = (Map<String, Object>) report.get("rootKindBreakdown");
        @SuppressWarnings("unchecked")
        Map<String, Object> expandedSummary = (Map<String, Object>) rootKindBreakdown.get("EXPANDED_DEPENDENCY");
        @SuppressWarnings("unchecked")
        Map<String, Object> additionalSummary = (Map<String, Object>) rootKindBreakdown.get("ADDITIONAL_DIRECTORY");
        assertEquals(2L, expandedSummary.get("total"));
        assertEquals(1L, expandedSummary.get("allowed"));
        assertEquals(1L, expandedSummary.get("denied"));
        assertEquals(1L, additionalSummary.get("denied"));
    }

    private static LambdaMeta meta(String ownerInternalName, String samInterfaceName) {
        String samInternalName = samInterfaceName.replace('.', '/');
        return new LambdaMeta(
            ownerInternalName + "::method()V#0|get|()L" + samInternalName + ";|8|java/util/LinkedHashMap::<init>()V",
            ownerInternalName,
            ownerInternalName.substring(0, ownerInternalName.lastIndexOf('/')),
            "method",
            "()V",
            0,
            "get",
            "()L" + samInternalName + ";",
            "()Ljava/lang/Object;",
            false,
            false,
            Opcodes.H_NEWINVOKESPECIAL,
            "java/util/LinkedHashMap",
            "<init>",
            "()V",
            "()Ljava/lang/Object;",
            0L
        );
    }
}
