package com.yourorg.jmoa.plugin.weave;

import com.yourorg.jmoa.plugin.scanner.LambdaSite;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class LambdaWeavingCoordinator {

    public WeaveExecutionResult rewriteEligibleClasses(
        List<LambdaSite> sites,
        LambdaWeavingPlan weavingPlan,
        ClassLoader projectClassLoader,
        boolean failFast,
        Consumer<String> warningLogger
    ) throws IOException {
        Map<File, List<LambdaSite>> classToSites = new LinkedHashMap<>();
        int plannedSites = 0;
        for (LambdaSite site : sites) {
            if (weavingPlan.targetFor(site.siteKey()) == null) {
                continue;
            }
            plannedSites++;
            classToSites.computeIfAbsent(site.classFile(), ignored -> new ArrayList<>()).add(site);
        }

        int rewrittenClasses = 0;
        List<String> failedClassNames = new ArrayList<>();
        LambdaClassWeaver weaver = new LambdaClassWeaver();
        LambdaWeaveSanityChecker sanityChecker = new LambdaWeaveSanityChecker();
        int verifiedEligibleSites = 0;
        int rewrittenEligibleSites = 0;
        int remainingEligibleSites = 0;
        int unexpectedRemovedSites = 0;
        List<String> remainingEligibleSiteKeys = new ArrayList<>();
        List<String> unexpectedRemovedSiteKeys = new ArrayList<>();
        List<RewrittenClassDelta> rewrittenClassDeltas = new ArrayList<>();

        for (File classFile : classToSites.keySet()) {
            try {
                byte[] originalBytes = Files.readAllBytes(classFile.toPath());
                String classInternalName = new org.objectweb.asm.ClassReader(originalBytes).getClassName();
                byte[] patchedBytes = weaver.weaveBytes(
                    originalBytes,
                    classInternalName,
                    weavingPlan,
                    projectClassLoader
                );
                if (Arrays.equals(originalBytes, patchedBytes)) {
                    throw new IOException("Weave produced identical bytes for targeted class " + classFile.getAbsolutePath());
                }
                LambdaWeaveSanityClassResult classResult = sanityChecker.verifyClass(originalBytes, patchedBytes, weavingPlan);
                Files.write(classFile.toPath(), patchedBytes);
                rewrittenClasses++;
                rewrittenClassDeltas.add(new RewrittenClassDelta(
                    classInternalName,
                    classFile.getAbsolutePath(),
                    originalBytes.length,
                    patchedBytes.length,
                    constantPoolCount(originalBytes),
                    constantPoolCount(patchedBytes),
                    classHash(originalBytes),
                    classHash(patchedBytes)
                ));
                verifiedEligibleSites += classResult.expectedEligibleSites();
                rewrittenEligibleSites += classResult.rewrittenEligibleSites();
                remainingEligibleSites += classResult.remainingEligibleSites();
                unexpectedRemovedSites += classResult.unexpectedRemovedSites();
                remainingEligibleSiteKeys.addAll(classResult.remainingEligibleSiteKeys());
                unexpectedRemovedSiteKeys.addAll(classResult.unexpectedRemovedSiteKeys());
            } catch (Exception e) {
                failedClassNames.add(classFile.getAbsolutePath());
                if (failFast) {
                    throw new IOException("Failed to rewrite class " + classFile.getAbsolutePath(), e);
                }
                warningLogger.accept("Skipping class rewrite for " + classFile.getAbsolutePath() + " because: " + e.getMessage());
            }
        }

        return new WeaveExecutionResult(
            failFast,
            plannedSites,
            classToSites.size(),
            rewrittenClasses,
            failedClassNames.size(),
            failedClassNames,
            new LambdaWeaveSanitySummary(
                plannedSites,
                verifiedEligibleSites,
                rewrittenEligibleSites,
                remainingEligibleSites,
                unexpectedRemovedSites,
                remainingEligibleSiteKeys,
                unexpectedRemovedSiteKeys
            ),
            rewrittenClassDeltas
        );
    }

    private int constantPoolCount(byte[] classBytes) {
        return Math.max(0, new org.objectweb.asm.ClassReader(classBytes).getItemCount() - 1);
    }

    private String classHash(byte[] classBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(classBytes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash class bytes", e);
        }
    }
}
