package com.yourorg.jmoa.plugin.modec;

import com.yourorg.jmoa.plugin.deps.DependencyExpansionResult;
import com.yourorg.jmoa.plugin.deps.ExpandedDependencyRoot;
import org.apache.maven.plugin.logging.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class OptimizedDependencyJarPackager {

    private final Log log;

    public OptimizedDependencyJarPackager(Log log) {
        this.log = log;
    }

    public OptimizedDependencyJarPackagingResult packageDependencies(
        DependencyExpansionResult expansionResult,
        File optimizedLibsDir
    ) throws IOException {
        return packageDependencies(expansionResult, optimizedLibsDir, Set.of());
    }

    public OptimizedDependencyJarPackagingResult packageDependencies(
        DependencyExpansionResult expansionResult,
        File optimizedLibsDir,
        Set<String> hybridOverlayCoordinates
    ) throws IOException {
        if (expansionResult == null || expansionResult.roots().isEmpty()) {
            return OptimizedDependencyJarPackagingResult.disabled(optimizedLibsDir);
        }

        Files.createDirectories(optimizedLibsDir.toPath());
        List<OptimizedDependencyJarArtifact> artifacts = new ArrayList<>();
        Set<String> hybridCoordinates = hybridOverlayCoordinates == null ? Set.of() : hybridOverlayCoordinates;
        for (ExpandedDependencyRoot root : expansionResult.roots()) {
            if (hybridCoordinates.contains(root.coordinate().displayName())) {
                log.info("JMOA optimized dependency jar skipped for hybrid overlay coordinate: "
                    + root.coordinate().displayName());
                continue;
            }
            OptimizedDependencyJarArtifact artifact = packageDependency(root, optimizedLibsDir);
            if (artifact != null) {
                artifacts.add(artifact);
            }
        }
        return new OptimizedDependencyJarPackagingResult(optimizedLibsDir, artifacts);
    }

    private OptimizedDependencyJarArtifact packageDependency(
        ExpandedDependencyRoot root,
        File optimizedLibsDir
    ) throws IOException {
        File optimizedJar = new File(optimizedLibsDir, toOptimizedJarName(root.originalJar().getName()));
        Map<String, File> overlayEntries = collectOverlayEntries(root.expandedRoot());
        Set<String> rewrittenEntries = new TreeSet<>();
        int rewrittenClasses = 0;
        int unchangedClasses = 0;
        int generatedAdapters = 0;
        int copiedResources = 0;
        int removedSignatures = 0;
        Map<String, byte[]> replacementEntries = new LinkedHashMap<>();

        try (JarFile originalJar = new JarFile(root.originalJar())) {
            for (Map.Entry<String, File> overlay : overlayEntries.entrySet()) {
                String entryName = overlay.getKey();
                File overlayFile = overlay.getValue();
                byte[] overlayBytes = Files.readAllBytes(overlayFile.toPath());
                JarEntry originalEntry = originalJar.getJarEntry(entryName);
                if (originalEntry != null) {
                    byte[] originalBytes = readAllBytes(originalJar.getInputStream(originalEntry));
                    if (java.util.Arrays.equals(originalBytes, overlayBytes)) {
                        unchangedClasses++;
                        continue;
                    }
                    rewrittenClasses++;
                    rewrittenEntries.add(entryName);
                    replacementEntries.put(entryName, overlayBytes);
                } else {
                    if (isPackageAdapterEntry(entryName)) {
                        generatedAdapters++;
                    }
                    replacementEntries.put(entryName, overlayBytes);
                }
            }
            if (rewrittenClasses == 0 && generatedAdapters == 0) {
                log.info("JMOA optimized dependency jar skipped (no rewritten classes or generated adapters): "
                    + root.coordinate().displayName());
                return null;
            }
        }

        try (JarFile originalJar = new JarFile(root.originalJar());
             OutputStream fos = new BufferedOutputStream(new FileOutputStream(optimizedJar));
             JarOutputStream jos = new JarOutputStream(fos)) {
            Enumeration<JarEntry> entries = originalJar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                if (isSignatureEntry(name)) {
                    removedSignatures++;
                    continue;
                }
                if (replacementEntries.containsKey(name)) {
                    continue;
                }
                writeJarEntry(jos, name, originalJar.getInputStream(entry));
                if (!name.endsWith(".class")) {
                    copiedResources++;
                }
            }

            for (Map.Entry<String, byte[]> replacement : replacementEntries.entrySet()) {
                writeJarEntry(jos, replacement.getKey(), replacement.getValue());
            }
        }

        log.info("JMOA optimized dependency jar: " + root.coordinate().displayName()
            + " -> " + optimizedJar.getAbsolutePath()
            + " (rewrittenClasses=" + rewrittenClasses
            + ", generatedAdapters=" + generatedAdapters + ")");

        return new OptimizedDependencyJarArtifact(
            root.coordinate(),
            root.originalJar(),
            optimizedJar,
            rewrittenClasses,
            unchangedClasses,
            generatedAdapters,
            copiedResources,
            removedSignatures,
            root.originalJar().length(),
            optimizedJar.length()
        );
    }

    private Map<String, File> collectOverlayEntries(File expandedRoot) throws IOException {
        Map<String, File> entries = new LinkedHashMap<>();
        if (expandedRoot == null || !expandedRoot.isDirectory()) {
            return entries;
        }
        Files.walk(expandedRoot.toPath())
            .filter(Files::isRegularFile)
            .forEach(path -> {
                String relative = expandedRoot.toPath().relativize(path).toString().replace(File.separatorChar, '/');
                if (relative.endsWith(".class")) {
                    entries.put(relative, path.toFile());
                }
            });
        return entries;
    }

    private void writeJarEntry(JarOutputStream jos, String entryName, InputStream inputStream) throws IOException {
        try (InputStream in = new BufferedInputStream(inputStream)) {
            JarEntry entry = new JarEntry(entryName);
            jos.putNextEntry(entry);
            in.transferTo(jos);
            jos.closeEntry();
        }
    }

    private void writeJarEntry(JarOutputStream jos, String entryName, byte[] bytes) throws IOException {
        JarEntry entry = new JarEntry(entryName);
        jos.putNextEntry(entry);
        jos.write(bytes);
        jos.closeEntry();
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        try (InputStream in = new BufferedInputStream(inputStream)) {
            return in.readAllBytes();
        }
    }

    private boolean isSignatureEntry(String entryName) {
        return entryName.startsWith("META-INF/")
            && (entryName.endsWith(".SF") || entryName.endsWith(".RSA") || entryName.endsWith(".DSA"));
    }

    private boolean isPackageAdapterEntry(String entryName) {
        return entryName.contains("JmoaPkgAdapters$");
    }

    private String toOptimizedJarName(String originalJarName) {
        if (originalJarName.endsWith(".jar")) {
            return originalJarName.substring(0, originalJarName.length() - 4) + "-jmoa.jar";
        }
        return originalJarName + "-jmoa.jar";
    }
}
