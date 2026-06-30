package com.yourorg.jmoa.plugin;

import com.yourorg.jmoa.plugin.measure.MeasurementCommandWriter;
import com.yourorg.jmoa.plugin.measure.MeasurementComparator;
import com.yourorg.jmoa.plugin.measure.MeasurementComparison;
import com.yourorg.jmoa.plugin.measure.MeasurementConfig;
import com.yourorg.jmoa.plugin.measure.MeasurementExecutor;
import com.yourorg.jmoa.plugin.measure.MeasurementPlan;
import com.yourorg.jmoa.plugin.measure.MeasurementReportWriter;
import com.yourorg.jmoa.plugin.measure.MeasurementResult;
import com.yourorg.jmoa.plugin.measure.MeasurementScenario;
import com.yourorg.jmoa.plugin.measure.MeasurementSummaryWriter;
import com.yourorg.jmoa.plugin.measure.ClassLoadDiffAnalyzer;
import com.yourorg.jmoa.plugin.measure.ClassLoadInventory;
import com.yourorg.jmoa.plugin.measure.ClassLoadLogParser;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Mojo(
    name = "measure-impact",
    defaultPhase = LifecyclePhase.VERIFY,
    requiresDependencyResolution = ResolutionScope.COMPILE
)
public class MeasureImpactMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "jmoa.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "jmoa.measureMainClass", required = true)
    private String mainClass;

    @Parameter(property = "jmoa.measureCandidateScenario", defaultValue = "MODE_C")
    private MeasurementScenario candidateScenario;

    @Parameter(property = "jmoa.measureRuns", defaultValue = "5")
    private int measurementRuns;

    @Parameter(property = "jmoa.measureExecute", defaultValue = "true")
    private boolean executeMeasurements;

    @Parameter(property = "jmoa.failOnMeasurementRegression", defaultValue = "false")
    private boolean failOnMeasurementRegression;

    @Parameter(property = "jmoa.minLambdaClassReduction")
    private Integer minLambdaClassReduction;

    @Parameter(property = "jmoa.minMetaspaceReductionKb")
    private Integer minMetaspaceReductionKb;

    @Parameter(property = "jmoa.maxStartupRegressionMs")
    private Double maxStartupRegressionMs;

    @Parameter(property = "jmoa.measureOutputDir", defaultValue = "${project.build.directory}/jmoa-measurements")
    private File outputDirectory;

    @Parameter(property = "jmoa.measureArgs")
    private List<String> measurementArgs = new ArrayList<>();

    @Parameter(property = "jmoa.measureTargetMainClass")
    private String targetMainClass;

    @Parameter(property = "jmoa.measureBaselineArgs")
    private List<String> baselineMeasurementArgs = new ArrayList<>();

    @Parameter(property = "jmoa.measureBaselineArgsFile")
    private File baselineMeasurementArgsFile;

    @Parameter(property = "jmoa.measureBaselineClasspathEntries")
    private List<File> baselineClasspathEntries = new ArrayList<>();

    @Parameter(property = "jmoa.measureBaselineClasspathFile")
    private File baselineClasspathFile;

    @Parameter(property = "jmoa.measureModeAArgs")
    private List<String> modeAMeasurementArgs = new ArrayList<>();

    @Parameter(property = "jmoa.measureModeAArgsFile")
    private File modeAMeasurementArgsFile;

    @Parameter(property = "jmoa.measureModeAClasspathEntries")
    private List<File> modeAClasspathEntries = new ArrayList<>();

    @Parameter(property = "jmoa.measureModeAClasspathFile")
    private File modeAClasspathFile;

    @Parameter(property = "jmoa.measureExpandedClasspathOnlyArgs")
    private List<String> expandedClasspathOnlyMeasurementArgs = new ArrayList<>();

    @Parameter(property = "jmoa.measureExpandedClasspathOnlyArgsFile")
    private File expandedClasspathOnlyMeasurementArgsFile;

    @Parameter(property = "jmoa.measureExpandedClasspathOnlyClasspathEntries")
    private List<File> expandedClasspathOnlyClasspathEntries = new ArrayList<>();

    @Parameter(property = "jmoa.measureExpandedClasspathOnlyClasspathFile")
    private File expandedClasspathOnlyClasspathFile;

    @Parameter(property = "jmoa.measureModeBArgs")
    private List<String> modeBMeasurementArgs = new ArrayList<>();

    @Parameter(property = "jmoa.measureModeBArgsFile")
    private File modeBMeasurementArgsFile;

    @Parameter(property = "jmoa.measureModeBClasspathEntries")
    private List<File> modeBClasspathEntries = new ArrayList<>();

    @Parameter(property = "jmoa.measureModeBClasspathFile")
    private File modeBClasspathFile;

    @Parameter(property = "jmoa.measureModeCArgs")
    private List<String> modeCMeasurementArgs = new ArrayList<>();

    @Parameter(property = "jmoa.measureModeCArgsFile")
    private File modeCMeasurementArgsFile;

    @Parameter(property = "jmoa.measureModeCClasspathEntries")
    private List<File> modeCClasspathEntries = new ArrayList<>();

    @Parameter(property = "jmoa.measureModeCClasspathFile")
    private File modeCClasspathFile;

    @Parameter(property = "jmoa.measureModeCOptimizedJarsArgs")
    private List<String> modeCOptimizedJarsMeasurementArgs = new ArrayList<>();

    @Parameter(property = "jmoa.measureModeCOptimizedJarsArgsFile")
    private File modeCOptimizedJarsMeasurementArgsFile;

    @Parameter(property = "jmoa.measureModeCOptimizedJarsClasspathEntries")
    private List<File> modeCOptimizedJarsClasspathEntries = new ArrayList<>();

    @Parameter(property = "jmoa.measureModeCOptimizedJarsClasspathFile")
    private File modeCOptimizedJarsClasspathFile;

    @Parameter(property = "jmoa.measureJavaExecutable")
    private File javaExecutable;

    @Parameter(property = "jmoa.measureBootstrapClasspathEntries")
    private List<File> bootstrapClasspathEntries = new ArrayList<>();

    @Parameter(property = "jmoa.measureBootstrapClasspathFile")
    private File bootstrapClasspathFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("JMOA measure-impact is skipped.");
            return;
        }
        if (mainClass == null || mainClass.isBlank()) {
            throw new MojoExecutionException("jmoa.measureMainClass is required for measure-impact.");
        }

        try {
            Files.createDirectories(outputDirectory.toPath());

            MeasurementScenario baselineScenario = MeasurementScenario.BASELINE;
            List<File> baselineEntries = resolveBaselineClasspathEntries();
            MeasurementConfig baselineConfig = buildConfig(baselineScenario);
            MeasurementCommandWriter commandWriter = new MeasurementCommandWriter();
            File resolvedJavaExecutable = resolveJavaExecutable();
            MeasurementPlan baselinePlan = commandWriter.buildPlan(
                baselineConfig,
                resolvedJavaExecutable,
                joinClasspath(resolveLaunchClasspathEntries(baselineEntries))
            );
            List<MeasurementScenario> candidateScenarios = resolveCandidateScenarios();
            List<MeasurementPlan> plans = new ArrayList<>();
            plans.add(baselinePlan);
            for (MeasurementScenario scenario : candidateScenarios) {
                plans.add(commandWriter.buildPlan(
                    buildConfig(scenario),
                    resolvedJavaExecutable,
                    joinClasspath(resolveLaunchClasspathEntries(resolveScenarioClasspathEntries(scenario)))
                ));
            }

            List<MeasurementResult> results = new ArrayList<>();
            List<MeasurementComparison> comparisons = new ArrayList<>();
            java.util.Map<MeasurementScenario, ClassLoadDiffAnalyzer.ClassLoadDiff> classDiffs = new java.util.LinkedHashMap<>();
            if (executeMeasurements) {
                MeasurementExecutor executor = new MeasurementExecutor();
                MeasurementResult baselineResult = executor.execute(baselinePlan, baselineConfig.measurementRuns());
                results.add(baselineResult);
                ClassLoadLogParser classLoadLogParser = new ClassLoadLogParser();
                ClassLoadInventory baselineInventory = classLoadLogParser.parseInventory(baselinePlan.classLoadLogFile());
                for (MeasurementPlan candidatePlan : plans.subList(1, plans.size())) {
                    MeasurementResult candidateResult = executor.execute(candidatePlan, measurementRuns);
                    results.add(candidateResult);
                    MeasurementComparison comparison = new MeasurementComparator().compare(
                        baselineResult,
                        candidateResult,
                        minLambdaClassReduction,
                        minMetaspaceReductionKb,
                        maxStartupRegressionMs
                    );
                    comparisons.add(comparison);
                    logSummary(baselineResult, candidateResult, comparison);
                    if (failOnMeasurementRegression && !comparison.passesThresholds()) {
                        throw new MojoFailureException(
                            "JMOA measure-impact failed: " + comparison.verdict()
                                + " baseline=" + baselineScenario
                                + ", candidate=" + candidatePlan.scenario()
                                + ", lambdaReduction=" + comparison.lambdaReductionAbsolute()
                                + ", metaspaceCommittedReductionKb=" + comparison.metaspaceCommittedReductionKb()
                                + ", startupChangeMs=" + String.format("%.3f", comparison.startupChangeMs())
                        );
                    }
                    ClassLoadInventory candidateInventory = classLoadLogParser.parseInventory(candidatePlan.classLoadLogFile());
                    classDiffs.put(
                        candidatePlan.scenario(),
                        new ClassLoadDiffAnalyzer().analyze(baselineInventory, candidateInventory)
                    );
                }
            } else {
                getLog().info("Measurement execution disabled. Writing launch plans only.");
            }

            File reportFile = new File(outputDirectory, "jmoa-measurement-report.json");
            MeasurementReportWriter.write(
                reportFile,
                baselineScenario,
                plans,
                results,
                comparisons,
                classDiffs
            );
            File summaryFile = new File(outputDirectory, "jmoa-measurement-summary.md");
            new MeasurementSummaryWriter().write(summaryFile, baselineScenario, results, comparisons);
            getLog().info("JMOA measurement report written to: " + reportFile.getAbsolutePath());
            getLog().info("JMOA measurement summary written to: " + summaryFile.getAbsolutePath());
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute JMOA measure-impact", e);
        }
    }

    private MeasurementConfig buildConfig(MeasurementScenario scenario) throws Exception {
        List<String> scenarioArgs = resolveScenarioMeasurementArgs(scenario);
        String resolvedTargetMainClass = targetMainClass == null || targetMainClass.isBlank()
            ? mainClass
            : targetMainClass;
        return new MeasurementConfig(
            scenario,
            outputDirectory,
            mainClass,
            scenarioArgs,
            resolvedTargetMainClass,
            measurementRuns,
            executeMeasurements,
            failOnMeasurementRegression,
            minLambdaClassReduction,
            minMetaspaceReductionKb
        );
    }

    private List<MeasurementScenario> resolveCandidateScenarios() {
        List<MeasurementScenario> scenarios = new ArrayList<>();
        if (hasScenarioClasspath(modeAClasspathEntries, modeAClasspathFile)) {
            scenarios.add(MeasurementScenario.MODE_A);
        }
        if (hasScenarioClasspath(expandedClasspathOnlyClasspathEntries, expandedClasspathOnlyClasspathFile)) {
            scenarios.add(MeasurementScenario.EXPANDED_CLASSPATH_ONLY);
        }
        if (hasScenarioClasspath(modeBClasspathEntries, modeBClasspathFile)) {
            scenarios.add(MeasurementScenario.MODE_B);
        }
        if (hasScenarioClasspath(modeCClasspathEntries, modeCClasspathFile)) {
            scenarios.add(MeasurementScenario.MODE_C);
        }
        if (hasScenarioClasspath(modeCOptimizedJarsClasspathEntries, modeCOptimizedJarsClasspathFile)) {
            scenarios.add(MeasurementScenario.MODE_C_OPTIMIZED_JARS);
        }
        if (scenarios.isEmpty() && candidateScenario != MeasurementScenario.BASELINE) {
            scenarios.add(candidateScenario);
        }
        return scenarios;
    }

    private boolean hasScenarioClasspath(List<File> entries, File classpathFile) {
        return (entries != null && !entries.isEmpty()) || classpathFile != null;
    }

    private List<File> resolveBaselineClasspathEntries() throws Exception {
        if (!baselineClasspathEntries.isEmpty()) {
            return baselineClasspathEntries;
        }
        if (baselineClasspathFile != null) {
            return readClasspathEntries(baselineClasspathFile);
        }
        return project.getCompileClasspathElements().stream()
            .map(File::new)
            .toList();
    }

    private List<File> resolveScenarioClasspathEntries(MeasurementScenario scenario) throws Exception {
        List<File> configured = switch (scenario) {
            case EXPANDED_CLASSPATH_ONLY -> expandedClasspathOnlyClasspathEntries;
            case MODE_A -> modeAClasspathEntries;
            case MODE_B -> modeBClasspathEntries;
            case MODE_C -> modeCClasspathEntries;
            case MODE_C_OPTIMIZED_JARS -> modeCOptimizedJarsClasspathEntries;
            case BASELINE -> baselineClasspathEntries;
        };
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        File configuredFile = switch (scenario) {
            case EXPANDED_CLASSPATH_ONLY -> expandedClasspathOnlyClasspathFile;
            case MODE_A -> modeAClasspathFile;
            case MODE_B -> modeBClasspathFile;
            case MODE_C -> modeCClasspathFile;
            case MODE_C_OPTIMIZED_JARS -> modeCOptimizedJarsClasspathFile;
            case BASELINE -> baselineClasspathFile;
        };
        if (configuredFile != null) {
            return readClasspathEntries(configuredFile);
        }
        return project.getCompileClasspathElements().stream()
            .map(File::new)
            .toList();
    }

    private List<File> resolveLaunchClasspathEntries(List<File> scenarioClasspathEntries) throws Exception {
        if (bootstrapClasspathEntries != null && !bootstrapClasspathEntries.isEmpty()) {
            return bootstrapClasspathEntries;
        }
        if (bootstrapClasspathFile != null) {
            return readClasspathEntries(bootstrapClasspathFile);
        }
        return scenarioClasspathEntries;
    }

    private List<File> readClasspathEntries(File classpathFile) throws Exception {
        String raw = Files.readString(classpathFile.toPath()).trim();
        if (raw.isEmpty()) {
            return List.of();
        }
        String normalized = raw.replace(System.lineSeparator(), File.pathSeparator)
            .replace("\r", File.pathSeparator)
            .replace("\n", File.pathSeparator);
        String separator = normalized.contains(";") ? ";" : File.pathSeparator;
        String[] tokens = normalized.split(Pattern.quote(separator));
        List<File> files = new ArrayList<>();
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                files.add(new File(trimmed));
            }
        }
        return files;
    }

    private List<String> resolveScenarioMeasurementArgs(MeasurementScenario scenario) throws Exception {
        List<String> configuredArgs = switch (scenario) {
            case BASELINE -> baselineMeasurementArgs;
            case EXPANDED_CLASSPATH_ONLY -> expandedClasspathOnlyMeasurementArgs;
            case MODE_A -> modeAMeasurementArgs;
            case MODE_B -> modeBMeasurementArgs;
            case MODE_C -> modeCMeasurementArgs;
            case MODE_C_OPTIMIZED_JARS -> modeCOptimizedJarsMeasurementArgs;
        };
        if (configuredArgs != null && !configuredArgs.isEmpty()) {
            return List.copyOf(configuredArgs);
        }
        File configuredArgsFile = switch (scenario) {
            case BASELINE -> baselineMeasurementArgsFile;
            case EXPANDED_CLASSPATH_ONLY -> expandedClasspathOnlyMeasurementArgsFile;
            case MODE_A -> modeAMeasurementArgsFile;
            case MODE_B -> modeBMeasurementArgsFile;
            case MODE_C -> modeCMeasurementArgsFile;
            case MODE_C_OPTIMIZED_JARS -> modeCOptimizedJarsMeasurementArgsFile;
        };
        if (configuredArgsFile != null) {
            return readArgumentEntries(configuredArgsFile);
        }
        return measurementArgs == null ? List.of() : List.copyOf(measurementArgs);
    }

    private List<String> readArgumentEntries(File argsFile) throws Exception {
        List<String> args = new ArrayList<>();
        for (String line : Files.readAllLines(argsFile.toPath())) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            args.add(trimmed);
        }
        return args;
    }

    private File resolveJavaExecutable() {
        if (javaExecutable != null) {
            return javaExecutable;
        }
        String javaHome = System.getProperty("java.home");
        return new File(new File(javaHome, "bin"), isWindows() ? "java.exe" : "java");
    }

    private String joinClasspath(List<File> entries) throws MojoExecutionException {
        if (entries == null || entries.isEmpty()) {
            throw new MojoExecutionException("Measurement classpath is empty.");
        }
        for (File entry : entries) {
            if (!entry.exists()) {
                throw new MojoExecutionException("Measurement classpath entry does not exist: " + entry.getAbsolutePath());
            }
        }
        return entries.stream()
            .map(File::getAbsolutePath)
            .reduce((left, right) -> left + File.pathSeparator + right)
            .orElseThrow(() -> new MojoExecutionException("Measurement classpath is empty."));
    }

    private void logSummary(
        MeasurementResult baseline,
        MeasurementResult candidate,
        MeasurementComparison comparison
    ) {
        getLog().info("Measurement baseline " + baseline.scenario() + ": "
            + baseline.lambdaClasses() + " lambda class(es), "
            + baseline.metaspaceCommittedKb() + "KB committed metaspace, "
            + String.format("%.3f", baseline.averageStartupMs()) + " ms average startup.");
        getLog().info("Measurement candidate " + candidate.scenario() + ": "
            + candidate.lambdaClasses() + " lambda class(es), "
            + candidate.metaspaceCommittedKb() + "KB committed metaspace, "
            + String.format("%.3f", candidate.averageStartupMs()) + " ms average startup.");
        getLog().info("Measurement comparison: lambdaReduction=" + comparison.lambdaReductionAbsolute()
            + " (" + String.format("%.2f", comparison.lambdaReductionPercent()) + "%), metaspaceCommittedReductionKb="
            + comparison.metaspaceCommittedReductionKb() + ", startupChangeMs="
            + String.format("%.3f", comparison.startupChangeMs()) + ", verdict=" + comparison.verdict());
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
