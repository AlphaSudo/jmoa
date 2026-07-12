package com.yourorg.jmoa.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.yourorg.jmoa.plugin.generated.GeneratedClassInventory;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeAttribution;
import com.yourorg.jmoa.plugin.generated.relevance.GeneratedFamilyMatchedEvidenceAnalyzer;
import com.yourorg.jmoa.plugin.generated.relevance.GeneratedFamilyMatchedEvidenceAnalyzer.EvidenceIdentity;
import com.yourorg.jmoa.plugin.generated.relevance.GeneratedFamilyMatchedEvidenceReportWriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Mojo(name = "analyze-generated-evidence")
public final class GeneratedFamilyMatchedEvidenceMojo extends AbstractMojo {
    private final ObjectMapper mapper = new ObjectMapper();
    @Parameter(property = "jmoa.generatedEvidence.enabled", defaultValue = "false") private boolean enabled;
    @Parameter(property = "jmoa.generatedEvidence.inventory", required = true) private File inventoryFile;
    @Parameter(property = "jmoa.generatedEvidence.staticIdentity") private File staticIdentityFile;
    @Parameter(property = "jmoa.generatedEvidence.lifecycleManifest") private File lifecycleManifest;
    @Parameter(property = "jmoa.generatedEvidence.startupCapture") private File startupCapture;
    @Parameter(property = "jmoa.generatedEvidence.warmupCapture") private File warmupCapture;
    @Parameter(property = "jmoa.generatedEvidence.workloadCapture") private File workloadCapture;
    @Parameter(property = "jmoa.generatedEvidence.staticArtifactSha256") private String staticSha;
    @Parameter(property = "jmoa.generatedEvidence.captureArtifactSha256") private String captureSha;
    @Parameter(property = "jmoa.generatedEvidence.service", defaultValue = "unknown") private String service;
    @Parameter(property = "jmoa.generatedEvidence.staticService") private String staticService;
    @Parameter(property = "jmoa.generatedEvidence.captureService") private String captureService;
    @Parameter(property = "jmoa.generatedEvidence.launchMode") private String launchMode;
    @Parameter(property = "jmoa.generatedEvidence.staticLaunchMode") private String staticLaunchMode;
    @Parameter(property = "jmoa.generatedEvidence.captureLaunchMode") private String captureLaunchMode;
    @Parameter(property = "jmoa.generatedEvidence.runtimePolicy") private String runtimePolicy;
    @Parameter(property = "jmoa.generatedEvidence.staticRuntimePolicy") private String staticRuntimePolicy;
    @Parameter(property = "jmoa.generatedEvidence.captureRuntimePolicy") private String captureRuntimePolicy;
    @Parameter(property = "jmoa.generatedEvidence.reducerEngine") private String reducerEngine;
    @Parameter(property = "jmoa.generatedEvidence.staticReducerEngine") private String staticReducerEngine;
    @Parameter(property = "jmoa.generatedEvidence.captureReducerEngine") private String captureReducerEngine;
    @Parameter(property = "jmoa.generatedEvidence.familyRegistryVersion") private String familyRegistryVersion;
    @Parameter(property = "jmoa.generatedEvidence.staticFamilyRegistryVersion") private String staticFamilyRegistryVersion;
    @Parameter(property = "jmoa.generatedEvidence.captureFamilyRegistryVersion") private String captureFamilyRegistryVersion;
    @Parameter(property = "jmoa.generatedEvidence.scannerVersion") private String scannerVersion;
    @Parameter(property = "jmoa.generatedEvidence.staticScannerVersion") private String staticScannerVersion;
    @Parameter(property = "jmoa.generatedEvidence.captureScannerVersion") private String captureScannerVersion;
    @Parameter(property = "jmoa.generatedEvidence.outputDir", required = true) private File outputDir;

    @Override public void execute() throws MojoExecutionException {
        if (!enabled) { getLog().info("JMOA V2-U generated evidence analysis is disabled."); return; }
        if (inventoryFile == null || !inventoryFile.isFile()) throw new MojoExecutionException("A generated-class-inventory.json file is required.");
        try {
            JsonNode manifest = readJson(lifecycleManifest);
            startupCapture = firstExisting(startupCapture, manifestStageFile(manifest, "startup"));
            warmupCapture = firstExisting(warmupCapture, manifestStageFile(manifest, "warmup"));
            workloadCapture = firstExisting(workloadCapture, manifestStageFile(manifest, "workload"));
            var report = new GeneratedFamilyMatchedEvidenceAnalyzer().analyze(
                staticIdentity(readJson(staticIdentityFile)),
                captureIdentity(manifest),
                mapper.readValue(inventoryFile, GeneratedClassInventory.class),
                read(startupCapture),
                read(warmupCapture),
                read(workloadCapture)
            );
            Files.createDirectories(outputDir.toPath());
            new GeneratedFamilyMatchedEvidenceReportWriter().write(outputDir.toPath(), report);
            getLog().info("JMOA V2-U matched evidence written to: " + outputDir.getAbsolutePath() + " status=" + report.evidenceStatus());
        } catch (Exception e) { throw new MojoExecutionException("Failed to analyze generated-family matched evidence", e); }
    }
    private GeneratedClassRuntimeAttribution read(File file) throws Exception { return file != null && file.isFile() ? mapper.readValue(file, GeneratedClassRuntimeAttribution.class) : null; }

    private JsonNode readJson(File file) throws Exception {
        return file != null && file.isFile() ? mapper.readTree(file) : null;
    }

    private EvidenceIdentity staticIdentity(JsonNode fileIdentity) {
        return new EvidenceIdentity(
            firstText(fileIdentity, "service", firstNonBlank(staticService, service)),
            firstText(fileIdentity, "artifactSha256", staticSha),
            firstText(fileIdentity, "launchMode", firstNonBlank(staticLaunchMode, launchMode)),
            firstText(fileIdentity, "runtimePolicy", firstNonBlank(staticRuntimePolicy, runtimePolicy)),
            firstText(fileIdentity, "reducerEngine", firstNonBlank(staticReducerEngine, reducerEngine)),
            firstText(fileIdentity, "familyRegistryVersion", firstNonBlank(staticFamilyRegistryVersion, familyRegistryVersion)),
            firstText(fileIdentity, "scannerVersion", firstNonBlank(staticScannerVersion, scannerVersion))
        );
    }

    private EvidenceIdentity captureIdentity(JsonNode manifest) {
        return new EvidenceIdentity(
            firstText(manifest, "service", firstNonBlank(captureService, service)),
            firstText(manifest, "artifactSha256", captureSha),
            firstText(manifest, "launchMode", firstNonBlank(captureLaunchMode, launchMode)),
            firstText(manifest, "runtimePolicy", firstNonBlank(captureRuntimePolicy, runtimePolicy)),
            firstText(manifest, "reducerEngine", firstNonBlank(captureReducerEngine, reducerEngine)),
            firstText(manifest, "familyRegistryVersion", firstNonBlank(captureFamilyRegistryVersion, familyRegistryVersion)),
            firstText(manifest, "scannerVersion", firstNonBlank(captureScannerVersion, scannerVersion))
        );
    }

    private File manifestStageFile(JsonNode manifest, String stage) {
        if (manifest == null || lifecycleManifest == null) return null;
        String value = firstText(manifest.path("stages").path(stage), "runtimeAttribution", null);
        if (value == null || value.isBlank()) return null;
        File file = new File(value);
        if (!file.isAbsolute()) {
            Path parent = lifecycleManifest.toPath().getParent();
            file = parent == null ? file : parent.resolve(value).normalize().toFile();
        }
        return file;
    }

    private static File firstExisting(File explicit, File discovered) {
        if (explicit != null && explicit.isFile()) return explicit;
        if (discovered != null && discovered.isFile()) return discovered;
        return explicit != null ? explicit : discovered;
    }

    private static String firstText(JsonNode root, String field, String fallback) {
        if (root != null && !root.isMissingNode() && root.hasNonNull(field) && !root.path(field).asText().isBlank()) {
            return root.path(field).asText();
        }
        return fallback;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second;
    }
}
