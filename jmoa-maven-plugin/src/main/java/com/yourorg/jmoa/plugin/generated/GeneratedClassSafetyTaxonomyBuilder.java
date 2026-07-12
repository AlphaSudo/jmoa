package com.yourorg.jmoa.plugin.generated;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class GeneratedClassSafetyTaxonomyBuilder {

    private static final String METADATA_VERSION = "v2-a3-safety-taxonomy";

    public GeneratedClassSafetyTaxonomy build(GeneratedClassInventory inventory) {
        List<GeneratedClassTransformEligibility> eligibility = new ArrayList<>();
        if (inventory != null) {
            for (GeneratedClassRecord record : inventory.classes()) {
                if (record.generatedLike()) {
                    eligibility.add(eligibilityFor(record));
                }
            }
        }
        eligibility = eligibility.stream()
            .sorted(Comparator
                .comparing(GeneratedClassTransformEligibility::family)
                .thenComparing(GeneratedClassTransformEligibility::className))
            .toList();
        Map<GeneratedClassSafetyCategory, Integer> counts = new EnumMap<>(GeneratedClassSafetyCategory.class);
        for (GeneratedClassTransformEligibility item : eligibility) {
            counts.merge(item.safetyCategory(), 1, Integer::sum);
        }
        return new GeneratedClassSafetyTaxonomy(
            METADATA_VERSION,
            Instant.now().toString(),
            eligibility.size(),
            counts,
            eligibility
        );
    }

    public GeneratedClassTransformEligibility eligibilityFor(GeneratedClassRecord record) {
        return switch (record.family()) {
            case SPRING_CGLIB, JDK_PROXY, BYTEBUDDY, HIBERNATE_PROXY -> unsafe(record,
                "Runtime proxy/helper class may participate in interception, identity, cache, or reflection contracts.");
            case SPRING_AOT_BEAN_DEFINITIONS, SPRING_AOT_REGISTRATION -> new GeneratedClassTransformEligibility(
                record.className(),
                record.family(),
                GeneratedClassSafetyCategory.SAFE_TO_REPACK_ONLY,
                List.of(
                    "Spring AOT generated class exists at build time and can be materialized or origin-verified safely.",
                    "Bytecode consolidation is not admitted until bean-count and endpoint behavior gates pass."
                ),
                List.of("REPORT_ONLY", "REPACK_ONLY", "RUNTIME_ORIGIN_VERIFY"),
                List.of("DELETE", "CONSOLIDATE", "REPLACE_WITH_SHARED_ADAPTER")
            );
            case LAMBDA_METAFATORY_SITE -> new GeneratedClassTransformEligibility(
                record.className(),
                record.family(),
                GeneratedClassSafetyCategory.SAFE_TO_REPLACE_WITH_SHARED_ADAPTER,
                List.of(
                    "LambdaMetafactory sites are already handled by the v1 candidate/admission pipeline.",
                    "Generated-class inventory alone is not sufficient to admit a rewrite."
                ),
                List.of("REPORT_ONLY", "V1_LAMBDA_PIPELINE"),
                List.of("DELETE", "PROXY_REWRITE")
            );
            case SPRING_DATA_GENERATED -> new GeneratedClassTransformEligibility(
                record.className(),
                record.family(),
                GeneratedClassSafetyCategory.UNKNOWN,
                List.of("Spring Data generated helpers can be property/repository-contract sensitive."),
                List.of("REPORT_ONLY", "RUNTIME_ORIGIN_VERIFY"),
                List.of("DELETE", "CONSOLIDATE", "REPLACE_WITH_SHARED_ADAPTER")
            );
            case SYNTHETIC_BRIDGE_METHODS, COMPILER_SYNTHETIC_HELPER, KOTLIN_SYNTHETIC,
                ANONYMOUS_INNER_CLASS, NESTMATE_GENERATED, UNKNOWN_GENERATED -> new GeneratedClassTransformEligibility(
                record.className(),
                record.family(),
                GeneratedClassSafetyCategory.UNKNOWN,
                List.of("Synthetic/compiler/nestmate helper shape may be required for dispatch, private access, or language runtime behavior."),
                List.of("REPORT_ONLY"),
                List.of("DELETE", "CONSOLIDATE", "REPLACE_WITH_SHARED_ADAPTER")
            );
            case PLAIN -> new GeneratedClassTransformEligibility(
                record.className(),
                record.family(),
                GeneratedClassSafetyCategory.UNKNOWN,
                List.of("Plain class is included only for accounting; no generated-class transform is admitted."),
                List.of("REPORT_ONLY"),
                List.of("DELETE", "CONSOLIDATE", "REPLACE_WITH_SHARED_ADAPTER")
            );
        };
    }

    private GeneratedClassTransformEligibility unsafe(GeneratedClassRecord record, String reason) {
        return new GeneratedClassTransformEligibility(
            record.className(),
            record.family(),
            GeneratedClassSafetyCategory.UNSAFE_RUNTIME_SEMANTIC,
            List.of(reason),
            List.of("REPORT_ONLY", "RUNTIME_ORIGIN_VERIFY"),
            List.of("DELETE", "CONSOLIDATE", "REPLACE_WITH_SHARED_ADAPTER", "MOVE_TO_AOT")
        );
    }
}
