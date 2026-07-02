package com.yourorg.jmoa.plugin.evidence;

import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceCapture;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.PerturbationReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RunManifest;

import java.util.ArrayList;
import java.util.List;

public final class EvidencePerturbationDetector {

    public PerturbationReport detect(RunManifest manifest, EvidenceCapture capture) {
        List<String> warnings = new ArrayList<>();
        boolean classLoad = manifest.classLoadLoggingEnabled() || capture.classloadLog() != null;
        boolean jfr = manifest.jfrEnabled();
        boolean nmtDetail = manifest.nmtMode() != null && manifest.nmtMode().equalsIgnoreCase("detail");
        boolean gcRun = manifest.gcRunBeforeCapture();

        if (classLoad) warnings.add("class-load logging enabled during run");
        if (jfr) warnings.add("JFR recording enabled during run");
        if (nmtDetail) warnings.add("NMT detail enabled during run");
        if (gcRun) warnings.add("GC.run occurred before official capture");

        return new PerturbationReport(
            classLoad,
            jfr,
            nmtDetail,
            gcRun,
            classLoad || jfr || nmtDetail || gcRun,
            warnings
        );
    }
}
