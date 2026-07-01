package com.yourorg.jmoa.plugin.generated;

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

public final class GeneratedClassRuntimeAttributor {

    private static final String METADATA_VERSION = "v2-a2-runtime-attribution";
    private static final Pattern HISTOGRAM_LINE =
        Pattern.compile("^\\s*(\\d+):\\s+(\\d+)\\s+(\\d+)\\s+(.+?)\\s*$");

    private final GeneratedClassClassifier classifier;

    public GeneratedClassRuntimeAttributor() {
        this(new GeneratedClassClassifier());
    }

    GeneratedClassRuntimeAttributor(GeneratedClassClassifier classifier) {
        this.classifier = classifier;
    }

    public GeneratedClassRuntimeAttribution attribute(
        GeneratedClassInventory inventory,
        File classLoadLog,
        File classHistogram
    ) throws IOException {
        Map<String, GeneratedClassRecord> staticByClass = new LinkedHashMap<>();
        if (inventory != null) {
            for (GeneratedClassRecord record : inventory.classes()) {
                staticByClass.put(record.className(), record);
            }
        }
        RuntimeLoadData loadData = readClassLoadLog(classLoadLog);
        Map<String, GeneratedClassHistogramEntry> histogram = readClassHistogram(classHistogram);

        Set<String> classNames = new LinkedHashSet<>();
        for (GeneratedClassRecord record : staticByClass.values()) {
            if (record.generatedLike()) {
                classNames.add(record.className());
            }
        }
        for (String className : loadData.loadedClasses()) {
            GeneratedClassClassification classification = classifier.classifyRuntimeClassName(className);
            if (classification.generatedLike()) {
                classNames.add(className);
            }
        }
        for (String className : histogram.keySet()) {
            GeneratedClassClassification classification = classifier.classifyRuntimeClassName(className);
            if (classification.generatedLike()) {
                classNames.add(className);
            }
        }

        List<GeneratedClassRuntimeClassRecord> classRecords = new ArrayList<>();
        for (String className : classNames) {
            GeneratedClassRecord staticRecord = staticByClass.get(className);
            GeneratedClassClassification runtimeClassification = classifier.classifyRuntimeClassName(className);
            GeneratedClassFamily family = staticRecord == null ? runtimeClassification.family() : staticRecord.family();
            GeneratedClassHistogramEntry histogramEntry = histogram.get(className);
            boolean loaded = loadData.loadedClasses().contains(className);
            boolean unloaded = loadData.unloadedClasses().contains(className);
            long histogramInstances = histogramEntry == null ? 0 : histogramEntry.instances();
            long histogramBytes = histogramEntry == null ? 0 : histogramEntry.bytes();
            classRecords.add(new GeneratedClassRuntimeClassRecord(
                className,
                family,
                staticRecord != null,
                loaded,
                unloaded,
                loadData.origins().get(className),
                histogramInstances,
                histogramBytes,
                survivalFor(loaded, unloaded, histogramInstances)
            ));
        }

        classRecords = classRecords.stream()
            .sorted(Comparator
                .comparing(GeneratedClassRuntimeClassRecord::family)
                .thenComparing(GeneratedClassRuntimeClassRecord::histogramBytes, Comparator.reverseOrder())
                .thenComparing(GeneratedClassRuntimeClassRecord::className))
            .toList();

        List<GeneratedClassFamilyRuntimeAttribution> families = buildFamilyAttribution(
            inventory,
            classRecords
        );
        int generatedRuntimeLoaded = (int) classRecords.stream()
            .filter(GeneratedClassRuntimeClassRecord::runtimeLoaded)
            .count();
        return new GeneratedClassRuntimeAttribution(
            METADATA_VERSION,
            Instant.now().toString(),
            classLoadLog == null ? null : classLoadLog.getAbsolutePath(),
            classHistogram == null ? null : classHistogram.getAbsolutePath(),
            loadData.loadedClasses().size(),
            generatedRuntimeLoaded,
            loadData.unloadedClasses().size(),
            histogram.values().stream().mapToLong(GeneratedClassHistogramEntry::bytes).sum(),
            families,
            classRecords
        );
    }

    private List<GeneratedClassFamilyRuntimeAttribution> buildFamilyAttribution(
        GeneratedClassInventory inventory,
        List<GeneratedClassRuntimeClassRecord> classRecords
    ) {
        Map<GeneratedClassFamily, List<GeneratedClassRuntimeClassRecord>> byFamily = new EnumMap<>(GeneratedClassFamily.class);
        for (GeneratedClassFamily family : GeneratedClassFamily.values()) {
            byFamily.put(family, new ArrayList<>());
        }
        for (GeneratedClassRuntimeClassRecord record : classRecords) {
            byFamily.computeIfAbsent(record.family(), ignored -> new ArrayList<>()).add(record);
        }

        Map<GeneratedClassFamily, Long> staticBytes = new EnumMap<>(GeneratedClassFamily.class);
        Map<GeneratedClassFamily, Integer> staticCounts = new EnumMap<>(GeneratedClassFamily.class);
        if (inventory != null) {
            for (GeneratedClassRecord record : inventory.classes()) {
                if (record.generatedLike()) {
                    staticBytes.merge(record.family(), record.classFileBytes(), Long::sum);
                    staticCounts.merge(record.family(), 1, Integer::sum);
                }
            }
        }

        List<GeneratedClassFamilyRuntimeAttribution> results = new ArrayList<>();
        for (GeneratedClassFamily family : GeneratedClassFamily.values()) {
            List<GeneratedClassRuntimeClassRecord> records = byFamily.getOrDefault(family, List.of());
            int staticClassCount = staticCounts.getOrDefault(family, 0);
            if (staticClassCount == 0 && records.isEmpty()) {
                continue;
            }
            int runtimeLoadedCount = (int) records.stream()
                .filter(GeneratedClassRuntimeClassRecord::runtimeLoaded)
                .count();
            int runtimeUnloadedCount = (int) records.stream()
                .filter(GeneratedClassRuntimeClassRecord::runtimeUnloaded)
                .count();
            int staticAndLoaded = (int) records.stream()
                .filter(GeneratedClassRuntimeClassRecord::staticInventoryPresent)
                .filter(GeneratedClassRuntimeClassRecord::runtimeLoaded)
                .count();
            int runtimeOnlyLoaded = (int) records.stream()
                .filter(record -> !record.staticInventoryPresent())
                .filter(GeneratedClassRuntimeClassRecord::runtimeLoaded)
                .count();
            int histogramClassCount = (int) records.stream()
                .filter(record -> record.histogramInstances() > 0)
                .count();
            long histogramInstances = records.stream()
                .mapToLong(GeneratedClassRuntimeClassRecord::histogramInstances)
                .sum();
            long histogramBytes = records.stream()
                .mapToLong(GeneratedClassRuntimeClassRecord::histogramBytes)
                .sum();
            results.add(new GeneratedClassFamilyRuntimeAttribution(
                family,
                staticClassCount,
                staticBytes.getOrDefault(family, 0L),
                runtimeLoadedCount,
                runtimeUnloadedCount,
                staticAndLoaded,
                runtimeOnlyLoaded,
                histogramClassCount,
                histogramInstances,
                histogramBytes,
                survivalForFamily(runtimeLoadedCount, runtimeUnloadedCount, histogramClassCount),
                priorityFor(family, runtimeLoadedCount, histogramBytes),
                records.stream()
                    .sorted(Comparator
                        .comparing(GeneratedClassRuntimeClassRecord::histogramBytes, Comparator.reverseOrder())
                        .thenComparing(GeneratedClassRuntimeClassRecord::className))
                    .limit(20)
                    .toList()
            ));
        }
        return results;
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
        return new RuntimeLoadData(loaded, unloaded, origins);
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
            return new RuntimeEvent(true, GeneratedClassClassifier.normalizeRuntimeName(className), origin);
        }
        if (line.contains("[class,unload]")) {
            int spaceIndex = remainder.indexOf(' ');
            String className = spaceIndex > 0 ? remainder.substring(0, spaceIndex).trim() : remainder;
            return new RuntimeEvent(false, GeneratedClassClassifier.normalizeRuntimeName(className), null);
        }
        return null;
    }

    private Map<String, GeneratedClassHistogramEntry> readClassHistogram(File classHistogram) throws IOException {
        Map<String, GeneratedClassHistogramEntry> entries = new LinkedHashMap<>();
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
            String className = GeneratedClassClassifier.normalizeRuntimeName(rawClassName);
            GeneratedClassHistogramEntry entry = new GeneratedClassHistogramEntry(
                Integer.parseInt(matcher.group(1)),
                Long.parseLong(matcher.group(2)),
                Long.parseLong(matcher.group(3)),
                className,
                rawClassName
            );
            entries.merge(className, entry, (left, right) -> new GeneratedClassHistogramEntry(
                Math.min(left.rank(), right.rank()),
                left.instances() + right.instances(),
                left.bytes() + right.bytes(),
                left.className(),
                left.rawClassName()
            ));
        }
        return entries;
    }

    private static String survivalFor(boolean loaded, boolean unloaded, long histogramInstances) {
        if (histogramInstances > 0) {
            return "HAS_LIVE_INSTANCES";
        }
        if (loaded && !unloaded) {
            return "LOADED_NO_HISTOGRAM_INSTANCES";
        }
        if (loaded) {
            return "LOADED_THEN_UNLOADED";
        }
        return "STATIC_ONLY";
    }

    private static String survivalForFamily(int loaded, int unloaded, int histogramClassCount) {
        if (histogramClassCount > 0) {
            return "SURVIVES_WORKLOAD";
        }
        if (loaded > 0 && unloaded == 0) {
            return "LOADED_NO_INSTANCES";
        }
        if (loaded > 0) {
            return "TRANSIENT_OR_UNLOADED";
        }
        return "STATIC_ONLY";
    }

    private static String priorityFor(GeneratedClassFamily family, int loaded, long histogramBytes) {
        if (loaded == 0 && histogramBytes == 0) {
            return "LOW";
        }
        if (family == GeneratedClassFamily.SPRING_CGLIB
            || family == GeneratedClassFamily.JDK_PROXY
            || family == GeneratedClassFamily.BYTEBUDDY
            || family == GeneratedClassFamily.HIBERNATE_PROXY) {
            return "REPORT_ONLY_UNSAFE";
        }
        if (histogramBytes >= 1024 * 1024 || loaded >= 100) {
            return "HIGH";
        }
        if (histogramBytes > 0 || loaded >= 10) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private record RuntimeEvent(boolean load, String className, String origin) {
    }

    private record RuntimeLoadData(Set<String> loadedClasses, Set<String> unloadedClasses, Map<String, String> origins) {
        static RuntimeLoadData empty() {
            return new RuntimeLoadData(Set.of(), Set.of(), Map.of());
        }
    }
}
