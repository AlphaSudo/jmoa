package com.yourorg.jmoa.plugin.measure;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ClassLoadLogParser {

    public ClassLoadMetrics parse(File classLoadLogFile) throws IOException {
        return toMetrics(parseInventory(classLoadLogFile));
    }

    public ClassLoadInventory parseInventory(File classLoadLogFile) throws IOException {
        return parseInventory(Files.readAllLines(classLoadLogFile.toPath()));
    }

    ClassLoadMetrics parse(List<String> lines) {
        return toMetrics(parseInventory(lines));
    }

    ClassLoadInventory parseInventory(List<String> lines) {
        Set<String> allClasses = new LinkedHashSet<>();
        Set<String> lambdaClasses = new LinkedHashSet<>();
        Set<String> applicationLambdaClasses = new LinkedHashSet<>();
        Set<String> frameworkLambdaClasses = new LinkedHashSet<>();
        Set<String> jmoaToolClasses = new LinkedHashSet<>();
        Set<String> jmoaRuntimeLibClasses = new LinkedHashSet<>();
        Set<String> jmoaGeneratedOptimizationClasses = new LinkedHashSet<>();
        Set<String> jmoaGeneratedPackageAdapterClasses = new LinkedHashSet<>();
        Set<String> jdkInternalClassfileClasses = new LinkedHashSet<>();
        Set<String> javaLangClassfileClasses = new LinkedHashSet<>();
        Set<String> springCoreClassReadingClasses = new LinkedHashSet<>();
        Set<String> unloadedClasses = new LinkedHashSet<>();
        Set<String> unloadedLambdaClasses = new LinkedHashSet<>();
        Set<String> unloadedApplicationLambdaClasses = new LinkedHashSet<>();
        Set<String> unloadedFrameworkLambdaClasses = new LinkedHashSet<>();
        Set<String> unloadedJmoaRuntimeLibClasses = new LinkedHashSet<>();
        Set<String> unloadedJmoaGeneratedOptimizationClasses = new LinkedHashSet<>();
        Set<String> unloadedJmoaGeneratedPackageAdapterClasses = new LinkedHashSet<>();

        for (String line : lines) {
            LogEvent event = extractLogEvent(line);
            String className = event == null ? null : event.className();
            if (className == null) {
                continue;
            }
            if (event.kind() == LogEventKind.LOAD) {
                allClasses.add(className);
                if (isJmoaToolClass(className)) {
                    jmoaToolClasses.add(className);
                } else if (isJmoaGeneratedPackageAdapterClass(className)) {
                    jmoaGeneratedPackageAdapterClasses.add(className);
                } else if (isJmoaGeneratedOptimizationClass(className)) {
                    jmoaGeneratedOptimizationClasses.add(className);
                } else if (isJmoaRuntimeLibClass(className)) {
                    jmoaRuntimeLibClasses.add(className);
                }
                if (isJdkInternalClassfileClass(className)) {
                    jdkInternalClassfileClasses.add(className);
                }
                if (isJavaLangClassfileClass(className)) {
                    javaLangClassfileClasses.add(className);
                }
                if (isSpringCoreClassReadingClass(className)) {
                    springCoreClassReadingClasses.add(className);
                }
                if (className.contains("$$Lambda")) {
                    lambdaClasses.add(className);
                    if (isAnyJmoaClass(className)) {
                        continue;
                    }
                    if (isFrameworkClass(className)) {
                        frameworkLambdaClasses.add(className);
                    } else {
                        applicationLambdaClasses.add(className);
                    }
                }
            } else if (event.kind() == LogEventKind.UNLOAD) {
                unloadedClasses.add(className);
                if (isJmoaGeneratedPackageAdapterClass(className)) {
                    unloadedJmoaGeneratedPackageAdapterClasses.add(className);
                } else if (isJmoaGeneratedOptimizationClass(className)) {
                    unloadedJmoaGeneratedOptimizationClasses.add(className);
                } else if (isJmoaRuntimeLibClass(className)) {
                    unloadedJmoaRuntimeLibClasses.add(className);
                }
                if (className.contains("$$Lambda")) {
                    unloadedLambdaClasses.add(className);
                    if (isAnyJmoaClass(className)) {
                        continue;
                    }
                    if (isFrameworkClass(className)) {
                        unloadedFrameworkLambdaClasses.add(className);
                    } else {
                        unloadedApplicationLambdaClasses.add(className);
                    }
                }
            }
        }

        return new ClassLoadInventory(
            allClasses,
            lambdaClasses,
            applicationLambdaClasses,
            frameworkLambdaClasses,
            jmoaToolClasses,
            jmoaRuntimeLibClasses,
            jmoaGeneratedOptimizationClasses,
            jmoaGeneratedPackageAdapterClasses,
            jdkInternalClassfileClasses,
            javaLangClassfileClasses,
            springCoreClassReadingClasses,
            unloadedClasses,
            unloadedLambdaClasses,
            unloadedApplicationLambdaClasses,
            unloadedFrameworkLambdaClasses,
            unloadedJmoaRuntimeLibClasses,
            unloadedJmoaGeneratedOptimizationClasses,
            unloadedJmoaGeneratedPackageAdapterClasses
        );
    }

    private ClassLoadMetrics toMetrics(ClassLoadInventory inventory) {
        return new ClassLoadMetrics(
            inventory.allClasses().size(),
            inventory.lambdaClasses().size(),
            inventory.applicationLambdaClasses().size(),
            inventory.frameworkLambdaClasses().size(),
            inventory.jmoaToolClasses().size(),
            inventory.jmoaRuntimeLibClasses().size(),
            inventory.jmoaGeneratedOptimizationClasses().size(),
            inventory.jmoaGeneratedPackageAdapterClasses().size(),
            inventory.jdkInternalClassfileClasses().size(),
            inventory.javaLangClassfileClasses().size(),
            inventory.springCoreClassReadingClasses().size(),
            inventory.unloadedClasses().size(),
            inventory.unloadedLambdaClasses().size(),
            inventory.unloadedApplicationLambdaClasses().size(),
            inventory.unloadedFrameworkLambdaClasses().size(),
            inventory.unloadedJmoaRuntimeLibClasses().size(),
            inventory.unloadedJmoaGeneratedOptimizationClasses().size(),
            inventory.unloadedJmoaGeneratedPackageAdapterClasses().size()
        );
    }

    private boolean isJmoaToolClass(String className) {
        return className.startsWith("jmoa/tools/") || className.startsWith("jmoa.tools.");
    }

    private boolean isAnyJmoaClass(String className) {
        return className.startsWith("jmoa/") || className.startsWith("jmoa.");
    }

    private boolean isJmoaRuntimeLibClass(String className) {
        return (className.startsWith("jmoa/runtime/") || className.startsWith("jmoa.runtime."))
            && !isJmoaGeneratedOptimizationClass(className);
    }

    private boolean isJmoaGeneratedOptimizationClass(String className) {
        return "jmoa/runtime/JmoaRuntime".equals(className) || "jmoa.runtime.JmoaRuntime".equals(className);
    }

    private boolean isJmoaGeneratedPackageAdapterClass(String className) {
        return className.contains("/JmoaPkgAdapters$")
            || className.contains(".JmoaPkgAdapters$")
            || className.contains("$JmoaPkgAdapters$");
    }

    private boolean isFrameworkClass(String className) {
        return className.startsWith("org/springframework/")
            || className.startsWith("org.springframework.")
            || className.startsWith("com/fasterxml/")
            || className.startsWith("com.fasterxml.")
            || className.startsWith("org/hibernate/")
            || className.startsWith("org.hibernate.");
    }

    private boolean isJdkInternalClassfileClass(String className) {
        return className.startsWith("jdk/internal/classfile/")
            || className.startsWith("jdk.internal.classfile.");
    }

    private boolean isJavaLangClassfileClass(String className) {
        return className.startsWith("java/lang/classfile/")
            || className.startsWith("java.lang.classfile.");
    }

    private boolean isSpringCoreClassReadingClass(String className) {
        return className.startsWith("org/springframework/core/type/classreading/")
            || className.startsWith("org.springframework.core.type.classreading.");
    }

    private LogEvent extractLogEvent(String line) {
        if (line == null) {
            return null;
        }
        int eventSeparator = line.indexOf("] ");
        if (eventSeparator < 0) {
            return null;
        }
        String remainder = line.substring(eventSeparator + 2).trim();
        if (line.contains("[class,load]")) {
            int sourceIndex = remainder.indexOf(" source:");
            if (sourceIndex > 0) {
                return new LogEvent(LogEventKind.LOAD, remainder.substring(0, sourceIndex).trim());
            }
            return new LogEvent(LogEventKind.LOAD, remainder);
        }
        if (line.contains("[class,unload]")) {
            int spaceIndex = remainder.indexOf(' ');
            String className = spaceIndex > 0 ? remainder.substring(0, spaceIndex).trim() : remainder;
            return new LogEvent(LogEventKind.UNLOAD, className);
        }
        return null;
    }

    public record ClassLoadMetrics(
        int totalLoadedClasses,
        int lambdaClasses,
        int applicationLambdaClasses,
        int frameworkLambdaClasses,
        int jmoaToolClasses,
        int jmoaRuntimeLibClasses,
        int jmoaGeneratedOptimizationClasses,
        int jmoaGeneratedPackageAdapterClasses,
        int jdkInternalClassfileClasses,
        int javaLangClassfileClasses,
        int springCoreClassReadingClasses,
        int totalUnloadedClasses,
        int unloadedLambdaClasses,
        int unloadedApplicationLambdaClasses,
        int unloadedFrameworkLambdaClasses,
        int unloadedJmoaRuntimeLibClasses,
        int unloadedJmoaGeneratedOptimizationClasses,
        int unloadedJmoaGeneratedPackageAdapterClasses
    ) {
        public int jmoaGeneratedClasses() {
            return jmoaGeneratedOptimizationClasses + jmoaGeneratedPackageAdapterClasses;
        }

        public int unloadedJmoaGeneratedClasses() {
            return unloadedJmoaGeneratedOptimizationClasses + unloadedJmoaGeneratedPackageAdapterClasses;
        }
    }

    private enum LogEventKind {
        LOAD,
        UNLOAD
    }

    private record LogEvent(LogEventKind kind, String className) {
    }
}
