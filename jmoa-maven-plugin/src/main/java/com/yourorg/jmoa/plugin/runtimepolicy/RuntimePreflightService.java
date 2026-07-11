package com.yourorg.jmoa.plugin.runtimepolicy;

import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyAdmissionInput;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyDecision;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyRecommendation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Builds a preflight report without changing an artifact or runtime policy. */
public final class RuntimePreflightService {

    public enum Readiness {
        READY_FOR_SMOKE,
        READY_FOR_SCREEN,
        READY_FOR_CONFIRMATION,
        BLOCK_MISSING_ARTIFACT_HASH,
        BLOCK_MISSING_CDS_ARCHIVE_HASH,
        BLOCK_RUNTIME_POLICY_UNDECLARED,
        BLOCK_POLICY_MISMATCH,
        BLOCK_CDS_ARCHIVE_MISMATCH,
        BLOCK_RUNTIME_STACK_MISSING,
        BLOCK_NO_SEMANTIC_SMOKE,
        BLOCK_RUNTIME_PROMOTION,
        NO_CLAIM
    }

    public record FileProbe(String role, String path, boolean present, long bytes, String sha256) {
        public FileProbe {
            role = role == null || role.isBlank() ? "unknown" : role.trim();
            path = path == null ? "" : path;
            sha256 = sha256 == null ? "" : sha256;
        }
    }

    public record RuntimePreflightReport(
        String metadataVersion,
        String generatedAt,
        Readiness readiness,
        List<String> blockers,
        List<String> nextActions,
        FileProbe artifact,
        FileProbe cdsArchive,
        RuntimePolicyAdmissionInput input,
        RuntimePolicyRecommendation recommendation,
        List<String> boundaries
    ) {
        public RuntimePreflightReport {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
            boundaries = boundaries == null ? List.of() : List.copyOf(boundaries);
        }
    }

    private static final List<String> BOUNDARIES = List.of(
        "Preflight is report-only. It does not train CDS, alter a launch command, or run a workload.",
        "A ready preflight is permission to take the next gate, not a runtime or memory claim.",
        "A CDS archive must be trained for and mapped by the exact artifact variant being screened.",
        "A runtime screen still requires semantic smoke, V2-C validation, and V2-D attribution before any claim."
    );

    public RuntimePreflightReport preflight(
        RuntimePolicyAdmissionInput input,
        RuntimePolicyRecommendation recommendation,
        File artifactFile,
        File cdsArchiveFile
    ) throws IOException {
        RuntimePolicyAdmissionInput enriched = withFileHashes(input, artifactFile, cdsArchiveFile);
        FileProbe artifact = probe("artifact", artifactFile, enriched.artifactSha256());
        FileProbe cds = probe("cdsArchive", cdsArchiveFile, enriched.cdsArchiveSha256());
        List<String> blockers = new ArrayList<>();
        Readiness readiness = readiness(enriched, recommendation, artifact, cds, blockers);
        List<String> nextActions = nextActions(readiness, recommendation);
        return new RuntimePreflightReport(
            "v2o-runtime-preflight",
            Instant.now().toString(),
            readiness,
            blockers,
            nextActions,
            artifact,
            cds,
            enriched,
            recommendation,
            BOUNDARIES
        );
    }

    public RuntimePolicyAdmissionInput withFileHashes(
        RuntimePolicyAdmissionInput input,
        File artifactFile,
        File cdsArchiveFile
    ) throws IOException {
        FileProbe artifact = probe("artifact", artifactFile, input.artifactSha256());
        FileProbe cds = probe("cdsArchive", cdsArchiveFile, input.cdsArchiveSha256());
        return new RuntimePolicyAdmissionInput(
            input.metadataVersion(),
            input.service(),
            input.launchMode(),
            input.runtimePolicy(),
            input.reducerEngine(),
            artifact.sha256(),
            cds.sha256(),
            input.comparisonRuntimePolicy(),
            input.cdsEnabled(),
            input.appCdsEnabled(),
            input.leydenEnabled(),
            input.javaagentPresent(),
            input.artifactEvidencePresent() || artifact.present(),
            input.runtimeStackAvailable(),
            input.semanticSmokePassed(),
            input.runtimeMaterializationProofPresent(),
            input.cdsMappedAtRuntime(),
            input.hasV2CConfirmation(),
            input.v2cVerdict(),
            input.hasV2DAttribution(),
            input.screenVerdict(),
            input.diagnosticOnly(),
            input.preflight(),
            input.scope(),
            input.sourceReports()
        );
    }

    private static Readiness readiness(
        RuntimePolicyAdmissionInput input,
        RuntimePolicyRecommendation recommendation,
        FileProbe artifact,
        FileProbe cds,
        List<String> blockers
    ) {
        String policy = RuntimePolicyRegistry.normalize(input.runtimePolicy());
        if (policy.isBlank() || "UNKNOWN".equals(policy)) {
            blockers.add("runtime policy is undeclared");
            return Readiness.BLOCK_RUNTIME_POLICY_UNDECLARED;
        }
        if (!artifact.present() || artifact.sha256().isBlank()) {
            blockers.add("artifact path or SHA-256 is missing");
            return Readiness.BLOCK_MISSING_ARTIFACT_HASH;
        }
        if (policy.contains("CDS") && !policy.startsWith("NO_CDS")
            && (!cds.present() || cds.sha256().isBlank())) {
            blockers.add("CDS policy requires an archive path and SHA-256");
            return Readiness.BLOCK_MISSING_CDS_ARCHIVE_HASH;
        }
        return switch (recommendation.decision()) {
            case BLOCK_POLICY_MISMATCH -> blocked(Readiness.BLOCK_POLICY_MISMATCH, recommendation, blockers);
            case BLOCK_CDS_ARCHIVE_MISMATCH -> blocked(Readiness.BLOCK_CDS_ARCHIVE_MISMATCH, recommendation, blockers);
            case BLOCK_RUNTIME_STACK_MISSING -> blocked(Readiness.BLOCK_RUNTIME_STACK_MISSING, recommendation, blockers);
            case BLOCK_NO_SEMANTIC_SMOKE -> blocked(Readiness.BLOCK_NO_SEMANTIC_SMOKE, recommendation, blockers);
            case BLOCK_RUNTIME_PROMOTION -> blocked(Readiness.BLOCK_RUNTIME_PROMOTION, recommendation, blockers);
            case RECOMMEND_CONFIRMED_POLICY -> Readiness.READY_FOR_CONFIRMATION;
            case RECOMMEND_SCREEN_REQUIRED -> input.semanticSmokePassed() == null
                ? Readiness.READY_FOR_SMOKE
                : Readiness.READY_FOR_SCREEN;
            case ALLOW_ARTIFACT_ONLY, ALLOW_DIAGNOSTIC_ONLY, BLOCK_NO_V2C_CONFIRMATION, UNKNOWN -> {
                if (Boolean.FALSE.equals(input.semanticSmokePassed())) {
                    yield blocked(Readiness.BLOCK_NO_SEMANTIC_SMOKE, recommendation, blockers);
                }
                yield input.semanticSmokePassed() == null ? Readiness.READY_FOR_SMOKE : Readiness.READY_FOR_SCREEN;
            }
        };
    }

    private static Readiness blocked(
        Readiness readiness,
        RuntimePolicyRecommendation recommendation,
        List<String> blockers
    ) {
        blockers.addAll(recommendation.reasons());
        blockers.addAll(recommendation.missingGates());
        return readiness;
    }

    private static List<String> nextActions(Readiness readiness, RuntimePolicyRecommendation recommendation) {
        List<String> actions = new ArrayList<>(recommendation.nextActions());
        switch (readiness) {
            case READY_FOR_SMOKE -> actions.add("Run scripts/runtime-semantic-smoke.ps1 before a memory screen.");
            case READY_FOR_SCREEN -> actions.add("Run scripts/runtime-screen-pair.ps1 for one clean pair.");
            case READY_FOR_CONFIRMATION -> actions.add("Use scripts/run-v2-confirmation.ps1 only with fresh valid pair evidence.");
            case BLOCK_MISSING_ARTIFACT_HASH -> actions.add("Provide the exact measured artifact path so preflight can compute SHA-256.");
            case BLOCK_MISSING_CDS_ARCHIVE_HASH -> actions.add("Train or provide a fresh CDS archive for this artifact variant.");
            default -> {
                // The runtime-policy engine already supplies the appropriate remediation action.
            }
        }
        return List.copyOf(actions);
    }

    private static FileProbe probe(String role, File file, String fallbackSha256) throws IOException {
        if (file == null) {
            return new FileProbe(role, "", false, 0, fallbackSha256);
        }
        if (!file.isFile()) {
            return new FileProbe(role, file.getAbsolutePath(), false, 0, fallbackSha256);
        }
        return new FileProbe(role, file.getAbsolutePath(), true, file.length(), sha256(file));
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file.toPath())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder value = new StringBuilder(hash.length * 2);
            for (byte current : hash) {
                value.append(String.format("%02X", current));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
