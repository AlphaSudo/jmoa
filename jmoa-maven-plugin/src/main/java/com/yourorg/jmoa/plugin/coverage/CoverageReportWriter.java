package com.yourorg.jmoa.plugin.coverage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CoverageReportWriter {

    private CoverageReportWriter() {
    }

    public static void write(File reportFile, CoverageAnalysis analysis) throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("timestamp", Instant.now().toString());
        report.put("metadataVersion", "v3.3-alpha");
        report.put("executionMode", analysis.executionMode());
        report.put("classRootsScanned", analysis.classRootsScanned());
        report.put("classesScanned", analysis.classesScanned());
        report.put("totalLambdaSites", analysis.totalLambdaSites());
        report.put("statelessCandidateSites", analysis.statelessCandidateSites());
        report.put("profileSiteCount", analysis.profileSiteCount());
        report.put("observedCurrentSites", analysis.observedCurrentSites());
        report.put("newSiteCount", analysis.newSiteCount());
        report.put("missingProfileSiteCount", analysis.missingProfileSiteCount());
        report.put("newSiteKeys", analysis.newSiteKeys());
        report.put("missingProfileSiteKeys", analysis.missingProfileSiteKeys());

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(reportFile, report);
    }
}
