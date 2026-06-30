package com.yourorg.jmoa.plugin.framework;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FrameworkAdmissionReportWriter {

    private final FrameworkAdmissionReportAnalyzer analyzer = new FrameworkAdmissionReportAnalyzer();

    public void write(
        File reportFile,
        String executionMode,
        LambdaFilterResult filterResult
    ) throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("timestamp", Instant.now().toString());
        report.put("metadataVersion", "v3.3-phase15b");
        report.put("executionMode", executionMode);
        report.putAll(analyzer.analyze(filterResult));

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(reportFile, report);
    }
}
