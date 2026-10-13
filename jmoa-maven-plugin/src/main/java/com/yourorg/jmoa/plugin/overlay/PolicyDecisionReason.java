package com.yourorg.jmoa.plugin.overlay;

/**
 * Machine-readable reasons the universal engine can accept or refuse a service
 * policy overlay decision. Core code never names a concrete service; the
 * overlay contributes data and selects among these universal reasons.
 */
public enum PolicyDecisionReason {
    ACCEPTED,
    UNKNOWN_SCHEMA_VERSION,
    MISSING_REQUIRED_FIELD,
    UNKNOWN_POLICY_FIELD,
    UNSUPPORTED_TIER1_ADAPTER_MODE,
    UNSUPPORTED_TIER2_CONSOLIDATION,
    UNSUPPORTED_REDUCER_OPERATION,
    UNSUPPORTED_REDUCER_JAR_LAYOUT,
    UNSUPPORTED_PACKAGING_MODE,
    MODE_NOT_ALLOWED_BY_OVERLAY,
    INVALID_SHA256,
    INVALID_SOURCE_REVISION,
    INVALID_SEMANTIC_REQUEST_COUNT,
    WEAKENED_GUARDRAIL
}
