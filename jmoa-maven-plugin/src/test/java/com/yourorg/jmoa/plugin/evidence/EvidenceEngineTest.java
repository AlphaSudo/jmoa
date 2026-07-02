package com.yourorg.jmoa.plugin.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceAnalysisReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceConfig;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RuntimePolicy;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.VarianceCategory;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceEngineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void confirmsCleanThreePairWin() throws Exception {
        writeRun("b1", "BASELINE", 1, 100_000, 90_000, 120_000_000, 40_000, 8_000, 20_000);
        writeRun("c1", "CANDIDATE", 1, 95_300, 85_100, 115_200_000, 33_000, 7_700, 19_900);
        writeRun("b2", "BASELINE", 2, 100_400, 90_200, 120_300_000, 40_400, 8_100, 20_100);
        writeRun("c2", "CANDIDATE", 2, 95_800, 85_400, 115_700_000, 33_500, 7_700, 20_000);
        writeRun("b3", "BASELINE", 3, 99_900, 89_800, 119_800_000, 39_900, 7_900, 19_900);
        writeRun("c3", "CANDIDATE", 3, 95_200, 85_000, 115_100_000, 33_000, 7_500, 19_800);

        EvidenceAnalysisReport report = new EvidenceEngine().analyze(tempDir.toFile(),
            new EvidenceConfig(RuntimePolicy.NO_CDS_LOW_DIRTY, true, true, true, true, true));

        assertEquals(6, report.validation().runs());
        assertEquals(0, report.validation().invalidRuns());
        assertEquals(Verdict.CONFIRMED_WIN, report.verdict());
        assertEquals(3, report.confirmation().pairedWins());
        assertEquals(-4_700, report.confirmation().medianPssDeltaKb());
        assertEquals(-4_800, report.confirmation().medianPrivateDirtyDeltaKb());
        assertEquals(-4_700_000, report.confirmation().medianMemoryCurrentDeltaBytes());
    }

    @Test
    void classifiesValidHeapPageTouchRegression() throws Exception {
        writeRun("b1", "BASELINE", 1, 100_000, 90_000, 120_000_000, 40_000, 8_000, 20_000);
        writeRun("c1", "CANDIDATE", 1, 108_700, 98_900, 129_000_000, 48_600, 7_900, 20_100);
        writeRun("b2", "BASELINE", 2, 100_100, 90_100, 120_100_000, 40_100, 8_100, 20_100);
        writeRun("c2", "CANDIDATE", 2, 108_900, 99_000, 129_200_000, 48_700, 8_000, 20_000);
        writeRun("b3", "BASELINE", 3, 99_900, 89_900, 119_900_000, 39_900, 7_900, 19_900);
        writeRun("c3", "CANDIDATE", 3, 108_500, 98_800, 128_900_000, 48_300, 7_900, 19_900);

        EvidenceAnalysisReport report = new EvidenceEngine().analyze(tempDir.toFile(),
            new EvidenceConfig(RuntimePolicy.NO_CDS_LOW_DIRTY, true, true, true, true, true));

        assertEquals(0, report.validation().invalidRuns());
        assertEquals(Verdict.CONFIRMED_REGRESSION, report.verdict());
        assertTrue(report.variance().categories().contains(VarianceCategory.HEAP_PAGE_TOUCH));
    }

    @Test
    void writesEvidenceReports() throws Exception {
        writeRun("b1", "BASELINE", 1, 100_000, 90_000, 120_000_000, 40_000, 8_000, 20_000);
        writeRun("c1", "CANDIDATE", 1, 98_000, 88_000, 118_000_000, 39_000, 7_900, 20_000);
        EvidenceAnalysisReport report = new EvidenceEngine().analyze(tempDir.toFile(), EvidenceConfig.defaults());
        Path output = tempDir.resolve("out");

        new EvidenceReportWriter().write(output.toFile(), report);

        assertTrue(Files.isRegularFile(output.resolve("jmoa-evidence-analysis.json")));
        assertTrue(Files.isRegularFile(output.resolve("jmoa-evidence-validation.md")));
        assertTrue(Files.isRegularFile(output.resolve("jmoa-paired-confirmation.md")));
        JsonNode json = MAPPER.readTree(output.resolve("jmoa-evidence-analysis.json").toFile());
        assertEquals("v2-c-evidence-analysis", json.path("metadataVersion").asText());
    }

    @Test
    void replaysHistoricalSuiteContract() throws Exception {
        writeRun("b1", "BASELINE", 1, 100_000, 90_000, 120_000_000, 40_000, 8_000, 20_000);
        writeRun("c1", "CANDIDATE", 1, 95_300, 85_100, 115_200_000, 33_000, 7_700, 19_900);
        writeRun("b2", "BASELINE", 2, 100_400, 90_200, 120_300_000, 40_400, 8_100, 20_100);
        writeRun("c2", "CANDIDATE", 2, 95_800, 85_400, 115_700_000, 33_500, 7_700, 20_000);
        writeRun("b3", "BASELINE", 3, 99_900, 89_800, 119_800_000, 39_900, 7_900, 19_900);
        writeRun("c3", "CANDIDATE", 3, 95_200, 85_000, 115_100_000, 33_000, 7_500, 19_800);
        Path suite = tempDir.resolve("historical-replay-suite.json");
        Files.writeString(suite, """
            {
              "cases": [
                {
                  "id": "phase33m-petclinic",
                  "description": "Known confirmed no-CDS win shape",
                  "inputDir": ".",
                  "expectedPolicy": "NO_CDS_LOW_DIRTY",
                  "expectedVerdict": "CONFIRMED_WIN"
                }
              ]
            }
            """);

        EvidenceReplaySuite.ReplayReport report = new EvidenceReplaySuite().replay(
            suite.toFile(),
            tempDir.toFile(),
            EvidenceConfig.defaults()
        );
        Path output = tempDir.resolve("replay-out");
        new EvidenceReplaySuite().write(output.toFile(), report);

        assertEquals(1, report.cases());
        assertEquals(1, report.passedCases());
        assertTrue(Files.isRegularFile(output.resolve("jmoa-evidence-replay-report.json")));
        assertTrue(Files.isRegularFile(output.resolve("jmoa-evidence-replay-report.md")));
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
        long heapUsedKb
    ) throws Exception {
        Path dir = tempDir.resolve(runId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("run-manifest.json"), """
            {
              "runId": "%s",
              "variant": "%s",
              "pairIndex": %d,
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
        Files.writeString(dir.resolve("class-histogram.txt"), "1: 1 16 java.lang.Object\nTotal 1 16\n");
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
}
