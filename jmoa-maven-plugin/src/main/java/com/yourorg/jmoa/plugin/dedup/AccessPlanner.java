package com.yourorg.jmoa.plugin.dedup;

import java.util.Optional;

public final class AccessPlanner {

    private AccessPlanner() {
    }

    public static Optional<AccessPlan> plan(
        DeduplicationKey key,
        AccessResolver.Visibility visibility,
        boolean widenSynthetics
    ) {
        return switch (visibility) {
            case UNKNOWN -> Optional.empty();
            case PUBLIC -> Optional.of(new AccessPlan(
                AccessTier.TIER1_PUBLIC_LOOKUP,
                visibility,
                false,
                null,
                "Public target can stay global and be resolved with public lookup semantics."
            ));
            case PROTECTED, PACKAGE_PRIVATE -> Optional.of(new AccessPlan(
                AccessTier.TIER2_PACKAGE_DIRECT,
                visibility,
                false,
                packageOf(key.implOwner()),
                "Non-public target requires same-package direct invocation."
            ));
            case PRIVATE -> {
                boolean isSyntheticLambda = key.implName().startsWith("lambda$");
                if (widenSynthetics && isSyntheticLambda) {
                    yield Optional.of(new AccessPlan(
                        AccessTier.TIER2_PACKAGE_DIRECT,
                        visibility,
                        true,
                        packageOf(key.implOwner()),
                        "Private synthetic lambda target is widened and invoked through a same-package adapter."
                    ));
                }
                yield Optional.empty();
            }
        };
    }

    static String packageOf(String internalClassName) {
        int idx = internalClassName.lastIndexOf('/');
        return idx == -1 ? "" : internalClassName.substring(0, idx);
    }
}
