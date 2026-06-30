package com.yourorg.jmoa.plugin.roi;

/**
 * Safety tier for framework lambda rewrite candidates.
 *
 * <p>Phase 20 classifies every denied framework site into one of these tiers
 * to determine the safest path to admission scaling.</p>
 *
 * <ul>
 *   <li>{@code TIER_S} — Already allowed by Phase 15/16 rules</li>
 *   <li>{@code TIER_A} — Low-risk expansion candidate</li>
 *   <li>{@code TIER_B} — Medium-risk candidate (split by {@link RewriteRoiAdmissionPrerequisite})</li>
 *   <li>{@code TIER_C} — High-risk candidate (requires separate design review)</li>
 *   <li>{@code TIER_D} — Never admit</li>
 * </ul>
 */
public enum RewriteRoiSafetyTier {

    /**
     * Already allowed by current Phase 15/16 framework safety rules.
     */
    TIER_S,

    /**
     * Low-risk expansion candidate.
     * <p>Must be: observed in full-startup profile, cold, non-capturing, non-serializable,
     * supported SAM, not altMetafactory, not denied package, not proxy/CGLIB/ByteBuddy/Jackson/Hibernate internals,
     * access-planner approved, Tier 1 preferred.</p>
     */
    TIER_A,

    /**
     * Medium-risk candidate. Aggregated by {@link RewriteRoiAdmissionPrerequisite} to avoid
     * an opaque single bucket.
     * <p>Examples: safe package but missing profile, observed but unknown package,
     * custom Spring SAM but simple functional shape, Tier 2 required but package is stable.</p>
     */
    TIER_B,

    /**
     * High-risk candidate. Requires separate design review — NOT included in any
     * automatic admission plan.
     * <p>Examples: reflection-heavy package, beans internals, AOP/security/proxy-adjacent,
     * access requires widening, custom SAM with unclear semantics.</p>
     */
    TIER_C,

    /**
     * Never admit.
     * <p>Includes: capturing lambdas, serializable lambdas, altMetafactory,
     * proxy/enhancer internals, Jackson internals, Hibernate internals,
     * ByteBuddy/Javassist/CGLIB internals, hot sites.</p>
     */
    TIER_D
}
