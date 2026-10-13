package com.yourorg.jmoa.plugin.overlay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicePolicyOverlayValidatorTest {

    @TempDir
    Path tempDir;

    private final ServicePolicyOverlayLoader loader = new ServicePolicyOverlayLoader();
    private final ServicePolicyOverlayValidator validator = new ServicePolicyOverlayValidator();

    @Test
    void acceptsWellFormedOverlayAndAllowsSelectedModes() throws IOException {
        ServicePolicyOverlayLoader.LoadedOverlay loaded = loader.load(write(VALID_OVERLAY));
        ServicePolicyOverlayValidator.ValidationResult result = validator.validate(loaded);
        assertTrue(result.valid(), () -> result.decisions().toString());

        assertTrue(validator.decideTier1AdapterMode(loaded.overlay(), "DIRECT").accepted());
        assertTrue(validator.decideTier1AdapterMode(loaded.overlay(), "RUNTIME").accepted());
        assertTrue(validator.decideReducerOperation(loaded.overlay(), "repack-only").accepted());
        assertTrue(validator.decidePackagingMode(loaded.overlay(), "EXPLODED_BOOT_APP").accepted());
    }

    @Test
    void refusesModeTheOverlayDidNotSelectEvenWhenUniversallySupported() throws IOException {
        String overlay = VALID_OVERLAY.replace("\"tier1AdapterModes\": [\"RUNTIME\", \"DIRECT\"]",
                "\"tier1AdapterModes\": [\"RUNTIME\"]");
        ServicePolicyOverlayLoader.LoadedOverlay loaded = loader.load(write(overlay));
        assertTrue(validator.validate(loaded).valid());

        ServicePolicyOverlayValidator.PolicyDecision decision = validator.decideTier1AdapterMode(loaded.overlay(), "DIRECT");
        assertFalse(decision.accepted());
        assertEquals(PolicyDecisionReason.MODE_NOT_ALLOWED_BY_OVERLAY, decision.reason());
    }

    @Test
    void refusesUnknownTopLevelAndNestedPolicyFields() throws IOException {
        ServicePolicyOverlayLoader.LoadedOverlay loaded = loader.load(write(
                VALID_OVERLAY.replace("\"workloadId\": \"corrected-petclinic-27x3\",",
                        "\"workloadId\": \"corrected-petclinic-27x3\", \"petclinicSpecialMode\": true,")));
        ServicePolicyOverlayValidator.ValidationResult result = validator.validate(loaded);
        assertFalse(result.valid());
        assertTrue(result.decisions().stream().anyMatch(d -> d.reason() == PolicyDecisionReason.UNKNOWN_POLICY_FIELD && d.field().equals("petclinicSpecialMode")));
    }

    @Test
    void refusesUnsupportedModeVocabulary() throws IOException {
        // ROOT_PACKAGE_SIGNATURE is a supported universal mode since track T4;
        // the refusal case must use a genuinely unknown vocabulary item.
        String overlay = VALID_OVERLAY.replace("\"tier2Consolidations\": [", "\"tier2Consolidations\": [\"ROOT_PACKAGE_FANTASY\", ");
        ServicePolicyOverlayValidator.ValidationResult result = validator.validate(loader.load(write(overlay)));
        assertFalse(result.valid());
        assertTrue(result.decisions().stream().anyMatch(d -> d.reason() == PolicyDecisionReason.UNSUPPORTED_TIER2_CONSOLIDATION));
    }

    @Test
    void acceptsOverlayV2WithRootPackageSignature() throws IOException {
        String overlay = VALID_OVERLAY
                .replace("jmoa-service-policy-overlay-v1", "jmoa-service-policy-overlay-v2")
                .replace("\"tier2Consolidations\": [", "\"tier2Consolidations\": [\"ROOT_PACKAGE_SIGNATURE\", ");
        ServicePolicyOverlayValidator.ValidationResult result = validator.validate(loader.load(write(overlay)));
        assertTrue(result.valid(), () -> result.decisions().toString());
    }

    @Test
    void refusesUnknownSchemaVersionAndMissingRequiredFields() throws IOException {
        ServicePolicyOverlayValidator.ValidationResult wrongVersion = validator.validate(
                loader.load(write(VALID_OVERLAY.replace("jmoa-service-policy-overlay-v1", "jmoa-service-policy-overlay-v99"))));
        assertFalse(wrongVersion.valid());
        assertTrue(wrongVersion.decisions().stream().anyMatch(d -> d.reason() == PolicyDecisionReason.UNKNOWN_SCHEMA_VERSION));

        ServicePolicyOverlayValidator.ValidationResult missingField = validator.validate(
                loader.load(write(VALID_OVERLAY.replace("\"workloadId\": \"corrected-petclinic-27x3\",", ""))));
        assertFalse(missingField.valid());
        assertTrue(missingField.decisions().stream().anyMatch(d -> d.reason() == PolicyDecisionReason.MISSING_REQUIRED_FIELD && d.field().equals("workloadId")));
    }

    @Test
    void refusesMalformedIdentitiesAndWeakenedGuardrails() throws IOException {
        ServicePolicyOverlayValidator.ValidationResult badHash = validator.validate(
                loader.load(write(VALID_OVERLAY.replace("AD1EC46AA174145392AFAD76824BACF9BB99E8D4FAA3F10A20CDA05AD3AA326A", "not-a-hash"))));
        assertFalse(badHash.valid());
        assertTrue(badHash.decisions().stream().anyMatch(d -> d.reason() == PolicyDecisionReason.INVALID_SHA256));

        ServicePolicyOverlayValidator.ValidationResult weakened = validator.validate(
                loader.load(write(VALID_OVERLAY.replace("\"requireMutationProof\": true", "\"requireMutationProof\": false"))));
        assertFalse(weakened.valid());
        assertTrue(weakened.decisions().stream().anyMatch(d -> d.reason() == PolicyDecisionReason.WEAKENED_GUARDRAIL));
    }

    @Test
    void syntheticNonPetClinicOverlayProvesTheEngineIsUniversal() throws IOException {
        // T10.5: the same universal engine must accept a different service with
        // different data and a narrower allowed-mode selection.
        String synthetic = """
                {
                  "schemaVersion": "jmoa-service-policy-overlay-v1",
                  "service": "synthetic-inventory-service",
                  "sourceRevision": "1111111111111111111111111111111111111111",
                  "profileSha256": "2222222222222222222222222222222222222222222222222222222222222222",
                  "configTreeSha256": "3333333333333333333333333333333333333333333333333333333333333333",
                  "safeSamsSha256": "4444444444444444444444444444444444444444444444444444444444444444",
                  "workloadId": "synthetic-workload-v1",
                  "allowedModes": {
                    "tier1AdapterModes": ["RUNTIME"],
                    "tier2Consolidations": ["CURRENT"],
                    "reducerOperations": ["repack-only"],
                    "reducerJarLayouts": ["current"],
                    "packagingModes": ["EXPLODED_BOOT_APP"]
                  },
                  "guardrails": {
                    "requiredSemanticRequests": 12,
                    "requireZeroWorkloadErrors": true,
                    "requireMutationProof": true,
                    "requireZeroFallback": true,
                    "admissionManifestRequired": false,
                    "requireProtectedComponentAudit": true,
                    "claimDiagnosticsSeparately": true
                  }
                }
                """;
        ServicePolicyOverlayLoader.LoadedOverlay loaded = loader.load(write(synthetic));
        assertTrue(validator.validate(loaded).valid());
        assertTrue(validator.decideTier1AdapterMode(loaded.overlay(), "RUNTIME").accepted());
        assertFalse(validator.decideTier1AdapterMode(loaded.overlay(), "DIRECT").accepted());
        assertFalse(validator.decideReducerOperation(loaded.overlay(), "strip").accepted());
    }

    private Path write(String json) throws IOException {
        Path file = tempDir.resolve("overlay-" + System.nanoTime() + ".json");
        Files.writeString(file, json);
        return file;
    }

    private static final String VALID_OVERLAY = """
            {
              "schemaVersion": "jmoa-service-policy-overlay-v1",
              "service": "spring-petclinic-customers-service",
              "sourceRevision": "305a1f13e4f961001d4e6cb50a9db51dc3fc5967",
              "profileSha256": "AD1EC46AA174145392AFAD76824BACF9BB99E8D4FAA3F10A20CDA05AD3AA326A",
              "configTreeSha256": "80EEA8F0321E72DD7AD87E4F94817A217C89DF5A66D4FFEFFACE50507BF89840",
              "safeSamsSha256": "BA93B6609B348CA5660D54148833218409A23B734245D999678D5FEC70D29683",
              "workloadId": "corrected-petclinic-27x3",
              "allowedModes": {
                "tier1AdapterModes": ["RUNTIME", "DIRECT"],
                "tier2Consolidations": ["CURRENT", "PACKAGE_SAM", "PACKAGE_SIGNATURE"],
                "reducerOperations": ["repack-only", "strip"],
                "reducerJarLayouts": ["current", "stable-raw-copy"],
                "packagingModes": ["EXPLODED_BOOT_APP", "FAT_BOOT_JAR"]
              },
              "guardrails": {
                "requiredSemanticRequests": 81,
                "requireZeroWorkloadErrors": true,
                "requireMutationProof": true,
                "requireZeroFallback": true,
                "admissionManifestRequired": true,
                "requireProtectedComponentAudit": true,
                "claimDiagnosticsSeparately": true
              }
            }
            """;
}
