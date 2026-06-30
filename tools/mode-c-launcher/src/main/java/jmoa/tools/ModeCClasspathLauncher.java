package jmoa.tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ModeCClasspathLauncher {

    public static void main(String[] args) throws Exception {
        LaunchRequest request = LaunchRequest.parse(args);
        LaunchOutcome outcome = launch(request);
        if (outcome.classification() != FailureClassification.NONE) {
            System.err.println("JMOA_MODE_C_LAUNCH_CLASSIFICATION=" + outcome.classification().name());
            System.err.println("JMOA_MODE_C_LAUNCH_MESSAGE=" + outcome.message());
            if (outcome.throwable() != null) {
                System.err.println(stackTraceOf(outcome.throwable()));
            }
            System.exit(outcome.classification().exitCode());
        }
    }

    static LaunchOutcome launch(LaunchRequest request) {
        Objects.requireNonNull(request, "request");
        List<URL> urls = new ArrayList<>();
        try {
            for (String entry : Files.readAllLines(request.classpathFile())) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                File file = new File(trimmed);
                if (!file.exists()) {
                    throw new IllegalArgumentException("Classpath entry does not exist: " + file.getAbsolutePath());
                }
                urls.add(file.getCanonicalFile().toURI().toURL());
            }
        } catch (Exception e) {
            return LaunchOutcome.failure(
                FailureClassification.CONFIGURATION_ERROR,
                "Failed to read Mode C classpath file.",
                e
            );
        }

        ClassLoader parent = ModeCClasspathLauncher.class.getClassLoader().getParent();
        try (URLClassLoader loader = createClassLoader(urls, parent, request)) {
            Thread currentThread = Thread.currentThread();
            ClassLoader previous = currentThread.getContextClassLoader();
            currentThread.setContextClassLoader(loader);
            try {
                Class<?> mainClass = Class.forName(request.mainClass(), true, loader);
                Method mainMethod = mainClass.getMethod("main", String[].class);
                if (!Modifier.isStatic(mainMethod.getModifiers())) {
                    throw new IllegalArgumentException("Main method is not static on " + request.mainClass());
                }
                if (request.sleepBeforeExitMs() > 0) {
                    // Web-app / long-running mode: run main() in a daemon thread,
                    // wait for the configured sleep (letting the app fully start),
                    // then force GC and hard-exit to capture NMT statistics.
                    Thread appThread = new Thread(() -> {
                        try {
                            mainMethod.invoke(null, (Object) request.applicationArgs().toArray(String[]::new));
                        } catch (InvocationTargetException e) {
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            FailureClassification classification = classify(cause);
                            System.err.println("JMOA_MODE_C_LAUNCH_CLASSIFICATION=" + classification.name());
                            System.err.println("JMOA_MODE_C_LAUNCH_MESSAGE=Target main class threw an exception.");
                            if (cause != null) {
                                System.err.println(stackTraceOf(cause));
                            }
                            System.exit(classification.exitCode());
                        } catch (Throwable t) {
                            FailureClassification classification = classify(t);
                            System.err.println("JMOA_MODE_C_LAUNCH_CLASSIFICATION=" + classification.name());
                            System.err.println("JMOA_MODE_C_LAUNCH_MESSAGE=Failed before target main method completed.");
                            System.err.println(stackTraceOf(t));
                            System.exit(classification.exitCode());
                        }
                    }, "jmoa-app-main");
                    appThread.setDaemon(true);
                    appThread.setContextClassLoader(loader);
                    appThread.start();
                    sleepAndShutdown(request);
                    return LaunchOutcome.success(); // unreachable in practice
                } else {
                    mainMethod.invoke(null, (Object) request.applicationArgs().toArray(String[]::new));
                    maybeForceGc(request);
                    return LaunchOutcome.success();
                }
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                FailureClassification classification = classify(cause);
                if (classification == FailureClassification.ENVIRONMENT_WALL) {
                    maybeForceGc(request);
                }
                return LaunchOutcome.failure(
                    classification,
                    "Target main class threw an exception.",
                    cause
                );
            } catch (Throwable t) {
                FailureClassification classification = classify(t);
                if (classification == FailureClassification.ENVIRONMENT_WALL) {
                    maybeForceGc(request);
                }
                return LaunchOutcome.failure(
                    classification,
                    "Failed before target main method completed.",
                    t
                );
            } finally {
                currentThread.setContextClassLoader(previous);
            }
        } catch (IOException e) {
            return LaunchOutcome.failure(
                FailureClassification.CONFIGURATION_ERROR,
                "Failed to close Mode C class loader.",
                e
            );
        }
    }

    private static URLClassLoader createClassLoader(List<URL> urls, ClassLoader parent, LaunchRequest request) {
        URL[] classpath = urls.toArray(URL[]::new);
        if (request.resourceLogFile() == null) {
            return new URLClassLoader(classpath, parent);
        }
        return new ResourceLoggingClassLoader(classpath, parent, request.resourceLogFile());
    }

    static FailureClassification classify(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof VerifyError
                || current instanceof IllegalAccessError
                || current instanceof NoSuchMethodError
                || current instanceof NoSuchFieldError
                || current instanceof ClassFormatError
                || current instanceof BootstrapMethodError
                || current instanceof ClassNotFoundException
                || current instanceof NoClassDefFoundError
                || current instanceof ClassCastException) {
                return FailureClassification.JMOA_LINKAGE_FAILURE;
            }
            if (current instanceof LinkageError) {
                return FailureClassification.JMOA_LINKAGE_FAILURE;
            }
            if (isEnvironmentWall(current)) {
                return FailureClassification.ENVIRONMENT_WALL;
            }
        }
        return FailureClassification.APPLICATION_FAILURE;
    }

    private static boolean isEnvironmentWall(Throwable throwable) {
        if (throwable instanceof SQLException) {
            return true;
        }
        String className = throwable.getClass().getName();
        if (className.contains("PSQLException")
            || className.contains("JDBCConnectionException")
            || className.contains("ConnectException")
            || className.contains("MongoTimeoutException")) {
            return true;
        }
        String message = throwable.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("Connection refused")
            || message.contains("could not obtain JDBC connection")
            || message.contains("Unable to determine Dialect")
            || message.contains("Communications link failure")
            || message.contains("Failed to configure a DataSource");
    }

    private static String stackTraceOf(Throwable throwable) {
        StringWriter buffer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }

    private static void maybeForceGc(LaunchRequest request) {
        if (!request.forceGcBeforeExit()) {
            return;
        }
        System.gc();
        System.runFinalization();
        long sleepMs = Math.max(0L, request.sleepBeforeExitMs());
        if (sleepMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Used by the web-app / long-running launch path.  Sleeps for the
     * configured duration (giving the application time to fully initialise),
     * then optionally forces GC, and finally hard-exits so that the JVM
     * prints NMT statistics ({@code -XX:+PrintNMTStatistics}).
     */
    private static void sleepAndShutdown(LaunchRequest request) {
        try {
            Thread.sleep(Math.max(0L, request.sleepBeforeExitMs()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (request.forceGcBeforeExit()) {
            System.gc();
            System.runFinalization();
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Hard-exit so the JVM prints NMT statistics via -XX:+PrintNMTStatistics.
        System.exit(0);
    }

    static final class LaunchRequest {
        private final Path classpathFile;
        private final String mainClass;
        private final List<String> applicationArgs;
        private final boolean forceGcBeforeExit;
        private final long sleepBeforeExitMs;
        private final Path resourceLogFile;

        private LaunchRequest(
            Path classpathFile,
            String mainClass,
            List<String> applicationArgs,
            boolean forceGcBeforeExit,
            long sleepBeforeExitMs,
            Path resourceLogFile
        ) {
            this.classpathFile = classpathFile;
            this.mainClass = mainClass;
            this.applicationArgs = List.copyOf(applicationArgs);
            this.forceGcBeforeExit = forceGcBeforeExit;
            this.sleepBeforeExitMs = Math.max(0L, sleepBeforeExitMs);
            this.resourceLogFile = resourceLogFile;
        }

        static LaunchRequest parse(String[] args) {
            Path classpathFile = null;
            String mainClass = null;
            List<String> applicationArgs = new ArrayList<>();
            boolean passthrough = false;
            boolean forceGcBeforeExit = false;
            long sleepBeforeExitMs = 0L;
            Path resourceLogFile = null;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (passthrough) {
                    applicationArgs.add(arg);
                    continue;
                }
                switch (arg) {
                    case "--classpath-file" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("--classpath-file requires a value");
                        }
                        classpathFile = Path.of(args[++i]);
                    }
                    case "--main-class" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("--main-class requires a value");
                        }
                        mainClass = args[++i];
                    }
                    case "--force-gc-before-exit" -> forceGcBeforeExit = true;
                    case "--resource-log-file" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("--resource-log-file requires a value");
                        }
                        resourceLogFile = Path.of(args[++i]);
                    }
                    case "--sleep-before-exit-ms" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("--sleep-before-exit-ms requires a value");
                        }
                        sleepBeforeExitMs = Long.parseLong(args[++i]);
                    }
                    default -> {
                        if (arg.startsWith("--sleep-before-exit-ms=")) {
                            sleepBeforeExitMs = Long.parseLong(arg.substring("--sleep-before-exit-ms=".length()));
                            continue;
                        }
                        if (arg.startsWith("--resource-log-file=")) {
                            resourceLogFile = Path.of(arg.substring("--resource-log-file=".length()));
                            continue;
                        }
                        if ("--".equals(arg)) {
                            passthrough = true;
                            continue;
                        }
                        throw new IllegalArgumentException("Unknown argument: " + arg);
                    }
                }
            }

            if (classpathFile == null) {
                throw new IllegalArgumentException("--classpath-file is required");
            }
            if (mainClass == null || mainClass.isBlank()) {
                throw new IllegalArgumentException("--main-class is required");
            }
            return new LaunchRequest(
                classpathFile,
                mainClass,
                applicationArgs,
                forceGcBeforeExit,
                sleepBeforeExitMs,
                resourceLogFile
            );
        }

        Path classpathFile() {
            return classpathFile;
        }

        String mainClass() {
            return mainClass;
        }

        List<String> applicationArgs() {
            return applicationArgs;
        }

        boolean forceGcBeforeExit() {
            return forceGcBeforeExit;
        }

        long sleepBeforeExitMs() {
            return sleepBeforeExitMs;
        }

        Path resourceLogFile() {
            return resourceLogFile;
        }
    }

    static final class ResourceLoggingClassLoader extends URLClassLoader {
        private final Path reportFile;
        private final Map<String, ResourceAccess> resources = new LinkedHashMap<>();

        ResourceLoggingClassLoader(URL[] urls, ClassLoader parent, Path reportFile) {
            super(urls, parent);
            this.reportFile = reportFile;
        }

        @Override
        public URL getResource(String name) {
            URL url = super.getResource(name);
            record(name, "getResource", url == null ? List.of() : List.of(url.toString()));
            return url;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            List<URL> urls = Collections.list(super.getResources(name));
            record(name, "getResources", urls.stream().map(URL::toString).toList());
            return Collections.enumeration(urls);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            InputStream stream = super.getResourceAsStream(name);
            record(name, "getResourceAsStream", stream == null ? List.of() : List.of("stream"));
            return stream;
        }

        @Override
        public void close() throws IOException {
            try {
                writeReport();
            } finally {
                super.close();
            }
        }

        private synchronized void record(String name, String method, List<String> sources) {
            ResourceAccess access = resources.computeIfAbsent(name, ResourceAccess::new);
            access.record(method, sources);
        }

        private void writeReport() throws IOException {
            if (reportFile.getParent() != null) {
                Files.createDirectories(reportFile.getParent());
            }
            StringBuilder json = new StringBuilder();
            json.append("{\n  \"resources\": [\n");
            List<ResourceAccess> values = new ArrayList<>(resources.values());
            for (int i = 0; i < values.size(); i++) {
                ResourceAccess access = values.get(i);
                json.append(access.toJson("    "));
                if (i + 1 < values.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            json.append("  ]\n}\n");
            Files.writeString(reportFile, json.toString());
        }
    }

    static final class ResourceAccess {
        private final String name;
        private int getResourceCalls;
        private int getResourcesCalls;
        private int getResourceAsStreamCalls;
        private int foundCalls;
        private int notFoundCalls;
        private final Set<String> sources = new LinkedHashSet<>();

        ResourceAccess(String name) {
            this.name = name;
        }

        void record(String method, List<String> foundSources) {
            switch (method) {
                case "getResource" -> getResourceCalls++;
                case "getResources" -> getResourcesCalls++;
                case "getResourceAsStream" -> getResourceAsStreamCalls++;
                default -> throw new IllegalArgumentException("Unknown resource method: " + method);
            }
            if (foundSources.isEmpty()) {
                notFoundCalls++;
            } else {
                foundCalls++;
                sources.addAll(foundSources);
            }
        }

        String toJson(String indent) {
            StringBuilder json = new StringBuilder();
            json.append(indent).append("{\n");
            json.append(indent).append("  \"name\": \"").append(escape(name)).append("\",\n");
            json.append(indent).append("  \"getResourceCalls\": ").append(getResourceCalls).append(",\n");
            json.append(indent).append("  \"getResourcesCalls\": ").append(getResourcesCalls).append(",\n");
            json.append(indent).append("  \"getResourceAsStreamCalls\": ").append(getResourceAsStreamCalls).append(",\n");
            json.append(indent).append("  \"foundCalls\": ").append(foundCalls).append(",\n");
            json.append(indent).append("  \"notFoundCalls\": ").append(notFoundCalls).append(",\n");
            json.append(indent).append("  \"sources\": [");
            List<String> orderedSources = new ArrayList<>(sources);
            for (int i = 0; i < orderedSources.size(); i++) {
                if (i > 0) {
                    json.append(", ");
                }
                json.append('"').append(escape(orderedSources.get(i))).append('"');
            }
            json.append("]\n");
            json.append(indent).append('}');
            return json.toString();
        }

        private static String escape(String input) {
            return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        }
    }

    enum FailureClassification {
        NONE(0),
        ENVIRONMENT_WALL(20),
        JMOA_LINKAGE_FAILURE(10),
        CONFIGURATION_ERROR(30),
        APPLICATION_FAILURE(40);

        private final int exitCode;

        FailureClassification(int exitCode) {
            this.exitCode = exitCode;
        }

        int exitCode() {
            return exitCode;
        }
    }

    record LaunchOutcome(
        FailureClassification classification,
        String message,
        Throwable throwable
    ) {
        static LaunchOutcome success() {
            return new LaunchOutcome(FailureClassification.NONE, "Launch completed successfully.", null);
        }

        static LaunchOutcome failure(
            FailureClassification classification,
            String message,
            Throwable throwable
        ) {
            return new LaunchOutcome(classification, message, throwable);
        }
    }
}
