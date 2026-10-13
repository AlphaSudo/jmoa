package com.yourorg.jmoa.plugin.overlay;

import com.yourorg.jmoa.plugin.LambdaDeduplicationMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T10 integration fixture: the REAL Mojo parameters drive the REAL boundary
 * with two different non-PetClinic services and no service-name branch. The
 * same code path must accept whichever overlay data is supplied, embed the
 * effective policy, and refuse mode selections the overlay does not allow.
 */
class ServicePolicyOverlayMojoBoundaryTest {

    @TempDir
    Path tempDir;

    @Test
    void sameMojoEnforcesTwoDifferentServiceOverlaysWithoutAnyServiceBranch() throws Exception {
        LambdaDeduplicationMojo inventoryMojo = configuredMojo(overlay(
                "synthetic-inventory-service", "\"RUNTIME\"", "\"CURRENT\""), "RUNTIME", "CURRENT");
        ServicePolicyOverlayBoundary.OverlayPolicyReport inventory = inventoryMojo.enforceServicePolicyOverlay();
        assertEquals("synthetic-inventory-service", inventory.serviceName());
        assertEquals("RUNTIME", inventory.effectiveModes().get("tier1AdapterMode"));
        assertEquals("CURRENT", inventory.effectiveModes().get("tier2Consolidation"));

        LambdaDeduplicationMojo billingMojo = configuredMojo(overlay(
                "synthetic-billing-service", "\"RUNTIME\",\"DIRECT\"", "\"PACKAGE_SIGNATURE\""), "DIRECT", "PACKAGE_SIGNATURE");
        ServicePolicyOverlayBoundary.OverlayPolicyReport billing = billingMojo.enforceServicePolicyOverlay();
        assertEquals("synthetic-billing-service", billing.serviceName());
        assertEquals("DIRECT", billing.effectiveModes().get("tier1AdapterMode"));
        assertEquals("PACKAGE_SIGNATURE", billing.effectiveModes().get("tier2Consolidation"));

        assertTrue(inventory.overlaySha256().matches("^[0-9A-F]{64}$"));
        assertTrue(inventory.guardrails().get("requireMutationProof"));
        assertTrue(inventory.acceptedFields().contains("guardrails"));
        assertTrue(inventory.rejectedFields().isEmpty());
        assertEquals(2, inventory.validationDecisions().stream().filter(
                ServicePolicyOverlayValidator.PolicyDecision::accepted).count());
    }

    @Test
    void mojoWithoutOverlayIsUntouchedAndDisallowedModesFailTheBuild() throws Exception {
        LambdaDeduplicationMojo bare = configuredMojo(null, "RUNTIME", "CURRENT");
        assertNull(bare.enforceServicePolicyOverlay());

        LambdaDeduplicationMojo restricted = configuredMojo(overlay(
                "synthetic-inventory-service", "\"RUNTIME\"", "\"CURRENT\""), "DIRECT", "CURRENT");
        MojoFailureException failure = assertThrows(MojoFailureException.class,
                restricted::enforceServicePolicyOverlay);
        assertTrue(failure.getMessage().contains("Overlay policy refused mode selection"));
        assertTrue(failure.getMessage().contains("tier1AdapterModes"));
    }

    @Test
    void invalidOverlayFailsTheMojoWithTypedReasons() throws Exception {
        LambdaDeduplicationMojo corrupted = configuredMojo(overlay(
                        "synthetic-inventory-service", "\"RUNTIME\"", "\"CURRENT\"")
                        .replace("\"requireMutationProof\": true", "\"requireMutationProof\": false"),
                "RUNTIME", "CURRENT");
        MojoFailureException failure = assertThrows(MojoFailureException.class,
                corrupted::enforceServicePolicyOverlay);
        assertTrue(failure.getMessage().contains("WEAKENED_GUARDRAIL"));
    }

    private LambdaDeduplicationMojo configuredMojo(String overlayJson, String tier1Mode, String tier2Mode)
            throws Exception {
        LambdaDeduplicationMojo mojo = new LambdaDeduplicationMojo();
        if (overlayJson != null) {
            Path overlayFile = tempDir.resolve("overlay-" + System.nanoTime() + ".json");
            Files.writeString(overlayFile, overlayJson);
            set(mojo, "servicePolicyOverlayPath", overlayFile.toFile());
        }
        set(mojo, "tier1AdapterMode", tier1Mode);
        set(mojo, "tier2AdapterConsolidation", tier2Mode);
        return mojo;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }

    private String overlay(String service, String tier1Modes, String tier2Modes) {
        return """
                {
                  "schemaVersion": "jmoa-service-policy-overlay-v1",
                  "service": "%s",
                  "sourceRevision": "1111111111111111111111111111111111111111",
                  "profileSha256": "2222222222222222222222222222222222222222222222222222222222222222",
                  "configTreeSha256": "3333333333333333333333333333333333333333333333333333333333333333",
                  "safeSamsSha256": "4444444444444444444444444444444444444444444444444444444444444444",
                  "workloadId": "synthetic-workload-v1",
                  "allowedModes": {
                    "tier1AdapterModes": [%s],
                    "tier2Consolidations": [%s],
                    "reducerOperations": ["repack-only", "strip"],
                    "reducerJarLayouts": ["current", "stable-raw-copy"],
                    "packagingModes": ["EXPLODED_BOOT_APP", "FAT_BOOT_JAR"]
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
                """.formatted(service, tier1Modes, tier2Modes);
    }
}
