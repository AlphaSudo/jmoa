package com.yourorg.jmoa.plugin;

import com.yourorg.jmoa.plugin.coverage.CoverageAnalysis;
import com.yourorg.jmoa.plugin.coverage.CoverageAnalyzer;
import com.yourorg.jmoa.plugin.coverage.CoverageReportWriter;
import com.yourorg.jmoa.plugin.coverage.CoverageSupport;
import com.yourorg.jmoa.plugin.deps.DependencyExpander;
import com.yourorg.jmoa.plugin.deps.DependencyExpansionResult;
import com.yourorg.jmoa.plugin.deps.DependencyExpansionSupport;
import com.yourorg.jmoa.plugin.filter.LambdaProfileIndex;
import com.yourorg.jmoa.plugin.scanner.ScanResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Mojo(
    name = "check-coverage",
    defaultPhase = LifecyclePhase.VERIFY,
    requiresDependencyResolution = ResolutionScope.COMPILE
)
public class CheckCoverageMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "jmoa.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "jmoa.profilePath")
    private File profilePath;

    @Parameter(property = "jmoa.mode", defaultValue = "MODE_A")
    private JmoaExecutionMode mode;

    @Parameter(property = "jmoa.additionalClassDirectories")
    private List<File> additionalClassDirectories = new ArrayList<>();

    @Parameter(property = "jmoa.expandDependencies", defaultValue = "false")
    private boolean expandDependencies;

    @Parameter(property = "jmoa.expandedDepsDir", defaultValue = "${project.build.directory}/jmoa-expanded-deps")
    private File expandedDepsDir;

    @Parameter(property = "jmoa.expandIncludes")
    private String expandIncludes;

    @Parameter(property = "jmoa.expandExcludes")
    private String expandExcludes;

    @Parameter(property = "jmoa.cleanExpandedDeps", defaultValue = "true")
    private boolean cleanExpandedDeps;

    @Parameter(property = "jmoa.maxExpandedClasses", defaultValue = "50000")
    private int maxExpandedClasses;

    @Parameter(property = "jmoa.failOnNewSites", defaultValue = "true")
    private boolean failOnNewSites;

    @Parameter(property = "jmoa.failOnMissingProfileSites", defaultValue = "false")
    private boolean failOnMissingProfileSites;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("JMOA coverage check is skipped.");
            return;
        }

        File classesDir = new File(project.getBuild().getOutputDirectory());
        if (!classesDir.isDirectory()) {
            getLog().info("Target classes directory does not exist: " + classesDir.getAbsolutePath() + ". Skipping.");
            return;
        }

        try {
            DependencyExpansionResult dependencyExpansionResult = DependencyExpansionSupport.maybeExpand(
                project,
                mode,
                expandDependencies,
                expandedDepsDir,
                expandIncludes,
                expandExcludes,
                cleanExpandedDeps,
                maxExpandedClasses,
                new DependencyExpander(getLog())
            );
            List<ClassRootDescriptor> classRoots = CoverageSupport.planClassRoots(
                project,
                classesDir,
                additionalClassDirectories,
                dependencyExpansionResult,
                mode,
                getLog()
            );
            ScanResult scanResult = CoverageSupport.scan(classRoots);
            LambdaProfileIndex profileIndex = CoverageSupport.loadProfileIndex(profilePath, getLog());
            CoverageAnalysis analysis = new CoverageAnalyzer().analyze(
                scanResult,
                profileIndex,
                mode.name(),
                classRoots.stream().map(root -> root.rootDirectory().getAbsolutePath()).toList()
            );

            File reportFile = new File(project.getBuild().getDirectory(), "jmoa-coverage-report.json");
            CoverageReportWriter.write(reportFile, analysis);

            logCoverageDetails(analysis);

            if (failOnNewSites && analysis.newSiteCount() > 0) {
                throw new MojoFailureException(
                    "JMOA coverage check failed: found " + analysis.newSiteCount()
                        + " new lambda site(s) not present in the baseline profile. "
                        + "Re-run training and refresh the profile. See " + reportFile.getAbsolutePath()
                );
            }
            if (failOnMissingProfileSites && analysis.missingProfileSiteCount() > 0) {
                throw new MojoFailureException(
                    "JMOA coverage check failed: found " + analysis.missingProfileSiteCount()
                        + " profile site(s) missing from the current build. "
                        + "Profile and bytecode state are out of sync. See " + reportFile.getAbsolutePath()
                );
            }
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute JMOA coverage check", e);
        }
    }

    private void logCoverageDetails(CoverageAnalysis analysis) {
        getLog().info("Coverage summary: observed " + analysis.observedCurrentSites()
            + "/" + analysis.statelessCandidateSites()
            + " current stateless site(s), " + analysis.newSiteCount()
            + " new, " + analysis.missingProfileSiteCount() + " missing from current build.");

        analysis.newSiteKeys().stream().limit(10)
            .forEach(siteKey -> getLog().warn("New unprofiled site: " + siteKey));
        analysis.missingProfileSiteKeys().stream().limit(10)
            .forEach(siteKey -> getLog().warn("Profile-only site missing from current build: " + siteKey));
    }
}
