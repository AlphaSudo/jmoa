package com.yourorg.jmoa.plugin;

import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyInputLoader;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyContext;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyScope;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimeRecommendationMode;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyRecommendationEngine;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyRegistry;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyReplaySuite;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyReportWriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.Locale;

@Mojo(name = "recommend-runtime", defaultPhase = LifecyclePhase.VERIFY)
public class RecommendRuntimeMojo extends AbstractMojo {

    @Parameter(property = "jmoa.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "jmoa.runtimeRecommendation.enabled", defaultValue = "false")
    private boolean runtimeRecommendationEnabled;

    @Parameter(property = "jmoa.runtimeRecommendation.mode", defaultValue = "analyze")
    private String runtimeRecommendationMode;

    @Parameter(property = "jmoa.runtimeRecommendation.inputDir", required = true)
    private File runtimeRecommendationInputDir;

    @Parameter(property = "jmoa.runtimeRecommendation.outputDir")
    private File runtimeRecommendationOutputDir;

    @Parameter(property = "jmoa.runtimeRecommendation.registry")
    private File runtimeRecommendationRegistry;

    @Parameter(property = "jmoa.runtimeRecommendation.replaySuite")
    private File runtimeRecommendationReplaySuite;

    @Parameter(property = "jmoa.runtimeRecommendation.service")
    private String service;

    @Parameter(property = "jmoa.runtimeRecommendation.launchMode", defaultValue = "UNKNOWN")
    private String launchMode;

    @Parameter(property = "jmoa.runtimeRecommendation.runtimePolicy", defaultValue = "UNKNOWN")
    private String runtimePolicy;

    @Parameter(property = "jmoa.runtimeRecommendation.reducerEngine", defaultValue = "unknown")
    private String reducerEngine;

    @Parameter(property = "jmoa.runtimeRecommendation.artifactSha256")
    private String artifactSha256;

    @Parameter(property = "jmoa.runtimeRecommendation.cdsArchiveSha256")
    private String cdsArchiveSha256;

    @Parameter(property = "jmoa.runtimeRecommendation.scope", defaultValue = "UNKNOWN")
    private String scope;

    @Parameter(property = "jmoa.runtimeRecommendation.failOnReplayMismatch", defaultValue = "true")
    private boolean failOnReplayMismatch;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip || !runtimeRecommendationEnabled) {
            getLog().info("JMOA runtime-policy recommendation engine is disabled.");
            return;
        }
        if (runtimeRecommendationInputDir == null || !runtimeRecommendationInputDir.isDirectory()) {
            throw new MojoExecutionException("jmoa.runtimeRecommendation.inputDir must point to a report directory.");
        }
        RuntimeRecommendationMode mode = mode(runtimeRecommendationMode);
        if (mode == null) {
            throw new MojoExecutionException("Unsupported jmoa.runtimeRecommendation.mode: " + runtimeRecommendationMode
                + ". Use 'analyze', 'preflight', or 'replay'.");
        }
        File outputDir = runtimeRecommendationOutputDir == null
            ? new File(runtimeRecommendationInputDir, "jmoa-runtime-recommendation")
            : runtimeRecommendationOutputDir;
        File registryFile = runtimeRecommendationRegistry == null
            ? new File(runtimeRecommendationInputDir, "runtime-protocol-registry.json")
            : runtimeRecommendationRegistry;
        try {
            RuntimePolicyRegistry registry = RuntimePolicyRegistry.load(registryFile);
            if (mode == RuntimeRecommendationMode.REPLAY) {
                runReplay(outputDir, registry);
                return;
            }
            RuntimePolicyContext context = new RuntimePolicyContext(
                service,
                launchMode,
                runtimePolicy,
                reducerEngine,
                artifactSha256,
                cdsArchiveSha256,
                scope(scope),
                mode
            );
            var input = new RuntimePolicyInputLoader().load(runtimeRecommendationInputDir, context);
            var recommendation = new RuntimePolicyRecommendationEngine(registry).recommend(input);
            new RuntimePolicyReportWriter().write(outputDir, input, recommendation);
            getLog().info("JMOA runtime-policy recommendation written to: " + outputDir.getAbsolutePath());
            getLog().info("JMOA runtime-policy decision: " + recommendation.decision()
                + " (scope=" + recommendation.scope()
                + ", registryMatch=" + recommendation.protocolMatchesRegistry() + ")");
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to run JMOA runtime-policy recommendation", e);
        }
    }

    private void runReplay(File outputDir, RuntimePolicyRegistry registry) throws Exception {
        File suite = runtimeRecommendationReplaySuite == null
            ? new File(runtimeRecommendationInputDir, "historical-runtime-policy-suite.json")
            : runtimeRecommendationReplaySuite;
        var report = new RuntimePolicyReplaySuite().replay(suite, registry);
        new RuntimePolicyReportWriter().writeReplay(outputDir, report);
        getLog().info("JMOA runtime-policy replay written to: " + outputDir.getAbsolutePath());
        getLog().info("JMOA runtime-policy replay: cases=" + report.cases()
            + ", passed=" + report.passedCases() + ", failed=" + report.failedCases());
        if (failOnReplayMismatch && report.failedCases() > 0) {
            throw new MojoExecutionException("Runtime-policy replay failed with " + report.failedCases()
                + " mismatched case(s). See " + outputDir.getAbsolutePath());
        }
    }

    private static RuntimeRecommendationMode mode(String value) {
        if (value == null || value.isBlank()) {
            return RuntimeRecommendationMode.ANALYZE;
        }
        try {
            return RuntimeRecommendationMode.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
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
