package com.yourorg.jmoa.plugin.measure;

public final class MeasurementComparator {

    public MeasurementComparison compare(
        MeasurementResult baseline,
        MeasurementResult candidate,
        Integer minLambdaClassReduction,
        Integer minMetaspaceReductionKb,
        Double maxStartupRegressionMs
    ) {
        int lambdaReductionAbsolute = baseline.lambdaClasses() - candidate.lambdaClasses();
        double lambdaReductionPercent = percentage(lambdaReductionAbsolute, baseline.lambdaClasses());
        long metaspaceReductionKb = baseline.metaspaceCommittedKb() - candidate.metaspaceCommittedKb();
        double metaspaceReductionPercent = percentage(metaspaceReductionKb, baseline.metaspaceCommittedKb());
        double startupChangeMs = candidate.averageStartupMs() - baseline.averageStartupMs();
        double startupChangePercent = percentage(startupChangeMs, baseline.averageStartupMs());

        boolean lambdaPass = minLambdaClassReduction == null || lambdaReductionAbsolute >= minLambdaClassReduction;
        boolean metaspacePass = minMetaspaceReductionKb == null || metaspaceReductionKb >= minMetaspaceReductionKb;
        boolean startupPass = maxStartupRegressionMs == null || startupChangeMs <= maxStartupRegressionMs;
        boolean passes = lambdaPass && metaspacePass && startupPass;
        boolean lambdaRegressed = lambdaReductionAbsolute < 0;
        boolean metaspaceRegressed = metaspaceReductionKb < 0;
        boolean startupRegressed = startupChangeMs > 0.0d;
        String verdict;
        if (!passes) {
            verdict = "FAIL";
        } else if (lambdaRegressed || metaspaceRegressed || startupRegressed) {
            verdict = "WARN";
        } else {
            verdict = "PASS";
        }

        return new MeasurementComparison(
            baseline.scenario(),
            candidate.scenario(),
            lambdaReductionAbsolute,
            lambdaReductionPercent,
            metaspaceReductionKb,
            metaspaceReductionPercent,
            startupChangeMs,
            startupChangePercent,
            passes,
            verdict
        );
    }

    private double percentage(double delta, double baseline) {
        if (baseline == 0.0d) {
            return 0.0d;
        }
        return (delta * 100.0d) / baseline;
    }
}
