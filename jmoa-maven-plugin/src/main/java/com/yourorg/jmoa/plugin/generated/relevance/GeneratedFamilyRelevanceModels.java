package com.yourorg.jmoa.plugin.generated.relevance;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;

import java.util.List;

public final class GeneratedFamilyRelevanceModels {

    private GeneratedFamilyRelevanceModels() {
    }

    public record FamilyCensus(
        GeneratedClassFamily family,
        int staticClasses,
        long classfileBytes,
        int methods,
        int syntheticMethods,
        int bridgeMethods,
        long constantPoolEntries,
        int bootstrapMethodsClasses,
        int nearLimitMethods,
        long lvtLvttBytes,
        long attributeBytes,
        String dataCompleteness
    ) { }

    public record FamilyRuntimeReconciliation(
        GeneratedClassFamily family,
        int staticClasses,
        int runtimeLoadedClasses,
        int staticAndLoadedClasses,
        int runtimeOnlyClasses,
        long histogramInstances,
        long histogramBytes,
        String runtimeUniverse,
        String classLoaderSummary,
        String relevance
    ) { }

    public record FamilySafety(
        GeneratedClassFamily family,
        String semanticRole,
        String mutationRisk,
        String defaultAdmissionState,
        boolean proxyDispatchSensitive,
        boolean classLoaderSensitive,
        boolean reflectionSensitive,
        List<String> requiredProofBeforePrototype
    ) {
        public FamilySafety {
            requiredProofBeforePrototype = requiredProofBeforePrototype == null ? List.of() : List.copyOf(requiredProofBeforePrototype);
        }
    }

    public record FamilyRoi(
        GeneratedClassFamily family,
        long staticBytes,
        int staticClasses,
        int runtimeLoadedClasses,
        long histogramBytes,
        String relevance,
        String defaultAdmissionState,
        int footprintScore,
        int runtimeScore,
        int crossServiceScore,
        int semanticRiskPenalty,
        int mutationComplexityPenalty,
        int verificationBurdenPenalty,
        int totalScore,
        String recommendation,
        List<String> reasons
    ) {
        public FamilyRoi {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public record GeneratedFamilyRelevanceReport(
        String metadataVersion,
        String generatedAt,
        String service,
        boolean diagnosticOnly,
        List<FamilyCensus> staticCensus,
        List<FamilyRuntimeReconciliation> reconciliation,
        List<FamilySafety> safetyMatrix,
        List<FamilyRoi> roiRanking,
        boolean prototypeAdmitted,
        GeneratedClassFamily admittedFamily,
        List<String> boundaries
    ) {
        public GeneratedFamilyRelevanceReport {
            staticCensus = staticCensus == null ? List.of() : List.copyOf(staticCensus);
            reconciliation = reconciliation == null ? List.of() : List.copyOf(reconciliation);
            safetyMatrix = safetyMatrix == null ? List.of() : List.copyOf(safetyMatrix);
            roiRanking = roiRanking == null ? List.of() : List.copyOf(roiRanking);
            boundaries = boundaries == null ? List.of() : List.copyOf(boundaries);
        }
    }
}
