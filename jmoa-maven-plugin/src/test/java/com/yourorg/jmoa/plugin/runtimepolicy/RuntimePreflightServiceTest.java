package com.yourorg.jmoa.plugin.runtimepolicy;

import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyAdmissionInput;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyRecommendation;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimePreflightServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void blocksWhenExactArtifactIsNotProvided() throws Exception {
        var input = input("NO_CDS_LOW_DIRTY", "");
        var recommendation = recommendation(RuntimePolicyModels.RuntimePolicyDecision.RECOMMEND_SCREEN_REQUIRED);

        var report = new RuntimePreflightService().preflight(input, recommendation, null, null);

        assertEquals(RuntimePreflightService.Readiness.BLOCK_MISSING_ARTIFACT_HASH, report.readiness());
        assertTrue(report.blockers().getFirst().contains("artifact"));
    }

    @Test
    void requiresCdsArchiveForCdsPolicy() throws Exception {
        Path artifact = Files.writeString(tempDir.resolve("service.jar"), "artifact");
        var input = input("CDS", "");
        var recommendation = recommendation(RuntimePolicyModels.RuntimePolicyDecision.RECOMMEND_SCREEN_REQUIRED);

        var report = new RuntimePreflightService().preflight(input, recommendation, artifact.toFile(), null);

        assertEquals(RuntimePreflightService.Readiness.BLOCK_MISSING_CDS_ARCHIVE_HASH, report.readiness());
    }

    @Test
    void routesPassedSmokeToScreen() throws Exception {
        Path artifact = Files.writeString(tempDir.resolve("service.jar"), "artifact");
        var input = new RuntimePolicyAdmissionInput(
            "test", "service", "EXPLODED_BOOT_APP", "NO_CDS_LOW_DIRTY", "raw", "", "", "UNKNOWN",
            false, false, false, false, true, true, true, true, null,
            false, "NO_CLAIM", false, "NOT_RUN", false, true, RuntimePolicyScope.PUBLIC, List.of()
        );
        var recommendation = recommendation(RuntimePolicyModels.RuntimePolicyDecision.RECOMMEND_SCREEN_REQUIRED);

        var report = new RuntimePreflightService().preflight(input, recommendation, artifact.toFile(), null);

        assertEquals(RuntimePreflightService.Readiness.READY_FOR_SCREEN, report.readiness());
        assertEquals(64, report.artifact().sha256().length());
    }

    private static RuntimePolicyAdmissionInput input(String policy, String artifactSha) {
        return new RuntimePolicyAdmissionInput(
            "test", "service", "EXPLODED_BOOT_APP", policy, "raw", artifactSha, "", "UNKNOWN",
            false, false, false, false, true, true, null, null, null,
            false, "NO_CLAIM", false, "NOT_RUN", false, true, RuntimePolicyScope.PUBLIC, List.of()
        );
    }

    private static RuntimePolicyRecommendation recommendation(RuntimePolicyModels.RuntimePolicyDecision decision) {
        return new RuntimePolicyRecommendation(
            "test", "now", decision, RuntimePolicyScope.PUBLIC, false, false, "", List.of(), List.of(), List.of(),
            input("NO_CDS_LOW_DIRTY", ""), List.of()
        );
    }
}
