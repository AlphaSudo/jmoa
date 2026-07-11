package com.yourorg.jmoa.plugin;

import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyInputLoader;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyContext;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyScope;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimeRecommendationMode;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyRecommendationEngine;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyRegistry;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePreflightReportWriter;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePreflightService;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.Locale;

@Mojo(name = "runtime-preflight", defaultPhase = LifecyclePhase.VERIFY)
public class RuntimePreflightMojo extends AbstractMojo {

    @Parameter(property = "jmoa.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "jmoa.runtimePreflight.enabled", defaultValue = "false")
    private boolean runtimePreflightEnabled;

    @Parameter(property = "jmoa.runtimePreflight.inputDir", required = true)
    private File inputDir;

    @Parameter(property = "jmoa.runtimePreflight.outputDir")
    private File outputDir;

    @Parameter(property = "jmoa.runtimePreflight.registry")
    private File registry;

    @Parameter(property = "jmoa.runtimePreflight.artifact")
    private File artifact;

    @Parameter(property = "jmoa.runtimePreflight.cdsArchive")
    private File cdsArchive;

    @Parameter(property = "jmoa.runtimePreflight.service")
    private String service;

    @Parameter(property = "jmoa.runtimePreflight.launchMode", defaultValue = "UNKNOWN")
    private String launchMode;

    @Parameter(property = "jmoa.runtimePreflight.runtimePolicy", defaultValue = "UNKNOWN")
    private String runtimePolicy;

    @Parameter(property = "jmoa.runtimePreflight.reducerEngine", defaultValue = "raw")
    private String reducerEngine;

    @Parameter(property = "jmoa.runtimePreflight.scope", defaultValue = "UNKNOWN")
    private String scope;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip || !runtimePreflightEnabled) {
            getLog().info("JMOA runtime preflight is disabled.");
            return;
        }
        if (inputDir == null || !inputDir.isDirectory()) {
            throw new MojoExecutionException("jmoa.runtimePreflight.inputDir must point to an evidence or report directory.");
        }
        File resolvedOutput = outputDir == null ? new File(inputDir, "jmoa-runtime-preflight") : outputDir;
        File resolvedRegistry = registry == null
            ? new File(inputDir, "runtime-protocol-registry.json")
            : registry;
        try {
            RuntimePolicyContext context = new RuntimePolicyContext(
                service,
                launchMode,
                runtimePolicy,
                reducerEngine,
                null,
                null,
                scope(scope),
                RuntimeRecommendationMode.PREFLIGHT
            );
            RuntimePolicyRegistry protocolRegistry = RuntimePolicyRegistry.load(resolvedRegistry);
            RuntimePreflightService preflightService = new RuntimePreflightService();
            var input = new RuntimePolicyInputLoader().load(inputDir, context);
            var enrichedInput = preflightService.withFileHashes(input, artifact, cdsArchive);
            var recommendation = new RuntimePolicyRecommendationEngine(protocolRegistry).recommend(enrichedInput);
            var report = preflightService.preflight(enrichedInput, recommendation, artifact, cdsArchive);
            new RuntimePreflightReportWriter().write(resolvedOutput, report);
            getLog().info("JMOA runtime preflight written to: " + resolvedOutput.getAbsolutePath());
            getLog().info("JMOA runtime preflight readiness: " + report.readiness());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to run JMOA runtime preflight", e);
        }
    }

    private static RuntimePolicyScope scope(String value) {
        if (value == null || value.isBlank()) {
            return RuntimePolicyScope.UNKNOWN;
        }
        try {
            return RuntimePolicyScope.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RuntimePolicyScope.UNKNOWN;
        }
    }
}
