package com.yourorg.jmoa.plugin.modec;

import com.yourorg.jmoa.plugin.ClassRootDescriptor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ModeCClasspathWriter {

    public File write(
        File targetDirectory,
        List<ClassRootDescriptor> roots,
        List<String> compileClasspathElements
    ) throws IOException {
        Files.createDirectories(targetDirectory.toPath());
        File output = new File(targetDirectory, "jmoa-mode-c-classpath.txt");

        Set<String> orderedEntries = new LinkedHashSet<>();
        for (ClassRootDescriptor root : roots) {
            addIfPresent(orderedEntries, root.rootDirectory());
        }
        for (String classpathElement : compileClasspathElements) {
            addIfPresent(orderedEntries, new File(classpathElement));
        }

        List<String> lines = new ArrayList<>(orderedEntries);
        Files.write(output.toPath(), lines);
        return output;
    }

    private void addIfPresent(Set<String> orderedEntries, File entry) throws IOException {
        if (entry == null || !entry.exists()) {
            return;
        }
        orderedEntries.add(entry.getCanonicalFile().getAbsolutePath());
    }
}
