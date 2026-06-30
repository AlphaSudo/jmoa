package com.yourorg.jmoa.plugin.coverage;

import com.yourorg.jmoa.plugin.ClassRootDescriptor;
import com.yourorg.jmoa.plugin.ClassRootPlanner;
import com.yourorg.jmoa.plugin.JmoaExecutionMode;
import com.yourorg.jmoa.plugin.deps.DependencyExpansionResult;
import com.yourorg.jmoa.plugin.filter.LambdaProfileIndex;
import com.yourorg.jmoa.plugin.filter.LambdaProfileReader;
import com.yourorg.jmoa.plugin.scanner.ClassFileWalker;
import com.yourorg.jmoa.plugin.scanner.LambdaScanner;
import com.yourorg.jmoa.plugin.scanner.ScanResult;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class CoverageSupport {

    private CoverageSupport() {
    }

    public static List<ClassRootDescriptor> planClassRoots(
        MavenProject project,
        File classesDir,
        List<File> additionalClassDirectories,
        DependencyExpansionResult dependencyExpansionResult,
        JmoaExecutionMode mode,
        Log log
    ) throws Exception {
        return new ClassRootPlanner().planRoots(
            classesDir,
            project.getCompileClasspathElements(),
            additionalClassDirectories,
            dependencyExpansionResult == null ? List.of() : dependencyExpansionResult.roots().stream()
                .map(root -> root.expandedRoot())
                .toList(),
            mode,
            message -> log.info(message)
        );
    }

    public static ScanResult scan(List<ClassRootDescriptor> classRoots) throws IOException {
        List<File> classFiles = new ArrayList<>();
        for (ClassRootDescriptor root : classRoots) {
            classFiles.addAll(ClassFileWalker.findClassFiles(root.rootDirectory()));
        }
        return LambdaScanner.scanClassFiles(classFiles);
    }

    public static LambdaProfileIndex loadProfileIndex(File profilePath, Log log) throws IOException {
        if (profilePath == null) {
            log.info("No V3.3 profile path configured. Coverage analysis will treat all scanned sites as new.");
            return LambdaProfileIndex.empty();
        }
        if (!profilePath.isFile()) {
            log.warn("Configured V3.3 profile not found: " + profilePath.getAbsolutePath() + ". Coverage analysis will use an empty profile.");
            return LambdaProfileIndex.empty();
        }
        log.info("Loading V3.3 profile snapshot from: " + profilePath.getAbsolutePath());
        return LambdaProfileReader.read(profilePath);
    }
}
