package com.yourorg.jmoa.plugin.roi;

/**
 * What would be required to admit a currently-denied framework lambda site.
 *
 * <p>Used to split Tier B candidates into actionable prerequisite groups rather than
 * leaving them as a single opaque bucket.</p>
 */
public enum RewriteRoiAdmissionPrerequisite {

    /** Already admitted — no prerequisite needed. */
    NONE,

    /** Site lacks profile observation. A training run with sufficient workload is needed. */
    PROFILE_REQUIRED,

    /** Site's package is unknown. Must be reviewed and added to the allow-prefix list. */
    PACKAGE_ALLOWLIST_REQUIRED,

    /** Site's SAM interface is not in the supported set. Must be added to safeSamInterfaces. */
    SAM_SUPPORT_REQUIRED,

    /** Access planning failed. Target method visibility must be resolved or widened. */
    ACCESS_FIX_REQUIRED,

    /** Site is excluded by frameworkPackageExclusions config. Exclusion must be removed. */
    FRAMEWORK_EXCLUSION_REMOVAL_REQUIRED,

    /** Site requires Tier 2 adapter. Cost acceptance decision is needed. */
    TIER2_COST_ACCEPTANCE_REQUIRED,

    /** Site can never be admitted (capturing, serializable, proxy internals, hot, etc.). */
    NEVER
}
