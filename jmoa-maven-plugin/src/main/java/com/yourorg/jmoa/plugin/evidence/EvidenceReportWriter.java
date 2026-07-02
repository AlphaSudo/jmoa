package com.yourorg.jmoa.plugin.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceAnalysisReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.PairResult;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RunEvidence;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EvidenceReportWriter {

    public void write(File outputDirectory, EvidenceAnalysisReport report) throws IOException {
        if (outputDirectory != null) {
            outputDirectory.mkdirs();
        }
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(outputDirectory, "jmoa-parsed-evidence.json"), parsedEvidence(report));
        mapper.writeValue(new File(outputDirectory, "jmoa-evidence-validation.json"), report.validation());
        mapper.writeValue(new File(outputDirectory, "jmoa-paired-confirmation.json"), report.confirmation());
        mapper.writeValue(new File(outputDirectory, "jmoa-variance-classification.json"), report.variance());
        mapper.writeValue(new File(outputDirectory, "jmoa-perturbation-report.json"), report.perturbations());
        mapper.writeValue(new File(outputDirectory, "jmoa-evidence-analysis.json"), report);
        writeValidationMarkdown(new File(outputDirectory, "jmoa-evidence-validation.md"), report);
        writeConfirmationMarkdown(new File(outputDirectory, "jmoa-paired-confirmation.md"), report);
        writeDashboardMarkdown(new File(outputDirectory, "jmoa-confirmation-summary.md"), report);
        writeVarianceMarkdown(new File(outputDirectory, "jmoa-variance-classification.md"), report);
        writePerturbationMarkdown(new File(outputDirectory, "jmoa-perturbation-report.md"), report);
    }

    private Map<String, Object> parsedEvidence(EvidenceAnalysisReport report) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", "v2-c-parsed-evidence");
        root.put("generatedAt", report.generatedAt());
        root.put("runs", report.validation().runEvidence());
        return root;
    }

    private void writeValidationMarkdown(File target, EvidenceAnalysisReport report) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# JMOA Evidence Validation\n\n");
        md.append("- Runs: `").append(report.validation().runs()).append("`\n");
        md.append("- Valid runs: `").append(report.validation().validRuns()).append("`\n");
        md.append("- Invalid runs: `").append(report.validation().invalidRuns()).append("`\n\n");
        md.append("| Run | Variant | Valid | Invalid reasons | Warnings |\n");
        md.append("| --- | --- | --- | --- | --- |\n");
        for (RunEvidence run : report.validation().runEvidence()) {
            md.append("| `").append(run.manifest().runId()).append("` | ")
                .append(run.manifest().variant()).append(" | ")
                .append(run.valid()).append(" | ")
                .append(String.join("; ", run.invalidReasons())).append(" | ")
                .append(String.join("; ", run.warnings())).append(" |\n");
        }
        Files.writeString(target.toPath(), md.toString(), StandardCharsets.UTF_8);
    }

    private void writeConfirmationMarkdown(File target, EvidenceAnalysisReport report) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# JMOA Paired Confirmation\n\n");
        md.append("- Verdict: `").append(report.confirmation().verdict()).append("`\n");
        md.append("- Pairs: `").append(report.confirmation().pairs()).append("`\n");
        md.append("- Paired wins: `").append(report.confirmation().pairedWins()).append("`\n");
        md.append("- Median PSS delta KB: `").append(report.confirmation().medianPssDeltaKb()).append("`\n");
        md.append("- Median Private_Dirty delta KB: `").append(report.confirmation().medianPrivateDirtyDeltaKb()).append("`\n");
        md.append("- Median memory.current delta bytes: `").append(report.confirmation().medianMemoryCurrentDeltaBytes()).append("`\n\n");
        md.append("| Pair | Baseline | Candidate | PSS KB | Private_Dirty KB | memory.current bytes | Pass | Valid |\n");
        md.append("| ---: | --- | --- | ---: | ---: | ---: | --- | --- |\n");
        for (PairResult pair : report.confirmation().pairResults()) {
            md.append("| ").append(pair.pair()).append(" | `")
                .append(pair.baselineRunId()).append("` | `")
                .append(pair.candidateRunId()).append("` | ")
                .append(pair.deltaPssKb()).append(" | ")
                .append(pair.deltaPrivateDirtyKb()).append(" | ")
                .append(pair.deltaMemoryCurrentBytes()).append(" | ")
                .append(pair.pass()).append(" | ")
                .append(pair.valid()).append(" |\n");
        }
        Files.writeString(target.toPath(), md.toString(), StandardCharsets.UTF_8);
    }

    private void writeDashboardMarkdown(File target, EvidenceAnalysisReport report) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# JMOA Confirmation Summary\n\n");
        md.append("## Verdict\n\n");
        md.append("```text\n").append(report.verdict()).append("\n```\n\n");
        md.append("## Next Action\n\n");
        md.append(report.nextRecommendedAction()).append("\n\n");
        md.append("## Primary Metrics\n\n");
        md.append("| Metric | Median delta |\n");
        md.append("| --- | ---: |\n");
        md.append("| PSS KB | ").append(report.confirmation().medianPssDeltaKb()).append(" |\n");
        md.append("| Private_Dirty KB | ").append(report.confirmation().medianPrivateDirtyDeltaKb()).append(" |\n");
        md.append("| memory.current bytes | ").append(report.confirmation().medianMemoryCurrentDeltaBytes()).append(" |\n");
        md.append("| heap PSS KB | ").append(report.confirmation().medianHeapPssDeltaKb()).append(" |\n");
        md.append("| anonymous_rw PSS KB | ").append(report.confirmation().medianAnonymousRwDeltaKb()).append(" |\n");
        md.append("| anonymous executable PSS KB | ").append(report.confirmation().medianAnonymousExecutableDeltaKb()).append(" |\n\n");
        md.append("## Variance\n\n");
        md.append("- Categories: `").append(report.variance().categories()).append("`\n");
        md.append("- Reasons: ").append(String.join("; ", report.variance().reasons())).append("\n");
        Files.writeString(target.toPath(), md.toString(), StandardCharsets.UTF_8);
    }

    private void writeVarianceMarkdown(File target, EvidenceAnalysisReport report) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# JMOA Variance Classification\n\n");
        md.append("## Categories\n\n");
        for (var category : report.variance().categories()) {
            md.append("- `").append(category).append("`\n");
        }
        md.append("\n## Reasons\n\n");
        for (String reason : report.variance().reasons()) {
            md.append("- ").append(reason).append("\n");
        }
        Files.writeString(target.toPath(), md.toString(), StandardCharsets.UTF_8);
    }

    private void writePerturbationMarkdown(File target, EvidenceAnalysisReport report) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# JMOA Perturbation Report\n\n");
        if (report.perturbations().isEmpty()) {
            md.append("No perturbing diagnostics detected.\n");
        } else {
            for (var perturbation : report.perturbations()) {
                md.append("- ").append(String.join("; ", perturbation.warnings())).append("\n");
            }
        }
        Files.writeString(target.toPath(), md.toString(), StandardCharsets.UTF_8);
    }
}
