package com.yourorg.jmoa.plugin.recommendation;

import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.AdmissionDecision;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ConfirmationScope;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ReducerAdmissionInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReducerRecommendationEngineTest {

    private final ReducerRecommendationEngine engine = new ReducerRecommendationEngine();

    @Test
    void recommendsConfirmedPublicProtocol() {
        Fixture input = confirmed("visits-service", "EXPLODED_BOOT_APP", "NO_CDS_LOW_DIRTY");
        input.scope = ConfirmationScope.PUBLIC;

        var report = engine.recommend(input.build());

        assertEquals(AdmissionDecision.RECOMMEND_CONFIRMED, report.decision());
        assertEquals(ConfirmationScope.PUBLIC, report.confirmationScope());
        assertTrue(report.protocolMatchesConfirmedScope());
        assertTrue(report.runtimePromotionAllowed());
    }

    @Test
    void normalizesEquivalentServiceLabels() {
        Fixture input = confirmed("spring-petclinic-visits-service", "EXPLODED_BOOT_APP", "NO_CDS_LOW_DIRTY");
        input.service = "Spring PetClinic visits-service";

        assertEquals(AdmissionDecision.RECOMMEND_CONFIRMED, engine.recommend(input.build()).decision());
    }

    @Test
    void recommendsConfirmedPrivateProtocolWithoutInventingAnotherDecisionType() {
        Fixture input = confirmed("doctor-service", "SPRING_BOOT_FAT_JAR", "CDS");
        input.scope = ConfirmationScope.PRIVATE;

        var report = engine.recommend(input.build());

        assertEquals(AdmissionDecision.RECOMMEND_CONFIRMED, report.decision());
        assertEquals(ConfirmationScope.PRIVATE, report.confirmationScope());
    }

    @Test
    void requiresFreshScreenWhenConfirmedProtocolDoesNotMatch() {
        Fixture input = confirmed("customers-service", "EXPLODED_BOOT_APP", "NO_CDS_LOW_DIRTY");
        input.launchMode = "SPRING_BOOT_FAT_JAR";

        var report = engine.recommend(input.build());

        assertEquals(AdmissionDecision.RECOMMEND_SCREEN_REQUIRED, report.decision());
        assertFalse(report.protocolMatchesConfirmedScope());
        assertFalse(report.runtimePromotionAllowed());
    }

    @Test
    void requiresScreenAfterArtifactAndSemanticGates() {
        Fixture input = artifactOnly();
        input.semanticSmokePassed = true;

        assertEquals(AdmissionDecision.RECOMMEND_SCREEN_REQUIRED, engine.recommend(input.build()).decision());
    }

    @Test
    void allowsArtifactOnlyWhenSemanticSmokeIsUnavailable() {
        assertEquals(AdmissionDecision.ALLOW_ARTIFACT_ONLY, engine.recommend(artifactOnly().build()).decision());
    }

    @Test
    void keepsLowRoiApplicationClassesArtifactOnlyEvenAfterSemanticSmoke() {
        Fixture input = artifactOnly();
        input.applicationClassEvidencePresent = true;
        input.applicationBytesRemoved = 480;
        input.applicationClassesReduced = 4;
        input.semanticSmokePassed = true;

        assertEquals(AdmissionDecision.APPLICATION_LOW_ROI_ARTIFACT_ONLY, engine.recommend(input.build()).decision());
    }

    @Test
    void requiresScreenForApplicationClassesWhenRoiThresholdIsPlausible() {
        Fixture input = artifactOnly();
        input.applicationClassEvidencePresent = true;
        input.applicationBytesRemoved = 40_000;
        input.applicationClassesReduced = 4;
        input.semanticSmokePassed = true;

        assertEquals(AdmissionDecision.APPLICATION_SCREEN_REQUIRED, engine.recommend(input.build()).decision());
    }

    @Test
    void keepsGeneratedDiscoveryReportOnlyWhenRoiIsNotHigh() {
        Fixture input = new Fixture();
        input.generatedSurfaceEvidencePresent = true;
        input.generatedEstimatedBytes = 40_000;
        input.generatedClassCount = 20;
        input.reducerEngine = "raw";

        assertEquals(AdmissionDecision.GENERATED_REPORT_ONLY, engine.recommend(input.build()).decision());
    }

    @Test
    void blocksGeneratedMutationWhenUnsafeFamiliesArePresent() {
        Fixture input = new Fixture();
        input.generatedSurfaceEvidencePresent = true;
        input.generatedEstimatedBytes = 2_000_000;
        input.generatedClassCount = 1000;
        input.generatedUnsafeFamilyPresent = true;
        input.reducerEngine = "raw";

        assertEquals(AdmissionDecision.GENERATED_MUTATION_BLOCKED, engine.recommend(input.build()).decision());
    }

    @Test
    void marksHighRoiGeneratedSurfaceAsPrototypeCandidateWithoutRuntimePromotion() {
        Fixture input = new Fixture();
        input.generatedSurfaceEvidencePresent = true;
        input.generatedEstimatedBytes = 300_000;
        input.generatedClassCount = 80;
        input.generatedPrototypeAdmissionEvidencePresent = true;
        input.reducerEngine = "raw";

        var report = engine.recommend(input.build());

        assertEquals(AdmissionDecision.CANDIDATE_FOR_PROTOTYPE, report.decision());
        assertFalse(report.runtimePromotionAllowed());
    }

    @Test
    void blocksFailedRawAuditBeforeHistoricalConfirmation() {
        Fixture input = confirmed("visits-service", "EXPLODED_BOOT_APP", "NO_CDS_LOW_DIRTY");
        input.failedAudits = 1;

        assertEquals(AdmissionDecision.BLOCK_UNSAFE, engine.recommend(input.build()).decision());
    }

    @Test
    void blocksUnsupportedStrippedAttribute() {
        Fixture input = artifactOnly();
        input.strippedAttributes = List.of("LocalVariableTable", "LineNumberTable");

        assertEquals(AdmissionDecision.BLOCK_UNSAFE, engine.recommend(input.build()).decision());
    }

    @Test
    void blocksUnsafeRequestedAttributeEvenWhenArtifactLooksConfirmed() {
        Fixture input = confirmed("visits-service", "EXPLODED_BOOT_APP", "NO_CDS_LOW_DIRTY");
        input.unsafeAttributesRequested = List.of("StackMapTable");

        assertEquals(AdmissionDecision.BLOCK_UNSAFE, engine.recommend(input.build()).decision());
    }

    @Test
    void blocksSemanticFailure() {
        Fixture input = artifactOnly();
        input.semanticSmokePassed = false;

        assertEquals(AdmissionDecision.BLOCK_SEMANTIC_FAILURE, engine.recommend(input.build()).decision());
    }

    @Test
    void blocksFailedRuntimeScreen() {
        Fixture input = artifactOnly();
        input.semanticSmokePassed = true;
        input.screenVerdict = "SCREEN_FAILED";

        assertEquals(AdmissionDecision.BLOCK_RUNTIME_PROMOTION, engine.recommend(input.build()).decision());
    }

    @Test
    void blocksV2cRegression() {
        Fixture input = confirmed("service", "EXPLODED_BOOT_APP", "NO_CDS_LOW_DIRTY");
        input.v2cVerdict = "CONFIRMED_REGRESSION";

        assertEquals(AdmissionDecision.BLOCK_RUNTIME_PROMOTION, engine.recommend(input.build()).decision());
    }

    @Test
    void blocksConfirmedVerdictWithoutAttribution() {
        Fixture input = confirmed("service", "EXPLODED_BOOT_APP", "NO_CDS_LOW_DIRTY");
        input.hasV2d = false;

        assertEquals(AdmissionDecision.BLOCK_NO_EVIDENCE, engine.recommend(input.build()).decision());
    }

    @Test
    void blocksMissingArtifactEvidence() {
        Fixture input = new Fixture();
        input.reducerEngine = "raw";

        assertEquals(AdmissionDecision.BLOCK_NO_EVIDENCE, engine.recommend(input.build()).decision());
    }

    @Test
    void marksExplicitDiagnosticEvidenceDiagnosticOnly() {
        Fixture input = artifactOnly();
        input.diagnosticOnly = true;

        assertEquals(AdmissionDecision.DIAGNOSTIC_ONLY, engine.recommend(input.build()).decision());
    }

    private static Fixture artifactOnly() {
        Fixture input = new Fixture();
        input.artifactBytesRemoved = 1_000_000;
        input.classesReduced = 100;
        input.artifactEvidencePresent = true;
        input.rawAuditPresent = true;
        input.reducerEngine = "raw";
        input.strippedAttributes = List.of("LocalVariableTable", "LocalVariableTypeTable");
        return input;
    }

    private static Fixture confirmed(String service, String launchMode, String runtimePolicy) {
        Fixture input = artifactOnly();
        input.service = service;
        input.launchMode = launchMode;
        input.runtimePolicy = runtimePolicy;
        input.semanticSmokePassed = true;
        input.screenVerdict = "PASSED";
        input.hasV2c = true;
        input.v2cVerdict = "CONFIRMED_WIN";
        input.hasV2d = true;
        input.confirmedService = service;
        input.confirmedLaunchMode = launchMode;
        input.confirmedRuntimePolicy = runtimePolicy;
        return input;
    }

    private static final class Fixture {
        String service = "service";
        String launchMode = "EXPLODED_BOOT_APP";
        String runtimePolicy = "NO_CDS_LOW_DIRTY";
        long artifactBytesRemoved;
        int classesReduced;
        boolean applicationClassEvidencePresent;
        long applicationBytesRemoved;
        int applicationClassesReduced;
        boolean generatedSurfaceEvidencePresent;
        long generatedEstimatedBytes;
        int generatedClassCount;
        boolean generatedRuntimeRelevant;
        boolean generatedUnsafeFamilyPresent;
        boolean generatedPrototypeAdmissionEvidencePresent;
        boolean artifactEvidencePresent;
        boolean rawAuditPresent;
        int failedAudits;
        Boolean semanticSmokePassed;
        String screenVerdict = "NOT_RUN";
        boolean hasV2c;
        String v2cVerdict = "NO_CLAIM";
        boolean hasV2d;
        boolean diagnosticOnly;
        String reducerEngine = "unknown";
        List<String> strippedAttributes = List.of();
        List<String> unsafeAttributesRequested = List.of();
        String confirmedService;
        String confirmedLaunchMode;
        String confirmedRuntimePolicy;
        ConfirmationScope scope = ConfirmationScope.UNKNOWN;

        ReducerAdmissionInput build() {
            return new ReducerAdmissionInput(
                "v2m-test-input",
                service,
                launchMode,
                runtimePolicy,
                false,
                0,
                0,
                artifactBytesRemoved,
                classesReduced,
                applicationClassEvidencePresent,
                applicationBytesRemoved,
                applicationClassesReduced,
                generatedSurfaceEvidencePresent,
                generatedEstimatedBytes,
                generatedClassCount,
                generatedRuntimeRelevant,
                generatedUnsafeFamilyPresent,
                generatedPrototypeAdmissionEvidencePresent,
                artifactEvidencePresent,
                rawAuditPresent,
                failedAudits,
                0,
                0,
                0,
                semanticSmokePassed,
                screenVerdict,
                hasV2c,
                v2cVerdict,
                hasV2d,
                diagnosticOnly,
                reducerEngine,
                strippedAttributes,
                unsafeAttributesRequested,
                confirmedService,
                confirmedLaunchMode,
                confirmedRuntimePolicy,
                scope,
                List.of()
            );
        }
    }
}
