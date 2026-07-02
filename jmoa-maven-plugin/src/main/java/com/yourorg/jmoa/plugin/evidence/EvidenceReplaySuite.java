package com.yourorg.jmoa.plugin.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceAnalysisReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceConfig;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RuntimePolicy;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.VarianceCategory;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.Verdict;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EvidenceReplaySuite {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final EvidenceEngine engine;

    public EvidenceReplaySuite() {
        this(new EvidenceEngine());
    }

    EvidenceReplaySuite(EvidenceEngine engine) {
        this.engine = engine;
    }

    public ReplayReport replay(File suiteFile, File baseDirectory, EvidenceConfig defaultConfig) throws IOException {
        JsonNode root = MAPPER.readTree(suiteFile);
        List<ReplayResult> results = new ArrayList<>();
        for (JsonNode json : root.path("cases")) {
            ReplayCase replayCase = parseCase(json);
            File input = resolveInput(baseDirectory, suiteFile, replayCase.inputDir());
            if (!input.isDirectory()) {
                boolean passed = replayCase.optional();
                results.add(new ReplayResult(
                    replayCase.id(),
                    replayCase.description(),
                    replayCase.inputDir(),
                    false,
                    passed,
                    replayCase.expectedVerdict(),
                    null,
                    replayCase.expectedVarianceCategories(),
                    List.of(),
                    passed ? "optional evidence directory not present" : "evidence directory not present"
                ));
                continue;
            }
            EvidenceConfig config = configFor(replayCase, defaultConfig);
            try {
                EvidenceAnalysisReport analysis = engine.analyze(input, config);
                boolean verdictPass = replayCase.expectedVerdict() == null
                    || replayCase.expectedVerdict() == analysis.verdict();
                boolean variancePass = replayCase.expectedVarianceCategories().isEmpty()
                    || analysis.variance().categories().containsAll(replayCase.expectedVarianceCategories());
                boolean passed = verdictPass && variancePass;
                results.add(new ReplayResult(
                    replayCase.id(),
                    replayCase.description(),
                    replayCase.inputDir(),
                    true,
                    passed,
                    replayCase.expectedVerdict(),
                    analysis.verdict(),
                    replayCase.expectedVarianceCategories(),
                    analysis.variance().categories(),
                    passed ? "matched expected replay outcome" : "replay outcome mismatch"
                ));
            } catch (Exception e) {
                results.add(new ReplayResult(
                    replayCase.id(),
                    replayCase.description(),
                    replayCase.inputDir(),
                    true,
                    false,
                    replayCase.expectedVerdict(),
                    null,
                    replayCase.expectedVarianceCategories(),
                    List.of(),
                    "replay failed: " + e.getMessage()
                ));
            }
        }
        int present = (int) results.stream().filter(ReplayResult::present).count();
        int passed = (int) results.stream().filter(ReplayResult::passed).count();
        int failed = results.size() - passed;
        return new ReplayReport(
            "v2-c-historical-replay-report",
            Instant.now().toString(),
            results.size(),
            present,
            passed,
            failed,
            results
        );
    }

    public void write(File outputDirectory, ReplayReport report) throws IOException {
        outputDirectory.mkdirs();
        MAPPER.writeValue(new File(outputDirectory, "jmoa-evidence-replay-report.json"), report);
        StringBuilder md = new StringBuilder();
        md.append("# JMOA Historical Evidence Replay\n\n");
        md.append("- Cases: `").append(report.cases()).append("`\n");
        md.append("- Present cases: `").append(report.presentCases()).append("`\n");
        md.append("- Passed cases: `").append(report.passedCases()).append("`\n");
        md.append("- Failed cases: `").append(report.failedCases()).append("`\n\n");
        md.append("| Case | Present | Passed | Expected | Actual | Expected variance | Actual variance | Message |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (ReplayResult result : report.results()) {
            md.append("| `").append(result.id()).append("` | ")
                .append(result.present()).append(" | ")
                .append(result.passed()).append(" | ")
                .append(result.expectedVerdict()).append(" | ")
                .append(result.actualVerdict()).append(" | `")
                .append(result.expectedVarianceCategories()).append("` | `")
                .append(result.actualVarianceCategories()).append("` | ")
                .append(result.message()).append(" |\n");
        }
        Files.writeString(new File(outputDirectory, "jmoa-evidence-replay-report.md").toPath(),
            md.toString(), StandardCharsets.UTF_8);
    }

    private static EvidenceConfig configFor(ReplayCase replayCase, EvidenceConfig defaults) {
        RuntimePolicy policy = replayCase.expectedPolicy() == null
            ? defaults.expectedPolicy()
            : replayCase.expectedPolicy();
        return new EvidenceConfig(
            policy,
            defaults.requireArtifactHashes(),
            defaults.requireWorkloadZeroErrors(),
            defaults.requireSmapsArithmetic(),
            defaults.failOnInvalidRun(),
            defaults.markPerturbingDiagnostics()
        );
    }

    private static ReplayCase parseCase(JsonNode json) {
        List<VarianceCategory> categories = new ArrayList<>();
        for (JsonNode category : json.path("expectedVarianceCategories")) {
            categories.add(enumValue(VarianceCategory.class, category.asText(), VarianceCategory.UNKNOWN));
        }
        return new ReplayCase(
            text(json, "id", "unnamed"),
            text(json, "description", ""),
            text(json, "inputDir", ""),
            enumValue(RuntimePolicy.class, text(json, "expectedPolicy", null), null),
            enumValue(Verdict.class, text(json, "expectedVerdict", null), null),
            categories,
            json.path("optional").asBoolean(false)
        );
    }

    private static File resolveInput(File baseDirectory, File suiteFile, String inputDir) {
        File input = new File(inputDir);
        if (input.isAbsolute()) {
            return input;
        }
        File base = baseDirectory != null && baseDirectory.isDirectory()
            ? baseDirectory
            : suiteFile.getParentFile();
        return new File(base, inputDir);
    }

    private static String text(JsonNode json, String field, String fallback) {
        if (json == null || !json.has(field) || json.path(field).isNull()) {
            return fallback;
        }
        return json.path(field).asText();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public record ReplayCase(
        String id,
        String description,
        String inputDir,
        RuntimePolicy expectedPolicy,
        Verdict expectedVerdict,
        List<VarianceCategory> expectedVarianceCategories,
        boolean optional
    ) {
        public ReplayCase {
            expectedVarianceCategories = expectedVarianceCategories == null
                ? List.of()
                : List.copyOf(expectedVarianceCategories);
        }
    }

    public record ReplayResult(
        String id,
        String description,
        String inputDir,
        boolean present,
        boolean passed,
        Verdict expectedVerdict,
        Verdict actualVerdict,
        List<VarianceCategory> expectedVarianceCategories,
        List<VarianceCategory> actualVarianceCategories,
        String message
    ) {
        public ReplayResult {
            expectedVarianceCategories = expectedVarianceCategories == null
                ? List.of()
                : List.copyOf(expectedVarianceCategories);
            actualVarianceCategories = actualVarianceCategories == null
                ? List.of()
                : List.copyOf(actualVarianceCategories);
        }
    }

    public record ReplayReport(
        String metadataVersion,
        String generatedAt,
        int cases,
        int presentCases,
        int passedCases,
        int failedCases,
        List<ReplayResult> results
    ) {
        public ReplayReport {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }
}
