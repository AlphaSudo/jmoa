package com.yourorg.jmoa.plugin;

import com.yourorg.jmoa.plugin.evidence.EvidenceEngine;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceAnalysisReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceConfig;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RuntimePolicy;
import com.yourorg.jmoa.plugin.evidence.EvidenceReplaySuite;
import com.yourorg.jmoa.plugin.evidence.EvidenceReplaySuite.ReplayReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceReportWriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.Locale;

@Mojo(name = "evidence", defaultPhase = LifecyclePhase.VERIFY)
public class EvidenceMojo extends AbstractMojo {

    @Parameter(property = "jmoa.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "jmoa.evidence.enabled", defaultValue = "false")
    private boolean evidenceEnabled;

    @Parameter(property = "jmoa.evidence.mode", defaultValue = "analyze")
    private String evidenceMode;

    @Parameter(property = "jmoa.evidence.inputDir", required = true)
    private File evidenceInputDir;

    @Parameter(property = "jmoa.evidence.outputDir")
    private File evidenceOutputDir;

    @Parameter(property = "jmoa.evidence.replaySuite")
    private File evidenceReplaySuite;

    @Parameter(property = "jmoa.evidence.expectedPolicy", defaultValue = "UNKNOWN")
    private String expectedPolicy;

    @Parameter(property = "jmoa.evidence.requireArtifactHashes", defaultValue = "true")
    private boolean requireArtifactHashes;

    @Parameter(property = "jmoa.evidence.requireWorkloadZeroErrors", defaultValue = "true")
    private boolean requireWorkloadZeroErrors;

    @Parameter(property = "jmoa.evidence.requireSmapsArithmetic", defaultValue = "true")
    private boolean requireSmapsArithmetic;

    @Parameter(property = "jmoa.evidence.failOnInvalidRun", defaultValue = "true")
    private boolean failOnInvalidRun;

    @Parameter(property = "jmoa.evidence.markPerturbingDiagnostics", defaultValue = "true")
    private boolean markPerturbingDiagnostics;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip || !evidenceEnabled) {
            getLog().info("JMOA evidence engine is disabled.");
            return;
        }
        if (!"analyze".equalsIgnoreCase(evidenceMode) && !"replay".equalsIgnoreCase(evidenceMode)) {
            throw new MojoExecutionException("Unsupported jmoa.evidence.mode: " + evidenceMode + ". Use 'analyze' or 'replay'.");
        }
        if (evidenceInputDir == null || !evidenceInputDir.isDirectory()) {
            throw new MojoExecutionException("jmoa.evidence.inputDir must point to an evidence directory or replay base directory.");
        }
        File outputDir = evidenceOutputDir == null
            ? new File(evidenceInputDir, "jmoa-evidence")
            : evidenceOutputDir;
        EvidenceConfig config = new EvidenceConfig(
            runtimePolicy(expectedPolicy),
            requireArtifactHashes,
            requireWorkloadZeroErrors,
            requireSmapsArithmetic,
            failOnInvalidRun,
            markPerturbingDiagnostics
        );

        try {
            if ("replay".equalsIgnoreCase(evidenceMode)) {
                runReplay(outputDir, config);
                return;
            }
            EvidenceAnalysisReport report = new EvidenceEngine().analyze(evidenceInputDir, config);
            new EvidenceReportWriter().write(outputDir, report);
            getLog().info("JMOA evidence analysis written to: " + outputDir.getAbsolutePath());
            getLog().info("JMOA evidence verdict: " + report.verdict()
                + " (runs=" + report.validation().runs()
                + ", invalidRuns=" + report.validation().invalidRuns()
                + ", pairs=" + report.confirmation().pairs()
                + ", pairedWins=" + report.confirmation().pairedWins() + ")");
            if (failOnInvalidRun && report.validation().invalidRuns() > 0) {
                throw new MojoExecutionException("JMOA evidence validation failed with "
                    + report.validation().invalidRuns() + " invalid run(s). See " + outputDir.getAbsolutePath());
            }
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to run JMOA evidence analysis", e);
        }
    }

    private void runReplay(File outputDir, EvidenceConfig config) throws Exception {
        File suite = evidenceReplaySuite == null
            ? new File(evidenceInputDir, "historical-replay-suite.json")
            : evidenceReplaySuite;
        if (!suite.isFile()) {
            throw new MojoExecutionException("jmoa.evidence.mode=replay requires jmoa.evidence.replaySuite or historical-replay-suite.json in inputDir.");
        }
        EvidenceReplaySuite replaySuite = new EvidenceReplaySuite();
        ReplayReport report = replaySuite.replay(suite, evidenceInputDir, config);
        replaySuite.write(outputDir, report);
        getLog().info("JMOA evidence replay written to: " + outputDir.getAbsolutePath());
        getLog().info("JMOA evidence replay: cases=" + report.cases()
            + ", present=" + report.presentCases()
            + ", passed=" + report.passedCases()
            + ", failed=" + report.failedCases());
        if (failOnInvalidRun && report.failedCases() > 0) {
            throw new MojoExecutionException("JMOA evidence replay failed with "
                + report.failedCases() + " failed case(s). See " + outputDir.getAbsolutePath());
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
