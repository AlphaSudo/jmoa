package com.yourorg.jmoa.plugin.runtimepolicy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyAdmissionInput;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyRecommendation;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyReplayReport;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class RuntimePolicyReportWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(
        File outputDirectory,
        RuntimePolicyAdmissionInput input,
        RuntimePolicyRecommendation recommendation
    ) throws IOException {
        outputDirectory.mkdirs();
        MAPPER.writeValue(new File(outputDirectory, "runtime-policy-admission-input.json"), input);
        MAPPER.writeValue(new File(outputDirectory, "jmoa-runtime-recommendation.json"), recommendation);
        Files.writeString(
            new File(outputDirectory, "jmoa-runtime-recommendation.md").toPath(),
            recommendationMarkdown(recommendation),
            StandardCharsets.UTF_8
        );
    }

    public void writeReplay(File outputDirectory, RuntimePolicyReplayReport report) throws IOException {
        outputDirectory.mkdirs();
        MAPPER.writeValue(new File(outputDirectory, "jmoa-runtime-recommendation-replay.json"), report);
        Files.writeString(
            new File(outputDirectory, "jmoa-runtime-recommendation-replay.md").toPath(),
            replayMarkdown(report),
            StandardCharsets.UTF_8
        );
    }

    private static String recommendationMarkdown(RuntimePolicyRecommendation report) {
        StringBuilder md = new StringBuilder();
        md.append("# JMOA Runtime Policy Recommendation\n\n");
        md.append("## Decision\n\n");
        md.append("```text\n").append(report.decision()).append("\n```\n\n");
        md.append("- Scope: `").append(report.scope()).append("`\n");
        md.append("- Registry protocol match: `").append(report.protocolMatchesRegistry()).append("`\n");
        md.append("- Runtime policy promotion allowed: `").append(report.runtimePolicyPromotionAllowed()).append("`\n");
        md.append("- Matched protocol: `").append(report.matchedProtocolId()).append("`\n\n");
        md.append("## Runtime Context\n\n");
        md.append("- Service: `").append(report.input().service()).append("`\n");
        md.append("- Launch mode: `").append(report.input().launchMode()).append("`\n");
        md.append("- Runtime policy: `").append(report.input().runtimePolicy()).append("`\n");
        md.append("- Reducer engine: `").append(report.input().reducerEngine()).append("`\n");
        appendList(md, "Reasons", report.reasons());
        appendList(md, "Missing Gates", report.missingGates());
        appendList(md, "Next Actions", report.nextActions());
        appendList(md, "Boundaries", report.boundaries());
        return md.toString();
    }

    private static String replayMarkdown(RuntimePolicyReplayReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# V2-N Historical Runtime Policy Replay\n\n");
        md.append("- Cases: `").append(report.cases()).append("`\n");
        md.append("- Passed: `").append(report.passedCases()).append("`\n");
        md.append("- Failed: `").append(report.failedCases()).append("`\n\n");
        md.append("| Case | Expected | Actual | Scope | Registry match | Passed |\n");
        md.append("| --- | --- | --- | --- | --- | --- |\n");
        for (var result : report.results()) {
            md.append("| `").append(result.id()).append("` | `")
                .append(result.expectedDecision()).append("` | `")
                .append(result.actualDecision()).append("` | `")
                .append(result.actualScope()).append("` | `")
                .append(result.actualProtocolMatch()).append("` | `")
                .append(result.passed()).append("` |\n");
        }
        return md.toString();
    }

    private static void appendList(StringBuilder md, String title, Iterable<String> values) {
        md.append("## ").append(title).append("\n\n");
        boolean any = false;
        for (String value : values) {
            md.append("- ").append(value).append('\n');
            any = true;
        }
        if (!any) {
            md.append("None.\n");
        }
        md.append('\n');
    }
}
