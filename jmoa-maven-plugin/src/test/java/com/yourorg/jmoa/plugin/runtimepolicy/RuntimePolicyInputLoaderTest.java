package com.yourorg.jmoa.plugin.runtimepolicy;

import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyContext;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyScope;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimeRecommendationMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimePolicyInputLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void aggregatesCanonicalCdsEvidence() throws Exception {
        Files.writeString(tempDir.resolve("doctor-confirmation.json"), """
            {
              "status":"CONFIRMED_WIN",
              "runtimeScope":{"launchMode":"SPRING_BOOT_FAT_JAR","runtimePolicy":"CDS","cdsMode":"ON","javaagentPresent":false},
              "v2c":{"verdict":"CONFIRMED_WIN"}
            }
            """);
        Files.writeString(tempDir.resolve("doctor-v2c-validation.json"), """
            {"status":"PASSED","validation":{"runtimePolicy":"CDS","cdsMode":"ON","javaagentPresent":false},"confirmation":{"verdict":"CONFIRMED_WIN"}}
            """);
        Files.writeString(tempDir.resolve("doctor-v2d-attribution.json"), """
            {"status":"PASSED","v2cValid":true}
            """);
        Files.writeString(tempDir.resolve("doctor-semantic-smoke.json"), """
            {"status":"NOT_ATTEMPTED"}
            """);
        Files.writeString(tempDir.resolve("doctor-semantic-smoke-result.json"), """
            {"status":"PASSED","workloadErrors":0}
            """);
        Files.writeString(tempDir.resolve("doctor-materialization-proof-result.json"), """
            {
              "status":"PASSED",
              "d2r":{"appJarSha256":"artifact-d2r","cdsSha256":"cds-d2r","cdsMappedInMeasuredRuns":true}
            }
            """);

        var input = new RuntimePolicyInputLoader().load(
            tempDir.toFile(),
            new RuntimePolicyContext(
                "doctor-corrected-d2",
                "SPRING_BOOT_FAT_JAR",
                "CDS",
                "raw",
                null,
                null,
                RuntimePolicyScope.PRIVATE,
                RuntimeRecommendationMode.ANALYZE
            )
        );

        assertEquals("doctor-corrected-d2", input.service());
        assertEquals("SPRING_BOOT_FAT_JAR", input.launchMode());
        assertEquals("CDS", input.runtimePolicy());
        assertEquals("raw", input.reducerEngine());
        assertEquals("artifact-d2r", input.artifactSha256());
        assertEquals("cds-d2r", input.cdsArchiveSha256());
        assertEquals(Boolean.TRUE, input.cdsEnabled());
        assertEquals(Boolean.FALSE, input.javaagentPresent());
        assertEquals(Boolean.TRUE, input.runtimeStackAvailable());
        assertEquals(Boolean.TRUE, input.runtimeMaterializationProofPresent());
        assertEquals(Boolean.TRUE, input.cdsMappedAtRuntime());
        assertEquals(Boolean.TRUE, input.semanticSmokePassed());
        assertTrue(input.hasV2CConfirmation());
        assertEquals("CONFIRMED_WIN", input.v2cVerdict());
        assertTrue(input.hasV2DAttribution());
        assertEquals(RuntimePolicyScope.PRIVATE, input.scope());
        assertEquals(5, input.sourceReports().size());
    }

    @Test
    void marksPreflightInNormalizedInput() throws Exception {
        Files.writeString(tempDir.resolve("runtime-policy-admission-input.json"), """
            {"service":"service","launchMode":"EXPLODED_BOOT_APP","runtimePolicy":"NO_CDS_LOW_DIRTY","reducerEngine":"raw","artifactEvidencePresent":true}
            """);

        var input = new RuntimePolicyInputLoader().load(
            tempDir.toFile(),
            new RuntimePolicyContext(
                null,
                null,
                null,
                null,
                null,
                null,
                RuntimePolicyScope.UNKNOWN,
                RuntimeRecommendationMode.PREFLIGHT
            )
        );

        assertTrue(input.preflight());
    }
}
