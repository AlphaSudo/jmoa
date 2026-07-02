package com.yourorg.jmoa.plugin.size;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BytecodeRuntimeCorrelator {

    private static final String METADATA_VERSION = "v2-b2-bytecode-runtime-correlation";
    private static final long MEMORY_CORRELATED_BYTES = 1024L * 1024L;
    private static final Pattern HISTOGRAM_LINE =
        Pattern.compile("^\\s*(\\d+):\\s+(\\d+)\\s+(\\d+)\\s+(.+?)\\s*$");

    public BytecodeRuntimeCorrelationReport correlate(
        ClassfileSizeProfile profile,
        File classLoadLog,
        File classHistogram
    ) throws IOException {
        RuntimeLoadData loadData = readClassLoadLog(classLoadLog);
        Map<String, HistogramEntry> histogram = readClassHistogram(classHistogram);
        boolean hasRuntimeEvidence = loadData.hasData() || !histogram.isEmpty();

        List<BytecodeRuntimeClassCorrelation> classRecords = new ArrayList<>();
        for (ClassfileSizeRecord record : profile.classes()) {
            HistogramEntry histogramEntry = histogram.get(record.className());
            boolean loaded = loadData.loadedClasses().contains(record.className());
            boolean unloaded = loadData.unloadedClasses().contains(record.className());
            long histogramInstances = histogramEntry == null ? 0 : histogramEntry.instances();
            long histogramBytes = histogramEntry == null ? 0 : histogramEntry.bytes();
            RuntimeCorrelationCategory category = categoryFor(record, loaded, histogramBytes, hasRuntimeEvidence);
            classRecords.add(new BytecodeRuntimeClassCorrelation(
                record.className(),
                record.artifact(),
                record.generatedFamily(),
                record.generatedLike(),
                record.classfileBytes(),
                record.largestMethodCodeLength(),
                record.totalMethodCodeBytes(),
                record.constantPoolCount(),
                record.totalAttributeBytes(),
                loaded,
                unloaded,
                loadData.origins().get(record.className()),
                histogramInstances,
                histogramBytes,
                category,
                priorityFor(record, loaded, histogramBytes, category)
            ));
        }

        classRecords = classRecords.stream()
            .sorted(Comparator
                .comparing(BytecodeRuntimeClassCorrelation::priority)
                .thenComparing(BytecodeRuntimeClassCorrelation::runtimeLoaded, Comparator.reverseOrder())
                .thenComparing(BytecodeRuntimeClassCorrelation::histogramBytes, Comparator.reverseOrder())
                .thenComparing(BytecodeRuntimeClassCorrelation::classfileBytes, Comparator.reverseOrder())
                .thenComparing(BytecodeRuntimeClassCorrelation::className))
            .toList();

        Map<String, BytecodeRuntimeClassCorrelation> bestByClass = bestClassCorrelationByName(classRecords);
        List<BytecodeRuntimeMethodCorrelation> methodRecords = profile.methods().stream()
            .filter(method -> method.threshold() != MethodSizeRisk.NORMAL)
            .map(method -> methodCorrelation(method, bestByClass.get(method.className())))
            .sorted(Comparator
                .comparing(BytecodeRuntimeMethodCorrelation::runtimeLoadedClass, Comparator.reverseOrder())
                .thenComparing(BytecodeRuntimeMethodCorrelation::codeLength, Comparator.reverseOrder())
                .thenComparing(BytecodeRuntimeMethodCorrelation::className)
                .thenComparing(BytecodeRuntimeMethodCorrelation::methodName))
            .toList();

        return new BytecodeRuntimeCorrelationReport(
            METADATA_VERSION,
            Instant.now().toString(),
            classLoadLog == null || !classLoadLog.isFile() ? null : classLoadLog.getAbsolutePath(),
            classHistogram == null || !classHistogram.isFile() ? null : classHistogram.getAbsolutePath(),
            profile.totalClassesScanned(),
            loadData.loadedClasses().size(),
            (int) classRecords.stream().filter(BytecodeRuntimeClassCorrelation::runtimeLoaded).count(),
            (int) classRecords.stream().filter(record -> record.histogramInstances() > 0).count(),
            histogram.values().stream().mapToLong(HistogramEntry::bytes).sum(),
            categoryCounts(classRecords),
            familyCorrelation(classRecords),
            classRecords,
            methodRecords
        );
    }

    private static BytecodeRuntimeMethodCorrelation methodCorrelation(
        MethodSizeRecord method,
        BytecodeRuntimeClassCorrelation clazz
    ) {
        return new BytecodeRuntimeMethodCorrelation(
            method.className(),
            method.methodName(),
            method.descriptor(),
            clazz == null ? GeneratedClassFamily.PLAIN : clazz.generatedFamily(),
            method.codeLength(),
            method.threshold(),
            method.staticInitializer(),
            method.synthetic(),
            method.bridge(),
            clazz != null && clazz.runtimeLoaded(),
            clazz == null ? 0 : clazz.histogramInstances(),
            clazz == null ? 0 : clazz.histogramBytes(),
            clazz == null ? RuntimeCorrelationCategory.UNKNOWN_NEEDS_JFR : clazz.category()
        );
    }

    private static Map<String, BytecodeRuntimeClassCorrelation> bestClassCorrelationByName(
        List<BytecodeRuntimeClassCorrelation> classRecords
    ) {
        Map<String, BytecodeRuntimeClassCorrelation> best = new LinkedHashMap<>();
        for (BytecodeRuntimeClassCorrelation record : classRecords) {
            best.merge(record.className(), record, BytecodeRuntimeCorrelator::preferRuntimeRelevant);
        }
        return best;
    }

    private static BytecodeRuntimeClassCorrelation preferRuntimeRelevant(
        BytecodeRuntimeClassCorrelation left,
        BytecodeRuntimeClassCorrelation right
    ) {
        if (left.runtimeLoaded() != right.runtimeLoaded()) {
            return left.runtimeLoaded() ? left : right;
        }
        if (left.histogramBytes() != right.histogramBytes()) {
            return left.histogramBytes() > right.histogramBytes() ? left : right;
        }
        return left.classfileBytes() >= right.classfileBytes() ? left : right;
    }

    private static RuntimeCorrelationCategory categoryFor(
        ClassfileSizeRecord record,
        boolean loaded,
        long histogramBytes,
        boolean hasRuntimeEvidence
    ) {
        if (!hasRuntimeEvidence) {
            return RuntimeCorrelationCategory.UNKNOWN_NEEDS_JFR;
        }
        if (histogramBytes >= MEMORY_CORRELATED_BYTES) {
            return RuntimeCorrelationCategory.MEMORY_CORRELATED;
        }
        if (histogramBytes > 0) {
            return RuntimeCorrelationCategory.WORKLOAD_SURVIVOR;
        }
        if (loaded && isSizeRisk(record)) {
            return RuntimeCorrelationCategory.RUNTIME_LOADED_HOT;
        }
        if (loaded) {
            return RuntimeCorrelationCategory.RUNTIME_LOADED_COLD;
        }
        return RuntimeCorrelationCategory.STATIC_ONLY_RISK;
    }

    private static String priorityFor(
        ClassfileSizeRecord record,
        boolean loaded,
        long histogramBytes,
        RuntimeCorrelationCategory category
    ) {
        if (category == RuntimeCorrelationCategory.MEMORY_CORRELATED) {
            return "HIGH_MEMORY_CORRELATED";
        }
        if (loaded && isSizeRisk(record)) {
            return "HIGH_RUNTIME_SIZE_RISK";
        }
        if (!loaded && record.largestMethodCodeLength() >= 49_152) {
            return "HIGH_STATIC_SIZE_RISK";
        }
        if (histogramBytes > 0) {
            return "MEDIUM_WORKLOAD_SURVIVOR";
        }
        if (isSizeRisk(record)) {
            return "MEDIUM_SIZE_RISK";
        }
        if (loaded) {
            return "LOW_RUNTIME_LOADED";
        }
        return "LOW";
    }

    private static boolean isSizeRisk(ClassfileSizeRecord record) {
        return record.classfileBytes() >= 128L * 1024L
            || record.largestMethodCodeLength() >= 49_152
            || record.debugAttributeBytes() >= 32L * 1024L;
    }

    private static Map<RuntimeCorrelationCategory, Integer> categoryCounts(
        List<BytecodeRuntimeClassCorrelation> records
    ) {
        Map<RuntimeCorrelationCategory, Integer> counts = new EnumMap<>(RuntimeCorrelationCategory.class);
        for (RuntimeCorrelationCategory category : RuntimeCorrelationCategory.values()) {
            counts.put(category, 0);
        }
        for (BytecodeRuntimeClassCorrelation record : records) {
            counts.merge(record.category(), 1, Integer::sum);
        }
        return counts;
    }

    private static List<BytecodeRuntimeFamilyCorrelation> familyCorrelation(
        List<BytecodeRuntimeClassCorrelation> records
    ) {
        Map<GeneratedClassFamily, List<BytecodeRuntimeClassCorrelation>> byFamily = new EnumMap<>(GeneratedClassFamily.class);
        for (GeneratedClassFamily family : GeneratedClassFamily.values()) {
            byFamily.put(family, new ArrayList<>());
        }
        for (BytecodeRuntimeClassCorrelation record : records) {
            byFamily.computeIfAbsent(record.generatedFamily(), ignored -> new ArrayList<>()).add(record);
        }

        List<BytecodeRuntimeFamilyCorrelation> families = new ArrayList<>();
        for (Map.Entry<GeneratedClassFamily, List<BytecodeRuntimeClassCorrelation>> entry : byFamily.entrySet()) {
            List<BytecodeRuntimeClassCorrelation> familyRecords = entry.getValue();
            if (familyRecords.isEmpty()) {
                continue;
            }
            families.add(new BytecodeRuntimeFamilyCorrelation(
                entry.getKey(),
                familyRecords.size(),
                (int) familyRecords.stream().filter(BytecodeRuntimeClassCorrelation::runtimeLoaded).count(),
                (int) familyRecords.stream().filter(record -> record.histogramInstances() > 0).count(),
                familyRecords.stream().mapToLong(BytecodeRuntimeClassCorrelation::classfileBytes).sum(),
                familyRecords.stream()
                    .filter(BytecodeRuntimeClassCorrelation::runtimeLoaded)
                    .mapToLong(BytecodeRuntimeClassCorrelation::classfileBytes)
                    .sum(),
                familyRecords.stream().mapToLong(BytecodeRuntimeClassCorrelation::histogramBytes).sum(),
                familyRecords.stream().mapToLong(BytecodeRuntimeClassCorrelation::histogramInstances).sum()
            ));
        }
        return families.stream()
            .sorted(Comparator
                .comparing(BytecodeRuntimeFamilyCorrelation::loadedClassfileBytes, Comparator.reverseOrder())
                .thenComparing(BytecodeRuntimeFamilyCorrelation::classfileBytes, Comparator.reverseOrder()))
            .toList();
    }

    private RuntimeLoadData readClassLoadLog(File classLoadLog) throws IOException {
        if (classLoadLog == null || !classLoadLog.isFile()) {
            return RuntimeLoadData.empty();
        }
        Set<String> loaded = new LinkedHashSet<>();
        Set<String> unloaded = new LinkedHashSet<>();
        Map<String, String> origins = new LinkedHashMap<>();
        for (String line : Files.readAllLines(classLoadLog.toPath())) {
            RuntimeEvent event = parseRuntimeEvent(line);
            if (event == null) {
                continue;
            }
            if (event.load()) {
                loaded.add(event.className());
                if (event.origin() != null && !event.origin().isBlank()) {
                    origins.putIfAbsent(event.className(), event.origin());
                }
            } else {
                unloaded.add(event.className());
            }
        }
        return new RuntimeLoadData(true, loaded, unloaded, origins);
    }

    private RuntimeEvent parseRuntimeEvent(String line) {
        if (line == null || !line.contains("[class,")) {
            return null;
        }
        int eventSeparator = line.indexOf("] ");
        if (eventSeparator < 0) {
            return null;
        }
        String remainder = line.substring(eventSeparator + 2).trim();
        if (line.contains("[class,load]")) {
            int sourceIndex = remainder.indexOf(" source:");
            String className = sourceIndex > 0 ? remainder.substring(0, sourceIndex).trim() : remainder;
            String origin = sourceIndex > 0 ? remainder.substring(sourceIndex + " source:".length()).trim() : null;
            return new RuntimeEvent(true, normalizeRuntimeName(className), origin);
        }
        if (line.contains("[class,unload]")) {
            int spaceIndex = remainder.indexOf(' ');
            String className = spaceIndex > 0 ? remainder.substring(0, spaceIndex).trim() : remainder;
            return new RuntimeEvent(false, normalizeRuntimeName(className), null);
        }
        return null;
    }

    private Map<String, HistogramEntry> readClassHistogram(File classHistogram) throws IOException {
        Map<String, HistogramEntry> entries = new LinkedHashMap<>();
        if (classHistogram == null || !classHistogram.isFile()) {
            return entries;
        }
        for (String line : Files.readAllLines(classHistogram.toPath())) {
            Matcher matcher = HISTOGRAM_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String rawClassName = matcher.group(4).trim();
            if ("<total>".equals(rawClassName)) {
                continue;
            }
            String className = normalizeRuntimeName(rawClassName);
            HistogramEntry entry = new HistogramEntry(
                Long.parseLong(matcher.group(2)),
                Long.parseLong(matcher.group(3))
            );
            entries.merge(className, entry, (left, right) -> new HistogramEntry(
                left.instances() + right.instances(),
                left.bytes() + right.bytes()
            ));
        }
        return entries;
    }

    private static String normalizeRuntimeName(String className) {
        if (className == null || className.isBlank()) {
            return "";
        }
        String normalized = className.trim();
        int moduleIndex = normalized.indexOf(" (");
        if (moduleIndex > 0) {
            normalized = normalized.substring(0, moduleIndex);
        }
        if (normalized.startsWith("[L") && normalized.endsWith(";")) {
            normalized = normalized.substring(2, normalized.length() - 1) + "[]";
        }
        return normalized.replace('/', '.');
    }

    private record RuntimeEvent(boolean load, String className, String origin) {
    }

    private record RuntimeLoadData(
        boolean hasData,
        Set<String> loadedClasses,
        Set<String> unloadedClasses,
        Map<String, String> origins
    ) {
        static RuntimeLoadData empty() {
            return new RuntimeLoadData(false, Set.of(), Set.of(), Map.of());
        }
    }

    private record HistogramEntry(long instances, long bytes) {
    }
}

