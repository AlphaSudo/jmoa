package com.yourorg.jmoa.plugin.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.AdmissionDecision;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ConfirmationScope;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.RecommendationReplayReport;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ReducerAdmissionInput;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ReplayCaseResult;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class RecommendationReplaySuite {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public RecommendationReplayReport replay(File suiteFile) throws IOException {
        if (suiteFile == null || !suiteFile.isFile()) {
            throw new IllegalArgumentException("Recommendation replay suite does not exist: " + suiteFile);
        }
        JsonNode root = MAPPER.readTree(suiteFile);
        List<ReplayCaseResult> results = new ArrayList<>();
        ReducerRecommendationEngine engine = new ReducerRecommendationEngine();
        for (JsonNode entry : root.path("cases")) {
            String id = entry.path("id").asText("unnamed");
            ReducerAdmissionInput input = MAPPER.treeToValue(entry.path("input"), ReducerAdmissionInput.class);
            AdmissionDecision expectedDecision = AdmissionDecision.valueOf(entry.path("expectedDecision").asText());
            ConfirmationScope expectedScope = ConfirmationScope.valueOf(
                entry.path("expectedScope").asText("UNKNOWN")
            );
            Boolean expectedProtocolMatch = entry.has("expectedProtocolMatch")
                ? entry.path("expectedProtocolMatch").asBoolean()
                : null;
            var actual = engine.recommend(input);
            boolean passed = expectedDecision == actual.decision()
                && expectedScope == actual.confirmationScope()
                && (expectedProtocolMatch == null
                    || expectedProtocolMatch == actual.protocolMatchesConfirmedScope());
            results.add(new ReplayCaseResult(
                id,
                expectedDecision,
                actual.decision(),
                expectedScope,
                actual.confirmationScope(),
                expectedProtocolMatch,
                actual.protocolMatchesConfirmedScope(),
                passed,
                actual.reasons()
            ));
        }
        int passed = (int) results.stream().filter(ReplayCaseResult::passed).count();
        return new RecommendationReplayReport(
            "v2m-historical-recommendation-replay",
            Instant.now().toString(),
            results.size(),
            passed,
            results.size() - passed,
            results
        );
    }
}
