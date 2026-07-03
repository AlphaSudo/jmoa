package com.yourorg.jmoa.plugin;

import com.yourorg.jmoa.plugin.attribution.AttributionModels.AttributionConfig;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.MemoryAttributionReport;
import com.yourorg.jmoa.plugin.attribution.MemoryAttributionEngine;
import com.yourorg.jmoa.plugin.attribution.MemoryAttributionReportWriter;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceConfig;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RuntimePolicy;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.Locale;

@Mojo(name = "attribution", defaultPhase = LifecyclePhase.VERIFY)
public class AttributionMojo extends AbstractMojo {

    @Parameter(property = "jmoa.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "jmoa.attribution.enabled", defaultValue = "false")
    private boolean attributionEnabled;

    @Parameter(property = "jmoa.attribution.mode", defaultValue = "analyze")
    private String attributionMode;

    @Parameter(property = "jmoa.attribution.inputDir", required = true)
    private File attributionInputDir;

    @Parameter(property = "jmoa.attribution.outputDir")
    private File attributionOutputDir;

    @Parameter(property = "jmoa.attribution.requireV2CValid", defaultValue = "true")
    private boolean requireV2cValid;

    @Parameter(property = "jmoa.attribution.includeJfr", defaultValue = "false")
    private boolean includeJfr;

    @Parameter(property = "jmoa.attribution.includeAsyncProfiler", defaultValue = "false")
    private boolean includeAsyncProfiler;

    @Parameter(property = "jmoa.attribution.includeJol", defaultValue = "false")
    private boolean includeJol;

    @Parameter(property = "jmoa.attribution.diagnosticOnly", defaultValue = "true")
    private boolean diagnosticOnly;

    @Parameter(property = "jmoa.attribution.generatedClassReport")
    private File generatedClassReport;

    @Parameter(property = "jmoa.attribution.bytecodeRuntimeCorrelationReport")
    private File bytecodeRuntimeCorrelationReport;

    @Parameter(property = "jmoa.evidence.expectedPolicy", defaultValue = "UNKNOWN")
    private String expectedPolicy;

    @Parameter(property = "jmoa.evidence.requireArtifactHashes", defaultValue = "true")
    private boolean requireArtifactHashes;

    @Parameter(property = "jmoa.evidence.requireWorkloadZeroErrors", defaultValue = "true")
    private boolean requireWorkloadZeroErrors;

    @Parameter(property = "jmoa.evidence.requireSmapsArithmetic", defaultValue = "true")
    private boolean requireSmapsArithmetic;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip || !attributionEnabled) {
            getLog().info("JMOA attribution engine is disabled.");
            return;
        }
        if (!"analyze".equalsIgnoreCase(attributionMode)) {
            throw new MojoExecutionException("Unsupported jmoa.attribution.mode: " + attributionMode + ". Use 'analyze'.");
        }
        if (attributionInputDir == null || !attributionInputDir.isDirectory()) {
            throw new MojoExecutionException("jmoa.attribution.inputDir must point to a V2-C evidence directory.");
        }
        File outputDir = attributionOutputDir == null
            ? new File(attributionInputDir, "jmoa-attribution")
            : attributionOutputDir;
        EvidenceConfig evidenceConfig = new EvidenceConfig(
            runtimePolicy(expectedPolicy),
            requireArtifactHashes,
            requireWorkloadZeroErrors,
            requireSmapsArithmetic,
            requireV2cValid,
            true
        );
        AttributionConfig attributionConfig = new AttributionConfig(
            requireV2cValid,
            includeJfr,
            includeAsyncProfiler,
            includeJol,
            diagnosticOnly,
            generatedClassReport,
            bytecodeRuntimeCorrelationReport
        );
        try {
            MemoryAttributionReport report = new MemoryAttributionEngine()
                .analyze(attributionInputDir, evidenceConfig, attributionConfig);
            new MemoryAttributionReportWriter().write(outputDir, report);
            getLog().info("JMOA memory attribution written to: " + outputDir.getAbsolutePath());
            getLog().info("JMOA memory attribution verdict source: " + report.evidenceVerdict()
                + " (v2cValid=" + report.v2cValid()
                + ", hypotheses=" + report.causalHypotheses().size() + ")");
            if (requireV2cValid && !report.v2cValid()) {
                throw new MojoExecutionException("JMOA attribution requires V2-C-valid evidence. See "
                    + outputDir.getAbsolutePath());
            }
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to run JMOA memory attribution", e);
        }
    }

    private static RuntimePolicy runtimePolicy(String value) {
        if (value == null || value.isBlank()) {
            return RuntimePolicy.UNKNOWN;
        }
        try {
            return RuntimePolicy.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RuntimePolicy.UNKNOWN;
        }
    }
}
