package com.yourorg.jmoa.plugin.runtimepolicy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePreflightService.RuntimePreflightReport;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class RuntimePreflightReportWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    public void write(File outputDirectory, RuntimePreflightReport report) throws IOException {
        Files.createDirectories(outputDirectory.toPath());
        MAPPER.writeValue(new File(outputDirectory, "jmoa-runtime-preflight.json"), report);
        Files.writeString(
            new File(outputDirectory, "jmoa-runtime-preflight.md").toPath(),
            markdown(report),
            StandardCharsets.UTF_8
        );
    }

    private static String markdown(RuntimePreflightReport report) {
        StringBuilder md = new StringBuilder("# JMOA Runtime Preflight\n\n");
        md.append("## Readiness\n\n```text\n").append(report.readiness()).append("\n```\n\n");
        md.append("- Service: `").append(report.input().service()).append("`\n");
        md.append("- Launch mode: `").append(report.input().launchMode()).append("`\n");
        md.append("- Runtime policy: `").append(report.input().runtimePolicy()).append("`\n");
        md.append("- Policy decision: `").append(report.recommendation().decision()).append("`\n\n");
        appendProbe(md, "Artifact", report.artifact());
        appendProbe(md, "CDS Archive", report.cdsArchive());
        appendList(md, "Blockers", report.blockers());
        appendList(md, "Next Actions", report.nextActions());
        appendList(md, "Claim Boundary", report.boundaries());
        return md.toString();
    }

    private static void appendProbe(StringBuilder md, String label, RuntimePreflightService.FileProbe probe) {
        md.append("## ").append(label).append("\n\n");
        md.append("- Present: `").append(probe.present()).append("`\n");
        md.append("- Bytes: `").append(probe.bytes()).append("`\n");
        md.append("- SHA-256: `").append(probe.sha256()).append("`\n\n");
    }

    private static void appendList(StringBuilder md, String title, Iterable<String> values) {
        md.append("## ").append(title).append("\n\n");
        boolean any = false;
        for (String value : values) {
            md.append("- ").append(value).append('\n');
            any = true;
        }
        if (!any) {
            md.append("None.\n");
        }
        md.append('\n');
    }
}
