package com.yourorg.jmoa.plugin;

import com.yourorg.jmoa.plugin.recommendation.RecommendationInputLoader;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ConfirmationScope;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.RecommendationContext;
import com.yourorg.jmoa.plugin.recommendation.RecommendationReplaySuite;
import com.yourorg.jmoa.plugin.recommendation.RecommendationReportWriter;
import com.yourorg.jmoa.plugin.recommendation.ReducerRecommendationEngine;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.Locale;

@Mojo(name = "recommend-reducer", defaultPhase = LifecyclePhase.VERIFY)
public class RecommendReducerMojo extends AbstractMojo {

    @Parameter(property = "jmoa.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "jmoa.recommendation.enabled", defaultValue = "false")
    private boolean recommendationEnabled;

    @Parameter(property = "jmoa.recommendation.mode", defaultValue = "analyze")
    private String recommendationMode;

    @Parameter(property = "jmoa.recommendation.inputDir", required = true)
    private File recommendationInputDir;

    @Parameter(property = "jmoa.recommendation.outputDir")
    private File recommendationOutputDir;

    @Parameter(property = "jmoa.recommendation.service")
    private String service;

    @Parameter(property = "jmoa.recommendation.launchMode", defaultValue = "UNKNOWN")
    private String launchMode;

    @Parameter(property = "jmoa.recommendation.runtimePolicy", defaultValue = "UNKNOWN")
    private String runtimePolicy;

    @Parameter(property = "jmoa.recommendation.confirmationScope", defaultValue = "UNKNOWN")
    private String confirmationScope;

    @Parameter(property = "jmoa.recommendation.replaySuite")
    private File recommendationReplaySuite;

    @Parameter(property = "jmoa.recommendation.failOnReplayMismatch", defaultValue = "true")
    private boolean failOnReplayMismatch;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip || !recommendationEnabled) {
            getLog().info("JMOA reducer recommendation engine is disabled.");
            return;
        }
        if (recommendationInputDir == null || !recommendationInputDir.isDirectory()) {
            throw new MojoExecutionException("jmoa.recommendation.inputDir must point to a report directory.");
        }
        File outputDir = recommendationOutputDir == null
            ? new File(recommendationInputDir, "jmoa-recommendation")
            : recommendationOutputDir;
        try {
            if ("replay".equalsIgnoreCase(recommendationMode)) {
                runReplay(outputDir);
                return;
            }
            if (!"analyze".equalsIgnoreCase(recommendationMode)) {
                throw new MojoExecutionException(
                    "Unsupported jmoa.recommendation.mode: " + recommendationMode + ". Use 'analyze' or 'replay'."
                );
            }
            RecommendationContext context = new RecommendationContext(
                service,
                launchMode,
                runtimePolicy,
                scope(confirmationScope)
            );
            var input = new RecommendationInputLoader().load(recommendationInputDir, context);
            var recommendation = new ReducerRecommendationEngine().recommend(input);
            new RecommendationReportWriter().write(outputDir, input, recommendation);
            getLog().info("JMOA reducer recommendation written to: " + outputDir.getAbsolutePath());
            getLog().info("JMOA reducer admission decision: " + recommendation.decision()
                + " (scope=" + recommendation.confirmationScope()
                + ", protocolMatch=" + recommendation.protocolMatchesConfirmedScope() + ")");
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to run JMOA reducer recommendation", e);
        }
    }

    private void runReplay(File outputDir) throws Exception {
        File suite = recommendationReplaySuite == null
            ? new File(recommendationInputDir, "historical-recommendation-suite.json")
            : recommendationReplaySuite;
        var report = new RecommendationReplaySuite().replay(suite);
        new RecommendationReportWriter().writeReplay(outputDir, report);
        getLog().info("JMOA reducer recommendation replay written to: " + outputDir.getAbsolutePath());
        getLog().info("JMOA reducer recommendation replay: cases=" + report.cases()
            + ", passed=" + report.passedCases()
            + ", failed=" + report.failedCases());
        if (failOnReplayMismatch && report.failedCases() > 0) {
            throw new MojoExecutionException("Reducer recommendation replay failed with "
                + report.failedCases() + " mismatched case(s). See " + outputDir.getAbsolutePath());
        }
    }

    private static ConfirmationScope scope(String value) {
        if (value == null || value.isBlank()) {
            return ConfirmationScope.UNKNOWN;
        }
        try {
            return ConfirmationScope.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ConfirmationScope.UNKNOWN;
        }
    }
}
