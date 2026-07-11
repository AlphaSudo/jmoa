package com.yourorg.jmoa.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyAdmissionInput;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyRegistryEntry;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendRuntimeMojoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void writesReportOnlyRuntimeRecommendation() throws Exception {
        RuntimePolicyAdmissionInput input = new RuntimePolicyAdmissionInput(
            "v2n-test-input",
            "spring-petclinic-visits-service",
            "EXPLODED_BOOT_APP",
            "NO_CDS_LOW_DIRTY",
            "raw",
            "layer-fingerprint",
            "",
            "UNKNOWN",
            false,
            false,
            false,
            false,
            true,
            true,
            true,
            true,
            null,
            true,
            "CONFIRMED_WIN",
            true,
            "PASSED",
            false,
            false,
            RuntimePolicyScope.PUBLIC,
            List.of("fixture")
        );
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(
            tempDir.resolve("runtime-policy-admission-input.json").toFile(), input
        );
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(
            tempDir.resolve("runtime-protocol-registry.json").toFile(),
            Map.of("protocols", List.of(new RuntimePolicyRegistryEntry(
                "visits-raw-no-cds",
                "spring-petclinic-visits-service",
                "EXPLODED_BOOT_APP",
                "NO_CDS_LOW_DIRTY",
                "raw",
                RuntimePolicyScope.PUBLIC,
                false,
                false,
                "",
                "",
                true,
                "CONFIRMED_WIN",
                "fixture"
            )))
        );
        Path output = tempDir.resolve("output");
        RecommendRuntimeMojo mojo = new RecommendRuntimeMojo();
        set(mojo, "runtimeRecommendationEnabled", true);
        set(mojo, "runtimeRecommendationMode", "analyze");
        set(mojo, "runtimeRecommendationInputDir", tempDir.toFile());
        set(mojo, "runtimeRecommendationOutputDir", output.toFile());
        set(mojo, "service", "Spring PetClinic visits-service");
        set(mojo, "launchMode", "EXPLODED_BOOT_APP");
        set(mojo, "runtimePolicy", "NO_CDS_LOW_DIRTY");
        set(mojo, "reducerEngine", "raw");
        set(mojo, "scope", "PUBLIC");

        mojo.execute();

        assertTrue(Files.isRegularFile(output.resolve("runtime-policy-admission-input.json")));
        assertTrue(Files.isRegularFile(output.resolve("jmoa-runtime-recommendation.json")));
        assertTrue(Files.isRegularFile(output.resolve("jmoa-runtime-recommendation.md")));
        JsonNode recommendation = MAPPER.readTree(output.resolve("jmoa-runtime-recommendation.json").toFile());
        assertEquals("RECOMMEND_CONFIRMED_POLICY", recommendation.path("decision").asText());
        assertTrue(recommendation.path("runtimePolicyPromotionAllowed").asBoolean());
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
