package com.yourorg.jmoa.plugin.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.RecommendationReplayReport;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ReducerAdmissionInput;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ReducerRecommendation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class RecommendationReportWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(File outputDirectory, ReducerAdmissionInput input, ReducerRecommendation recommendation)
        throws IOException {
        outputDirectory.mkdirs();
        MAPPER.writeValue(new File(outputDirectory, "reducer-admission-input.json"), input);
        MAPPER.writeValue(new File(outputDirectory, "jmoa-reducer-recommendation.json"), recommendation);
        Files.writeString(
            new File(outputDirectory, "jmoa-reducer-recommendation.md").toPath(),
            recommendationMarkdown(recommendation),
            StandardCharsets.UTF_8
        );
    }

    public void writeReplay(File outputDirectory, RecommendationReplayReport report) throws IOException {
        outputDirectory.mkdirs();
        MAPPER.writeValue(new File(outputDirectory, "jmoa-reducer-recommendation-replay.json"), report);
        Files.writeString(
            new File(outputDirectory, "jmoa-reducer-recommendation-replay.md").toPath(),
            replayMarkdown(report),
            StandardCharsets.UTF_8
        );
    }

    private static String recommendationMarkdown(ReducerRecommendation report) {
        StringBuilder md = new StringBuilder();
        md.append("# JMOA Reducer Recommendation\n\n");
        md.append("## Decision\n\n");
        md.append("```text\n").append(report.decision()).append("\n```\n\n");
        md.append("- Confirmation scope: `").append(report.confirmationScope()).append("`\n");
        md.append("- Protocol match: `").append(report.protocolMatchesConfirmedScope()).append("`\n");
        md.append("- Runtime promotion allowed: `").append(report.runtimePromotionAllowed()).append("`\n\n");
        md.append("## Runtime Context\n\n");
        md.append("- Service: `").append(report.input().service()).append("`\n");
        md.append("- Launch mode: `").append(report.input().launchMode()).append("`\n");
        md.append("- Runtime policy: `").append(report.input().runtimePolicy()).append("`\n");
        md.append("- Reducer engine: `").append(report.input().reducerEngine()).append("`\n\n");
        appendList(md, "Reasons", report.reasons());
        appendList(md, "Evidence Gaps", report.evidenceGaps());
        appendList(md, "Next Actions", report.nextActions());
        appendList(md, "Boundaries", report.boundaries());
        return md.toString();
    }

    private static String replayMarkdown(RecommendationReplayReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# V2-M Historical Recommendation Replay\n\n");
        md.append("- Cases: `").append(report.cases()).append("`\n");
        md.append("- Passed: `").append(report.passedCases()).append("`\n");
        md.append("- Failed: `").append(report.failedCases()).append("`\n\n");
        md.append("| Case | Expected | Actual | Scope | Protocol match | Passed |\n");
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
