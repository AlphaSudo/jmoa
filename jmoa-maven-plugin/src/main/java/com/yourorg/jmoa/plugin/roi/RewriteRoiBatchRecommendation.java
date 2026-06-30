package com.yourorg.jmoa.plugin.roi;

/**
 * Recommended action for a candidate batch.
 *
 * <p>Richer than a simple score — tells you <em>what work is required</em>,
 * not just whether to proceed.</p>
 *
 * <p>Hard constraints:</p>
 * <ul>
 *   <li>Unknown package batches cannot be {@code ADMIT_NEXT} — must be {@code REVIEW_PACKAGE}</li>
 *   <li>Missing-profile batches cannot be {@code ADMIT_NEXT} — must be {@code PROFILE_FIRST}</li>
 *   <li>Unsupported SAM batches cannot be {@code ADMIT_NEXT} — must be {@code ADD_SAM_SUPPORT}</li>
 *   <li>Access-denied batches cannot be {@code ADMIT_NEXT} — must be {@code FIX_ACCESS}</li>
 * </ul>
 */
public enum RewriteRoiBatchRecommendation {

    /** Safe to admit in Phase 21 with no prerequisites. */
    ADMIT_NEXT,

    /** Candidates need profile observation before admission. Run training workload first. */
    PROFILE_FIRST,

    /** Candidates need package review and allowlist addition. */
    REVIEW_PACKAGE,

    /** Candidates need SAM interface support added. */
    ADD_SAM_SUPPORT,

    /** Candidates need access visibility fix. */
    FIX_ACCESS,

    /** Candidates are not prioritized — defer to a later phase. */
    DEFER,

    /** Candidates should not be admitted. */
    REJECT
}
