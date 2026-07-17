package jmoa.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads a frozen class set without initialization for constrained dynamic AppCDS training. */
public final class CommonClassArchivePreloader {
    private CommonClassArchivePreloader() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: CommonClassArchivePreloader <class-list>");
            System.exit(64);
        }

        List<String> classNames = readClassNames(Path.of(args[0]));
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        List<String> failures = new ArrayList<>();
        int loaded = 0;
        for (String className : classNames) {
            try {
                Class.forName(className, false, loader);
                loaded++;
            } catch (LinkageError | ClassNotFoundException failure) {
                failures.add(className + " | " + failure.getClass().getSimpleName() + " | " + failure.getMessage());
            }
        }

        System.out.printf("JMOA_PRELOAD requested=%d loaded=%d failed=%d%n", classNames.size(), loaded, failures.size());
        failures.forEach(failure -> System.err.println("JMOA_PRELOAD_FAILURE " + failure));
        if (!failures.isEmpty()) {
            System.exit(2);
        }
    }

    static List<String> readClassNames(Path path) throws IOException {
        List<String> result = new ArrayList<>();
        for (String rawLine : Files.readAllLines(path)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String normalized = line.replace('/', '.');
            if (isRuntimeGenerated(normalized)) {
                throw new IllegalArgumentException("Runtime-generated class is forbidden in the common set: " + normalized);
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private static boolean isRuntimeGenerated(String className) {
        return className.startsWith("[")
                || className.contains("/0x")
                || className.contains("$$Lambda")
                || className.contains("$$SpringCGLIB")
                || className.contains("ByteBuddy")
                || className.contains("HibernateProxy")
                || className.matches("(?:jdk\\.proxy.*|.*\\$Proxy\\d+.*)");
    }
}
