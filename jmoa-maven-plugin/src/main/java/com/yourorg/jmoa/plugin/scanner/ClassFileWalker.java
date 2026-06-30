package com.yourorg.jmoa.plugin.scanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ClassFileWalker {

    public static List<File> findClassFiles(File baseDir) throws IOException {
        if (baseDir == null || !baseDir.exists() || !baseDir.isDirectory()) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(baseDir.toPath())) {
            return walk
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".class"))
                .map(Path::toFile)
                .collect(Collectors.toList());
        }
    }
}
