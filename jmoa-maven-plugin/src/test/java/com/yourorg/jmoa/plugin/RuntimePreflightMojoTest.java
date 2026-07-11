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

class RuntimePreflightMojoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void writesAHashBackedPreflightReport() throws Exception {
        RuntimePolicyAdmissionInput input = new RuntimePolicyAdmissionInput(
            "test", "spring-petclinic-visits-service", "EXPLODED_BOOT_APP", "NO_CDS_LOW_DIRTY", "raw", "", "",
            "UNKNOWN", false, false, false, false, true, true, true, true, null,
            false, "NO_CLAIM", false, "NOT_RUN", false, true, RuntimePolicyScope.PUBLIC, List.of()
        );
        MAPPER.writeValue(tempDir.resolve("runtime-policy-admission-input.json").toFile(), input);
        MAPPER.writeValue(tempDir.resolve("runtime-protocol-registry.json").toFile(), Map.of("protocols", List.of(
            new RuntimePolicyRegistryEntry(
                "visits", "spring-petclinic-visits-service", "EXPLODED_BOOT_APP", "NO_CDS_LOW_DIRTY", "raw",
                RuntimePolicyScope.PUBLIC, false, false, "", "", true, "CONFIRMED_WIN", "fixture"
            )
        )));
        Path artifact = Files.writeString(tempDir.resolve("service.jar"), "artifact");
        Path output = tempDir.resolve("output");

        RuntimePreflightMojo mojo = new RuntimePreflightMojo();
        set(mojo, "runtimePreflightEnabled", true);
        set(mojo, "inputDir", tempDir.toFile());
        set(mojo, "outputDir", output.toFile());
        set(mojo, "artifact", artifact.toFile());
        set(mojo, "service", "spring-petclinic-visits-service");
        set(mojo, "launchMode", "EXPLODED_BOOT_APP");
        set(mojo, "runtimePolicy", "NO_CDS_LOW_DIRTY");
        set(mojo, "scope", "PUBLIC");

        mojo.execute();

        assertTrue(Files.isRegularFile(output.resolve("jmoa-runtime-preflight.json")));
        JsonNode report = MAPPER.readTree(output.resolve("jmoa-runtime-preflight.json").toFile());
        assertEquals("READY_FOR_SCREEN", report.path("readiness").asText());
        assertTrue(report.path("artifact").path("sha256").asText().matches("[0-9A-F]{64}"));
    }

    @Test
    void blocksAStaleCdsArchiveWhenTheSuppliedArtifactDoesNotMatchTheRegistry() throws Exception {
        RuntimePolicyAdmissionInput input = new RuntimePolicyAdmissionInput(
            "test", "doctor-corrected-d2", "SPRING_BOOT_FAT_JAR", "CDS", "raw", "", "",
            "CDS", true, false, false, false, true, true, true, true, true,
            true, "CONFIRMED_WIN", true, "PASSED", false, true, RuntimePolicyScope.PRIVATE, List.of()
        );
        MAPPER.writeValue(tempDir.resolve("runtime-policy-admission-input.json").toFile(), input);
        MAPPER.writeValue(tempDir.resolve("runtime-protocol-registry.json").toFile(), Map.of("protocols", List.of(
            new RuntimePolicyRegistryEntry(
                "doctor", "doctor-corrected-d2", "SPRING_BOOT_FAT_JAR", "CDS", "raw",
                RuntimePolicyScope.PRIVATE, true, true,
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                true, "CONFIRMED_WIN", "fixture"
            )
        )));
        Path artifact = Files.writeString(tempDir.resolve("reduced.jar"), "new artifact");
        Path archive = Files.writeString(tempDir.resolve("old.jsa"), "old archive");
        Path output = tempDir.resolve("cds-output");

        RuntimePreflightMojo mojo = new RuntimePreflightMojo();
        set(mojo, "runtimePreflightEnabled", true);
        set(mojo, "inputDir", tempDir.toFile());
        set(mojo, "outputDir", output.toFile());
        set(mojo, "artifact", artifact.toFile());
        set(mojo, "cdsArchive", archive.toFile());
        set(mojo, "service", "doctor-corrected-d2");
        set(mojo, "launchMode", "SPRING_BOOT_FAT_JAR");
        set(mojo, "runtimePolicy", "CDS");
        set(mojo, "scope", "PRIVATE");

        mojo.execute();

        JsonNode report = MAPPER.readTree(output.resolve("jmoa-runtime-preflight.json").toFile());
        assertEquals("BLOCK_CDS_ARCHIVE_MISMATCH", report.path("readiness").asText());
        assertEquals("BLOCK_CDS_ARCHIVE_MISMATCH", report.path("recommendation").path("decision").asText());
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
