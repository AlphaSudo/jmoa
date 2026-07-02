package com.yourorg.jmoa.plugin.evidence;

import com.yourorg.jmoa.plugin.evidence.EvidenceModels.ConfirmationReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceAnalysisReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceCapture;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceConfig;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceValidationReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.PerturbationReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RunEvidence;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RunManifest;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.VarianceClassification;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.Verdict;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class EvidenceEngine {

    private final EvidenceParsers parsers;
    private final EvidenceValidator validator;
    private final EvidencePerturbationDetector perturbationDetector;
    private final EvidencePairAnalyzer pairAnalyzer;
    private final EvidenceVarianceClassifier varianceClassifier;

    public EvidenceEngine() {
        this(new EvidenceParsers(), new EvidenceValidator(), new EvidencePerturbationDetector(),
            new EvidencePairAnalyzer(), new EvidenceVarianceClassifier());
    }

    EvidenceEngine(
        EvidenceParsers parsers,
        EvidenceValidator validator,
        EvidencePerturbationDetector perturbationDetector,
        EvidencePairAnalyzer pairAnalyzer,
        EvidenceVarianceClassifier varianceClassifier
    ) {
        this.parsers = parsers;
        this.validator = validator;
        this.perturbationDetector = perturbationDetector;
        this.pairAnalyzer = pairAnalyzer;
        this.varianceClassifier = varianceClassifier;
    }

    public EvidenceAnalysisReport analyze(File inputDirectory, EvidenceConfig config) throws IOException {
        List<RunEvidence> runs = new ArrayList<>();
        for (File runDirectory : parsers.runDirectories(inputDirectory)) {
            runs.add(parseRun(runDirectory, config));
        }
        int validRuns = (int) runs.stream().filter(RunEvidence::valid).count();
        EvidenceValidationReport validation = new EvidenceValidationReport(
            runs.size(),
            validRuns,
            runs.size() - validRuns,
            runs
        );
        ConfirmationReport confirmation = pairAnalyzer.analyze(runs);
        VarianceClassification variance = varianceClassifier.classify(confirmation);
        Verdict verdict = validation.invalidRuns() > 0 ? Verdict.INVALID_EVIDENCE : confirmation.verdict();
        return new EvidenceAnalysisReport(
            "v2-c-evidence-analysis",
            Instant.now().toString(),
            config,
            validation,
            confirmation,
            variance,
            runs.stream().map(RunEvidence::perturbation).filter(PerturbationReport::diagnosticOnly).toList(),
            verdict,
            nextAction(verdict, validation, confirmation)
        );
    }

    private RunEvidence parseRun(File runDirectory, EvidenceConfig config) throws IOException {
        RunManifest manifest = parsers.parseManifest(runDirectory);
        EvidenceCapture capture = parsers.discoverCapture(manifest.runId(), runDirectory);
        PerturbationReport perturbation = perturbationDetector.detect(manifest, capture);
        RunEvidence raw = new RunEvidence(
            manifest,
            capture,
            parsers.parseMemory(capture),
            parsers.parseNmtSummary(capture),
            parsers.parseHeapInfo(capture),
            parsers.parseClassHistogram(capture),
            parsers.parseWorkload(capture),
            parsers.parseRuntimeVerification(capture),
            perturbation,
            true,
            List.of(),
            List.of()
        );
        return validator.validate(raw, config);
    }

    private static String nextAction(
        Verdict verdict,
        EvidenceValidationReport validation,
        ConfirmationReport confirmation
    ) {
        if (validation.invalidRuns() > 0) {
            return "Fix invalid evidence before calculating or citing medians.";
        }
        return switch (verdict) {
            case CONFIRMED_WIN -> "Evidence is claimable under the configured policy.";
            case CONFIRMED_REGRESSION -> "Reject candidate or continue diagnosis; do not promote.";
            case MARGINAL_WIN_NEEDS_5PAIR -> "Run five-pair confirmation before claiming.";
            case MIXED_METRICS_NEEDS_RERUN -> "Rerun with clean pairing and inspect variance classification.";
            case DIAGNOSTIC_ONLY -> "Use for diagnosis only; collect non-perturbing confirmation.";
            case NO_CLAIM -> confirmation.pairs() < 3
                ? "Single/short run is screen-only; collect at least three clean pairs."
                : "No claim under current metrics.";
            case INVALID_EVIDENCE -> "Fix invalid evidence before using result.";
        };
    }
}
