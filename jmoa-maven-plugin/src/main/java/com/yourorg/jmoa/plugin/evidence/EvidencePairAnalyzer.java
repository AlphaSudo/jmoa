package com.yourorg.jmoa.plugin.evidence;

import com.yourorg.jmoa.plugin.evidence.EvidenceModels.ConfirmationReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.PairResult;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RunEvidence;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.Variant;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.Verdict;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EvidencePairAnalyzer {

    public ConfirmationReport analyze(List<RunEvidence> runs) {
        Map<Integer, RunEvidence> baselineByPair = new LinkedHashMap<>();
        Map<Integer, RunEvidence> candidateByPair = new LinkedHashMap<>();
        for (RunEvidence run : runs) {
            int pair = run.manifest().pairIndex() <= 0 ? 1 : run.manifest().pairIndex();
            if (run.manifest().variant() == Variant.BASELINE) {
                baselineByPair.put(pair, run);
            } else if (run.manifest().variant() == Variant.CANDIDATE) {
                candidateByPair.put(pair, run);
            }
        }

        List<PairResult> pairs = new ArrayList<>();
        for (Map.Entry<Integer, RunEvidence> entry : baselineByPair.entrySet()) {
            RunEvidence baseline = entry.getValue();
            RunEvidence candidate = candidateByPair.get(entry.getKey());
            if (candidate == null) {
                continue;
            }
            List<String> invalid = new ArrayList<>();
            if (!baseline.valid()) invalid.addAll(baseline.invalidReasons());
            if (!candidate.valid()) invalid.addAll(candidate.invalidReasons());
            long deltaPss = candidate.memory().pssKb() - baseline.memory().pssKb();
            long deltaPrivateDirty = candidate.memory().privateDirtyKb() - baseline.memory().privateDirtyKb();
            long deltaMemoryCurrent = candidate.memory().memoryCurrentBytes() - baseline.memory().memoryCurrentBytes();
            long deltaHeapPss = candidate.memory().heapPssKb() - baseline.memory().heapPssKb();
            long deltaAnonymousRw = candidate.memory().anonymousRwPssKb() - baseline.memory().anonymousRwPssKb();
            long deltaAnonymousExec = candidate.memory().anonymousExecutablePssKb() - baseline.memory().anonymousExecutablePssKb();
            long deltaHeapUsed = candidate.heapInfo().usedKb() - baseline.heapInfo().usedKb();
            boolean pass = deltaPss <= -1024 && deltaPrivateDirty <= -1024 && deltaMemoryCurrent <= -1024L * 1024L;
            pairs.add(new PairResult(
                entry.getKey(),
                baseline.manifest().runId(),
                candidate.manifest().runId(),
                deltaPss,
                deltaPrivateDirty,
                deltaMemoryCurrent,
                deltaHeapPss,
                deltaAnonymousRw,
                deltaAnonymousExec,
                deltaHeapUsed,
                pass,
                invalid.isEmpty(),
                invalid
            ));
        }
        pairs.sort(Comparator.comparingInt(PairResult::pair));
        return confirmation(pairs);
    }

    private static ConfirmationReport confirmation(List<PairResult> pairs) {
        if (pairs.isEmpty()) {
            return new ConfirmationReport("candidate", 0, 0, 0, 0, 0, 0, 0, 0, Verdict.NO_CLAIM, pairs);
        }
        if (pairs.stream().anyMatch(pair -> !pair.valid())) {
            return new ConfirmationReport(
                "candidate",
                pairs.size(),
                0,
                median(pairs, PairResult::deltaPssKb),
                median(pairs, PairResult::deltaPrivateDirtyKb),
                median(pairs, PairResult::deltaMemoryCurrentBytes),
                median(pairs, PairResult::deltaHeapPssKb),
                median(pairs, PairResult::deltaAnonymousRwPssKb),
                median(pairs, PairResult::deltaAnonymousExecutablePssKb),
                Verdict.INVALID_EVIDENCE,
                pairs
            );
        }
        int wins = (int) pairs.stream().filter(PairResult::pass).count();
        long medianPss = median(pairs, PairResult::deltaPssKb);
        long medianPrivateDirty = median(pairs, PairResult::deltaPrivateDirtyKb);
        long medianMemoryCurrent = median(pairs, PairResult::deltaMemoryCurrentBytes);
        Verdict verdict;
        if (pairs.size() == 1) {
            verdict = Verdict.NO_CLAIM;
        } else if (wins >= 2 && medianPss <= -1024 && medianPrivateDirty <= -1024 && medianMemoryCurrent <= -1024L * 1024L) {
            verdict = pairs.size() >= 3 ? Verdict.CONFIRMED_WIN : Verdict.MARGINAL_WIN_NEEDS_5PAIR;
        } else if (wins == 0 && medianPss > 1024 && medianPrivateDirty > 1024 && medianMemoryCurrent > 1024L * 1024L) {
            verdict = Verdict.CONFIRMED_REGRESSION;
        } else {
            verdict = Verdict.MIXED_METRICS_NEEDS_RERUN;
        }
        return new ConfirmationReport(
            "candidate",
            pairs.size(),
            wins,
            medianPss,
            medianPrivateDirty,
            medianMemoryCurrent,
            median(pairs, PairResult::deltaHeapPssKb),
            median(pairs, PairResult::deltaAnonymousRwPssKb),
            median(pairs, PairResult::deltaAnonymousExecutablePssKb),
            verdict,
            pairs
        );
    }

    private static long median(List<PairResult> pairs, java.util.function.ToLongFunction<PairResult> extractor) {
        return EvidenceMath.medianLong(pairs.stream().map(extractor::applyAsLong).toList());
    }
}
