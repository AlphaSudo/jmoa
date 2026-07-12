package com.yourorg.jmoa.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ConfirmationScope;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ReducerAdmissionInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendReducerMojoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void writesReportOnlyRecommendationWithoutInvokingReducer() throws Exception {
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
            1,
            2,
            1,
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
            List.of("fixture")
        );
        MAPPER.writerWithDefaultPrettyPrinter()
            .writeValue(tempDir.resolve("reducer-admission-input.json").toFile(), input);
        Path output = tempDir.resolve("output");
        RecommendReducerMojo mojo = new RecommendReducerMojo();
        set(mojo, "recommendationEnabled", true);
        set(mojo, "recommendationMode", "analyze");
        set(mojo, "recommendationInputDir", tempDir.toFile());
        set(mojo, "recommendationOutputDir", output.toFile());
        set(mojo, "service", "visits-service");
        set(mojo, "launchMode", "EXPLODED_BOOT_APP");
        set(mojo, "runtimePolicy", "NO_CDS_LOW_DIRTY");
        set(mojo, "confirmationScope", "PUBLIC");

        mojo.execute();

        assertTrue(Files.isRegularFile(output.resolve("reducer-admission-input.json")));
        assertTrue(Files.isRegularFile(output.resolve("jmoa-reducer-recommendation.json")));
        assertTrue(Files.isRegularFile(output.resolve("jmoa-reducer-recommendation.md")));
        JsonNode recommendation = MAPPER.readTree(output.resolve("jmoa-reducer-recommendation.json").toFile());
        assertEquals("RECOMMEND_CONFIRMED", recommendation.path("decision").asText());
        assertTrue(recommendation.path("runtimePromotionAllowed").asBoolean());
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
