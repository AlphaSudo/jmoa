package com.yourorg.jmoa.plugin.modec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * T11 integration on the REAL rebuilt candidate: generates the frozen
 * spring-core hybrid origin manifest (expanded rewritten root before its
 * original fallback JAR) from the actual T2 build outputs. Skips when the
 * build outputs are absent so CI elsewhere stays green.
 */
class HybridOriginManifestRealBuildIntegrationTest {

    private static final String EXPANDED =
            "C:\\jmoa-runs\\petclinic-qaboutz-v3-20260822\\10-t2-build-b\\work\\spring-petclinic-microservices\\spring-petclinic-customers-service\\target\\jmoa-expanded-deps\\org.springframework__spring-core__7.0.2";
    private static final String FALLBACK =
            "C:\\jmoa-runs\\petclinic-qaboutz-v3-20260822\\10-t2-build-b\\materialized\\v1a\\dependencies\\BOOT-INF\\lib\\spring-core-7.0.2.jar";
    private static final String OUTPUT =
            "C:\\jmoa-runs\\petclinic-qaboutz-v3-20260822\\21-t11-origin-manifest\\spring-core-hybrid-origin-manifest.json";

    @Test
    void generatesFrozenSpringCoreOriginManifestFromRealBuild() throws Exception {
        assumeTrue(Files.isDirectory(Path.of(EXPANDED)), "real expanded hybrid root is absent; skipping");
        assumeTrue(Files.isRegularFile(Path.of(FALLBACK)), "real fallback JAR is absent; skipping");

        // spring-core 7.0.2 is a multi-release JAR (version floors 21/24 for
        // shadowing classreading/task variants). The frozen runtime tuple is
        // JDK 17, so the typed JDK-floor proof accepts only because every
        // floor is unreachable under the frozen runtime; the proof is
        // recorded inside the manifest.
        HybridOriginManifestGenerator.OriginManifest manifest =
                HybridOriginManifestGenerator.generate(
                        List.of(Path.of(EXPANDED), Path.of(FALLBACK)),
                        HybridOriginManifestGenerator.MultiReleasePolicy.jdkFloorProof(17));

        assertEquals(2, manifest.originCount());
        assertTrue(manifest.entryCount() > 100, "spring-core carries hundreds of entries");
        assertTrue(manifest.entries().stream()
                .filter(e -> e.contenders().size() >= 2)
                .allMatch(e -> e.winner().classpathOrdinal() == 0),
                "entries present in both origins must be won by the expanded rewritten root");
        assertTrue(manifest.refusals().isEmpty(), () -> String.join("; ", manifest.refusals()));
        assertTrue(manifest.multiReleaseProofs().stream().anyMatch(proof -> proof.contains("floors >= 21")),
                "the multi-release proof must record the minimum floor");

        Path output = Path.of(OUTPUT);
        Files.createDirectories(output.getParent());
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(output.toFile(), manifest);
    }
}
