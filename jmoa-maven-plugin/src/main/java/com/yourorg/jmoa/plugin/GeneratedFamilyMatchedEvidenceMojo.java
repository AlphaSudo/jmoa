package com.yourorg.jmoa.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.generated.GeneratedClassInventory;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeAttribution;
import com.yourorg.jmoa.plugin.generated.relevance.GeneratedFamilyMatchedEvidenceAnalyzer;
import com.yourorg.jmoa.plugin.generated.relevance.GeneratedFamilyMatchedEvidenceReportWriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Files;

@Mojo(name = "analyze-generated-evidence")
public final class GeneratedFamilyMatchedEvidenceMojo extends AbstractMojo {
    private final ObjectMapper mapper = new ObjectMapper();
    @Parameter(property = "jmoa.generatedEvidence.enabled", defaultValue = "false") private boolean enabled;
    @Parameter(property = "jmoa.generatedEvidence.inventory", required = true) private File inventoryFile;
    @Parameter(property = "jmoa.generatedEvidence.startupCapture") private File startupCapture;
    @Parameter(property = "jmoa.generatedEvidence.warmupCapture") private File warmupCapture;
    @Parameter(property = "jmoa.generatedEvidence.workloadCapture") private File workloadCapture;
    @Parameter(property = "jmoa.generatedEvidence.staticArtifactSha256") private String staticSha;
    @Parameter(property = "jmoa.generatedEvidence.captureArtifactSha256") private String captureSha;
    @Parameter(property = "jmoa.generatedEvidence.service", defaultValue = "unknown") private String service;
    @Parameter(property = "jmoa.generatedEvidence.outputDir", required = true) private File outputDir;

    @Override public void execute() throws MojoExecutionException {
        if (!enabled) { getLog().info("JMOA V2-T generated evidence analysis is disabled."); return; }
        if (inventoryFile == null || !inventoryFile.isFile()) throw new MojoExecutionException("A generated-class-inventory.json file is required.");
        try {
            var report = new GeneratedFamilyMatchedEvidenceAnalyzer().analyze(service, staticSha, captureSha,
                mapper.readValue(inventoryFile, GeneratedClassInventory.class), read(startupCapture), read(warmupCapture), read(workloadCapture));
            Files.createDirectories(outputDir.toPath());
            new GeneratedFamilyMatchedEvidenceReportWriter().write(outputDir.toPath(), report);
            getLog().info("JMOA V2-T matched evidence written to: " + outputDir.getAbsolutePath() + " status=" + report.evidenceStatus());
        } catch (Exception e) { throw new MojoExecutionException("Failed to analyze generated-family matched evidence", e); }
    }
    private GeneratedClassRuntimeAttribution read(File file) throws Exception { return file != null && file.isFile() ? mapper.readValue(file, GeneratedClassRuntimeAttribution.class) : null; }
}
