package com.yourorg.jmoa.plugin.runtimepolicy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyRegistryEntry;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimePolicyReplaySuiteTest {

    @TempDir
    Path tempDir;

    @Test
    void replaysKnownPolicyAndArchiveMismatch() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var registry = new RuntimePolicyRegistry(List.of(new RuntimePolicyRegistryEntry(
            "doctor-cds",
            "doctor-corrected-d2",
            "SPRING_BOOT_FAT_JAR",
            "CDS",
            "raw",
            RuntimePolicyScope.PRIVATE,
            true,
            true,
            "artifact-d2r",
            "cds-d2r",
            true,
            "CONFIRMED_WIN",
            "fixture"
        )));
        Path suite = tempDir.resolve("suite.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(suite.toFile(), Map.of("cases", List.of(
            Map.of(
                "id", "confirmed",
                "expectedDecision", "RECOMMEND_CONFIRMED_POLICY",
                "expectedScope", "PRIVATE",
                "expectedProtocolMatch", true,
                "input", Map.ofEntries(
                    Map.entry("service", "doctor-corrected-d2"),
                    Map.entry("launchMode", "SPRING_BOOT_FAT_JAR"),
                    Map.entry("runtimePolicy", "CDS"),
                    Map.entry("reducerEngine", "raw"),
                    Map.entry("artifactSha256", "artifact-d2r"),
                    Map.entry("cdsArchiveSha256", "cds-d2r"),
                    Map.entry("cdsEnabled", true),
                    Map.entry("javaagentPresent", false),
                    Map.entry("artifactEvidencePresent", true),
                    Map.entry("runtimeStackAvailable", true),
                    Map.entry("semanticSmokePassed", true),
                    Map.entry("runtimeMaterializationProofPresent", true),
                    Map.entry("cdsMappedAtRuntime", true),
                    Map.entry("hasV2CConfirmation", true),
                    Map.entry("v2cVerdict", "CONFIRMED_WIN"),
                    Map.entry("hasV2DAttribution", true),
                    Map.entry("scope", "PRIVATE")
                )
            ),
            Map.of(
                "id", "old-archive",
                "expectedDecision", "BLOCK_CDS_ARCHIVE_MISMATCH",
                "expectedScope", "PRIVATE",
                "expectedProtocolMatch", true,
                "input", Map.ofEntries(
                    Map.entry("service", "doctor-corrected-d2"),
                    Map.entry("launchMode", "SPRING_BOOT_FAT_JAR"),
                    Map.entry("runtimePolicy", "CDS"),
                    Map.entry("reducerEngine", "raw"),
                    Map.entry("artifactSha256", "artifact-d2r"),
                    Map.entry("cdsArchiveSha256", "old-d2-cds"),
                    Map.entry("cdsEnabled", true),
                    Map.entry("javaagentPresent", false),
                    Map.entry("artifactEvidencePresent", true),
                    Map.entry("runtimeStackAvailable", true),
                    Map.entry("semanticSmokePassed", true),
                    Map.entry("runtimeMaterializationProofPresent", true),
                    Map.entry("cdsMappedAtRuntime", true),
                    Map.entry("hasV2CConfirmation", true),
                    Map.entry("v2cVerdict", "CONFIRMED_WIN"),
                    Map.entry("hasV2DAttribution", true),
                    Map.entry("scope", "PRIVATE")
                )
            )
        )));

        var report = new RuntimePolicyReplaySuite().replay(suite.toFile(), registry);

        assertEquals(2, report.cases());
        assertEquals(2, report.passedCases());
        assertEquals(0, report.failedCases());
    }
}
