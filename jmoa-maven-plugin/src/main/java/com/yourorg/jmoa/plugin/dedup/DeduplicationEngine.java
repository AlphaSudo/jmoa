package com.yourorg.jmoa.plugin.dedup;

import com.yourorg.jmoa.plugin.scanner.LambdaSite;
import com.yourorg.jmoa.plugin.synth.SynthNamingStrategy;
import org.objectweb.asm.Type;

import java.util.*;

public final class DeduplicationEngine {

    public static List<SharedGroup> groupSites(List<LambdaSite> sites, ClassLoader classLoader, boolean widenSynthetics) {
        Map<DeduplicationKey, List<LambdaSite>> rawGroups = new HashMap<>();
        for (LambdaSite site : sites) {
            DeduplicationKey key = createKey(site);
            rawGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(site);
        }

        List<SharedGroup> sharedGroups = new ArrayList<>();

        for (Map.Entry<DeduplicationKey, List<LambdaSite>> entry : rawGroups.entrySet()) {
            DeduplicationKey key = entry.getKey();
            List<LambdaSite> groupSites = entry.getValue();

            AccessResolver.Visibility visibility = AccessResolver.resolveVisibility(groupSites.get(0).implHandle(), classLoader);
            Optional<AccessPlan> accessPlan = AccessPlanner.plan(key, visibility, widenSynthetics);
            if (accessPlan.isEmpty()) {
                continue;
            }
            if (accessPlan.get().isTier2()) {
                Map<String, List<LambdaSite>> pkgGroups = groupByPackage(groupSites);
                for (Map.Entry<String, List<LambdaSite>> pkgEntry : pkgGroups.entrySet()) {
                    String targetPkg = pkgEntry.getKey();
                    List<LambdaSite> subSites = pkgEntry.getValue();
                    if (subSites.size() >= 2) {
                        String synthName = SynthNamingStrategy.getInternalName(key, targetPkg);
                        AccessPlan packageScopedPlan = new AccessPlan(
                            accessPlan.get().tier(),
                            accessPlan.get().sourceVisibility(),
                            accessPlan.get().requiresAccessWidening(),
                            targetPkg,
                            accessPlan.get().rationale()
                        );
                        sharedGroups.add(new SharedGroup(key, synthName, subSites, packageScopedPlan));
                    }
                }
            } else {
                if (groupSites.size() >= 2) {
                    String synthName = SynthNamingStrategy.getInternalName(key, accessPlan.get().targetPackageInternal());
                    sharedGroups.add(new SharedGroup(key, synthName, groupSites, accessPlan.get()));
                }
            }
        }

        return sharedGroups;
    }

    private static DeduplicationKey createKey(LambdaSite site) {
        String samInterface = Type.getReturnType(site.indyDesc()).getInternalName();
        return new DeduplicationKey(
            samInterface,
            site.samMethodType().getDescriptor(),
            site.implHandle().getTag(),
            site.implHandle().getOwner(),
            site.implHandle().getName(),
            site.implHandle().getDesc(),
            site.instantiatedMethodType().getDescriptor()
        );
    }

    private static Map<String, List<LambdaSite>> groupByPackage(List<LambdaSite> sites) {
        Map<String, List<LambdaSite>> groups = new HashMap<>();
        for (LambdaSite site : sites) {
            String pkg = getPackage(site.implHandle().getOwner());
            groups.computeIfAbsent(pkg, p -> new ArrayList<>()).add(site);
        }
        return groups;
    }

    private static String getPackage(String internalClassName) {
        int idx = internalClassName.lastIndexOf('/');
        return idx == -1 ? "" : internalClassName.substring(0, idx);
    }
}
