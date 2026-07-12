package com.yourorg.jmoa.plugin.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.AdmissionDecision;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ConfirmationScope;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ReducerAdmissionInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationReplaySuiteTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void replaysExpectedAdmissionDecisionAndWritesReports() throws Exception {
        ReducerAdmissionInput input = new ReducerAdmissionInput(
            "v2m-test-input",
            "visits-service",
            "EXPLODED_BOOT_APP",
            "NO_CDS_LOW_DIRTY",
            false,
            0,
            0,
            1000,
            10,
            false,
            0,
            0,
            false,
            0,
            0,
            false,
            false,
            true,
            true,
            0,
            0,
            0,
            0,
            true,
            "PASSED",
            true,
            "CONFIRMED_WIN",
            true,
            false,
            "raw",
            List.of("LocalVariableTable", "LocalVariableTypeTable"),
            List.of(),
            "visits-service",
            "EXPLODED_BOOT_APP",
            "NO_CDS_LOW_DIRTY",
            ConfirmationScope.PUBLIC,
            List.of()
        );
        ObjectNode replayCase = MAPPER.createObjectNode();
        replayCase.put("id", "confirmed-public");
        replayCase.set("input", MAPPER.valueToTree(input));
        replayCase.put("expectedDecision", AdmissionDecision.RECOMMEND_CONFIRMED.name());
        replayCase.put("expectedScope", ConfirmationScope.PUBLIC.name());
        replayCase.put("expectedProtocolMatch", true);
        JsonNode root = MAPPER.createObjectNode()
            .set("cases", MAPPER.createArrayNode().add(replayCase));
        Path suite = tempDir.resolve("suite.json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(suite.toFile(), root);

        var report = new RecommendationReplaySuite().replay(suite.toFile());
        Path output = tempDir.resolve("output");
        new RecommendationReportWriter().writeReplay(output.toFile(), report);

        assertEquals(1, report.cases());
        assertEquals(1, report.passedCases());
        assertEquals(0, report.failedCases());
        assertTrue(Files.isRegularFile(output.resolve("jmoa-reducer-recommendation-replay.json")));
        assertTrue(Files.isRegularFile(output.resolve("jmoa-reducer-recommendation-replay.md")));
    }
}
