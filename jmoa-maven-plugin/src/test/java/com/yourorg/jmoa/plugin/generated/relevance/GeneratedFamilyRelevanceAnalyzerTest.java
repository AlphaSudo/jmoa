package com.yourorg.jmoa.plugin.generated.relevance;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;
import com.yourorg.jmoa.plugin.generated.GeneratedClassFamilyRuntimeAttribution;
import com.yourorg.jmoa.plugin.generated.GeneratedClassInventory;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRecord;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRiskLevel;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeAttribution;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeClassRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedFamilyRelevanceAnalyzerTest {

    @Test
    void keepsStaticOnlySpringDataFamilyReportOnly() {
        var report = new GeneratedFamilyRelevanceAnalyzer().analyze("service", inventory(GeneratedClassFamily.SPRING_DATA_GENERATED), null);

        var reconciliation = report.reconciliation().getFirst();
        var roi = report.roiRanking().getFirst();

        assertEquals("RUNTIME_RELEVANCE_UNKNOWN", reconciliation.relevance());
        assertEquals("GENERATED_REPORT_ONLY", roi.recommendation());
        assertFalse(report.prototypeAdmitted());
    }

    @Test
    void blocksRuntimeRelevantCglibFromPrototypeAdmission() {
        var record = new GeneratedClassRuntimeClassRecord(
            "example.Service$$SpringCGLIB$$0", GeneratedClassFamily.SPRING_CGLIB,
            true, true, false, "app", 4, 256, "SURVIVES_WORKLOAD"
        );
        var family = new GeneratedClassFamilyRuntimeAttribution(
            GeneratedClassFamily.SPRING_CGLIB, 1, 2048, 1, 0, 1, 0, 1, 4, 256,
            "SURVIVES_WORKLOAD", "HIGH", List.of(record)
        );
        var runtime = new GeneratedClassRuntimeAttribution(
            "test", "now", "class-load.log", "histogram.txt", 100, 1, 0, 256, List.of(family), List.of(record)
        );

        var report = new GeneratedFamilyRelevanceAnalyzer().analyze("service", inventory(GeneratedClassFamily.SPRING_CGLIB), runtime);

        assertEquals("RUNTIME_RELEVANT", report.reconciliation().getFirst().relevance());
        assertEquals("GENERATED_MUTATION_BLOCKED", report.roiRanking().getFirst().recommendation());
        assertTrue(report.safetyMatrix().stream()
            .filter(item -> item.family() == GeneratedClassFamily.SPRING_CGLIB)
            .findFirst()
            .orElseThrow()
            .proxyDispatchSensitive());
        assertFalse(report.prototypeAdmitted());
    }

    private static GeneratedClassInventory inventory(GeneratedClassFamily family) {
        var record = new GeneratedClassRecord(
            "example.Generated", "example/Generated", "root", "example/Generated.class", "app", family,
            List.of("ACC_SYNTHETIC"), 4, 1, 0, 0, 2048, 24, 0,
            List.of("test"), GeneratedClassRiskLevel.UNKNOWN, null, true
        );
        return new GeneratedClassInventory("test", "now", 1, 1, 2048, List.of(), List.of(record));
    }
}
