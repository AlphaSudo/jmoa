package com.yourorg.jmoa.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.generated.GeneratedClassInventory;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeAttributor;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeAttributionReportWriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

/** Converts one diagnostic lifecycle stage into a V2-U runtime attribution report. */
@Mojo(name = "analyze-generated-runtime")
public final class GeneratedClassRuntimeAttributionMojo extends AbstractMojo {
    private final ObjectMapper mapper = new ObjectMapper();

    @Parameter(property = "jmoa.generatedRuntime.enabled", defaultValue = "false")
    private boolean enabled;

    @Parameter(property = "jmoa.generatedRuntime.inventory", required = true)
    private File inventoryFile;

    @Parameter(property = "jmoa.generatedRuntime.classLoadLog", required = true)
    private File classLoadLog;

    @Parameter(property = "jmoa.generatedRuntime.classHistogram", required = true)
    private File classHistogram;

    @Parameter(property = "jmoa.generatedRuntime.outputDir", required = true)
    private File outputDir;

    @Override
    public void execute() throws MojoExecutionException {
        if (!enabled) {
            getLog().info("JMOA V2-V generated runtime attribution is disabled.");
            return;
        }
        requireFile(inventoryFile, "generated-class-inventory.json");
        requireFile(classLoadLog, "class-load.log");
        requireFile(classHistogram, "class-histogram.txt");
        try {
            GeneratedClassInventory inventory = mapper.readValue(inventoryFile, GeneratedClassInventory.class);
            var attribution = new GeneratedClassRuntimeAttributor()
                .attribute(inventory, classLoadLog, classHistogram);
            new GeneratedClassRuntimeAttributionReportWriter().write(outputDir, attribution);
            getLog().info("JMOA V2-V generated runtime attribution written to: "
                + outputDir.getAbsolutePath()
                + " generatedRuntimeLoaded=" + attribution.totalGeneratedRuntimeLoadedClasses());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to analyze generated runtime attribution", e);
        }
    }

    private static void requireFile(File file, String label) throws MojoExecutionException {
        if (file == null || !file.isFile()) {
            throw new MojoExecutionException("Missing required V2-V capture file: " + label);
        }
    }
}
