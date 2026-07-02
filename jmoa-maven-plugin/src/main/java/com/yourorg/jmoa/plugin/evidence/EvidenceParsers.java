package com.yourorg.jmoa.plugin.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.CdsMode;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceCapture;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.HeapInfo;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.LaunchMode;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.MemoryMetrics;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.NmtSummary;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RuntimePolicy;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RuntimeVerificationGate;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.SmapsRegionSummary;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.Variant;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.WorkloadResult;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RunManifest;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.ClassHistogramSummary;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EvidenceParsers {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern KB_LINE = Pattern.compile("^([A-Za-z_]+):\\s+(\\d+)\\s+kB.*$");
    private static final Pattern HISTOGRAM_LINE =
        Pattern.compile("^\\s*(\\d+):\\s+(\\d+)\\s+(\\d+)\\s+(.+?)\\s*$");
    private static final Pattern NMT_CATEGORY =
        Pattern.compile("^\\s*-\\s+(.+?)\\s+\\(reserved=(\\d+)KB,\\s+committed=(\\d+)KB.*$");
    private static final Pattern NMT_TOTAL =
        Pattern.compile("^\\s*Total:\\s+reserved=(\\d+)KB,\\s+committed=(\\d+)KB.*$");
    private static final Pattern HEAP_TOTAL_USED =
        Pattern.compile(".*total\\s+(\\d+)K,\\s+used\\s+(\\d+)K.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEAP_REGION =
        Pattern.compile(".*\\s+(\\d+)K,\\s+(\\d+)K\\s+used.*", Pattern.CASE_INSENSITIVE);

    public List<File> runDirectories(File inputDirectory) {
        if (inputDirectory == null || !inputDirectory.isDirectory()) {
            return List.of();
        }
        File[] children = inputDirectory.listFiles(File::isDirectory);
        if (children == null || children.length == 0) {
            return List.of(inputDirectory);
        }
        List<File> runs = new ArrayList<>();
        for (File child : children) {
            if (looksLikeRunDirectory(child)) {
                runs.add(child);
            }
        }
        if (runs.isEmpty() && looksLikeRunDirectory(inputDirectory)) {
            runs.add(inputDirectory);
        }
        runs.sort(Comparator.comparing(File::getName));
        return runs;
    }

    public boolean looksLikeRunDirectory(File directory) {
        return find(directory, "run-manifest.json", "manifest.json", "smaps_rollup", "smaps_rollup.txt",
            "smaps-rollup.txt", "memory.current", "workload.json", "workload-result.json").isPresent();
    }

    public RunManifest parseManifest(File runDirectory) throws IOException {
        Optional<File> manifestFile = find(runDirectory, "run-manifest.json", "manifest.json");
        if (manifestFile.isPresent()) {
            JsonNode json = MAPPER.readTree(manifestFile.get());
            return new RunManifest(
                text(json, "runId", runDirectory.getName()),
                intValue(json, "pairIndex", inferPairIndex(runDirectory.getName())),
                enumValue(Variant.class, text(json, "variant", inferVariant(runDirectory.getName()).name()), Variant.UNKNOWN),
                text(json, "service", null),
                text(json, "phase", null),
                text(json, "artifactSha256", null),
                text(json, "expectedArtifactSha256", null),
                text(json, "imageId", null),
                text(json, "containerId", null),
                json.has("pid") ? json.path("pid").asInt() : null,
                enumValue(LaunchMode.class, text(json, "launchMode", "UNKNOWN"), LaunchMode.UNKNOWN),
                enumValue(RuntimePolicy.class, text(json, "runtimePolicy", "UNKNOWN"), RuntimePolicy.UNKNOWN),
                enumValue(CdsMode.class, text(json, "cdsMode", "UNKNOWN"), CdsMode.UNKNOWN),
                boolValue(json, "javaagentPresent", false),
                text(json, "mallocArenaMax", null),
                text(json, "javaVersion", null),
                text(json, "workloadId", null),
                text(json, "timestampStart", null),
                text(json, "timestampPost", null),
                boolValue(json, "classLoadLoggingEnabled", hasFile(runDirectory, "classload.log", "class-load.log")),
                boolValue(json, "jfrEnabled", hasFileExtension(runDirectory, ".jfr")),
                text(json, "nmtMode", null),
                boolValue(json, "gcRunBeforeCapture", false)
            );
        }
        return new RunManifest(
            runDirectory.getName(),
            inferPairIndex(runDirectory.getName()),
            inferVariant(runDirectory.getName()),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            LaunchMode.UNKNOWN,
            RuntimePolicy.UNKNOWN,
            CdsMode.UNKNOWN,
            false,
            null,
            null,
            null,
            null,
            null,
            hasFile(runDirectory, "classload.log", "class-load.log"),
            hasFileExtension(runDirectory, ".jfr"),
            null,
            false
        );
    }

    public EvidenceCapture discoverCapture(String runId, File runDirectory) {
        return new EvidenceCapture(
            runId,
            "post-workload",
            path(find(runDirectory, "smaps_rollup", "smaps_rollup.txt", "smaps-rollup.txt", "smaps-rollup")),
            path(find(runDirectory, "smaps", "smaps.txt", "smaps-full.txt", "smaps_full.txt")),
            path(find(runDirectory, "nmt-summary.txt", "nmt_summary.txt", "VM.native_memory.summary.txt", "native-memory-summary.txt")),
            path(find(runDirectory, "heap-info.txt", "GC.heap_info.txt", "heap_info.txt")),
            path(find(runDirectory, "class-histogram.txt", "GC.class_histogram.txt", "class_histogram.txt")),
            path(find(runDirectory, "metaspace.txt", "VM.metaspace.txt")),
            path(find(runDirectory, "classload.log", "class-load.log", "jmoa-classload.log")),
            path(find(runDirectory, "workload.json", "workload-result.json", "workload-result.txt")),
            path(find(runDirectory, "memory.current", "memory-current.txt")),
            path(find(runDirectory, "runtime-verification.json", "jmoa-runtime-verification.json")),
            path(find(runDirectory, "adapter-validation.json", "adapter-reference-validation.json"))
        );
    }

    public MemoryMetrics parseMemory(EvidenceCapture capture) throws IOException {
        Map<String, Long> rollup = parseKbFile(file(capture.smapsRollup()));
        SmapsRegionSummary regions = parseSmapsRegions(file(capture.smapsFull()));
        long memoryCurrent = parseLongFile(file(capture.memoryCurrent()));
        return new MemoryMetrics(
            rollup.getOrDefault("Rss", 0L),
            rollup.getOrDefault("Pss", 0L),
            rollup.getOrDefault("Private_Dirty", 0L),
            rollup.getOrDefault("Private_Clean", 0L),
            rollup.getOrDefault("Shared_Clean", 0L),
            rollup.getOrDefault("Shared_Dirty", 0L),
            memoryCurrent,
            regions.pssKbByCategory().getOrDefault("heap", 0L),
            regions.privateDirtyKbByCategory().getOrDefault("heap", 0L),
            regions.pssKbByCategory().getOrDefault("anonymous_rw", 0L),
            regions.privateDirtyKbByCategory().getOrDefault("anonymous_rw", 0L),
            regions.pssKbByCategory().getOrDefault("anonymous_executable_code", 0L),
            regions.pssKbByCategory().getOrDefault("native_library", 0L),
            regions.pssKbByCategory().getOrDefault("mapped_file", 0L),
            regions.pssKbByCategory().getOrDefault("stack", 0L)
        );
    }

    public SmapsRegionSummary parseSmapsRegions(File smaps) throws IOException {
        if (smaps == null || !smaps.isFile()) {
            return new SmapsRegionSummary(Map.of(), Map.of());
        }
        Map<String, Long> pss = new LinkedHashMap<>();
        Map<String, Long> dirty = new LinkedHashMap<>();
        String category = "anonymous_other";
        for (String line : Files.readAllLines(smaps.toPath(), StandardCharsets.UTF_8)) {
            if (isSmapsHeader(line)) {
                category = categoryForSmapsHeader(line);
                continue;
            }
            Matcher matcher = KB_LINE.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }
            String key = matcher.group(1);
            long value = Long.parseLong(matcher.group(2));
            if ("Pss".equals(key)) {
                pss.merge(category, value, Long::sum);
            } else if ("Private_Dirty".equals(key)) {
                dirty.merge(category, value, Long::sum);
            }
        }
        return new SmapsRegionSummary(pss, dirty);
    }

    public NmtSummary parseNmtSummary(EvidenceCapture capture) throws IOException {
        File file = file(capture.nmtSummary());
        if (file == null || !file.isFile()) {
            return new NmtSummary(0, 0, 0, 0, 0, 0, 0, 0);
        }
        long total = 0;
        long heap = 0;
        long metaspace = 0;
        long clazz = 0;
        long code = 0;
        long arena = 0;
        long mallocKb = 0;
        long mallocCount = 0;
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            Matcher totalMatcher = NMT_TOTAL.matcher(line);
            if (totalMatcher.matches()) {
                total = Long.parseLong(totalMatcher.group(2));
            }
            Matcher categoryMatcher = NMT_CATEGORY.matcher(line);
            if (!categoryMatcher.matches()) {
                continue;
            }
            String category = categoryMatcher.group(1).trim();
            long committed = Long.parseLong(categoryMatcher.group(3));
            if (category.startsWith("Java Heap")) heap = committed;
            if (category.startsWith("Metaspace")) metaspace = committed;
            if (category.equals("Class")) clazz = committed;
            if (category.equals("Code")) code = committed;
            if (category.contains("Arena Chunk")) arena = committed;
            Matcher mallocMatcher = Pattern.compile("malloc=(\\d+)KB\\s+#(\\d+)").matcher(line);
            if (mallocMatcher.find()) {
                mallocKb += Long.parseLong(mallocMatcher.group(1));
                mallocCount += Long.parseLong(mallocMatcher.group(2));
            }
        }
        return new NmtSummary(total, heap, metaspace, clazz, code, arena, mallocKb, mallocCount);
    }

    public HeapInfo parseHeapInfo(EvidenceCapture capture) throws IOException {
        File file = file(capture.heapInfo());
        if (file == null || !file.isFile()) {
            return new HeapInfo(0, 0);
        }
        long committed = 0;
        long used = 0;
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            Matcher totalUsed = HEAP_TOTAL_USED.matcher(line);
            if (totalUsed.matches()) {
                committed = Math.max(committed, Long.parseLong(totalUsed.group(1)));
                used = Math.max(used, Long.parseLong(totalUsed.group(2)));
                continue;
            }
            Matcher region = HEAP_REGION.matcher(line);
            if (region.matches()) {
                committed += Long.parseLong(region.group(1));
                used += Long.parseLong(region.group(2));
            }
        }
        return new HeapInfo(committed, used);
    }

    public ClassHistogramSummary parseClassHistogram(EvidenceCapture capture) throws IOException {
        File file = file(capture.classHistogram());
        if (file == null || !file.isFile()) {
            return new ClassHistogramSummary(0, 0, 0);
        }
        long instances = 0;
        long bytes = 0;
        int classes = 0;
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            Matcher matcher = HISTOGRAM_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String className = matcher.group(4).trim();
            if ("<total>".equals(className)) {
                instances = Long.parseLong(matcher.group(2));
                bytes = Long.parseLong(matcher.group(3));
                continue;
            }
            classes++;
            instances += Long.parseLong(matcher.group(2));
            bytes += Long.parseLong(matcher.group(3));
        }
        return new ClassHistogramSummary(instances, bytes, classes);
    }

    public WorkloadResult parseWorkload(EvidenceCapture capture) throws IOException {
        File file = file(capture.workloadResult());
        if (file == null || !file.isFile()) {
            return new WorkloadResult(null, 0, 0, false);
        }
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        if (text.trim().startsWith("{")) {
            JsonNode json = MAPPER.readTree(text);
            return new WorkloadResult(
                text(json, "health", text(json, "status", null)),
                intValue(json, "errors", intValue(json, "errorCount", 0)),
                intValue(json, "requests", intValue(json, "requestCount", 0)),
                true
            );
        }
        int errors = containsIgnoreCase(text, "error") && !containsIgnoreCase(text, "0 errors") ? 1 : 0;
        String health = containsIgnoreCase(text, "UP") ? "UP" : null;
        return new WorkloadResult(health, errors, 0, true);
    }

    public RuntimeVerificationGate parseRuntimeVerification(EvidenceCapture capture) throws IOException {
        JsonNode runtime = readOptionalJson(file(capture.runtimeVerification()));
        JsonNode adapter = readOptionalJson(file(capture.adapterValidation()));
        boolean present = runtime != null || adapter != null;
        return new RuntimeVerificationGate(
            present,
            boolValue(runtime, "dynamicOriginProven", boolValue(runtime, "dynamicOriginsVerified", false)),
            boolValue(runtime, "optimizedOriginsVerified", boolValue(runtime, "optimizedJarsLoaded", false)),
            boolValue(runtime, "runtimeLibPresent", false),
            boolValue(runtime, "originalJarShadowing", boolValue(runtime, "newDuplicateShadowingRisk", false)),
            intValue(adapter, "missingAdapterRefs", intValue(adapter, "missingReferences", 0)),
            text(runtime, "launchMode", null)
        );
    }

    private Map<String, Long> parseKbFile(File file) throws IOException {
        Map<String, Long> values = new LinkedHashMap<>();
        if (file == null || !file.isFile()) {
            return values;
        }
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            Matcher matcher = KB_LINE.matcher(line.trim());
            if (matcher.matches()) {
                values.put(matcher.group(1), Long.parseLong(matcher.group(2)));
            }
        }
        return values;
    }

    private long parseLongFile(File file) throws IOException {
        if (file == null || !file.isFile()) {
            return 0;
        }
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
        Matcher matcher = Pattern.compile("(\\d+)").matcher(text);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0;
    }

    Optional<File> find(File directory, String... names) {
        if (directory == null || !directory.isDirectory()) {
            return Optional.empty();
        }
        for (String name : names) {
            File direct = new File(directory, name);
            if (direct.isFile()) {
                return Optional.of(direct);
            }
        }
        try (var stream = Files.walk(directory.toPath(), 3)) {
            return stream
                .filter(Files::isRegularFile)
                .map(java.nio.file.Path::toFile)
                .filter(file -> {
                    String fileName = file.getName();
                    for (String name : names) {
                        if (fileName.equals(name)) {
                            return true;
                        }
                    }
                    return false;
                })
                .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String path(Optional<File> file) {
        return file.map(File::getAbsolutePath).orElse(null);
    }

    private static File file(String path) {
        return path == null || path.isBlank() ? null : new File(path);
    }

    private static boolean hasFile(File directory, String... names) {
        for (String name : names) {
            if (new File(directory, name).isFile()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFileExtension(File directory, String extension) {
        File[] files = directory == null ? null : directory.listFiles();
        if (files == null) {
            return false;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSmapsHeader(String line) {
        return line != null && line.matches("^[0-9a-fA-F]+-[0-9a-fA-F]+\\s+.*$");
    }

    private static String categoryForSmapsHeader(String header) {
        String lower = header.toLowerCase(Locale.ROOT);
        if (lower.contains("[heap]")) return "heap";
        if (lower.contains("[stack")) return "stack";
        if (lower.contains(".so")) return "native_library";
        if (lower.contains("[vdso]") || lower.contains("[vvar]") || lower.contains("[vsyscall]")) return "special_mapping";
        String[] parts = header.trim().split("\\s+", 6);
        String perms = parts.length > 1 ? parts[1] : "";
        String path = parts.length > 5 ? parts[5] : "";
        if (perms.contains("x") && (path.isBlank() || path.startsWith("["))) return "anonymous_executable_code";
        if (perms.startsWith("rw") && (path.isBlank() || path.startsWith("["))) return "anonymous_rw";
        if (!path.isBlank() && !path.startsWith("[")) return "mapped_file";
        return "anonymous_other";
    }

    private static int inferPairIndex(String name) {
        Matcher matcher = Pattern.compile("(\\d+)").matcher(name == null ? "" : name);
        int pair = 1;
        while (matcher.find()) {
            pair = Integer.parseInt(matcher.group(1));
        }
        return pair;
    }

    private static Variant inferVariant(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.matches(".*(^|[-_])b(aseline)?\\d*.*") || lower.contains("baseline")) return Variant.BASELINE;
        if (lower.matches(".*(^|[-_])(c|p|candidate|optimized)\\d*.*")
            || lower.contains("candidate")
            || lower.contains("optimized")) return Variant.CANDIDATE;
        if (lower.contains("diagnostic")) return Variant.DIAGNOSTIC;
        return Variant.UNKNOWN;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String text(JsonNode json, String field, String fallback) {
        if (json == null || !json.has(field) || json.path(field).isNull()) {
            return fallback;
        }
        return json.path(field).asText();
    }

    private static int intValue(JsonNode json, String field, int fallback) {
        if (json == null || !json.has(field) || json.path(field).isNull()) {
            return fallback;
        }
        return json.path(field).asInt(fallback);
    }

    private static boolean boolValue(JsonNode json, String field, boolean fallback) {
        if (json == null || !json.has(field) || json.path(field).isNull()) {
            return fallback;
        }
        return json.path(field).asBoolean(fallback);
    }

    private static JsonNode readOptionalJson(File file) throws IOException {
        if (file == null || !file.isFile()) {
            return null;
        }
        return MAPPER.readTree(file);
    }

    private static boolean containsIgnoreCase(String text, String token) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }
}
