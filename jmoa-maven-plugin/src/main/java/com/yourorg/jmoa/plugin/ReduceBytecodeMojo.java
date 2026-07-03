package com.yourorg.jmoa.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yourorg.jmoa.plugin.reducer.DebugMetadataSavingsEstimator;
import com.yourorg.jmoa.plugin.reducer.JarReducer;
import com.yourorg.jmoa.plugin.reducer.ReducerConfig;
import com.yourorg.jmoa.plugin.reducer.ReducerFailureReport;
import com.yourorg.jmoa.plugin.reducer.ReducerReport;
import com.yourorg.jmoa.plugin.reducer.ReducerReportWriter;
import com.yourorg.jmoa.plugin.reducer.ReducerSafetyPolicy;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;

@Mojo(name = "reduce-bytecode", defaultPhase = LifecyclePhase.VERIFY)
public class ReduceBytecodeMojo extends AbstractMojo {

    @Parameter(property = "jmoa.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "jmoa.reducer.enabled", defaultValue = "false")
    private boolean reducerEnabled;

    @Parameter(property = "jmoa.reducer.reportOnly", defaultValue = "true")
    private boolean reportOnly;

    @Parameter(property = "jmoa.reducer.optimize", defaultValue = "false")
    private boolean optimize;

    @Parameter(property = "jmoa.reducer.profile", defaultValue = "none")
    private String profile;

    @Parameter(property = "jmoa.reducer.inputDir", required = true)
    private File inputDir;

    @Parameter(property = "jmoa.reducer.outputDir", defaultValue = "${project.build.directory}/jmoa-reduced-libs")
    private File outputDir;

    @Parameter(property = "jmoa.reducer.stripLocalVariableTable", defaultValue = "false")
    private boolean stripLocalVariableTable;

    @Parameter(property = "jmoa.reducer.stripLocalVariableTypeTable", defaultValue = "false")
    private boolean stripLocalVariableTypeTable;

    @Parameter(property = "jmoa.reducer.stripLineNumberTable", defaultValue = "false")
    private boolean stripLineNumberTable;

    @Parameter(property = "jmoa.reducer.stripSourceFile", defaultValue = "false")
    private boolean stripSourceFile;

    @Parameter(property = "jmoa.reducer.stripStackMapTable", defaultValue = "false")
    private boolean stripStackMapTable;

    @Parameter(property = "jmoa.reducer.stripAnnotations", defaultValue = "false")
    private boolean stripAnnotations;

    @Parameter(property = "jmoa.reducer.stripSignature", defaultValue = "false")
    private boolean stripSignature;

    @Parameter(property = "jmoa.reducer.stripBootstrapMethods", defaultValue = "false")
    private boolean stripBootstrapMethods;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip || !reducerEnabled) {
            getLog().info("JMOA bytecode reducer is disabled.");
            return;
        }
        ReducerConfig config = new ReducerConfig(
            reportOnly,
            optimize,
            profile,
            inputDir,
            outputDir,
            stripLocalVariableTable,
            stripLocalVariableTypeTable,
            stripLineNumberTable,
            stripSourceFile,
            stripStackMapTable,
            stripAnnotations,
            stripSignature,
            stripBootstrapMethods
        );
        try {
            new ReducerSafetyPolicy().validate(config);
            ReducerReport report = config.mutationEnabled()
                ? new JarReducer(config).reduce()
                : new DebugMetadataSavingsEstimator(config).estimate();
            new ReducerReportWriter().write(outputDir, report);
            getLog().info("JMOA bytecode reducer reports written to: " + outputDir.getAbsolutePath());
            getLog().info("JMOA bytecode reducer summary: jars=" + report.jarCount()
                + ", classes=" + report.classCount()
                + ", estimatedRemovableBytes=" + report.totalEstimatedRemovableBytes()
                + ", removedBytes=" + report.totalRemovedBytes()
                + ", mutationEnabled=" + report.mutationEnabled());
        } catch (IllegalArgumentException e) {
            writeFailure(e);
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (Exception e) {
            writeFailure(e);
            throw new MojoExecutionException("Failed to run JMOA bytecode reducer", e);
        }
    }

    private void writeFailure(Exception error) {
        try {
            File target = outputDir == null ? new File("target/jmoa-reduced-libs") : outputDir;
            target.mkdirs();
            ReducerFailureReport report = new ReducerFailureReport(
                "v2-e-reducer-failure",
                Instant.now().toString(),
                error.getClass().getName(),
                error.getMessage(),
                "Reducer did not produce a promotable artifact. Fix the failure before materialization or measurement."
            );
            new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(new File(target, "reducer-failure-report.json"), report);
            Files.writeString(
                new File(target, "reducer-failure-report.md").toPath(),
                "# V2-E Reducer Failure Report\n\n"
                    + "- Error type: `" + report.errorType() + "`\n"
                    + "- Message: `" + report.message() + "`\n"
                    + "- Action: " + report.action() + "\n",
                StandardCharsets.UTF_8
            );
        } catch (Exception ignored) {
            getLog().warn("Failed to write reducer failure report: " + ignored.getMessage());
        }
    }
}
