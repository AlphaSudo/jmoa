package com.yourorg.jmoa.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.generated.GeneratedClassInventory;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeAttribution;
import com.yourorg.jmoa.plugin.generated.relevance.GeneratedFamilyRelevanceAnalyzer;
import com.yourorg.jmoa.plugin.generated.relevance.GeneratedFamilyRelevanceReportWriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

@Mojo(name = "analyze-generated-relevance", defaultPhase = LifecyclePhase.VERIFY)
public final class GeneratedFamilyRelevanceMojo extends AbstractMojo {

    private final ObjectMapper mapper = new ObjectMapper();

    @Parameter(property = "jmoa.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "jmoa.generatedRelevance.enabled", defaultValue = "false")
    private boolean enabled;

    @Parameter(property = "jmoa.generatedRelevance.inputDir", required = true)
    private File inputDirectory;

    @Parameter(property = "jmoa.generatedRelevance.outputDir")
    private File outputDirectory;

    @Parameter(property = "jmoa.generatedRelevance.service", defaultValue = "unknown")
    private String service;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip || !enabled) {
            getLog().info("JMOA V2-S generated-family relevance analysis is disabled.");
            return;
        }
        if (inputDirectory == null || !inputDirectory.isDirectory()) {
            throw new MojoExecutionException("jmoa.generatedRelevance.inputDir must contain V2-A inventory reports.");
        }
        File inventoryFile = new File(inputDirectory, "generated-class-inventory.json");
        if (!inventoryFile.isFile()) {
            throw new MojoExecutionException("Missing generated-class-inventory.json in " + inputDirectory.getAbsolutePath());
        }
        try {
            GeneratedClassInventory inventory = mapper.readValue(inventoryFile, GeneratedClassInventory.class);
            File runtimeFile = new File(inputDirectory, "generated-class-runtime-attribution.json");
            GeneratedClassRuntimeAttribution runtime = runtimeFile.isFile()
                ? mapper.readValue(runtimeFile, GeneratedClassRuntimeAttribution.class)
                : null;
            File target = outputDirectory == null ? new File(inputDirectory, "v2s-generated-relevance") : outputDirectory;
            var report = new GeneratedFamilyRelevanceAnalyzer().analyze(service, inventory, runtime);
            new GeneratedFamilyRelevanceReportWriter().write(target, report);
            getLog().info("JMOA V2-S generated-family relevance report written to: " + target.getAbsolutePath());
            getLog().info("JMOA V2-S is diagnostic/report-only; prototype admitted=" + report.prototypeAdmitted());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to analyze V2-S generated-family relevance", e);
        }
    }
}
