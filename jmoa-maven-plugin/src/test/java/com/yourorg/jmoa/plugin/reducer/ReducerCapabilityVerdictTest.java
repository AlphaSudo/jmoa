package com.yourorg.jmoa.plugin.reducer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReducerCapabilityVerdictTest {

    @Test
    void separatesArtifactSizeLayoutAndRamCapabilities() {
        ReducerReport report = report("strip", "stable-raw-copy", 4284521L, List.of(auditRecord()));
        ReducerReproducibilityProof.ProofRecord proof = new ReducerReproducibilityProof.ProofRecord(
                "C:\\build-a", "C:\\build-b", 24, 8937, true, List.of(), true, true);
        ReducerCapabilityVerdicts.CapabilityVerdictReport verdict = ReducerCapabilityVerdicts.compute(report, proof);

        assertTrue(verdict.supports(ReducerCapabilityVerdict.ARTIFACT_SIZE_WIN));
        assertTrue(verdict.supports(ReducerCapabilityVerdict.REPRODUCIBLE_LAYOUT_WIN));
        assertTrue(verdict.supports(ReducerCapabilityVerdict.RAM_NOT_SUPPORTED));
        assertEquals(3, verdict.capabilities().size());
    }

    @Test
    void layoutWinRequiresAttachedTwoBuildProofNotJustAName() {
        // Chapter-29 F7: a stable layout plus audits is NOT sufficient — the
        // verdict requires an attached passing two-build proof record.
        assertFalse(ReducerCapabilityVerdicts.compute(report("strip", "stable-raw-copy", 100L, List.of(auditRecord())))
                .supports(ReducerCapabilityVerdict.REPRODUCIBLE_LAYOUT_WIN),
                "no proof attached -> the layout win must not be claimed");
        ReducerReproducibilityProof.ProofRecord passingProof = new ReducerReproducibilityProof.ProofRecord(
                "C:\\build-a", "C:\\build-b", 2, 40, true, List.of(), true, true);
        assertTrue(ReducerCapabilityVerdicts.compute(report("strip", "stable-raw-copy", 100L, List.of(auditRecord())), passingProof)
                .supports(ReducerCapabilityVerdict.REPRODUCIBLE_LAYOUT_WIN));
        ReducerReproducibilityProof.ProofRecord failingProof = new ReducerReproducibilityProof.ProofRecord(
                "C:\\build-a", "C:\\build-b", 2, 40, false, List.of("jar bytes differ: lib.jar"), false, false);
        ReducerCapabilityVerdicts.CapabilityVerdictReport failed =
                ReducerCapabilityVerdicts.compute(report("repack-only", "stable-raw-copy", 100L, List.of(auditRecord())), failingProof);
        assertFalse(failed.supports(ReducerCapabilityVerdict.REPRODUCIBLE_LAYOUT_WIN));
        assertTrue(failed.capabilities().stream()
                .anyMatch(e -> e.verdict() == ReducerCapabilityVerdict.REPRODUCIBLE_LAYOUT_WIN
                        && e.rationale().contains("proof FAILED")));
        assertFalse(ReducerCapabilityVerdicts.compute(report("strip", "current", 100L, List.of(auditRecord())), passingProof)
                .supports(ReducerCapabilityVerdict.REPRODUCIBLE_LAYOUT_WIN));
        ReducerCapabilityVerdicts.CapabilityVerdictReport repackOnly =
                ReducerCapabilityVerdicts.compute(
                        report("repack-only", "stable-raw-copy", 100L, List.of()), passingProof);
        assertTrue(repackOnly.supports(ReducerCapabilityVerdict.REPRODUCIBLE_LAYOUT_WIN),
                "repack-only changes no class bodies, so an empty mutation-audit list is valid when the two-build proof passes");
        assertTrue(repackOnly.capabilities().stream()
                .anyMatch(e -> e.verdict() == ReducerCapabilityVerdict.REPRODUCIBLE_LAYOUT_WIN
                        && e.rationale().contains("zero raw class mutation audits is valid")));

        ReducerCapabilityVerdicts.CapabilityVerdictReport unauditedStrip =
                ReducerCapabilityVerdicts.compute(
                        report("strip", "stable-raw-copy", 100L, List.of()), passingProof);
        assertFalse(unauditedStrip.supports(ReducerCapabilityVerdict.REPRODUCIBLE_LAYOUT_WIN),
                "strip permits class mutation and must retain raw class preservation audits");
        assertTrue(unauditedStrip.capabilities().stream()
                .anyMatch(e -> e.verdict() == ReducerCapabilityVerdict.REPRODUCIBLE_LAYOUT_WIN
                        && e.rationale().contains("requires raw preservation audits")));
    }

    @Test
    void sizeWinRequiresPositiveNetSavings() {
        ReducerCapabilityVerdicts.CapabilityVerdictReport verdict =
                ReducerCapabilityVerdicts.compute(report("repack-only", "current", -12L, List.of()));
        assertFalse(verdict.supports(ReducerCapabilityVerdict.ARTIFACT_SIZE_WIN));
        assertTrue(verdict.capabilities().stream()
                .anyMatch(e -> e.verdict() == ReducerCapabilityVerdict.ARTIFACT_SIZE_WIN
                        && e.rationale().contains("-12")));
    }

    @Test
    void ramNotSupportedIsAlwaysStatedWithItsBoundary() {
        for (String operation : List.of("repack-only", "strip")) {
            for (String layout : List.of("current", "stable-raw-copy")) {
                ReducerCapabilityVerdicts.CapabilityVerdictReport verdict =
                        ReducerCapabilityVerdicts.compute(report(operation, layout, 1L, List.of()));
                assertTrue(verdict.supports(ReducerCapabilityVerdict.RAM_NOT_SUPPORTED),
                        "RAM must stay unsupported for " + operation + "/" + layout);
                assertTrue(verdict.capabilities().stream()
                        .filter(e -> e.verdict() == ReducerCapabilityVerdict.RAM_NOT_SUPPORTED)
                        .allMatch(e -> e.rationale().contains("loaded-class/resident-byte mechanism")));
            }
        }
    }

    @Test
    void writerEmitsCapabilityVerdictJson() throws Exception {
        java.io.File outputDir = java.nio.file.Files.createTempDirectory("reducer-verdict").toFile();
        try {
            new ReducerReportWriter().write(outputDir, report("strip", "stable-raw-copy", 77L, List.of(auditRecord())));
            java.nio.file.Path verdictJson = outputDir.toPath().resolve("reducer-capability-verdict.json");
            assertTrue(java.nio.file.Files.exists(verdictJson), "writer must emit reducer-capability-verdict.json");
            String json = java.nio.file.Files.readString(verdictJson);
            assertTrue(json.contains("ARTIFACT_SIZE_WIN"));
            assertTrue(json.contains("REPRODUCIBLE_LAYOUT_WIN"));
            assertTrue(json.contains("RAM_NOT_SUPPORTED"));
            assertFalse(json.contains("\"optimized\""), "no generic optimized verdict may be exposed");
        } finally {
            java.nio.file.Files.walk(outputDir.toPath())
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        }
    }

    private static ReducerReport report(String operation, String jarLayout, long netSaved,
            List<RawReducerClassAuditRecord> audits) {
        return new ReducerReport(
                "test", "2026-08-22T00:00:00Z", true, false,
                "debug", "asm", operation, jarLayout,
                1, 1, 1000L, 900L, -netSaved, netSaved, netSaved, netSaved,
                new ReducerSafetyTaxonomy("test", List.of(), java.util.Map.of()),
                List.of(), audits, null, List.of());
    }

    private static RawReducerClassAuditRecord auditRecord() {
        return new RawReducerClassAuditRecord(
                "example/Class", "artifact.jar", "example/Class.class", "asm",
                "o", "r", 100, 90, List.of(), "n", "ocp", "rcp", "ob", "rb",
                "osm", "rsm", "obm", "rbm", "oa", "ra",
                true, true, true, true, true, true, true, true, "AUDITED");
    }
}
