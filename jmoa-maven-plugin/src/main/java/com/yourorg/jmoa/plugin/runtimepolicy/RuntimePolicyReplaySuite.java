package com.yourorg.jmoa.plugin.runtimepolicy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyAdmissionInput;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyDecision;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyReplayCaseResult;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyReplayReport;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyScope;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class RuntimePolicyReplaySuite {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public RuntimePolicyReplayReport replay(File suiteFile, RuntimePolicyRegistry registry) throws IOException {
        if (suiteFile == null || !suiteFile.isFile()) {
            throw new IllegalArgumentException("Runtime-policy replay suite does not exist: " + suiteFile);
        }
        JsonNode root = MAPPER.readTree(suiteFile);
        RuntimePolicyRecommendationEngine engine = new RuntimePolicyRecommendationEngine(registry);
        List<RuntimePolicyReplayCaseResult> results = new ArrayList<>();
        for (JsonNode entry : root.path("cases")) {
            String id = entry.path("id").asText("unnamed");
            RuntimePolicyAdmissionInput input = MAPPER.treeToValue(
                entry.path("input"), RuntimePolicyAdmissionInput.class
            );
            RuntimePolicyDecision expectedDecision = RuntimePolicyDecision.valueOf(
                entry.path("expectedDecision").asText()
            );
            RuntimePolicyScope expectedScope = RuntimePolicyScope.valueOf(
                entry.path("expectedScope").asText("UNKNOWN")
            );
            Boolean expectedRegistryMatch = entry.has("expectedProtocolMatch")
                ? entry.path("expectedProtocolMatch").asBoolean()
                : null;
            var actual = engine.recommend(input);
            boolean passed = expectedDecision == actual.decision()
                && expectedScope == actual.scope()
                && (expectedRegistryMatch == null
                    || expectedRegistryMatch == actual.protocolMatchesRegistry());
            results.add(new RuntimePolicyReplayCaseResult(
                id,
                expectedDecision,
                actual.decision(),
                expectedScope,
                actual.scope(),
                expectedRegistryMatch,
                actual.protocolMatchesRegistry(),
                passed,
                actual.reasons()
            ));
        }
        int passedCases = (int) results.stream().filter(RuntimePolicyReplayCaseResult::passed).count();
        return new RuntimePolicyReplayReport(
            "v2n-historical-runtime-policy-replay",
            Instant.now().toString(),
            results.size(),
            passedCases,
            results.size() - passedCases,
            results
        );
    }
}
