package com.yourorg.jmoa.plugin.recommendation;

import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ConfirmationScope;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.RecommendationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationInputLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void aggregatesCanonicalReducerAndRuntimeReports() throws Exception {
        Files.writeString(tempDir.resolve("attribute-size-report.json"), """
            {"classes":[
              {"debugAttributeBytes":100,"localVariableTableBytes":60},
              {"debugAttributeBytes":200,"localVariableTableBytes":90}
            ]}
            """);
        Files.writeString(tempDir.resolve("reducer-build-report.json"), """
            {
              "mutationEnabled": true,
              "engine": "raw",
              "jarCount": 2,
              "totalOriginalBytes": 5000,
              "totalReducedBytes": 4000,
              "totalRemovedBytes": 1000,
              "artifacts": [{"reducedClassCount": 4}, {"reducedClassCount": 6}]
            }
            """);
        Files.writeString(tempDir.resolve("raw-reducer-byte-preservation-report.json"), """
            {"engine":"raw","auditedClassCount":10,"failedAuditCount":0}
            """);
        Files.writeString(tempDir.resolve("v2q-application-byte-preservation-report.json"), """
            {"classesReduced":4,"bytesRemoved":480,"rawAuditsFailed":0}
            """);
        Files.writeString(tempDir.resolve("v2r-application-surface-census.json"), """
            {
              "generatedCandidateSummary": {
                "generatedClassCount": 290,
                "estimatedBytes": 1379849,
                "runtimeRelevant": false,
                "unsafeFamilyPresent": false
              }
            }
            """);
        Files.writeString(tempDir.resolve("v2f-jar-safety-report.json"), """
            {"signedJarsSkipped":1,"multiReleaseJarsSkipped":2,"sealedJarsSkipped":3}
            """);
        Files.writeString(tempDir.resolve("jmoa-reducer-manifest-v2.json"), """
            {
              "engine":"raw",
              "mutationEnabled":true,
              "strippedAttributes":["LocalVariableTable","LocalVariableTypeTable"],
              "artifacts":[{"removedBytes":1000,"classesReduced":10}]
            }
            """);
        Files.writeString(tempDir.resolve("jmoa-semantic-smoke.json"), """
            {"status":"PASSED","workloadErrors":0}
            """);
        Files.writeString(tempDir.resolve("jmoa-runtime-screen.json"), """
            {"status":"PASSED"}
            """);
        Files.writeString(tempDir.resolve("jmoa-paired-confirmation.json"), """
            {"verdict":"CONFIRMED_WIN"}
            """);
        Files.writeString(tempDir.resolve("jmoa-evidence-validation.json"), """
            {
              "runEvidence":[{"manifest":{
                "service":"visits-service",
                "launchMode":"EXPLODED_BOOT_APP",
                "runtimePolicy":"NO_CDS_LOW_DIRTY"
              }}]
            }
            """);
        Files.writeString(tempDir.resolve("jmoa-memory-attribution.json"), """
            {"v2cValid":true}
            """);

        var input = new RecommendationInputLoader().load(
            tempDir.toFile(),
            new RecommendationContext(
                "visits-service",
                "EXPLODED_BOOT_APP",
                "NO_CDS_LOW_DIRTY",
                ConfirmationScope.PUBLIC
            )
        );

        assertEquals(1000, input.artifactBytesRemoved());
        assertEquals(10, input.classesReduced());
        assertTrue(input.applicationClassEvidencePresent());
        assertEquals(480, input.applicationBytesRemoved());
        assertEquals(4, input.applicationClassesReduced());
        assertTrue(input.generatedSurfaceEvidencePresent());
        assertEquals(1_379_849, input.generatedEstimatedBytes());
        assertEquals(290, input.generatedClassCount());
        assertTrue(input.v2bAttributeReportPresent());
        assertEquals(300, input.v2bDebugAttributeBytes());
        assertEquals(150, input.v2bLocalVariableTableBytes());
        assertTrue(input.rawAuditPresent());
        assertEquals(0, input.failedAudits());
        assertEquals(1, input.signedJarsSkipped());
        assertEquals(2, input.multiReleaseJarsSkipped());
        assertEquals(3, input.sealedJarsSkipped());
        assertEquals(Boolean.TRUE, input.semanticSmokePassed());
        assertTrue(input.hasV2CConfirmation());
        assertTrue(input.hasV2DAttribution());
        assertEquals("visits-service", input.confirmedService());
        assertEquals(ConfirmationScope.PUBLIC, input.confirmationScope());
        assertEquals(12, input.sourceReports().size());
    }

    @Test
    void treatsFailedNonTargetPreservationAsAnAuditFailure() throws Exception {
        Path reports = Files.createDirectories(tempDir.resolve("jmoa-recommendation-candidate"));
        Files.writeString(reports.resolve("raw-reducer-byte-preservation-report.json"), """
            {
              "engine":"raw",
              "auditedClassCount":10,
              "failedAuditCount":0,
              "preservedNonTargetStructures":false
            }
            """);

        var input = new RecommendationInputLoader().load(
            reports.toFile(),
            new RecommendationContext("service", "MODE", "POLICY", ConfirmationScope.PUBLIC)
        );

        assertTrue(input.rawAuditPresent());
        assertEquals(1, input.failedAudits());
    }
}
