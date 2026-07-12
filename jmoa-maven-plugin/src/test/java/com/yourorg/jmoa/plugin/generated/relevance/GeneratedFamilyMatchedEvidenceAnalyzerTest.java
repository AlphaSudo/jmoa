package com.yourorg.jmoa.plugin.generated.relevance;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;
import com.yourorg.jmoa.plugin.generated.GeneratedClassFamilyRuntimeAttribution;
import com.yourorg.jmoa.plugin.generated.GeneratedClassInventory;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRecord;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRiskLevel;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeAttribution;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeClassRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GeneratedFamilyMatchedEvidenceAnalyzerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void refusesToJoinCapturesWithoutMatchingFingerprints() {
        var report = new GeneratedFamilyMatchedEvidenceAnalyzer().analyze(
            "service", "static-sha", "capture-sha", inventory(), runtime(), runtime(), runtime());

        assertEquals("ARTIFACT_FINGERPRINT_MISMATCH", report.evidenceStatus());
        assertFalse(report.artifactFingerprintMatch());
        assertFalse(report.prototypeAdmitted());
    }

    @Test
    void requiresAllLifecycleCapturesBeforeMatchedStatus() {
        var report = new GeneratedFamilyMatchedEvidenceAnalyzer().analyze(
            "service", "same-sha", "same-sha", inventory(), null, null, runtime());

        assertEquals("LIFECYCLE_CAPTURE_INCOMPLETE", report.evidenceStatus());
        assertEquals("RUNTIME_GENERATED_WORKLOAD", report.lifecycle().getFirst().classification());
        assertFalse(report.prototypeAdmitted());
    }

    @Test
    void reportsExclusiveCensusWithoutDoubleCountingOverlapSignals() {
        var report = new GeneratedFamilyMatchedEvidenceAnalyzer().analyze(
            "service", "same-sha", "same-sha", inventory(), runtime(), runtime(), runtime());

        assertEquals("MATCHED_DIAGNOSTIC_EVIDENCE", report.evidenceStatus());
        assertEquals(1, report.uniqueGeneratedClasses());
        assertEquals(2048L, report.uniqueGeneratedClassfileBytes());
        assertEquals(1, report.overlappingClassificationClasses());
        assertEquals(GeneratedClassFamily.LAMBDA_METAFATORY_SITE,
            report.exclusivePrimaryFamilyCensus().getFirst().primaryFamily());
    }

    @Test
    void writesJsonAndMarkdownAuditCompanions() throws Exception {
        var report = new GeneratedFamilyMatchedEvidenceAnalyzer().analyze(
            "service", "same-sha", "same-sha", inventory(), runtime(), runtime(), runtime());

        new GeneratedFamilyMatchedEvidenceReportWriter().write(temporaryDirectory, report);

        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("v2t-generated-family-matched-evidence.json")));
        assertTrue(Files.readString(temporaryDirectory.resolve("v2t-generated-family-matched-evidence.md"))
            .contains("MATCHED_DIAGNOSTIC_EVIDENCE"));
    }

    private static GeneratedClassInventory inventory() {
        var record = new GeneratedClassRecord(
            "example.Generated", "example/Generated", "root", "example/Generated.class", "app",
            GeneratedClassFamily.LAMBDA_METAFATORY_SITE, List.of("ACC_SYNTHETIC"), 4, 1, 1, 1,
            2048, 24, 1, List.of("test"), GeneratedClassRiskLevel.UNKNOWN, null, true);
        return new GeneratedClassInventory("test", "now", 1, 1, 2048, List.of(), List.of(record));
    }

    private static GeneratedClassRuntimeAttribution runtime() {
        var record = new GeneratedClassRuntimeClassRecord(
            "example.Generated", GeneratedClassFamily.LAMBDA_METAFATORY_SITE,
            true, true, false, "app", 4, 256, "SURVIVES_WORKLOAD");
        var family = new GeneratedClassFamilyRuntimeAttribution(
            GeneratedClassFamily.LAMBDA_METAFATORY_SITE, 1, 2048, 1, 0, 1, 1, 1, 4, 256,
            "SURVIVES_WORKLOAD", "HIGH", List.of(record));
        return new GeneratedClassRuntimeAttribution(
            "test", "now", "class-load.log", "histogram.txt", 1, 1, 0, 256, List.of(family), List.of(record));
    }
}
