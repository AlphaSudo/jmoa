package com.yourorg.jmoa.plugin.runtimepolicy;

import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyAdmissionInput;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyDecision;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyRegistryEntry;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimePolicyRecommendationEngineTest {

    private static final String D2R_ARTIFACT = "9D00877C0AF90E02B0C8D812F8BC659297CA67D6A939016CAF96D3D5CED79742";
    private static final String D2R_CDS = "64A4331695D092148A105ADAA47FEEA0CA46CB0CC561C3289F0413D1A67B6ACC";

    private final RuntimePolicyRecommendationEngine engine = new RuntimePolicyRecommendationEngine(
        new RuntimePolicyRegistry(List.of(
            new RuntimePolicyRegistryEntry(
                "petclinic-visits-raw-no-cds",
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
                "Public no-CDS confirmation."
            ),
            new RuntimePolicyRegistryEntry(
                "doctor-d2r-raw-cds",
                "doctor-corrected-d2",
                "SPRING_BOOT_FAT_JAR",
                "CDS",
                "raw",
                RuntimePolicyScope.PRIVATE,
                true,
                true,
                D2R_ARTIFACT,
                D2R_CDS,
                true,
                "CONFIRMED_WIN",
                "Private CDS confirmation with a variant-specific archive."
            )
        ))
    );

    @Test
    void recommendsConfirmedPublicNoCdsPolicy() {
        Fixture fixture = confirmedNoCds("Spring PetClinic visits-service");

        var report = engine.recommend(fixture.build());

        assertEquals(RuntimePolicyDecision.RECOMMEND_CONFIRMED_POLICY, report.decision());
        assertEquals(RuntimePolicyScope.PUBLIC, report.scope());
        assertTrue(report.protocolMatchesRegistry());
        assertTrue(report.runtimePolicyPromotionAllowed());
    }

    @Test
    void recommendsConfirmedPrivateCdsPolicyOnlyWithMatchedVariantArchive() {
        Fixture fixture = confirmedCds();

        var report = engine.recommend(fixture.build());

        assertEquals(RuntimePolicyDecision.RECOMMEND_CONFIRMED_POLICY, report.decision());
        assertEquals(RuntimePolicyScope.PRIVATE, report.scope());
        assertTrue(report.protocolMatchesRegistry());
    }

    @Test
    void blocksOldD2ArchiveWithD2rArtifact() {
        Fixture fixture = confirmedCds();
        fixture.cdsArchiveSha256 = "6FE999095F8800D3B820B87D60239EDD2217E730B5E75F9328737670EF6B8E3B";

        var report = engine.recommend(fixture.build());

        assertEquals(RuntimePolicyDecision.BLOCK_CDS_ARCHIVE_MISMATCH, report.decision());
        assertFalse(report.runtimePolicyPromotionAllowed());
        assertTrue(report.missingGates().stream().anyMatch(value -> value.contains("CDS archive hash")));
    }

    @Test
    void blocksNoCdsCandidateAgainstCdsBaseline() {
        Fixture fixture = confirmedNoCds("spring-petclinic-visits-service");
        fixture.comparisonRuntimePolicy = "CDS";

        assertEquals(RuntimePolicyDecision.BLOCK_POLICY_MISMATCH, engine.recommend(fixture.build()).decision());
    }

    @Test
    void blocksV2hFailedHardenedScreen() {
        Fixture fixture = confirmedNoCds("spring-petclinic-customers-service");
        fixture.reducerEngine = "asm";
        fixture.screenVerdict = "DO_NOT_PROMOTE_TO_3PAIR_CONFIRMATION";
        fixture.hasV2c = false;
        fixture.v2cVerdict = "NO_CLAIM";

        assertEquals(RuntimePolicyDecision.BLOCK_RUNTIME_PROMOTION, engine.recommend(fixture.build()).decision());
    }

    @Test
    void preflightRequiresNoCdsArtifactFingerprint() {
        Fixture fixture = confirmedNoCds("spring-petclinic-visits-service");
        fixture.preflight = true;
        fixture.artifactSha256 = "";

        var report = engine.recommend(fixture.build());

        assertEquals(RuntimePolicyDecision.RECOMMEND_SCREEN_REQUIRED, report.decision());
        assertTrue(report.missingGates().contains("artifact SHA-256 or deployment-layer fingerprint"));
    }

    @Test
    void requiresMaterializationProofForRegisteredNoCdsPolicy() {
        Fixture fixture = confirmedNoCds("spring-petclinic-visits-service");
        fixture.runtimeMaterializationProofPresent = false;

        var report = engine.recommend(fixture.build());

        assertEquals(RuntimePolicyDecision.RECOMMEND_SCREEN_REQUIRED, report.decision());
        assertTrue(report.missingGates().contains("runtime materialization proof"));
    }

    @Test
    void requiresExplicitNoCdsDisabledStateEvidence() {
        Fixture fixture = confirmedNoCds("spring-petclinic-visits-service");
        fixture.appCdsEnabled = null;

        var report = engine.recommend(fixture.build());

        assertEquals(RuntimePolicyDecision.RECOMMEND_SCREEN_REQUIRED, report.decision());
        assertTrue(report.missingGates().contains("AppCDS disabled-state evidence"));
    }

    @Test
    void allowsArtifactOnlyOutsidePreflightWhenSemanticSmokeIsUnavailable() {
        Fixture fixture = confirmedNoCds("spring-petclinic-visits-service");
        fixture.semanticSmokePassed = null;
        fixture.hasV2c = false;
        fixture.v2cVerdict = "NO_CLAIM";
        fixture.hasV2d = false;

        assertEquals(RuntimePolicyDecision.ALLOW_ARTIFACT_ONLY, engine.recommend(fixture.build()).decision());
    }

    @Test
    void blocksMissingRuntimeStackBeforeSemanticSmoke() {
        Fixture fixture = confirmedNoCds("spring-petclinic-visits-service");
        fixture.runtimeStackAvailable = false;

        assertEquals(RuntimePolicyDecision.BLOCK_RUNTIME_STACK_MISSING, engine.recommend(fixture.build()).decision());
    }

    @Test
    void leavesUnregisteredPolicyScreenRequiredAfterConfirmation() {
        Fixture fixture = confirmedNoCds("another-service");

        var report = engine.recommend(fixture.build());

        assertEquals(RuntimePolicyDecision.RECOMMEND_SCREEN_REQUIRED, report.decision());
        assertFalse(report.protocolMatchesRegistry());
    }

    private static Fixture confirmedNoCds(String service) {
        Fixture fixture = new Fixture();
        fixture.service = service;
        fixture.launchMode = "EXPLODED_BOOT_APP";
        fixture.runtimePolicy = "NO_CDS_LOW_DIRTY";
        fixture.reducerEngine = "raw";
        fixture.artifactSha256 = "layer-fingerprint";
        fixture.cdsEnabled = false;
        fixture.appCdsEnabled = false;
        fixture.leydenEnabled = false;
        fixture.javaagentPresent = false;
        fixture.scope = RuntimePolicyScope.PUBLIC;
        return fixture;
    }

    private static Fixture confirmedCds() {
        Fixture fixture = new Fixture();
        fixture.service = "doctor-corrected-d2";
        fixture.launchMode = "SPRING_BOOT_FAT_JAR";
        fixture.runtimePolicy = "CDS";
        fixture.reducerEngine = "raw";
        fixture.artifactSha256 = D2R_ARTIFACT;
        fixture.cdsArchiveSha256 = D2R_CDS;
        fixture.cdsEnabled = true;
        fixture.javaagentPresent = false;
        fixture.scope = RuntimePolicyScope.PRIVATE;
        return fixture;
    }

    private static final class Fixture {
        String service = "service";
        String launchMode = "EXPLODED_BOOT_APP";
        String runtimePolicy = "NO_CDS_LOW_DIRTY";
        String reducerEngine = "raw";
        String artifactSha256 = "fingerprint";
        String cdsArchiveSha256 = "";
        String comparisonRuntimePolicy = "UNKNOWN";
        Boolean cdsEnabled = false;
        Boolean appCdsEnabled = false;
        Boolean leydenEnabled = false;
        Boolean javaagentPresent = false;
        boolean artifactEvidencePresent = true;
        Boolean runtimeStackAvailable = true;
        Boolean semanticSmokePassed = true;
        Boolean runtimeMaterializationProofPresent = true;
        Boolean cdsMappedAtRuntime = true;
        boolean hasV2c = true;
        String v2cVerdict = "CONFIRMED_WIN";
        boolean hasV2d = true;
        String screenVerdict = "PASSED";
        boolean diagnosticOnly;
        boolean preflight;
        RuntimePolicyScope scope = RuntimePolicyScope.PUBLIC;

        RuntimePolicyAdmissionInput build() {
            return new RuntimePolicyAdmissionInput(
                "v2n-test-input",
                service,
                launchMode,
                runtimePolicy,
                reducerEngine,
                artifactSha256,
                cdsArchiveSha256,
                comparisonRuntimePolicy,
                cdsEnabled,
                appCdsEnabled,
                leydenEnabled,
                javaagentPresent,
                artifactEvidencePresent,
                runtimeStackAvailable,
                semanticSmokePassed,
                runtimeMaterializationProofPresent,
                cdsMappedAtRuntime,
                hasV2c,
                v2cVerdict,
                hasV2d,
                screenVerdict,
                diagnosticOnly,
                preflight,
                scope,
                List.of("fixture")
            );
        }
    }
}
