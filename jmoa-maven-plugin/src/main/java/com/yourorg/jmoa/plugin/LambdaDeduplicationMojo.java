package com.yourorg.jmoa.plugin;

import com.yourorg.jmoa.plugin.deps.DependencyExpander;
import com.yourorg.jmoa.plugin.deps.DependencyExpansionResult;
import com.yourorg.jmoa.plugin.deps.DependencyExpansionSupport;
import com.yourorg.jmoa.plugin.filter.LambdaFilter;
import com.yourorg.jmoa.plugin.filter.LambdaFilterConfig;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;
import com.yourorg.jmoa.plugin.filter.LambdaProfileIndex;
import com.yourorg.jmoa.plugin.filter.LambdaProfileReader;
import com.yourorg.jmoa.plugin.filter.LambdaSourceIndex;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyConfig;
import com.yourorg.jmoa.plugin.framework.FrameworkAdmissionReportWriter;
import com.yourorg.jmoa.plugin.generated.GeneratedClassInventory;
import com.yourorg.jmoa.plugin.generated.GeneratedClassInventoryReportWriter;
import com.yourorg.jmoa.plugin.generated.GeneratedClassInventoryScanner;
import com.yourorg.jmoa.plugin.generated.GeneratedClassOptimizer;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRoiV2ReportWriter;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeAttribution;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeAttributionReportWriter;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeAttributor;
import com.yourorg.jmoa.plugin.generated.GeneratedClassSafetyTaxonomy;
import com.yourorg.jmoa.plugin.generated.GeneratedClassSafetyTaxonomyBuilder;
import com.yourorg.jmoa.plugin.generated.GeneratedClassSafetyTaxonomyReportWriter;
import com.yourorg.jmoa.plugin.generated.SyntheticPrototypeReportWriter;
import com.yourorg.jmoa.plugin.modec.ModeCClasspathWriter;
import com.yourorg.jmoa.plugin.roi.RewriteRoiAnalyzer;
import com.yourorg.jmoa.plugin.roi.RewriteRoiReport;
import com.yourorg.jmoa.plugin.roi.RewriteRoiReportWriter;
import com.yourorg.jmoa.plugin.modec.HybridPackagingSummary;
import com.yourorg.jmoa.plugin.modec.ModeCOptimizedClasspathWriter;
import com.yourorg.jmoa.plugin.modec.ModeCOptimizedClasspathResult;
import com.yourorg.jmoa.plugin.modec.OptimizedDependencyJarPackager;
import com.yourorg.jmoa.plugin.modec.OptimizedDependencyJarPackagingResult;
import com.yourorg.jmoa.plugin.runtime.GeneratedRuntimeArtifact;
import com.yourorg.jmoa.plugin.runtime.Tier1RuntimeClassGenerator;
import com.yourorg.jmoa.plugin.runtime.ClassFileVersionResolver;
import com.yourorg.jmoa.plugin.runtime.Tier1RuntimePlanResult;
import com.yourorg.jmoa.plugin.runtime.Tier1RuntimePlanner;
import com.yourorg.jmoa.plugin.runtime.Tier2AdapterArtifact;
import com.yourorg.jmoa.plugin.runtime.Tier2AdapterNamingStrategy;
import com.yourorg.jmoa.plugin.runtime.Tier2PackageAdapterGenerator;
import com.yourorg.jmoa.plugin.size.BytecodeSizeConfig;
import com.yourorg.jmoa.plugin.size.BytecodeSizeReportWriter;
import com.yourorg.jmoa.plugin.size.ClassfileSizeProfile;
import com.yourorg.jmoa.plugin.size.ClassfileSizeScanner;
import com.yourorg.jmoa.plugin.size.MethodSizeRecord;
import com.yourorg.jmoa.plugin.report.ObservedAdmissionReportContext;
import com.yourorg.jmoa.plugin.scanner.ClassFileWalker;
import com.yourorg.jmoa.plugin.scanner.LambdaScanner;
import com.yourorg.jmoa.plugin.scanner.LambdaSite;
import com.yourorg.jmoa.plugin.weave.LambdaWeavingCoordinator;
import com.yourorg.jmoa.plugin.weave.LambdaWeavingPlan;
import com.yourorg.jmoa.plugin.weave.LambdaWeavingPlanBuilder;
import com.yourorg.jmoa.plugin.weave.WeaveExecutionResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mojo(
    name = "deduplicate-lambdas",
    defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    requiresDependencyResolution = ResolutionScope.RUNTIME
)
public class LambdaDeduplicationMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter
    private List<String> excludes = new ArrayList<>();

    @Parameter(property = "jmoa.reportOnly", defaultValue = "false")
    private boolean reportOnly;

    @Parameter(property = "jmoa.widenSynthetics", defaultValue = "true")
    private boolean widenSynthetics;

    @Parameter(property = "jmoa.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "jmoa.verbose", defaultValue = "true")
    private boolean verbose;

    @Parameter(property = "jmoa.profilePath")
    private File profilePath;

    @Parameter(property = "jmoa.hotThreshold", defaultValue = "10000")
    private long hotThreshold;

    @Parameter
    private List<String> frameworkExclusions = new ArrayList<>();

    @Parameter(property = "jmoa.frameworkFiltering", defaultValue = "false")
    private boolean frameworkFiltering;

    @Parameter(property = "jmoa.allowSpringAotFrameworkSites", defaultValue = "true")
    private boolean allowSpringAotFrameworkSites;

    @Parameter(property = "jmoa.allowExpandedDependencySites", defaultValue = "true")
    private boolean allowExpandedDependencySites;

    @Parameter(property = "jmoa.allowUnknownFrameworkSites", defaultValue = "false")
    private boolean allowUnknownFrameworkSites;

    @Parameter
    private List<String> frameworkAllowPrefixes = new ArrayList<>();

    @Parameter
    private List<String> frameworkDenyPrefixes = new ArrayList<>();

    @Parameter(property = "jmoa.frameworkHotThreshold", defaultValue = "10000")
    private long frameworkHotThreshold;

    @Parameter(property = "jmoa.additionalSafeSamInterfaces")
    private String additionalSafeSamInterfaces;

    @Parameter(property = "jmoa.disableTier1FrameworkSites", defaultValue = "false")
    private boolean disableTier1FrameworkSites;

    @Parameter(property = "jmoa.disableTier2FrameworkSites", defaultValue = "false")
    private boolean disableTier2FrameworkSites;

    @Parameter(property = "jmoa.enableObservedSiteAdmission", defaultValue = "false")
    private boolean enableObservedSiteAdmission;

    @Parameter(property = "jmoa.observedAdmissionSitesFile")
    private String observedAdmissionSitesFile;

    private ObservedAdmissionReportContext observedAdmissionReportContext = ObservedAdmissionReportContext.disabled();

    @Parameter(property = "jmoa.generateTier1Runtime", defaultValue = "false")
    private boolean generateTier1Runtime;

    @Parameter(property = "jmoa.tier2AdapterConsolidation", defaultValue = "CURRENT")
    private String tier2AdapterConsolidation;

    @Parameter(property = "jmoa.compactGeneratedAdapters", defaultValue = "false")
    private boolean compactGeneratedAdapters;

    // ---- Tier 2 adapter emission accounting (populated during execute(), read by reporting) ----
    // logicalCount  = number of distinct adapter classes generated (one per consolidated bucket)
    // physicalCount = number of adapter class files written to disk across all roots
    //                 (>= logicalCount when a consolidated bucket spans multiple class roots;
    //                  the adapter must be present in every root whose rewritten classes
    //                  reference it, otherwise ClassNotFoundException at runtime).
    private int lastLogicalAdapterCount;
    private int lastPhysicalEmittedAdapterClasses;
    private int lastMultiRootAdapters;

    @Parameter(property = "jmoa.failOnMissingRuntimeLibrary", defaultValue = "false")
    private boolean failOnMissingRuntimeLibrary;

    @Parameter(property = "jmoa.failFastRewrite", defaultValue = "false")
    private boolean failFastRewrite;

    /**
     * When true, fail the build if any rewritten output class root contains a
     * class that references a {@code JmoaPkgAdapters$*} adapter class that is
     * NOT present in that same root. This is the build-time guard for the
     * PACKAGE_SAM multi-root adapter-placement fix (Phase 32K-F1). Defaults to
     * true so the defect cannot ship silently.
     */
    @Parameter(property = "jmoa.failOnMissingAdapterReferences", defaultValue = "true")
    private boolean failOnMissingAdapterReferences;

    @Parameter(property = "jmoa.mode", defaultValue = "MODE_A")
    private JmoaExecutionMode mode;

    @Parameter(property = "jmoa.additionalClassDirectories")
    private List<File> additionalClassDirectories = new ArrayList<>();

    @Parameter(property = "jmoa.additionalClasspathJars")
    private String additionalClasspathJars;

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

    @Parameter(property = "jmoa.packageOptimizedDependencies", defaultValue = "false")
    private boolean packageOptimizedDependencies;

    @Parameter(property = "jmoa.optimizedLibsDir", defaultValue = "${project.build.directory}/jmoa-optimized-libs")
    private File optimizedLibsDir;

    @Parameter(property = "jmoa.hybridOverlayCoordinates")
    private String hybridOverlayCoordinates;

    @Parameter(property = "jmoa.debugProfileMatches", defaultValue = "false")
    private boolean debugProfileMatches;

    @Parameter(property = "jmoa.rewriteRoiReport", defaultValue = "true")
    private boolean rewriteRoiReport;

    @Parameter(property = "jmoa.rewriteRoiIncludeCandidates", defaultValue = "true")
    private boolean rewriteRoiIncludeCandidates;

    @Parameter(property = "jmoa.synthetic.enabled", defaultValue = "false")
    private boolean syntheticEnabled;

    @Parameter(property = "jmoa.synthetic.inventoryOnly", defaultValue = "true")
    private boolean syntheticInventoryOnly;

    @Parameter(property = "jmoa.synthetic.optimizeFamily", defaultValue = "none")
    private String syntheticOptimizeFamily;

    @Parameter(property = "jmoa.synthetic.failOnUnsafe", defaultValue = "true")
    private boolean syntheticFailOnUnsafe;

    @Parameter(property = "jmoa.synthetic.scanClasspathJars", defaultValue = "false")
    private boolean syntheticScanClasspathJars;

    @Parameter(property = "jmoa.synthetic.jarPaths")
    private String syntheticJarPaths;

    @Parameter(property = "jmoa.synthetic.classLoadLog")
    private File syntheticClassLoadLog;

    @Parameter(property = "jmoa.synthetic.classHistogram")
    private File syntheticClassHistogram;

    @Parameter(property = "jmoa.size.enabled", defaultValue = "false")
    private boolean sizeEnabled;

    @Parameter(property = "jmoa.size.reportOnly", defaultValue = "true")
    private boolean sizeReportOnly;

    @Parameter(property = "jmoa.size.optimize", defaultValue = "false")
    private boolean sizeOptimize;

    @Parameter(property = "jmoa.size.failOnNear64k", defaultValue = "false")
    private boolean sizeFailOnNear64k;

    @Parameter(property = "jmoa.size.warnMethodBytes", defaultValue = "32768")
    private int sizeWarnMethodBytes;

    @Parameter(property = "jmoa.size.dangerMethodBytes", defaultValue = "49152")
    private int sizeDangerMethodBytes;

    @Parameter(property = "jmoa.size.failMethodBytes", defaultValue = "65535")
    private int sizeFailMethodBytes;

    @Parameter(property = "jmoa.size.stripDebugAttributes", defaultValue = "false")
    private boolean sizeStripDebugAttributes;

    @Parameter(property = "jmoa.size.stripLocalVariableTables", defaultValue = "false")
    private boolean sizeStripLocalVariableTables;

    @Parameter(property = "jmoa.size.stripLineNumberTables", defaultValue = "false")
    private boolean sizeStripLineNumberTables;

    @Parameter(property = "jmoa.size.stripSourceFile", defaultValue = "false")
    private boolean sizeStripSourceFile;

    @Parameter(property = "jmoa.size.optimizeBootstrapMethods", defaultValue = "false")
    private boolean sizeOptimizeBootstrapMethods;

    @Parameter(property = "jmoa.size.optimizeConstantPool", defaultValue = "false")
    private boolean sizeOptimizeConstantPool;

    @Parameter(property = "jmoa.size.scanClasspathJars", defaultValue = "false")
    private boolean sizeScanClasspathJars;

    @Parameter(property = "jmoa.size.jarPaths")
    private String sizeJarPaths;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("JMOA Lambda Deduplication is skipped.");
            return;
        }

        String outputDirectory = project.getBuild().getOutputDirectory();
        File classesDir = new File(outputDirectory);
        if (!classesDir.exists() || !classesDir.isDirectory()) {
            getLog().info("Target classes directory does not exist: " + outputDirectory + ". Skipping.");
            return;
        }

        getLog().info("JMOA scanning classes directory: " + classesDir.getAbsolutePath());

        try {
            // Construct the project classloader from compile classpath elements.
            List<String> classpathElements = new ArrayList<>(project.getCompileClasspathElements());
            List<String> launchClasspathElements = buildLaunchClasspathElements(classpathElements);
            appendAdditionalClasspathJars(classpathElements);
            appendAdditionalClasspathJars(launchClasspathElements);
            URL[] urls = new URL[classpathElements.size()];
            for (int i = 0; i < classpathElements.size(); i++) {
                urls[i] = new File(classpathElements.get(i)).toURI().toURL();
            }
            ClassLoader projectClassLoader = new URLClassLoader(urls, getClass().getClassLoader());
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
            List<ClassRootDescriptor> classRoots = new ClassRootPlanner().planRoots(
                classesDir,
                classpathElements,
                additionalClassDirectories,
                DependencyExpansionSupport.expandedRootDirectories(dependencyExpansionResult),
                mode,
                message -> getLog().info(message)
            );
            getLog().info("JMOA execution mode: " + mode + " across " + classRoots.size() + " class root(s).");
            if (mode == JmoaExecutionMode.MODE_C) {
                getLog().info("MODE_C dependency expansion roots: " + dependencyExpansionResult.roots().size()
                    + " root(s), " + dependencyExpansionResult.totalClassesExpanded() + " class(es) expanded.");
                File modeCClasspathFile = new ModeCClasspathWriter().write(
                    new File(project.getBuild().getDirectory()),
                    classRoots,
                    launchClasspathElements
                );
                getLog().info("MODE_C launch classpath file written to: " + modeCClasspathFile.getAbsolutePath());
            }

            // Stage 1: Scan class files
            List<File> classFiles = collectClassFiles(classRoots);
            getLog().info("Found " + classFiles.size() + " class files to scan.");
            maybeWriteGeneratedClassInventory(classesDir, classRoots, classpathElements);
            maybeWriteBytecodeSizeReports(classesDir, classRoots, classpathElements);

            com.yourorg.jmoa.plugin.scanner.ScanResult scanResult = LambdaScanner.scanClassFiles(classFiles);
            List<LambdaSite> sites = scanResult.sites();
            List<File> expandedRootDirectories = DependencyExpansionSupport.expandedRootDirectories(dependencyExpansionResult);
            List<ClassRootDescriptor> optimizationRoots = classRoots.stream()
                .filter(root -> expandedRootDirectories.stream().noneMatch(expandedRoot -> sameFile(root.rootDirectory(), expandedRoot)))
                .toList();
            List<LambdaSite> optimizationSites = filterSitesByRoots(sites, optimizationRoots);
            LambdaSourceIndex sourceIndex = LambdaSourceIndex.fromSites(sites, classRoots);
            List<LambdaSite> projectSites = sites.stream()
                .filter(site -> isUnderRoot(site.classFile(), classesDir))
                .toList();
            OptimizedDependencyJarPackagingResult optimizedJarPackagingResult =
                OptimizedDependencyJarPackagingResult.disabled(optimizedLibsDir);
            HybridPackagingSummary hybridPackagingSummary = HybridPackagingSummary.disabled();
            getLog().info("Found " + sites.size() + " stateless lambda candidate sites. (Total BSMs scanned: "
                    + scanResult.totalLambdaSites() + ", capturing: " + scanResult.skippedCapturing()
                    + ", serializable/alt: " + scanResult.skippedSerializable() + ")");
            if (mode == JmoaExecutionMode.MODE_C && !expandedRootDirectories.isEmpty()) {
                getLog().info("Phase 14 foundation: expanded dependency roots are scanned and reported, but optimization remains limited to non-expanded roots until framework-safe filtering is implemented.");
            }

            LambdaProfileIndex profileIndex = loadProfileIndex();
            List<com.yourorg.jmoa.plugin.model.LambdaMeta> rawFilterMetadata = mode == JmoaExecutionMode.MODE_C
                ? sites.stream().map(LambdaSite::toMeta).toList()
                : optimizationSites.stream().map(LambdaSite::toMeta).toList();
            List<com.yourorg.jmoa.plugin.model.LambdaMeta> filterMetadata = distinctMetadataBySiteKey(rawFilterMetadata);
            int duplicateMetadataSites = rawFilterMetadata.size() - filterMetadata.size();
            if (duplicateMetadataSites > 0) {
                getLog().warn("JMOA de-duplicated " + duplicateMetadataSites
                    + " duplicate lambda metadata site(s) by siteKey before planning.");
            }
            LambdaFilterConfig filterConfig = buildFilterConfig();
            LambdaFilterResult filterResult = new LambdaFilter(filterConfig, projectClassLoader, sourceIndex)
                .filter(filterMetadata, profileIndex);
            maybeWriteFrameworkAdmissionReport(filterResult);
            LambdaFilterResult executionFilterResult = filterResult;
            Tier1RuntimePlanResult runtimePlanResult = new Tier1RuntimePlanner().plan(executionFilterResult);
            boolean packageSamTier2Consolidation = isPackageSamTier2Consolidation();
            boolean packageTier2Consolidation = isPackageTier2Consolidation();
            boolean packageSignatureTier2Consolidation = isPackageSignatureTier2Consolidation();
            LambdaWeavingPlan weavingPlan = new LambdaWeavingPlanBuilder(
                packageSamTier2Consolidation, packageTier2Consolidation,
                packageSignatureTier2Consolidation, compactGeneratedAdapters)
                .build(executionFilterResult, runtimePlanResult);
            getLog().info("V3.3 filter preview: " + filterResult.eligible().size()
                + " eligible sites (" + filterResult.tier1Eligible().size() + " Tier 1, "
                + filterResult.tier2Eligible().size() + " Tier 2), "
                + filterResult.excluded().size() + " excluded.");
            if (mode == JmoaExecutionMode.MODE_C) {
                getLog().info("Phase 16 Mode C preview: " + executionFilterResult.eligible().size()
                    + " execution-eligible site(s), "
                    + filterResult.frameworkSafetySummary().allowedFrameworkSites()
                    + " framework site(s) classified safe, "
                    + filterResult.frameworkSafetySummary().deniedFrameworkSites()
                    + " denied.");
            }
            getLog().info("Tier 1 runtime preview: " + runtimePlanResult.supportedPlans().size()
                + " supported runtime slots, " + runtimePlanResult.unsupportedTier1Sites().size()
                + " unsupported Tier 1 sites.");
            getLog().info("Weave preview: " + weavingPlan.targetsBySiteKey().size() + " eligible sites mapped to concrete rewrite targets.");
            logProfileDebug(filterResult);

            if (verbose) {
                for (LambdaSite site : sites) {
                    getLog().info("Candidate: " + site.classNode().name + " calling " + site.implHandle());
                }
            }

            // Stage 2: Group sites
            List<com.yourorg.jmoa.plugin.dedup.SharedGroup> groups = com.yourorg.jmoa.plugin.dedup.DeduplicationEngine.groupSites(
                    projectSites, projectClassLoader, widenSynthetics);
            boolean legacyV2RewriteEnabled = mode != JmoaExecutionMode.MODE_C;
            List<com.yourorg.jmoa.plugin.dedup.SharedGroup> activeGroups = legacyV2RewriteEnabled
                ? excludePlannedSites(groups, weavingPlan)
                : List.of();
            getLog().info("Grouped into " + activeGroups.size() + " deduplicated classes after excluding V3.3 planned rewrite sites.");
            if (!legacyV2RewriteEnabled && !groups.isEmpty()) {
                getLog().info("MODE_C is running in strict V3.3 execution mode. Legacy V2 synth/patch rewrites are disabled to preserve framework admission and weave sanity accounting.");
            }

            int skippedPrivateAccess = 0;
            for (LambdaSite site : projectSites) {
                boolean inGroup = false;
                for (com.yourorg.jmoa.plugin.dedup.SharedGroup g : activeGroups) {
                    if (g.sites().contains(site)) {
                        inGroup = true;
                        break;
                    }
                }
                if (!inGroup) {
                    com.yourorg.jmoa.plugin.dedup.AccessResolver.Visibility visibility =
                            com.yourorg.jmoa.plugin.dedup.AccessResolver.resolveVisibility(site.implHandle(), projectClassLoader);
                    if (visibility == com.yourorg.jmoa.plugin.dedup.AccessResolver.Visibility.PRIVATE) {
                        skippedPrivateAccess++;
                    }
                }
            }

            int syntheticMethodsWidened = 0;
            for (com.yourorg.jmoa.plugin.dedup.SharedGroup g : activeGroups) {
                if (g.needsAccessWidening()) {
                    syntheticMethodsWidened++;
                }
            }

            int classFileVersion = resolveProjectClassFileVersion(classesDir);
            GeneratedRuntimeArtifact generatedRuntimeArtifact =
                maybeGenerateTier1Runtime(classesDir, runtimePlanResult, projectClassLoader, classFileVersion);
            List<Tier2AdapterArtifact> tier2Artifacts = maybeGenerateTier2Adapters(classRoots, sites, filterResult, classFileVersion);
            WeaveExecutionResult weaveExecutionResult = new WeaveExecutionResult(
                failFastRewrite,
                weavingPlan.targetsBySiteKey().size(),
                0,
                0,
                0,
                List.of(),
                new com.yourorg.jmoa.plugin.weave.LambdaWeaveSanitySummary(
                    weavingPlan.targetsBySiteKey().size(),
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of()
                ),
                List.of()
            );

            if (!reportOnly) {
                getLog().info("JMOA is running in rewrite mode. Applying V3.3 weave plan"
                    + (legacyV2RewriteEnabled
                        ? ", then preserving legacy synth/patch behavior for any remaining V2 groups."
                        : " with legacy V2 synth/patch rewrites disabled for MODE_C."));
                weaveExecutionResult = new LambdaWeavingCoordinator().rewriteEligibleClasses(
                    sites,
                    weavingPlan,
                    projectClassLoader,
                    failFastRewrite,
                    message -> getLog().warn(message)
                );
                getLog().info("Weave result: " + weaveExecutionResult.rewrittenClasses()
                    + "/" + weaveExecutionResult.targetedClasses()
                    + " targeted classes rewritten for "
                    + weaveExecutionResult.plannedSites() + " planned sites"
                    + (weaveExecutionResult.failedClasses() > 0
                        ? " (" + weaveExecutionResult.failedClasses() + " class rewrite failures skipped)"
                        : "."));
                logWeaveSanity(weaveExecutionResult);

                for (com.yourorg.jmoa.plugin.dedup.SharedGroup group : activeGroups) {
                    byte[] synthBytes = com.yourorg.jmoa.plugin.synth.ClassSynthesizer.synthesize(group);
                    File synthFile = new File(classesDir, group.synthClassName() + ".class");
                    if (synthFile.getParentFile() != null) {
                        synthFile.getParentFile().mkdirs();
                    }
                    try (java.io.OutputStream os = new java.io.FileOutputStream(synthFile)) {
                        os.write(synthBytes);
                    }
                    getLog().info("Synthesized helper class: " + group.synthClassName());
                }

                Map<File, List<LambdaSite>> classToSites = new HashMap<>();
                Map<LambdaSite, com.yourorg.jmoa.plugin.dedup.SharedGroup> siteToGroup = new HashMap<>();
                for (com.yourorg.jmoa.plugin.dedup.SharedGroup group : activeGroups) {
                    for (LambdaSite site : group.sites()) {
                        siteToGroup.put(site, group);
                        classToSites.computeIfAbsent(site.classFile(), f -> new ArrayList<>()).add(site);
                    }
                }

                for (Map.Entry<File, List<LambdaSite>> entry : classToSites.entrySet()) {
                    File targetClassFile = entry.getKey();
                    List<LambdaSite> sitesToPatch = entry.getValue();
                    byte[] patchedBytes = com.yourorg.jmoa.plugin.synth.ClassPatcher.patchClass(
                            targetClassFile,
                            sitesToPatch,
                            siteToGroup,
                            projectClassLoader
                    );
                    if (patchedBytes != null) {
                        try (java.io.OutputStream os = new java.io.FileOutputStream(targetClassFile)) {
                            os.write(patchedBytes);
                        }
                        getLog().info("Patched class: " + targetClassFile.getAbsolutePath());
                    }
                }
            } else {
                getLog().info("JMOA is running in report-only mode. No classes will be modified.");
            }

            // ---- Phase 32K-F1: post-weave adapter placement self-heal ----
            // Adapter generation runs BEFORE weaving, so it cannot know which
            // additional roots the weaver will rewrite classes into. Spring AOT, in
            // particular, generates __BeanDefinitions classes into target/classes
            // for autoconfigure beans whose lambda sites live in expanded-dep roots;
            // the weaver rewrites those target/classes copies to reference adapters
            // that were emitted into a different root. This fan-out pass runs AFTER
            // weaving, scans the actual rewritten output, and writes any adapter
            // that is referenced-but-absent into the missing root. The adapter
            // bytes already exist in memory from generation, so this is the plugin
            // completing its own generation pass, not post-build JAR injection.
            if (!reportOnly && failOnMissingAdapterReferences && !tier2Artifacts.isEmpty()) {
                lastPhysicalEmittedAdapterClasses += selfHealAdapterPlacement(
                    classRoots, tier2Artifacts);
            }

            if (mode == JmoaExecutionMode.MODE_C && packageOptimizedDependencies && !reportOnly) {
                Set<String> hybridCoordinates = parseHybridOverlayCoordinates();
                optimizedJarPackagingResult = new OptimizedDependencyJarPackager(getLog())
                    .packageDependencies(dependencyExpansionResult, optimizedLibsDir, hybridCoordinates);
                ModeCOptimizedClasspathResult optimizedClasspathResult = new ModeCOptimizedClasspathWriter().write(
                    new File(project.getBuild().getDirectory()),
                    classRoots,
                    launchClasspathElements,
                    dependencyExpansionResult.roots(),
                    optimizedJarPackagingResult,
                    hybridCoordinates
                );
                File optimizedModeCClasspathFile = optimizedClasspathResult.classpathFile();
                hybridPackagingSummary = optimizedClasspathResult.hybridPackagingSummary();
                optimizedJarPackagingResult = optimizedJarPackagingResult.withHybridPackagingSummary(hybridPackagingSummary);
                getLog().info("MODE_C optimized-jars classpath file written to: " + optimizedModeCClasspathFile.getAbsolutePath());
                if (hybridPackagingSummary.enabled()) {
                    getLog().info("MODE_C hybrid overlay coordinates: "
                        + String.join(", ", hybridPackagingSummary.hybridOverlayCoordinates()));
                }
            }

            // Build-time guard: every output root whose rewritten classes reference a
            // JmoaPkgAdapters$* adapter must contain that adapter class file. This
            // catches the multi-root adapter-placement defect (Phase 32K-F1) before
            // it can ship a fat JAR that throws ClassNotFoundException at runtime.
            if (!reportOnly && failOnMissingAdapterReferences) {
                validateAdapterReferences(classRoots);
            }

            File reportFile = new File(project.getBuild().getDirectory(), "jmoa-lambda-report.json");
            com.yourorg.jmoa.plugin.report.DeduplicationReport.writeReport(
                    reportFile,
                    classFiles.size(),
                    scanResult,
                    skippedPrivateAccess,
                    syntheticMethodsWidened,
                    activeGroups,
                    filterResult,
                    runtimePlanResult,
                    generatedRuntimeArtifact,
                    tier2Artifacts,
                    weaveExecutionResult,
                    mode.name(),
                    classRoots.stream().map(root -> root.rootDirectory().getAbsolutePath()).toList(),
                    dependencyExpansionResult,
                    optimizedJarPackagingResult,
                    observedAdmissionReportContext
            );
            getLog().info("Deduplication report written to: " + reportFile.getAbsolutePath());

            // Phase 20: ROI report generation
            if (mode == JmoaExecutionMode.MODE_C && rewriteRoiReport) {
                RewriteRoiAnalyzer roiAnalyzer = new RewriteRoiAnalyzer();
                RewriteRoiReport roiReport = roiAnalyzer.analyze(filterResult, profilePath, profileIndex);
                File roiReportFile = new File(project.getBuild().getDirectory(), "jmoa-rewrite-roi-report.json");
                File roiDocsFile = new File(project.getBasedir(), "docs/phase20-rewrite-roi.md");
                new RewriteRoiReportWriter(rewriteRoiIncludeCandidates).write(roiReportFile, roiDocsFile, roiReport);
                getLog().info("Phase 20 ROI report written to: " + roiReportFile.getAbsolutePath());
                getLog().info("Phase 20 ROI docs written to: " + roiDocsFile.getAbsolutePath());
                if (!Boolean.TRUE.equals(roiReport.reconciliation().get("reconciled"))) {
                    getLog().warn("Phase 20 ROI report reconciliation FAILED — rankings may be unreliable.");
                }
            }

        } catch (Exception e) {
            getLog().error("Failed to execute JMOA Lambda Deduplication", e);
            throw new MojoExecutionException("Error during lambda deduplication", e);
        }
    }

    private LambdaFilterConfig buildFilterConfig() {
        LambdaFilterConfig defaults = LambdaFilterConfig.defaults()
            .withHotInvocationThreshold(hotThreshold)
            .withExecutionMode(mode)
            .withFrameworkSafetyConfig(buildFrameworkSafetyConfig())
            .withDiagnosticTierDisables(disableTier1FrameworkSites, disableTier2FrameworkSites);
        if (frameworkExclusions == null || frameworkExclusions.isEmpty()) {
            return defaults;
        }
        List<String> merged = new ArrayList<>(defaults.frameworkPackageExclusions());
        merged.addAll(frameworkExclusions);
        return defaults.withFrameworkPackageExclusions(merged);
    }

    private FrameworkSafetyConfig buildFrameworkSafetyConfig() {
        FrameworkSafetyConfig defaults = FrameworkSafetyConfig.defaults();
        List<String> allowPrefixes = frameworkAllowPrefixes == null || frameworkAllowPrefixes.isEmpty()
            ? defaults.allowPrefixes()
            : frameworkAllowPrefixes;
        List<String> denyPrefixes = frameworkDenyPrefixes == null || frameworkDenyPrefixes.isEmpty()
            ? defaults.denyPrefixes()
            : frameworkDenyPrefixes;
        Set<String> admissionSiteKeys = loadObservedAdmissionSiteKeys();
        List<String> safeSamInterfaces = mergeSafeSamInterfaces(defaults.safeSamInterfaces(), additionalSafeSamInterfaces);
        return new FrameworkSafetyConfig(
            frameworkFiltering,
            allowSpringAotFrameworkSites,
            allowExpandedDependencySites,
            allowUnknownFrameworkSites,
            allowPrefixes,
            denyPrefixes,
            frameworkHotThreshold,
            safeSamInterfaces,
            admissionSiteKeys
        );
    }

    private List<String> mergeSafeSamInterfaces(List<String> defaults, String additionalSamInterfaces) {
        List<String> merged = new ArrayList<>(defaults == null ? List.of() : defaults);
        if (additionalSamInterfaces == null || additionalSamInterfaces.isBlank()) {
            return merged;
        }
        for (String token : additionalSamInterfaces.split("[,;]")) {
            String sam = token == null ? "" : token.trim().replace('.', '/');
            if (!sam.isBlank() && !merged.contains(sam)) {
                merged.add(sam);
            }
        }
        getLog().info("JMOA additional safe SAM interfaces: " + (merged.size() - (defaults == null ? 0 : defaults.size()))
            + " added, " + merged.size() + " total.");
        return merged;
    }

    private Set<String> loadObservedAdmissionSiteKeys() {
        if (!enableObservedSiteAdmission || observedAdmissionSitesFile == null || observedAdmissionSitesFile.isBlank()) {
            observedAdmissionReportContext = new ObservedAdmissionReportContext(
                enableObservedSiteAdmission,
                observedAdmissionSitesFile,
                "",
                false,
                Set.of(),
                "EXPLICIT_SITE_KEYS"
            );
            return Set.of();
        }
        try {
            Path path = Path.of(observedAdmissionSitesFile);
            if (!path.isAbsolute()) {
                path = Path.of(project.getBasedir().getAbsolutePath(), observedAdmissionSitesFile);
            }
            if (!Files.exists(path)) {
                getLog().warn("Observed admission sites file not found: " + path);
                observedAdmissionReportContext = new ObservedAdmissionReportContext(
                    enableObservedSiteAdmission,
                    observedAdmissionSitesFile,
                    path.toAbsolutePath().toString(),
                    false,
                    Set.of(),
                    "EXPLICIT_SITE_KEYS"
                );
                return Set.of();
            }
            List<String> lines = Files.readAllLines(path);
            Set<String> keys = new HashSet<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    keys.add(trimmed);
                }
            }
            getLog().info("JMOA observed siteKey admission: loaded " + keys.size() + " siteKeys from " + path);
            observedAdmissionReportContext = new ObservedAdmissionReportContext(
                enableObservedSiteAdmission,
                observedAdmissionSitesFile,
                path.toAbsolutePath().toString(),
                true,
                keys,
                "EXPLICIT_SITE_KEYS"
            );
            return keys;
        } catch (IOException e) {
            getLog().warn("Failed to load observed admission sites file: " + e.getMessage());
            observedAdmissionReportContext = new ObservedAdmissionReportContext(
                enableObservedSiteAdmission,
                observedAdmissionSitesFile,
                "",
                false,
                Set.of(),
                "EXPLICIT_SITE_KEYS"
            );
            return Set.of();
        }
    }

    private void maybeWriteFrameworkAdmissionReport(LambdaFilterResult filterResult) throws IOException {
        if (mode != JmoaExecutionMode.MODE_C) {
            return;
        }
        File reportFile = new File(project.getBuild().getDirectory(), "jmoa-framework-admission-report.json");
        new FrameworkAdmissionReportWriter().write(reportFile, mode.name(), filterResult);
        getLog().info("Framework admission report written to: " + reportFile.getAbsolutePath());
    }

    private LambdaProfileIndex loadProfileIndex() throws java.io.IOException {
        if (profilePath == null) {
            getLog().info("No V3.3 profile path configured. Filter preview will require observed sites and may exclude everything by default.");
            return LambdaProfileIndex.empty();
        }
        if (!profilePath.isFile()) {
            getLog().warn("Configured V3.3 profile not found: " + profilePath.getAbsolutePath() + ". Using an empty profile index.");
            return LambdaProfileIndex.empty();
        }
        getLog().info("Loading V3.3 profile snapshot from: " + profilePath.getAbsolutePath());
        return LambdaProfileReader.read(profilePath);
    }

    private GeneratedRuntimeArtifact maybeGenerateTier1Runtime(
        File classesDir,
        Tier1RuntimePlanResult runtimePlanResult,
        ClassLoader projectClassLoader,
        int classFileVersion
    ) throws MojoExecutionException {
        if (!generateTier1Runtime) {
            return null;
        }
        if (reportOnly) {
            getLog().info("Tier 1 runtime generation is enabled but report-only mode is active. Skipping generated runtime emission.");
            return null;
        }
        if (runtimePlanResult.supportedPlans().isEmpty()) {
            getLog().info("Tier 1 runtime generation is enabled but there are no supported Tier 1 runtime slots to emit.");
            return null;
        }
        if (!isRuntimeLibraryAvailable(projectClassLoader)) {
            String message = "Tier 1 runtime generation requested, but jmoa.runtime.JmoaRuntimeSupport is not available on the project classpath.";
            if (failOnMissingRuntimeLibrary) {
                throw new MojoExecutionException(message);
            }
            getLog().warn(message + " Skipping generated runtime emission.");
            return null;
        }

        try {
            Tier1RuntimeClassGenerator generator = new Tier1RuntimeClassGenerator();
            byte[] runtimeBytes = generator.generate(runtimePlanResult, projectClassLoader, classFileVersion);
            generator.writeTo(classesDir, runtimeBytes);
            getLog().info("Generated Tier 1 runtime class: " + new File(classesDir, Tier1RuntimeClassGenerator.GENERATED_INTERNAL_NAME + ".class").getAbsolutePath());
            getLog().info("Generated Tier 1 runtime classfile version: " + classFileVersion);
            return new GeneratedRuntimeArtifact(Tier1RuntimeClassGenerator.GENERATED_INTERNAL_NAME, runtimeBytes);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate Tier 1 runtime class", e);
        }
    }

    private List<Tier2AdapterArtifact> maybeGenerateTier2Adapters(
        List<ClassRootDescriptor> classRoots,
        List<LambdaSite> sites,
        LambdaFilterResult filterResult,
        int classFileVersion
    ) throws MojoExecutionException {
        if (reportOnly) {
            getLog().info("Tier 2 adapter generation is skipped because report-only mode is active.");
            return List.of();
        }
        if (filterResult.tier2Eligible().isEmpty()) {
            getLog().info("No Tier 2 eligible sites found. Skipping package-local adapter generation.");
            return List.of();
        }

        Map<String, File> siteKeyToRoot = buildSiteKeyToRootMap(classRoots, sites);
        List<Tier2AdapterArtifact> artifacts = new ArrayList<>();
        int physicalEmittedAdapterClasses = 0;
        int multiRootAdapters = 0;
        try {
            if (isPackageSignatureTier2Consolidation()) {
                Tier2PackageAdapterGenerator sigGen = new Tier2PackageAdapterGenerator(compactGeneratedAdapters);
                for (Tier2AdapterArtifact artifact : sigGen.generatePackageSignatureAdapters(filterResult.tier2Eligible(), classFileVersion)) {
                    List<File> outputRoots = resolvePackageSignatureTier2OutputRoots(artifact, filterResult, siteKeyToRoot);
                    for (File outputRoot : outputRoots) {
                        writeGeneratedClass(outputRoot, artifact.internalName(), artifact.classBytes());
                        physicalEmittedAdapterClasses++;
                    }
                    if (outputRoots.size() > 1) {
                        multiRootAdapters++;
                    }
                    artifacts.add(artifact);
                }
            } else if (isPackageTier2Consolidation()) {
                Tier2PackageAdapterGenerator pkgGen = new Tier2PackageAdapterGenerator(compactGeneratedAdapters);
                for (Tier2AdapterArtifact artifact : pkgGen.generatePackageAdapters(filterResult.tier2Eligible(), classFileVersion)) {
                    List<File> outputRoots = resolveConsolidatedTier2OutputRoots(artifact, filterResult, siteKeyToRoot, true);
                    for (File outputRoot : outputRoots) {
                        writeGeneratedClass(outputRoot, artifact.internalName(), artifact.classBytes());
                        physicalEmittedAdapterClasses++;
                    }
                    if (outputRoots.size() > 1) {
                        multiRootAdapters++;
                    }
                    artifacts.add(artifact);
                }
            } else if (isPackageSamTier2Consolidation()) {
                Tier2PackageAdapterGenerator samGen = new Tier2PackageAdapterGenerator(compactGeneratedAdapters);
                for (Tier2AdapterArtifact artifact : samGen.generatePackageSamAdapters(filterResult.tier2Eligible(), classFileVersion)) {
                    List<File> outputRoots = resolveConsolidatedTier2OutputRoots(artifact, filterResult, siteKeyToRoot, false);
                    for (File outputRoot : outputRoots) {
                        writeGeneratedClass(outputRoot, artifact.internalName(), artifact.classBytes());
                        physicalEmittedAdapterClasses++;
                    }
                    if (outputRoots.size() > 1) {
                        multiRootAdapters++;
                    }
                    artifacts.add(artifact);
                }
            } else {
                Tier2PackageAdapterGenerator defaultGen = new Tier2PackageAdapterGenerator(compactGeneratedAdapters);
                for (var decision : filterResult.tier2Eligible()) {
                    Tier2AdapterArtifact artifact = defaultGen.generate(decision, classFileVersion);
                    File outputRoot = siteKeyToRoot.get(decision.meta().siteKey());
                    if (outputRoot == null) {
                        throw new MojoExecutionException("No class root found for Tier 2 site " + decision.meta().siteKey());
                    }
                    writeGeneratedClass(outputRoot, artifact.internalName(), artifact.classBytes());
                    physicalEmittedAdapterClasses++;
                    artifacts.add(artifact);
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate Tier 2 package adapter classes", e);
        }

        getLog().info("Generated " + artifacts.size() + " Tier 2 package-local adapter class(es)"
            + " (logicalAdapters=" + artifacts.size()
            + ", physicalEmittedAdapterClasses=" + physicalEmittedAdapterClasses
            + ", multiRootAdapters=" + multiRootAdapters + ").");
        lastLogicalAdapterCount = artifacts.size();
        lastPhysicalEmittedAdapterClasses = physicalEmittedAdapterClasses;
        lastMultiRootAdapters = multiRootAdapters;
        return List.copyOf(artifacts);
    }

    /**
     * Second-pass fan-out: scan every rewritten output root for
     * {@code JmoaPkgAdapters$*} references that are not backed by a present
     * adapter class file, and write the missing adapter bytes (from the already
     * generated {@code artifacts}) into the missing root. Returns the number of
     * additional physical adapter class files written.
     *
     * <p>This closes the gap between "where the filter decision's site lives"
     * (which drives first-pass placement) and "where rewritten classes that
     * reference the adapter live" (which is what the JVM actually resolves). It
     * is the correctness guarantee that the build-time validator enforces.
     */
    private int selfHealAdapterPlacement(
        List<ClassRootDescriptor> classRoots,
        List<Tier2AdapterArtifact> artifacts
    ) throws MojoExecutionException {
        if (artifacts.isEmpty()) {
            return 0;
        }
        // Index generated adapter bytes by internal name.
        Map<String, byte[]> bytesByInternalName = new LinkedHashMap<>();
        for (Tier2AdapterArtifact a : artifacts) {
            bytesByInternalName.put(a.internalName(), a.classBytes());
        }

        List<File> roots = new ArrayList<>();
        for (ClassRootDescriptor root : classRoots) {
            roots.add(root.rootDirectory());
        }
        com.yourorg.jmoa.plugin.weave.AdapterReferenceValidator validator =
            new com.yourorg.jmoa.plugin.weave.AdapterReferenceValidator(getLog());
        com.yourorg.jmoa.plugin.weave.AdapterReferenceValidator.Result result;
        try {
            result = validator.validate(roots);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to validate Tier 2 adapter references during self-heal", e);
        }

        if (result.allRootsClean()) {
            return 0;
        }
        int additionalEmitted = 0;
        List<String> fanOut = new ArrayList<>();
        for (var rootReport : result.roots()) {
            if (rootReport.clean()) {
                continue;
            }
            for (var missing : rootReport.missing()) {
                byte[] bytes = bytesByInternalName.get(missing.referencedAdapterInternal());
                if (bytes == null) {
                    // The referenced adapter was never generated at all. This is a
                    // genuine weave/generator bug, not a placement gap — surface it.
                    getLog().error("Adapter self-heal: referenced adapter was never generated: "
                        + missing.referencedAdapterInternal()
                        + " (referenced by " + missing.referencingClassBinaryNames().size()
                        + " class(es) in " + rootReport.root().getAbsolutePath() + ")");
                    continue;
                }
                try {
                    writeGeneratedClass(rootReport.root(), missing.referencedAdapterInternal(), bytes);
                    additionalEmitted++;
                    fanOut.add(missing.referencedAdapterInternal()
                        + " -> " + rootReport.root().getName());
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to fan out adapter "
                        + missing.referencedAdapterInternal()
                        + " into " + rootReport.root().getAbsolutePath(), e);
                }
            }
        }
        if (additionalEmitted > 0) {
            getLog().info("Adapter placement self-heal: fanned out " + additionalEmitted
                + " additional adapter file(s) into roots whose rewritten classes reference them"
                + " (spanning " + fanOut.size() + " root/adapter pair(s)).");
        }
        return additionalEmitted;
    }

    /**
     * Resolves the complete set of distinct output roots that a consolidated
     * PACKAGE_SIGNATURE Tier 2 adapter must be emitted into.
     * <p>
     * A consolidated adapter serves every Tier 2 decision whose
     * {@code packageSignatureInternalName} equals the artifact's internal name.
     * Each such decision's rewritten class lives in exactly one class root (per
     * {@link #buildSiteKeyToRootMap}). For the JVM to resolve the adapter at
     * runtime, the adapter class file must be present in <em>every</em> runtime
     * root/jar that contains a rewritten class referencing it — otherwise the
     * rewritten class throws {@code ClassNotFoundException} /
     * {@code NoClassDefFoundError}. This method therefore returns the deduplicated
     * set of all roots, not just the first.
     */
    private List<File> resolvePackageSignatureTier2OutputRoots(
        Tier2AdapterArtifact artifact,
        LambdaFilterResult filterResult,
        Map<String, File> siteKeyToRoot
    ) throws MojoExecutionException {
        return collectConsolidatedOutputRoots(artifact, filterResult, siteKeyToRoot, decision -> {
            String expectedName = compactGeneratedAdapters
                ? Tier2AdapterNamingStrategy.compactPackageSignatureInternalName(decision.meta())
                : Tier2AdapterNamingStrategy.packageSignatureInternalName(decision.meta());
            return artifact.internalName().equals(expectedName);
        }, artifact.internalName());
    }

    /**
     * Resolves the complete set of distinct output roots that a consolidated
     * PACKAGE or PACKAGE_SAM Tier 2 adapter must be emitted into. See
     * {@link #resolvePackageSignatureTier2OutputRoots} for why every root (not
     * just the first) must receive the adapter class file.
     *
     * @param packageMode {@code true} for PACKAGE consolidation (group by
     *                    package), {@code false} for PACKAGE_SAM (group by
     *                    package + SAM interface)
     */
    private List<File> resolveConsolidatedTier2OutputRoots(
        Tier2AdapterArtifact artifact,
        LambdaFilterResult filterResult,
        Map<String, File> siteKeyToRoot,
        boolean packageMode
    ) throws MojoExecutionException {
        return collectConsolidatedOutputRoots(artifact, filterResult, siteKeyToRoot, decision -> {
            String expectedName;
            if (packageMode) {
                expectedName = Tier2AdapterNamingStrategy.packageOnlyInternalName(decision.meta());
            } else {
                expectedName = compactGeneratedAdapters
                    ? Tier2AdapterNamingStrategy.compactPackageSamInternalName(decision.meta())
                    : Tier2AdapterNamingStrategy.packageSamInternalName(decision.meta());
            }
            return artifact.internalName().equals(expectedName);
        }, artifact.internalName());
    }

    /**
     * Shared core: walks every Tier 2 eligible decision, keeps those selected by
     * {@code matcher}, and collects the <em>distinct</em> roots for all of them.
     * Order-stable and deduplicated so each physical root receives the adapter
     * exactly once. Emits an informational log line when an adapter legitimately
     * spans multiple roots (this is expected for packages present in both the
     * AOT class directory and an expanded dependency).
     */
    private List<File> collectConsolidatedOutputRoots(
        Tier2AdapterArtifact artifact,
        LambdaFilterResult filterResult,
        Map<String, File> siteKeyToRoot,
        java.util.function.Predicate<LambdaFilterDecision> matcher,
        String internalNameForErrors
    ) throws MojoExecutionException {
        List<File> orderedRoots = new ArrayList<>();
        java.util.Set<File> seen = new java.util.LinkedHashSet<>();
        for (var decision : filterResult.tier2Eligible()) {
            if (!matcher.test(decision)) {
                continue;
            }
            File candidateRoot = siteKeyToRoot.get(decision.meta().siteKey());
            if (candidateRoot == null) {
                throw new MojoExecutionException("No class root found for Tier 2 site " + decision.meta().siteKey());
            }
            if (seen.add(candidateRoot)) {
                orderedRoots.add(candidateRoot);
            }
        }
        if (orderedRoots.isEmpty()) {
            throw new MojoExecutionException("No class root found for consolidated Tier 2 adapter " + internalNameForErrors);
        }
        if (orderedRoots.size() > 1) {
            StringBuilder roots = new StringBuilder();
            for (int i = 0; i < orderedRoots.size(); i++) {
                if (i > 0) {
                    roots.append(", ");
                }
                roots.append(orderedRoots.get(i).getAbsolutePath());
            }
            getLog().info("Consolidated Tier 2 adapter spans multiple class roots; emitting adapter into each root ("
                + orderedRoots.size() + "): " + artifact.internalName() + " roots=[" + roots + "]");
        }
        return orderedRoots;
    }

    private boolean isPackageTier2Consolidation() {
        return "PACKAGE".equalsIgnoreCase(tier2AdapterConsolidation == null ? "" : tier2AdapterConsolidation.trim());
    }

    private boolean isPackageSamTier2Consolidation() {
        return "PACKAGE_SAM".equalsIgnoreCase(tier2AdapterConsolidation == null ? "" : tier2AdapterConsolidation.trim());
    }

    private boolean isPackageSignatureTier2Consolidation() {
        return "PACKAGE_SIGNATURE".equalsIgnoreCase(tier2AdapterConsolidation == null ? "" : tier2AdapterConsolidation.trim());
    }

    private boolean isRuntimeLibraryAvailable(ClassLoader projectClassLoader) {
        try {
            Class.forName("jmoa.runtime.JmoaRuntimeSupport", false, projectClassLoader);
            Class.forName("jmoa.runtime.JmoaFactory", false, projectClassLoader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private List<String> buildLaunchClasspathElements(List<String> compileClasspathElements) throws MojoExecutionException {
        List<String> runtimeClasspathElements;
        try {
            runtimeClasspathElements = project.getRuntimeClasspathElements();
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve runtime classpath elements for MODE_C launch export", e);
        }
        if (runtimeClasspathElements == null || runtimeClasspathElements.isEmpty()) {
            return compileClasspathElements;
        }
        return runtimeClasspathElements;
    }

    private void appendAdditionalClasspathJars(List<String> classpathElements) throws MojoExecutionException {
        if (additionalClasspathJars == null || additionalClasspathJars.isBlank()) {
            return;
        }
        for (String raw : additionalClasspathJars.split("[;\\r\\n]+")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            File jar = new File(trimmed);
            if (!jar.exists() || !jar.isFile()) {
                throw new MojoExecutionException("Additional JMOA classpath jar not found: " + jar.getAbsolutePath());
            }
            String absolutePath = jar.getAbsolutePath();
            if (!classpathElements.contains(absolutePath)) {
                classpathElements.add(absolutePath);
            }
        }
    }

    private Set<String> parseHybridOverlayCoordinates() {
        if (hybridOverlayCoordinates == null || hybridOverlayCoordinates.isBlank()) {
            return Set.of();
        }
        Set<String> coordinates = new HashSet<>();
        for (String raw : hybridOverlayCoordinates.split("[,;\\r\\n]+")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                coordinates.add(trimmed);
            }
        }
        return coordinates;
    }

    private int resolveProjectClassFileVersion(File classesDir) throws IOException, MojoExecutionException {
        List<File> classFiles = ClassFileWalker.findClassFiles(classesDir);
        try {
            return ClassFileVersionResolver.resolveHighestVersion(
                classFiles,
                Tier1RuntimeClassGenerator.DEFAULT_CLASSFILE_VERSION
            );
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to resolve target class file version", e);
        }
    }

    private void writeGeneratedClass(File classesDir, String internalName, byte[] classBytes) throws IOException {
        File classFile = new File(classesDir, internalName + ".class");
        if (classFile.getParentFile() != null) {
            classFile.getParentFile().mkdirs();
        }
        try (java.io.OutputStream os = new java.io.FileOutputStream(classFile)) {
            os.write(classBytes);
        }
    }

    private List<File> collectClassFiles(List<ClassRootDescriptor> classRoots) throws IOException {
        List<File> files = new ArrayList<>();
        for (ClassRootDescriptor root : classRoots) {
            files.addAll(ClassFileWalker.findClassFiles(root.rootDirectory()));
        }
        return files;
    }

    private void maybeWriteGeneratedClassInventory(
        File classesDir,
        List<ClassRootDescriptor> classRoots,
        List<String> classpathElements
    ) throws IOException {
        if (!syntheticEnabled) {
            return;
        }
        try {
            new GeneratedClassOptimizer().ensureInventoryOnly(syntheticOptimizeFamily, syntheticInventoryOnly);
        } catch (IllegalStateException e) {
            throw new IOException(e.getMessage(), e);
        }

        List<ClassRootDescriptor> inventoryRoots = syntheticInventoryRoots(classesDir, classRoots);
        List<File> inventoryJars = syntheticInventoryJars(classpathElements);
        GeneratedClassInventory inventory = new GeneratedClassInventoryScanner().scan(inventoryRoots, inventoryJars);
        File outputDirectory = new File(project.getBuild().getDirectory());
        new GeneratedClassInventoryReportWriter().write(outputDirectory, inventory);
        GeneratedClassSafetyTaxonomy safetyTaxonomy = new GeneratedClassSafetyTaxonomyBuilder().build(inventory);
        new GeneratedClassSafetyTaxonomyReportWriter().write(outputDirectory, safetyTaxonomy);
        new SyntheticPrototypeReportWriter().write(outputDirectory, inventory);
        GeneratedClassRuntimeAttribution runtimeAttribution = maybeWriteGeneratedRuntimeAttribution(outputDirectory, inventory);
        new GeneratedClassRoiV2ReportWriter().write(outputDirectory, inventory, runtimeAttribution, safetyTaxonomy);
        getLog().info("V2-A generated-class inventory written under: " + project.getBuild().getDirectory());
        getLog().info("V2-A generated-class inventory summary: scanned=" + inventory.totalClassesScanned()
            + ", generatedLike=" + inventory.generatedLikeClasses()
            + ", families=" + inventory.familyBreakdown().size()
            + ", failOnUnsafe=" + syntheticFailOnUnsafe
            + ", inventoryOnly=" + syntheticInventoryOnly + ".");
    }

    private GeneratedClassRuntimeAttribution maybeWriteGeneratedRuntimeAttribution(
        File outputDirectory,
        GeneratedClassInventory inventory
    ) throws IOException {
        boolean hasClassLoadLog = syntheticClassLoadLog != null && syntheticClassLoadLog.isFile();
        boolean hasClassHistogram = syntheticClassHistogram != null && syntheticClassHistogram.isFile();
        if (!hasClassLoadLog && !hasClassHistogram) {
            return null;
        }
        GeneratedClassRuntimeAttribution attribution = new GeneratedClassRuntimeAttributor()
            .attribute(inventory, hasClassLoadLog ? syntheticClassLoadLog : null, hasClassHistogram ? syntheticClassHistogram : null);
        new GeneratedClassRuntimeAttributionReportWriter().write(outputDirectory, attribution);
        getLog().info("V2-A runtime attribution written"
            + " (classLoadLog=" + hasClassLoadLog
            + ", classHistogram=" + hasClassHistogram
            + ", generatedRuntimeLoaded=" + attribution.totalGeneratedRuntimeLoadedClasses() + ").");
        return attribution;
    }

    private void maybeWriteBytecodeSizeReports(
        File classesDir,
        List<ClassRootDescriptor> classRoots,
        List<String> classpathElements
    ) throws IOException {
        if (!sizeEnabled) {
            return;
        }
        ensureBytecodeSizeReportOnly();
        List<ClassRootDescriptor> sizeRoots = syntheticInventoryRoots(classesDir, classRoots);
        List<File> sizeJars = bytecodeSizeJars(classpathElements);
        BytecodeSizeConfig config = new BytecodeSizeConfig(
            sizeWarnMethodBytes,
            sizeDangerMethodBytes,
            sizeFailMethodBytes,
            sizeFailOnNear64k
        );
        ClassfileSizeProfile profile = new ClassfileSizeScanner(config).scan(sizeRoots, sizeJars);
        new BytecodeSizeReportWriter().write(new File(project.getBuild().getDirectory()), profile);
        if (sizeFailOnNear64k) {
            List<MethodSizeRecord> dangerMethods = profile.methods().stream()
                .filter(method -> method.codeLength() >= sizeDangerMethodBytes)
                .toList();
            if (!dangerMethods.isEmpty()) {
                throw new IOException("V2-B near-64KB method guard found " + dangerMethods.size()
                    + " method(s) at or above " + sizeDangerMethodBytes + " bytes.");
            }
        }
        getLog().info("V2-B bytecode size reports written under: " + project.getBuild().getDirectory());
        getLog().info("V2-B bytecode size summary: scanned=" + profile.totalClassesScanned()
            + ", totalClassfileBytes=" + profile.totalClassfileBytes()
            + ", largestMethodCodeLength=" + profile.largestMethodCodeLength()
            + ", reportOnly=" + sizeReportOnly + ".");
    }

    private void ensureBytecodeSizeReportOnly() throws IOException {
        if (!sizeReportOnly
            || sizeOptimize
            || sizeStripDebugAttributes
            || sizeStripLocalVariableTables
            || sizeStripLineNumberTables
            || sizeStripSourceFile
            || sizeOptimizeBootstrapMethods
            || sizeOptimizeConstantPool) {
            throw new IOException(
                "JMOA V2-B bytecode-size optimizer is report-only in this release. "
                    + "Use -Djmoa.size.reportOnly=true and leave all jmoa.size.optimize/strip flags disabled."
            );
        }
    }

    private List<File> bytecodeSizeJars(List<String> classpathElements) throws IOException {
        Map<String, File> jars = new LinkedHashMap<>();
        addSyntheticJarPaths(jars, sizeJarPaths);
        if (sizeScanClasspathJars && classpathElements != null) {
            for (String element : classpathElements) {
                addSyntheticJar(jars, new File(element));
            }
        }
        addJarsUnderDirectory(jars, optimizedLibsDir);
        addJarsUnderDirectory(jars, new File(project.getBuild().getDirectory(), "dependencies"));
        addJarsUnderDirectory(jars, new File(project.getBuild().getDirectory(), "snapshot-dependencies"));
        addSyntheticJar(jars, new File(project.getBuild().getDirectory(), project.getBuild().getFinalName() + ".jar"));
        return new ArrayList<>(jars.values());
    }

    private List<ClassRootDescriptor> syntheticInventoryRoots(File classesDir, List<ClassRootDescriptor> classRoots) throws IOException {
        Map<String, ClassRootDescriptor> roots = new LinkedHashMap<>();
        for (ClassRootDescriptor root : classRoots) {
            if (root != null && root.rootDirectory() != null && root.rootDirectory().isDirectory()) {
                roots.put(root.rootDirectory().getCanonicalPath(), root);
            }
        }
        addSyntheticRootIfPresent(roots, new File(project.getBuild().getDirectory(), "spring-aot/main/classes"), true);
        addSyntheticRootIfPresent(roots, new File(project.getBuild().getDirectory(), "BOOT-INF/classes"), true);
        addSyntheticRootIfPresent(roots, new File(classesDir, "BOOT-INF/classes"), true);
        return new ArrayList<>(roots.values());
    }

    private void addSyntheticRootIfPresent(Map<String, ClassRootDescriptor> roots, File directory, boolean projectOwned) throws IOException {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File canonical = directory.getCanonicalFile();
        roots.putIfAbsent(
            canonical.getCanonicalPath(),
            new ClassRootDescriptor(canonical, projectOwned, ClassRootKind.ADDITIONAL_DIRECTORY)
        );
    }

    private List<File> syntheticInventoryJars(List<String> classpathElements) throws IOException {
        Map<String, File> jars = new LinkedHashMap<>();
        addSyntheticJarPaths(jars, syntheticJarPaths);
        if (syntheticScanClasspathJars && classpathElements != null) {
            for (String element : classpathElements) {
                addSyntheticJar(jars, new File(element));
            }
        }
        addJarsUnderDirectory(jars, optimizedLibsDir);
        addSyntheticJar(jars, new File(project.getBuild().getDirectory(), project.getBuild().getFinalName() + ".jar"));
        return new ArrayList<>(jars.values());
    }

    private void addSyntheticJarPaths(Map<String, File> jars, String rawPaths) throws IOException {
        if (rawPaths == null || rawPaths.isBlank()) {
            return;
        }
        for (String raw : rawPaths.split("[;\\r\\n]+")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                addSyntheticJar(jars, new File(trimmed));
            }
        }
    }

    private void addJarsUnderDirectory(Map<String, File> jars, File directory) throws IOException {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(directory.toPath())) {
            for (Path path : walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".jar")).toList()) {
                addSyntheticJar(jars, path.toFile());
            }
        }
    }

    private void addSyntheticJar(Map<String, File> jars, File jar) throws IOException {
        if (jar == null || !jar.isFile() || !jar.getName().endsWith(".jar")) {
            return;
        }
        File canonical = jar.getCanonicalFile();
        jars.putIfAbsent(canonical.getCanonicalPath(), canonical);
    }

    private List<LambdaSite> filterSitesByRoots(List<LambdaSite> sites, List<ClassRootDescriptor> roots) {
        return sites.stream()
            .filter(site -> roots.stream().anyMatch(root -> isUnderRoot(site.classFile(), root.rootDirectory())))
            .toList();
    }

    private Map<String, File> buildSiteKeyToRootMap(List<ClassRootDescriptor> classRoots, List<LambdaSite> sites) {
        Map<String, File> map = new HashMap<>();
        for (LambdaSite site : sites) {
            for (ClassRootDescriptor root : classRoots) {
                if (isUnderRoot(site.classFile(), root.rootDirectory())) {
                    map.put(site.siteKey(), root.rootDirectory());
                    break;
                }
            }
        }
        return map;
    }

    private List<com.yourorg.jmoa.plugin.model.LambdaMeta> distinctMetadataBySiteKey(
        List<com.yourorg.jmoa.plugin.model.LambdaMeta> metadata
    ) {
        Map<String, com.yourorg.jmoa.plugin.model.LambdaMeta> bySiteKey = new LinkedHashMap<>();
        for (com.yourorg.jmoa.plugin.model.LambdaMeta meta : metadata) {
            bySiteKey.putIfAbsent(meta.siteKey(), meta);
        }
        return List.copyOf(bySiteKey.values());
    }

    private boolean isUnderRoot(File file, File rootDirectory) {
        try {
            String filePath = file.getCanonicalPath();
            String rootPath = rootDirectory.getCanonicalPath();
            return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean sameFile(File left, File right) {
        try {
            return left.getCanonicalFile().equals(right.getCanonicalFile());
        } catch (IOException e) {
            return false;
        }
    }

    private List<com.yourorg.jmoa.plugin.dedup.SharedGroup> excludePlannedSites(
        List<com.yourorg.jmoa.plugin.dedup.SharedGroup> groups,
        LambdaWeavingPlan weavingPlan
    ) {
        List<com.yourorg.jmoa.plugin.dedup.SharedGroup> filtered = new ArrayList<>();
        for (com.yourorg.jmoa.plugin.dedup.SharedGroup group : groups) {
            List<LambdaSite> remainingSites = group.sites().stream()
                .filter(site -> weavingPlan.targetFor(site.siteKey()) == null)
                .toList();
            if (remainingSites.size() < 2) {
                continue;
            }
            filtered.add(new com.yourorg.jmoa.plugin.dedup.SharedGroup(
                group.key(),
                group.synthClassName(),
                remainingSites,
                group.accessPlan()
            ));
        }
        return List.copyOf(filtered);
    }

    private void logProfileDebug(LambdaFilterResult filterResult) {
        if (!debugProfileMatches) {
            return;
        }

        List<com.yourorg.jmoa.plugin.filter.LambdaFilterDecision> observed = filterResult.decisions().stream()
            .filter(com.yourorg.jmoa.plugin.filter.LambdaFilterDecision::observedInProfile)
            .limit(20)
            .toList();
        getLog().info("Profile debug: observed " + filterResult.observedSiteCount() + " site(s) in filter result.");
        for (com.yourorg.jmoa.plugin.filter.LambdaFilterDecision decision : observed) {
            getLog().info("Observed profile site: " + decision.meta().siteKey()
                + " | tier=" + decision.tier()
                + " | exclusion=" + decision.exclusionReason());
        }
    }

    private void logWeaveSanity(WeaveExecutionResult weaveExecutionResult) {
        var sanity = weaveExecutionResult.sanitySummary();
        getLog().info("Weave sanity: verified " + sanity.rewrittenEligibleSites()
            + "/" + sanity.verifiedEligibleSites()
            + " eligible site rewrites, remaining eligible invokedynamic="
            + sanity.remainingEligibleSites()
            + ", unexpected removals="
            + sanity.unexpectedRemovedSites()
            + ", unverified eligible sites="
            + sanity.unverifiedEligibleSites() + ".");
        if (!sanity.assertionsPassed()) {
            if (!sanity.remainingEligibleSiteKeys().isEmpty()) {
                sanity.remainingEligibleSiteKeys().stream().limit(10)
                    .forEach(siteKey -> getLog().warn("Eligible site still remained as invokedynamic after weave: " + siteKey));
            }
            if (!sanity.unexpectedRemovedSiteKeys().isEmpty()) {
                sanity.unexpectedRemovedSiteKeys().stream().limit(10)
                    .forEach(siteKey -> getLog().warn("Unexpected lambda invokedynamic removal: " + siteKey));
            }
        }
    }

    /**
     * Build-time guard for the PACKAGE_SAM multi-root adapter-placement fix
     * (Phase 32K-F1). Validates that every output class root whose rewritten
     * classes reference a {@code JmoaPkgAdapters$*} adapter also contains that
     * adapter class file. Throws if any reference is unresolved, so a fat JAR
     * that would throw {@code ClassNotFoundException} at runtime can never be
     * produced.
     */
    private void validateAdapterReferences(List<ClassRootDescriptor> classRoots) throws MojoExecutionException {
        List<File> roots = new ArrayList<>();
        for (ClassRootDescriptor root : classRoots) {
            roots.add(root.rootDirectory());
        }
        com.yourorg.jmoa.plugin.weave.AdapterReferenceValidator validator =
            new com.yourorg.jmoa.plugin.weave.AdapterReferenceValidator(getLog());
        com.yourorg.jmoa.plugin.weave.AdapterReferenceValidator.Result result;
        try {
            result = validator.validate(roots);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to validate Tier 2 adapter references", e);
        }
        int totalRefs = 0;
        int totalPresent = 0;
        for (var rootReport : result.roots()) {
            totalRefs += rootReport.referencedAdapters().size();
            totalPresent += rootReport.presentAdapters().size();
        }
        getLog().info("Adapter reference validation: roots=" + result.roots().size()
            + ", referencedAdapterClasses=" + totalRefs
            + ", presentAdapterClasses=" + totalPresent
            + ", missing=" + result.missingReferences().size());
        if (result.allRootsClean()) {
            return;
        }
        getLog().error("Tier 2 adapter reference validation FAILED: "
            + result.missingReferences().size() + " missing adapter reference(s).");
        int shown = 0;
        for (var rootReport : result.roots()) {
            for (var missing : rootReport.missing()) {
                if (shown >= 25) {
                    getLog().error("  ... (" + (result.missingReferences().size() - shown)
                        + " more missing references omitted)");
                    break;
                }
                getLog().error("  Root " + rootReport.root().getAbsolutePath()
                    + " is missing adapter " + missing.referencedAdapterInternal()
                    + " (referenced by " + missing.referencingClassBinaryNames().size()
                    + " rewritten class(es), e.g. "
                    + missing.referencingClassBinaryNames().stream().findFirst().orElse("?") + ")");
                shown++;
            }
        }
        getLog().error("A rewritten class references a JmoaPkgAdapters class that is not present in "
            + "the same class root. This produces ClassNotFoundException / NoClassDefFoundError at "
            + "runtime. This indicates the consolidated adapter was not emitted into every root that "
            + "contains rewritten classes referencing it.");
        throw new MojoExecutionException("Tier 2 adapter reference validation failed: "
            + result.missingReferences().size() + " missing adapter reference(s). "
            + "Set -Djmoa.failOnMissingAdapterReferences=false ONLY for diagnosis.");
    }

}
