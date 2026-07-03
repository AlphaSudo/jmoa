package com.yourorg.jmoa.plugin.attribution;

import com.yourorg.jmoa.plugin.attribution.AttributionModels.AttributionConfig;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.CausalHypothesisType;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.MemoryAttributionReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceConfig;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RuntimePolicy;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryAttributionEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void attributesConfirmedWinToHeapPageTouchReductionAndClassCount() throws Exception {
        writeRun("b1", "BASELINE", 1, 100_000, 90_000, 120_000_000, 40_000, 8_000, 20_000, 1_000_000, 3000);
        writeRun("c1", "CANDIDATE", 1, 95_200, 85_100, 115_100_000, 33_000, 8_200, 19_900, 980_000, 2846);
        writeRun("b2", "BASELINE", 2, 100_300, 90_200, 120_300_000, 40_300, 8_100, 20_100, 1_001_000, 3001);
        writeRun("c2", "CANDIDATE", 2, 95_700, 85_500, 115_700_000, 33_400, 8_300, 20_000, 981_000, 2847);
        writeRun("b3", "BASELINE", 3, 99_900, 89_900, 119_900_000, 39_900, 8_000, 19_900, 999_000, 3000);
        writeRun("c3", "CANDIDATE", 3, 95_100, 85_000, 115_000_000, 32_900, 8_100, 19_900, 979_000, 2846);

        MemoryAttributionReport report = new MemoryAttributionEngine().analyze(
            tempDir.toFile(),
            new EvidenceConfig(RuntimePolicy.NO_CDS_LOW_DIRTY, true, true, true, true, true),
            AttributionConfig.defaults()
        );

        assertEquals(Verdict.CONFIRMED_WIN, report.evidenceVerdict());
        assertEquals("HEAP_PAGE_TOUCH_REDUCTION", report.heapObjectAttribution().classification());
        assertTrue(report.causalHypotheses().stream()
            .anyMatch(h -> h.hypothesis() == CausalHypothesisType.HEAP_PAGE_TOUCH_REDUCTION));
        assertTrue(report.causalHypotheses().stream()
            .anyMatch(h -> h.hypothesis() == CausalHypothesisType.CLASS_COUNT_SAVINGS));
    }

    @Test
    void attributesRegressionToHeapPageTouchGrowthNotRetainedObjects() throws Exception {
        writeRun("b1", "BASELINE", 1, 100_000, 90_000, 120_000_000, 40_000, 8_000, 20_000, 1_000_000, 3000);
        writeRun("c1", "CANDIDATE", 1, 108_800, 99_000, 129_000_000, 48_700, 7_900, 20_100, 1_000_100, 2999);
        writeRun("b2", "BASELINE", 2, 100_200, 90_100, 120_100_000, 40_100, 8_100, 20_100, 1_001_000, 3001);
        writeRun("c2", "CANDIDATE", 2, 109_000, 99_200, 129_300_000, 48_900, 8_000, 20_000, 1_001_100, 3001);
        writeRun("b3", "BASELINE", 3, 99_900, 89_900, 119_900_000, 39_900, 8_000, 19_900, 999_000, 3000);
        writeRun("c3", "CANDIDATE", 3, 108_700, 98_900, 129_100_000, 48_600, 7_900, 19_900, 999_100, 2999);

        MemoryAttributionReport report = new MemoryAttributionEngine().analyze(
            tempDir.toFile(),
            new EvidenceConfig(RuntimePolicy.NO_CDS_LOW_DIRTY, true, true, true, true, true),
            AttributionConfig.defaults()
        );

        assertEquals(Verdict.CONFIRMED_REGRESSION, report.evidenceVerdict());
        assertEquals("HEAP_PAGE_TOUCH_GROWTH", report.heapObjectAttribution().classification());
        assertTrue(report.causalHypotheses().stream()
            .anyMatch(h -> h.hypothesis() == CausalHypothesisType.HEAP_PAGE_TOUCH_GROWTH));
    }

    @Test
    void writesAttributionReports() throws Exception {
        writeRun("b1", "BASELINE", 1, 100_000, 90_000, 120_000_000, 40_000, 8_000, 20_000, 1_000_000, 3000);
        writeRun("c1", "CANDIDATE", 1, 95_200, 85_100, 115_100_000, 33_000, 8_200, 19_900, 980_000, 2846);
        MemoryAttributionReport report = new MemoryAttributionEngine().analyze(
            tempDir.toFile(),
            new EvidenceConfig(RuntimePolicy.NO_CDS_LOW_DIRTY, true, true, true, false, true),
            AttributionConfig.defaults()
        );
        Path output = tempDir.resolve("out");

        new MemoryAttributionReportWriter().write(output.toFile(), report);

        assertTrue(Files.isRegularFile(output.resolve("jmoa-memory-attribution.json")));
        assertTrue(Files.isRegularFile(output.resolve("jmoa-memory-attribution.md")));
        assertTrue(Files.isRegularFile(output.resolve("jmoa-category-deltas.json")));
        assertTrue(Files.isRegularFile(output.resolve("jmoa-causal-hypotheses.json")));
        assertTrue(Files.isRegularFile(output.resolve("jmoa-top-object-deltas.csv")));
    }

    private void writeRun(
        String runId,
        String variant,
        int pair,
        long pssKb,
        long privateDirtyKb,
        long memoryCurrentBytes,
        long heapPssKb,
        long anonymousRwPssKb,
        long heapUsedKb,
        long histogramBytes,
        int histogramClassCount
    ) throws Exception {
        Path dir = tempDir.resolve(runId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("run-manifest.json"), """
            {
              "runId": "%s",
              "variant": "%s",
              "pairIndex": %d,
              "service": "fixture-service",
              "phase": "v2d-test",
              "runtimePolicy": "NO_CDS_LOW_DIRTY",
              "cdsMode": "OFF",
              "javaagentPresent": false,
              "mallocArenaMax": "1",
              "artifactSha256": "abc",
              "expectedArtifactSha256": "abc"
            }
            """.formatted(runId, variant, pair));
        long rss = pssKb + 12_000;
        long privateClean = 1_000;
        long sharedClean = rss - privateClean - privateDirtyKb;
        Files.writeString(dir.resolve("smaps_rollup"), """
            Rss:              %d kB
            Pss:              %d kB
            Private_Clean:    %d kB
            Private_Dirty:    %d kB
            Shared_Clean:     %d kB
            Shared_Dirty:     0 kB
            """.formatted(rss, pssKb, privateClean, privateDirtyKb, sharedClean));
        Files.writeString(dir.resolve("smaps"), smaps(heapPssKb, anonymousRwPssKb));
        Files.writeString(dir.resolve("memory.current"), Long.toString(memoryCurrentBytes));
        Files.writeString(dir.resolve("workload.json"), "{\"health\":\"UP\",\"errors\":0,\"requests\":9}");
        Files.writeString(dir.resolve("heap-info.txt"), "garbage-first heap total 65536K, used %dK\n".formatted(heapUsedKb));
        Files.writeString(dir.resolve("nmt-summary.txt"), """
            Total: reserved=100000KB, committed=50000KB
            - Java Heap (reserved=65536KB, committed=65536KB)
            - Class (reserved=1000KB, committed=900KB)
            - Code (reserved=2000KB, committed=1800KB)
            - Metaspace (reserved=4000KB, committed=3000KB)
            - Arena Chunk (reserved=100KB, committed=100KB)
            """);
        Files.writeString(dir.resolve("class-histogram.txt"), histogram(histogramBytes, histogramClassCount));
    }

    private static String smaps(long heapPssKb, long anonymousRwPssKb) {
        return """
            1000-2000 rw-p 00000000 00:00 0 [heap]
            Rss:               %d kB
            Pss:               %d kB
            Private_Dirty:     %d kB
            3000-4000 rw-p 00000000 00:00 0
            Rss:               %d kB
            Pss:               %d kB
            Private_Dirty:     %d kB
            """.formatted(heapPssKb, heapPssKb, heapPssKb, anonymousRwPssKb, anonymousRwPssKb, anonymousRwPssKb);
    }

    private static String histogram(long bytes, int classCount) {
        StringBuilder out = new StringBuilder();
        long perClass = Math.max(1, bytes / Math.max(1, classCount));
        for (int i = 1; i <= classCount; i++) {
            out.append(i).append(": 1 ").append(perClass).append(" java.lang.Object").append(i).append('\n');
        }
        out.append("Total ").append(classCount).append(' ').append(bytes).append('\n');
        return out.toString();
    }
}
