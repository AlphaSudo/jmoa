package com.yourorg.jmoa.plugin.siteeconomics;

import java.util.HashSet;
import java.util.Set;

/**
 * One typed, nullable-by-design feature row per lambda site. Missing values
 * stay null; nothing here is or implies per-site PSS. VALIDATION-phase counts
 * may be carried for safety but are forbidden from ranking — the ranker never
 * reads them.
 */
public record SiteFeatureRow(
        String siteKey,
        String familyKey,
        String ownerInternalName,
        String packageInternalName,
        String physicalOutputRoot,
        String tier,
        String samDescriptor,
        Integer startupInvocationCount,
        Integer trainingInvocationCount,
        Integer validationInvocationCount,
        Integer heldOutRecurrenceCount,
        Long firstActivationNanos,
        Boolean loadedClassObserved,
        Long generatedAdapterCount,
        Long generatedClassBytes,
        Long measuredFamilyAllocationBytes,
        Long measuredFamilyNativePageBytes) {

    public SiteFeatureRow {
        if (siteKey == null || siteKey.isBlank()) {
            throw new IllegalArgumentException("siteKey is required");
        }
        if (familyKey == null || familyKey.isBlank()) {
            throw new IllegalArgumentException("familyKey is required for family aggregation");
        }
    }

    /** Selection features only: STARTUP + TRAINING counts, never VALIDATION. */
    long selectionInvocationCount() {
        long startup = startupInvocationCount == null ? 0L : startupInvocationCount;
        long training = trainingInvocationCount == null ? 0L : trainingInvocationCount;
        return startup + training;
    }

    /** Builder that forces siteKey/familyKey to be set deliberately. */
    public static final class Builder {
        private String siteKey;
        private String familyKey;
        private String ownerInternalName;
        private String packageInternalName;
        private String physicalOutputRoot;
        private String tier;
        private String samDescriptor;
        private Integer startupInvocationCount;
        private Integer trainingInvocationCount;
        private Integer validationInvocationCount;
        private Integer heldOutRecurrenceCount;
        private Long firstActivationNanos;
        private Boolean loadedClassObserved;
        private Long generatedAdapterCount;
        private Long generatedClassBytes;
        private Long measuredFamilyAllocationBytes;
        private Long measuredFamilyNativePageBytes;
        private final Set<String> touched = new HashSet<>();

        public Builder siteKey(String value) { this.siteKey = value; touched.add("siteKey"); return this; }
        public Builder familyKey(String value) { this.familyKey = value; touched.add("familyKey"); return this; }
        public Builder ownerInternalName(String value) { this.ownerInternalName = value; return this; }
        public Builder packageInternalName(String value) { this.packageInternalName = value; return this; }
        public Builder physicalOutputRoot(String value) { this.physicalOutputRoot = value; return this; }
        public Builder tier(String value) { this.tier = value; return this; }
        public Builder samDescriptor(String value) { this.samDescriptor = value; return this; }
        public Builder startupInvocationCount(Integer value) { this.startupInvocationCount = value; return this; }
        public Builder trainingInvocationCount(Integer value) { this.trainingInvocationCount = value; return this; }
        public Builder validationInvocationCount(Integer value) { this.validationInvocationCount = value; return this; }
        public Builder heldOutRecurrenceCount(Integer value) { this.heldOutRecurrenceCount = value; return this; }
        public Builder firstActivationNanos(Long value) { this.firstActivationNanos = value; return this; }
        public Builder loadedClassObserved(Boolean value) { this.loadedClassObserved = value; return this; }
        public Builder generatedAdapterCount(Long value) { this.generatedAdapterCount = value; return this; }
        public Builder generatedClassBytes(Long value) { this.generatedClassBytes = value; return this; }
        public Builder measuredFamilyAllocationBytes(Long value) { this.measuredFamilyAllocationBytes = value; return this; }
        public Builder measuredFamilyNativePageBytes(Long value) { this.measuredFamilyNativePageBytes = value; return this; }

        public SiteFeatureRow build() {
            if (!touched.contains("siteKey") || !touched.contains("familyKey")) {
                throw new IllegalArgumentException("siteKey and familyKey must be set explicitly");
            }
            return new SiteFeatureRow(siteKey, familyKey, ownerInternalName, packageInternalName,
                    physicalOutputRoot, tier, samDescriptor, startupInvocationCount, trainingInvocationCount,
                    validationInvocationCount, heldOutRecurrenceCount, firstActivationNanos,
                    loadedClassObserved, generatedAdapterCount, generatedClassBytes,
                    measuredFamilyAllocationBytes, measuredFamilyNativePageBytes);
        }
    }
}
