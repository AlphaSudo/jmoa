package com.yourorg.jmoa.plugin.reducer;

public record JarSafetyAssessment(
    boolean signedJar,
    boolean multiReleaseJar,
    boolean sealedJar,
    String skipReason
) {

    public boolean mutationAllowed() {
        return skipReason == null || skipReason.isBlank();
    }
}
