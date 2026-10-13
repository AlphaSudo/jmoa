package com.yourorg.jmoa.plugin.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaTier;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Replays admitted historical generator inputs; this does not prove fresh eligibility. */
class GoldenRecipeDifferentialTest {
    private static JsonNode report;
    private static final Map<String, JsonNode> profile = new LinkedHashMap<>();
    private static final Map<String, Path> jars = new HashMap<>();
    private static Path application;
    private static Path producerHybridRoot;

    @BeforeAll
    static void loadBoundExternalCorpus() throws Exception {
        Assumptions.assumeTrue(System.getProperty("jmoa.golden.report") != null,
            "Explicit private corpus required; normal unit runs do not claim golden admission");
        ObjectMapper mapper = new ObjectMapper();
        report = mapper.readTree(Path.of(System.getProperty("jmoa.golden.report")).toFile());
        JsonNode raw = mapper.readTree(Path.of(System.getProperty("jmoa.golden.profile")).toFile());
        for (JsonNode row : raw.get("lambdaSites")) {
            assertNull(profile.put(row.get("siteKey").asText(), row), "duplicate original profile key");
        }
        Path root = Path.of(System.getProperty("jmoa.golden.root"));
        application = root.resolve("application/BOOT-INF/classes");
        String hybrid = System.getProperty("jmoa.golden.producerHybridRoot");
        producerHybridRoot = hybrid == null ? null : Path.of(hybrid);
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".jar")).sorted().toList()) {
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (name.endsWith(".class")) jars.putIfAbsent(name, path);
                    }
                }
            }
        }
    }

    @Test
    void replaysAll116DirectSitesAndMatchesFourGoldenHelpersExactly() throws Exception {
        List<Tier1RuntimePlan> plans = new ArrayList<>();
        for (JsonNode row : report.get("tier1RuntimeSummary").get("supportedPlans")) {
            LambdaMeta meta = originalMeta(row.get("siteKey").asText());
            RuntimeAdapterKind kind = RuntimeAdapterKind.fromSamInterface(row.get("samInterface").asText()).orElseThrow();
            LambdaFilterDecision d = LambdaFilterDecision.eligible(meta, true, false,
                meta.invocationCount(), LambdaTier.TIER1, AccessResolver.Visibility.PUBLIC);
            plans.add(new Tier1RuntimePlan(row.get("slotId").asInt(), kind, d));
        }
        assertEquals(116, plans.size());
        byte[] sample = bytes("jmoa/runtime/JmoaDirectAdapters$0502b1.class");
        int version = ((sample[6] & 255) << 8) | (sample[7] & 255);
        List<Tier2AdapterArtifact> emitted = new Tier2PackageAdapterGenerator(true)
            .generateTier1DirectAdapters(plans, version);
        assertEquals(4, emitted.size());
        for (Tier2AdapterArtifact artifact : emitted) {
            assertArrayEquals(bytes(artifact.internalName() + ".class"), artifact.classBytes(), artifact.internalName());
        }
    }

    @Test
    void replaysAll183PackageSitesAndMatches108GoldenAdaptersExactly() throws Exception {
        Map<String, Set<String>> goldenFields = new LinkedHashMap<>();
        for (JsonNode adapter : report.get("tier2AdapterSummary").get("generatedAdapters")) {
            String name = adapter.get("internalName").asText();
            Set<String> fields = new HashSet<>();
            new ClassReader(bytes(name + ".class")).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String field, String descriptor, String signature, Object value) {
                    if ((access & Opcodes.ACC_STATIC) != 0 && field.startsWith("f")) fields.add(field);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertFalse(fields.isEmpty(), name);
            assertNull(goldenFields.put(name, fields));
        }
        List<LambdaFilterDecision> decisions = new ArrayList<>();
        Map<String, Set<String>> matched = new HashMap<>();
        for (String key : profile.keySet()) {
            LambdaMeta meta = originalMeta(key, false);
            String group = Tier2AdapterNamingStrategy.compactPackageSignatureInternalName(meta);
            String field = Tier2AdapterNamingStrategy.compactFieldName(meta);
            if (goldenFields.getOrDefault(group, Set.of()).contains(field)) {
                meta = originalMeta(key);
                assertTrue(matched.computeIfAbsent(group, k -> new HashSet<>()).add(field), "ambiguous key/field binding");
                decisions.add(LambdaFilterDecision.eligible(meta, true, false, meta.invocationCount(),
                    LambdaTier.TIER2, AccessResolver.Visibility.PACKAGE_PRIVATE));
            }
        }
        assertEquals(goldenFields, matched, "every admitted historical field must bind one exact original key");
        assertEquals(183, decisions.size());
        String sampleName = goldenFields.keySet().iterator().next();
        byte[] sample = bytes(sampleName + ".class");
        int version = ((sample[6] & 255) << 8) | (sample[7] & 255);
        List<Tier2AdapterArtifact> emitted = new Tier2PackageAdapterGenerator(true)
            .generatePackageSignatureAdapters(decisions, version);
        assertEquals(108, emitted.size());
        for (Tier2AdapterArtifact artifact : emitted) {
            assertArrayEquals(bytes(artifact.internalName() + ".class"), artifact.classBytes(), artifact.internalName());
        }
    }

    private static LambdaMeta originalMeta(String key) throws Exception {
        return originalMeta(key, true);
    }

    private static LambdaMeta originalMeta(String key, boolean bindType) throws Exception {
        JsonNode n = profile.get(key);
        assertNotNull(n, "historical site absent from exact profile: " + key);
        String owner = n.get("implOwner").asText();
        boolean iface = bindType && (new ClassReader(bytes(owner + ".class")).getAccess() & Opcodes.ACC_INTERFACE) != 0;
        return new LambdaMeta(key, n.get("ownerInternalName").asText(), n.get("packageInternalName").asText(),
            n.get("enclosingMethodName").asText(), n.get("enclosingMethodDesc").asText(), n.get("siteOrdinalInMethod").asInt(),
            n.get("indyName").asText(), n.get("indyFactoryDesc").asText(), n.get("samMethodTypeDesc").asText(),
            n.get("capturing").asBoolean(), n.get("serializable").asBoolean(), n.get("implTag").asInt(), owner, iface,
            n.get("implName").asText(), n.get("implDesc").asText(), n.get("instantiatedMethodTypeDesc").asText(),
            n.get("invocationCount").asLong());
    }

    private static byte[] bytes(String entry) throws Exception {
        // Input type flags are unchanged by historical weaving; target instructions
        // are never used to synthesize the candidate generator output.
        Path file = application.resolve(entry);
        if (Files.isRegularFile(file)) return Files.readAllBytes(file);
        Path jar = jars.get(entry);
        if (jar != null) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                return zip.getInputStream(zip.getEntry(entry)).readAllBytes();
            }
        }
        // The original producer kept the hybrid coordinate outside replacement
        // JARs. Bind that separate corpus explicitly; it is generator evidence,
        // never proof that the selected old deployment contained the overlay.
        if (producerHybridRoot != null && entry.startsWith("org/springframework/")
                && entry.contains("/J$")) {
            Path generated = producerHybridRoot.resolve(entry);
            if (Files.isRegularFile(generated)) return Files.readAllBytes(generated);
        }
        try (InputStream stream = ClassLoader.getSystemResourceAsStream(entry)) {
            assertNotNull(stream, "missing golden/JDK class: " + entry);
            return stream.readAllBytes();
        }
    }
}
