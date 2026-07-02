package com.yourorg.jmoa.plugin.attribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.AttributionConfig;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.BytecodeRuntimeAttribution;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.CausalHypothesis;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.CausalHypothesisType;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.ClassMetaspaceAttribution;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.Confidence;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.GeneratedFamilyAttribution;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.GeneratedFamilySummary;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.HeapObjectAttribution;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.MemoryAttributionReport;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.MemoryCategoryDelta;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.ObjectFamilyDelta;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.SmapsNmtReconciliation;
import com.yourorg.jmoa.plugin.evidence.EvidenceEngine;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.ClassHistogramSummary;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.ConfirmationReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceAnalysisReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceConfig;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.MemoryMetrics;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.NmtSummary;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.PairResult;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RunEvidence;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.Verdict;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MemoryAttributionEngine {

    private static final long ONE_MB_KB = 1024;
    private static final long ONE_MB_BYTES = 1024L * 1024L;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern HISTOGRAM_LINE =
        Pattern.compile("^\\s*(\\d+):\\s+(\\d+)\\s+(\\d+)\\s+(.+?)\\s*$");

    private final EvidenceEngine evidenceEngine;

    public MemoryAttributionEngine() {
        this(new EvidenceEngine());
    }

    MemoryAttributionEngine(EvidenceEngine evidenceEngine) {
        this.evidenceEngine = evidenceEngine;
    }

    public MemoryAttributionReport analyze(
        File inputDirectory,
        EvidenceConfig evidenceConfig,
        AttributionConfig attributionConfig
    ) throws IOException {
        EvidenceAnalysisReport evidence = evidenceEngine.analyze(inputDirectory, evidenceConfig);
        boolean v2cValid = evidence.validation().invalidRuns() == 0 && evidence.confirmation().pairs() > 0;
        if (attributionConfig.requireV2cValid() && !v2cValid) {
            return invalidReport(evidence);
        }

        List<PairRuns> pairs = pairRuns(evidence);
        List<MemoryCategoryDelta> categories = categoryDeltas(pairs);
        HeapObjectAttribution heap = heapObjectAttribution(pairs);
        SmapsNmtReconciliation reconciliation = smapsNmtReconciliation(categories, heap);
        ClassMetaspaceAttribution classMeta = classMetaspaceAttribution(pairs);
        GeneratedFamilyAttribution generatedFamilies = generatedFamilyAttribution(attributionConfig.generatedClassReport());
        BytecodeRuntimeAttribution bytecodeRuntime = bytecodeRuntimeAttribution(attributionConfig.bytecodeRuntimeCorrelationReport());
        List<CausalHypothesis> hypotheses = causalHypotheses(evidence.confirmation(), reconciliation, heap, classMeta);

        return new MemoryAttributionReport(
            "v2-d-memory-attribution",
            Instant.now().toString(),
            "V2-C Evidence Engine",
            evidence.verdict(),
            v2cValid,
            firstNonBlank(pairs.stream().map(p -> p.baseline().manifest().service()).toList()),
            firstNonBlank(pairs.stream().map(p -> p.baseline().manifest().phase()).toList()),
            categories,
            reconciliation,
            heap,
            classMeta,
            generatedFamilies,
            bytecodeRuntime,
            hypotheses,
            List.of(
                "V2-D is report-only and does not mutate bytecode.",
                "JFR, async-profiler, and JOL diagnostics are not enabled by this report.",
                "Causality is hypothesis-ranked from paired evidence; reducer admission still requires V2-C confirmation."
            )
        );
    }

    private static MemoryAttributionReport invalidReport(EvidenceAnalysisReport evidence) {
        return new MemoryAttributionReport(
            "v2-d-memory-attribution",
            Instant.now().toString(),
            "V2-C Evidence Engine",
            evidence.verdict(),
            false,
            null,
            null,
            List.of(),
            new SmapsNmtReconciliation(0, 0, 0, 0, "INVALID_EVIDENCE", List.of("V2-C evidence is invalid.")),
            new HeapObjectAttribution(0, 0, 0, 0, 0, "INVALID_EVIDENCE", List.of()),
            new ClassMetaspaceAttribution(0, 0, 0, 0, "INVALID_EVIDENCE"),
            missingGeneratedFamilyAttribution(),
            missingBytecodeRuntimeAttribution(),
            List.of(new CausalHypothesis(
                CausalHypothesisType.UNKNOWN,
                Confidence.HIGH,
                List.of("V2-C validation did not produce valid paired evidence."),
                List.of(),
                "Fix evidence validity before running memory attribution."
            )),
            List.of("V2-D requires V2-C-valid evidence by default.")
        );
    }

    private static GeneratedFamilyAttribution generatedFamilyAttribution(File report) throws IOException {
        if (report == null || !report.isFile()) {
            return missingGeneratedFamilyAttribution();
        }
        JsonNode root = MAPPER.readTree(report);
        List<GeneratedFamilySummary> families = new ArrayList<>();
        for (JsonNode family : root.path("familyBreakdown")) {
            families.add(new GeneratedFamilySummary(
                family.path("family").asText("UNKNOWN"),
                family.path("classCount").asLong(0),
                family.path("generatedLikeClassCount").asLong(0),
                family.path("classFileBytes").asLong(0),
                0,
                0,
                0
            ));
        }
        families.sort(Comparator.comparingLong(GeneratedFamilySummary::classfileBytes).reversed());
        return new GeneratedFamilyAttribution(
            true,
            root.path("metadataVersion").asText("unknown"),
            root.path("totalClassesScanned").asLong(0),
            root.path("generatedLikeClasses").asLong(0),
            families.stream().limit(20).toList(),
            "V2-A generated-family inventory was provided. Family cost remains report-only and not transform-safe by default."
        );
    }

    private static GeneratedFamilyAttribution missingGeneratedFamilyAttribution() {
        return new GeneratedFamilyAttribution(
            false,
            null,
            0,
            0,
            List.of(),
            "V2-A generated-family report was not provided."
        );
    }

    private static BytecodeRuntimeAttribution bytecodeRuntimeAttribution(File report) throws IOException {
        if (report == null || !report.isFile()) {
            return missingBytecodeRuntimeAttribution();
        }
        JsonNode root = MAPPER.readTree(report);
        List<GeneratedFamilySummary> families = new ArrayList<>();
        for (JsonNode family : root.path("families")) {
            families.add(new GeneratedFamilySummary(
                family.path("generatedFamily").asText("UNKNOWN"),
                family.path("staticClassCount").asLong(0),
                0,
                family.path("classfileBytes").asLong(0),
                family.path("runtimeLoadedClassCount").asLong(0),
                family.path("workloadSurvivorClassCount").asLong(0),
                family.path("histogramBytes").asLong(0)
            ));
        }
        long near64k = 0;
        long near64kLoaded = 0;
        for (JsonNode method : root.path("methods")) {
            long codeLength = method.path("codeLength").asLong(0);
            String threshold = method.path("threshold").asText("");
            if (codeLength >= 49152 || "DANGER".equals(threshold) || "CRITICAL".equals(threshold)) {
                near64k++;
                if (method.path("runtimeLoadedClass").asBoolean(false)) {
                    near64kLoaded++;
                }
            }
        }
        if (near64k == 0) {
            for (JsonNode clazz : root.path("classes")) {
                for (JsonNode ignored : clazz.path("near64kMethods")) {
                    near64k++;
                    if (clazz.path("runtimeLoaded").asBoolean(false)) {
                        near64kLoaded++;
                    }
                }
            }
        }
        String interpretation = near64kLoaded == 0
            ? "V2-B runtime correlation was provided; near-64KB methods remain static risk in this evidence."
            : "V2-B runtime correlation was provided; at least one near-64KB method belongs to a loaded class.";
        return new BytecodeRuntimeAttribution(
            true,
            root.path("metadataVersion").asText("unknown"),
            root.path("totalProfileClasses").asLong(0),
            root.path("totalRuntimeLoadedClasses").asLong(0),
            root.path("profileClassesObservedLoaded").asLong(0),
            root.path("profileClassesWithHistogramInstances").asLong(0),
            near64k,
            near64kLoaded,
            families.stream().limit(20).toList(),
            interpretation
        );
    }

    private static BytecodeRuntimeAttribution missingBytecodeRuntimeAttribution() {
        return new BytecodeRuntimeAttribution(
            false,
            null,
            0,
            0,
            0,
            0,
            0,
            0,
            List.of(),
            "V2-B bytecode runtime correlation report was not provided."
        );
    }

    private static List<PairRuns> pairRuns(EvidenceAnalysisReport evidence) {
        Map<String, RunEvidence> byId = new LinkedHashMap<>();
        for (RunEvidence run : evidence.validation().runEvidence()) {
            byId.put(run.manifest().runId(), run);
        }
        List<PairRuns> pairs = new ArrayList<>();
        for (PairResult pair : evidence.confirmation().pairResults()) {
            RunEvidence baseline = byId.get(pair.baselineRunId());
            RunEvidence candidate = byId.get(pair.candidateRunId());
            if (baseline != null && candidate != null && baseline.valid() && candidate.valid()) {
                pairs.add(new PairRuns(pair.pair(), baseline, candidate));
            }
        }
        return pairs;
    }

    private static List<MemoryCategoryDelta> categoryDeltas(List<PairRuns> pairs) {
        List<MemoryCategoryDelta> deltas = new ArrayList<>();
        addDelta(deltas, pairs, "TOTAL_PSS", "KB", p -> p.candidate().memory().pssKb() - p.baseline().memory().pssKb());
        addDelta(deltas, pairs, "PRIVATE_DIRTY", "KB",
            p -> p.candidate().memory().privateDirtyKb() - p.baseline().memory().privateDirtyKb());
        addDelta(deltas, pairs, "CGROUP_MEMORY_CURRENT", "bytes",
            p -> p.candidate().memory().memoryCurrentBytes() - p.baseline().memory().memoryCurrentBytes());
        addDelta(deltas, pairs, "HEAP_PSS", "KB", p -> p.candidate().memory().heapPssKb() - p.baseline().memory().heapPssKb());
        addDelta(deltas, pairs, "HEAP_PRIVATE_DIRTY", "KB",
            p -> p.candidate().memory().heapPrivateDirtyKb() - p.baseline().memory().heapPrivateDirtyKb());
        addDelta(deltas, pairs, "HEAP_USED", "KB", p -> p.candidate().heapInfo().usedKb() - p.baseline().heapInfo().usedKb());
        addDelta(deltas, pairs, "HEAP_COMMITTED", "KB",
            p -> p.candidate().heapInfo().committedKb() - p.baseline().heapInfo().committedKb());
        addDelta(deltas, pairs, "CLASS_HISTOGRAM_BYTES", "bytes",
            p -> p.candidate().classHistogram().bytes() - p.baseline().classHistogram().bytes());
        addDelta(deltas, pairs, "ANONYMOUS_RW_PSS", "KB",
            p -> p.candidate().memory().anonymousRwPssKb() - p.baseline().memory().anonymousRwPssKb());
        addDelta(deltas, pairs, "ANONYMOUS_EXECUTABLE_CODE_PSS", "KB",
            p -> p.candidate().memory().anonymousExecutablePssKb() - p.baseline().memory().anonymousExecutablePssKb());
        addDelta(deltas, pairs, "NATIVE_LIBRARY_PSS", "KB",
            p -> p.candidate().memory().nativeLibraryPssKb() - p.baseline().memory().nativeLibraryPssKb());
        addDelta(deltas, pairs, "MAPPED_FILE_PSS", "KB",
            p -> p.candidate().memory().mappedFilePssKb() - p.baseline().memory().mappedFilePssKb());
        addDelta(deltas, pairs, "STACK_PSS", "KB", p -> p.candidate().memory().stackPssKb() - p.baseline().memory().stackPssKb());
        addDelta(deltas, pairs, "NMT_TOTAL_COMMITTED", "KB",
            p -> p.candidate().nmt().totalCommittedKb() - p.baseline().nmt().totalCommittedKb());
        addDelta(deltas, pairs, "NMT_JAVA_HEAP_COMMITTED", "KB",
            p -> p.candidate().nmt().javaHeapCommittedKb() - p.baseline().nmt().javaHeapCommittedKb());
        addDelta(deltas, pairs, "NMT_METASPACE_COMMITTED", "KB",
            p -> p.candidate().nmt().metaspaceCommittedKb() - p.baseline().nmt().metaspaceCommittedKb());
        addDelta(deltas, pairs, "NMT_CLASS_COMMITTED", "KB",
            p -> p.candidate().nmt().classCommittedKb() - p.baseline().nmt().classCommittedKb());
        addDelta(deltas, pairs, "NMT_CODE_COMMITTED", "KB",
            p -> p.candidate().nmt().codeCommittedKb() - p.baseline().nmt().codeCommittedKb());
        addDelta(deltas, pairs, "NMT_ARENA_CHUNK_COMMITTED", "KB",
            p -> p.candidate().nmt().arenaChunkCommittedKb() - p.baseline().nmt().arenaChunkCommittedKb());
        addDelta(deltas, pairs, "NMT_MALLOC", "KB", p -> p.candidate().nmt().mallocKb() - p.baseline().nmt().mallocKb());
        return deltas;
    }

    private static void addDelta(
        List<MemoryCategoryDelta> output,
        List<PairRuns> pairs,
        String category,
        String unit,
        ToLongFunction<PairRuns> extractor
    ) {
        List<Long> values = pairs.stream().map(p -> extractor.applyAsLong(p)).toList();
        output.add(new MemoryCategoryDelta(category, unit, median(values), values));
    }

    private static SmapsNmtReconciliation smapsNmtReconciliation(
        List<MemoryCategoryDelta> categories,
        HeapObjectAttribution heap
    ) {
        long pss = delta(categories, "TOTAL_PSS");
        long dirty = delta(categories, "PRIVATE_DIRTY");
        long nmt = delta(categories, "NMT_TOTAL_COMMITTED");
        long gap = pss - nmt;
        List<String> reasons = new ArrayList<>();
        String classification;
        if (Math.abs(pss) < ONE_MB_KB) {
            classification = "LOW_SIGNAL";
            reasons.add("PSS delta is below 1 MB.");
        } else if (Math.abs(nmt) >= Math.round(Math.abs(pss) * 0.6d)) {
            classification = "NMT_VISIBLE";
            reasons.add("NMT total committed explains at least 60% of PSS movement.");
        } else {
            classification = "NMT_INVISIBLE_OR_PARTIAL";
            reasons.add("PSS/Private_Dirty movement is larger than NMT total committed movement.");
        }
        if (heap.classification().contains("PAGE_TOUCH")) {
            reasons.add("Heap PSS moved while heap used and histogram bytes stayed near flat.");
        }
        if (Math.abs(delta(categories, "ANONYMOUS_RW_PSS")) >= ONE_MB_KB) {
            reasons.add("anonymous_rw PSS moved by at least 1 MB.");
        }
        return new SmapsNmtReconciliation(pss, dirty, nmt, gap, classification, reasons);
    }

    private static HeapObjectAttribution heapObjectAttribution(List<PairRuns> pairs) throws IOException {
        long heapPss = median(pairs.stream()
            .map(p -> p.candidate().memory().heapPssKb() - p.baseline().memory().heapPssKb()).toList());
        long heapDirty = median(pairs.stream()
            .map(p -> p.candidate().memory().heapPrivateDirtyKb() - p.baseline().memory().heapPrivateDirtyKb()).toList());
        long heapUsed = median(pairs.stream()
            .map(p -> p.candidate().heapInfo().usedKb() - p.baseline().heapInfo().usedKb()).toList());
        long heapCommitted = median(pairs.stream()
            .map(p -> p.candidate().heapInfo().committedKb() - p.baseline().heapInfo().committedKb()).toList());
        long histogramBytes = median(pairs.stream()
            .map(p -> p.candidate().classHistogram().bytes() - p.baseline().classHistogram().bytes()).toList());
        String classification = heapClassification(heapPss, heapUsed, histogramBytes, heapCommitted);
        return new HeapObjectAttribution(
            heapPss,
            heapDirty,
            heapUsed,
            heapCommitted,
            histogramBytes,
            classification,
            objectFamilyDeltas(pairs)
        );
    }

    private static String heapClassification(long heapPss, long heapUsed, long histogramBytes, long heapCommitted) {
        boolean liveFlat = Math.abs(heapUsed) < ONE_MB_KB && Math.abs(histogramBytes) < ONE_MB_BYTES;
        boolean committedFlat = Math.abs(heapCommitted) < ONE_MB_KB;
        if (heapPss >= ONE_MB_KB && liveFlat && committedFlat) {
            return "HEAP_PAGE_TOUCH_GROWTH";
        }
        if (heapPss <= -ONE_MB_KB && liveFlat) {
            return "HEAP_PAGE_TOUCH_REDUCTION";
        }
        if (heapPss >= ONE_MB_KB && (heapUsed >= ONE_MB_KB || histogramBytes >= ONE_MB_BYTES)) {
            return "RETAINED_OBJECT_GROWTH";
        }
        if (heapPss <= -ONE_MB_KB && (heapUsed <= -ONE_MB_KB || histogramBytes <= -ONE_MB_BYTES)) {
            return "RETAINED_OBJECT_REDUCTION";
        }
        return "UNKNOWN";
    }

    private static List<ObjectFamilyDelta> objectFamilyDeltas(List<PairRuns> pairs) throws IOException {
        Map<String, List<Long>> instanceDeltas = new LinkedHashMap<>();
        Map<String, List<Long>> byteDeltas = new LinkedHashMap<>();
        for (PairRuns pair : pairs) {
            Map<String, HistogramFamilyStats> baseline = readHistogramFamilies(pair.baseline());
            Map<String, HistogramFamilyStats> candidate = readHistogramFamilies(pair.candidate());
            Set<String> families = new LinkedHashSet<>();
            families.addAll(baseline.keySet());
            families.addAll(candidate.keySet());
            for (String family : families) {
                HistogramFamilyStats b = baseline.getOrDefault(family, HistogramFamilyStats.ZERO);
                HistogramFamilyStats c = candidate.getOrDefault(family, HistogramFamilyStats.ZERO);
                instanceDeltas.computeIfAbsent(family, ignored -> new ArrayList<>()).add(c.instances() - b.instances());
                byteDeltas.computeIfAbsent(family, ignored -> new ArrayList<>()).add(c.bytes() - b.bytes());
            }
        }
        List<ObjectFamilyDelta> output = new ArrayList<>();
        for (String family : byteDeltas.keySet()) {
            output.add(new ObjectFamilyDelta(
                family,
                median(instanceDeltas.getOrDefault(family, List.of())),
                median(byteDeltas.getOrDefault(family, List.of()))
            ));
        }
        output.sort(Comparator.comparingLong((ObjectFamilyDelta d) -> Math.abs(d.medianByteDelta())).reversed());
        return output.stream().limit(25).toList();
    }

    private static Map<String, HistogramFamilyStats> readHistogramFamilies(RunEvidence run) throws IOException {
        String path = run.capture().classHistogram();
        if (path == null || path.isBlank()) {
            return Map.of();
        }
        File file = new File(path);
        if (!file.isFile()) {
            return Map.of();
        }
        Map<String, HistogramFamilyStats> families = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            Matcher matcher = HISTOGRAM_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            long instances = Long.parseLong(matcher.group(2));
            long bytes = Long.parseLong(matcher.group(3));
            String family = objectFamily(matcher.group(4));
            HistogramFamilyStats current = families.getOrDefault(family, HistogramFamilyStats.ZERO);
            families.put(family, new HistogramFamilyStats(current.instances() + instances, current.bytes() + bytes));
        }
        return families;
    }

    private static String objectFamily(String className) {
        String c = className == null ? "" : className;
        String lower = c.toLowerCase(Locale.ROOT);
        if (c.startsWith("[B")) return "byte_arrays";
        if (c.startsWith("[C")) return "char_arrays";
        if (c.startsWith("[")) return "arrays";
        if ("java.lang.String".equals(c)) return "strings";
        if (lower.contains("hashmap") || lower.contains("map$") || lower.contains("concurrenthashmap")) return "maps";
        if (lower.contains("arraylist") || lower.contains("list") || lower.contains("set") || lower.contains("queue")) {
            return "collections";
        }
        if (c.startsWith("org.springframework.")) return "spring";
        if (c.startsWith("com.fasterxml.jackson.") || c.startsWith("tools.jackson.")) return "jackson";
        if (c.startsWith("org.hibernate.")) return "hibernate";
        if (c.startsWith("org.apache.catalina.") || c.startsWith("org.apache.tomcat.")) return "tomcat";
        if (c.startsWith("io.netty.") || c.startsWith("reactor.netty.")) return "netty";
        if (c.contains("$$Lambda") || c.contains("/0x")) return "lambda_objects";
        if (c.startsWith("jmoa.runtime.") || c.contains("JmoaPkgAdapters")) return "jmoa_runtime";
        if (c.startsWith("java.lang.reflect.") || c.startsWith("jdk.internal.reflect.") || c.startsWith("sun.reflect.")) {
            return "reflection";
        }
        if (c.startsWith("java.lang.invoke.")) return "method_handles";
        if (c.contains("ClassLoader")) return "classloader";
        if (c.contains("$Proxy") || c.contains("$$SpringCGLIB") || c.contains("ByteBuddy") || c.contains("HibernateProxy")) {
            return "proxy_objects";
        }
        return "unknown";
    }

    private static ClassMetaspaceAttribution classMetaspaceAttribution(List<PairRuns> pairs) {
        long classCount = median(pairs.stream()
            .map(p -> (long) p.candidate().classHistogram().classCount() - p.baseline().classHistogram().classCount()).toList());
        long metaspace = median(pairs.stream()
            .map(p -> p.candidate().nmt().metaspaceCommittedKb() - p.baseline().nmt().metaspaceCommittedKb()).toList());
        long clazz = median(pairs.stream()
            .map(p -> p.candidate().nmt().classCommittedKb() - p.baseline().nmt().classCommittedKb()).toList());
        long code = median(pairs.stream()
            .map(p -> p.candidate().nmt().codeCommittedKb() - p.baseline().nmt().codeCommittedKb()).toList());
        String interpretation = classCount < 0
            ? "Class histogram class count decreased; memory attribution still requires heap/smaps categories."
            : "Class count was neutral or higher; do not attribute result to class-count savings alone.";
        return new ClassMetaspaceAttribution(classCount, metaspace, clazz, code, interpretation);
    }

    private static List<CausalHypothesis> causalHypotheses(
        ConfirmationReport confirmation,
        SmapsNmtReconciliation reconciliation,
        HeapObjectAttribution heap,
        ClassMetaspaceAttribution classMeta
    ) {
        List<CausalHypothesis> output = new ArrayList<>();
        if ("HEAP_PAGE_TOUCH_REDUCTION".equals(heap.classification())) {
            output.add(new CausalHypothesis(
                CausalHypothesisType.HEAP_PAGE_TOUCH_REDUCTION,
                Confidence.HIGH,
                List.of(
                    "heap PSS delta " + heap.medianHeapPssDeltaKb() + " KB",
                    "heap used delta " + heap.medianHeapUsedDeltaKb() + " KB",
                    "class histogram bytes delta " + heap.medianClassHistogramBytesDelta() + " bytes"
                ),
                List.of("Result is not explained by retained heap-object shrinkage alone."),
                "Investigate launch-mode and page-touch behavior before enabling mutation."
            ));
        } else if ("HEAP_PAGE_TOUCH_GROWTH".equals(heap.classification())) {
            output.add(new CausalHypothesis(
                CausalHypothesisType.HEAP_PAGE_TOUCH_GROWTH,
                Confidence.HIGH,
                List.of(
                    "heap PSS delta +" + heap.medianHeapPssDeltaKb() + " KB",
                    "heap used stayed near flat",
                    "class histogram bytes stayed near flat"
                ),
                List.of("Regression is not explained by retained-object growth."),
                "Reject candidate shape or diagnose allocation/page-touch timing."
            ));
        }
        if (classMeta.medianClassHistogramClassCountDelta() < 0) {
            output.add(new CausalHypothesis(
                CausalHypothesisType.CLASS_COUNT_SAVINGS,
                Confidence.MEDIUM,
                List.of("class histogram class-count delta " + classMeta.medianClassHistogramClassCountDelta()),
                List.of("Class count alone did not explain all PSS movement."),
                "Treat class-count savings as supporting evidence, not sole causality."
            ));
        }
        long anon = confirmation.medianAnonymousRwDeltaKb();
        if (Math.abs(anon) >= ONE_MB_KB) {
            output.add(new CausalHypothesis(
                anon < 0 ? CausalHypothesisType.ANONYMOUS_RW_ALLOCATOR_REDUCTION : CausalHypothesisType.ANONYMOUS_RW_ALLOCATOR_GROWTH,
                Confidence.MEDIUM,
                List.of("anonymous_rw PSS median delta " + anon + " KB"),
                List.of("NMT may only partially explain allocator dirty pages."),
                "Keep allocator policy explicit in evidence reports."
            ));
        }
        if (output.isEmpty()) {
            output.add(new CausalHypothesis(
                CausalHypothesisType.UNKNOWN,
                Confidence.LOW,
                List.of("No high-confidence attribution rule matched."),
                List.of("Additional JFR/async-profiler diagnostics may be needed."),
                "Collect richer diagnostic evidence before mutation."
            ));
        }
        return output;
    }

    private static long delta(List<MemoryCategoryDelta> deltas, String category) {
        return deltas.stream()
            .filter(d -> d.category().equals(category))
            .mapToLong(MemoryCategoryDelta::medianDelta)
            .findFirst()
            .orElse(0L);
    }

    private static long median(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return Math.round((sorted.get(middle - 1) + sorted.get(middle)) / 2.0d);
    }

    private static String firstNonBlank(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse(null);
    }

    private record PairRuns(int pair, RunEvidence baseline, RunEvidence candidate) {
    }

    private record HistogramFamilyStats(long instances, long bytes) {
        static final HistogramFamilyStats ZERO = new HistogramFamilyStats(0, 0);
    }
}
