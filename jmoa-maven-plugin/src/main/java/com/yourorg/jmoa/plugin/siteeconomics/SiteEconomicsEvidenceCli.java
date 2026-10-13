package com.yourorg.jmoa.plugin.siteeconomics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Command-line evidence boundary used by the no-runtime Package-C script. */
public final class SiteEconomicsEvidenceCli {
    public static void main(String[] args) throws Exception {
        File profile = null;
        File audit = null;
        File output = null;
        List<SiteEconomicsOrchestrator.CandidateSpec> candidates = new ArrayList<>();
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if ("--profile".equals(argument)) profile = new File(requireValue(args, ++index, argument));
            else if ("--static-audit".equals(argument)) audit = new File(requireValue(args, ++index, argument));
            else if ("--output".equals(argument)) output = new File(requireValue(args, ++index, argument));
            else if ("--candidate".equals(argument)) candidates.add(parseCandidate(requireValue(args, ++index, argument)));
            else throw new IllegalArgumentException("Unknown argument: " + argument);
        }
        if (profile == null || audit == null || output == null) {
            throw new IllegalArgumentException("--profile, --static-audit, and --output are required");
        }
        SiteEconomicsOrchestrator.Result result = new SiteEconomicsOrchestrator().evaluate(profile, audit, candidates);
        File parent = output.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create output directory: " + parent);
        }
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(output, result);
        System.out.println(result.status() + ":" + result.reason());
    }

    private static SiteEconomicsOrchestrator.CandidateSpec parseCandidate(String value) {
        String[] fields = value.split(":", -1);
        if (fields.length != 3 || !("true".equals(fields[2]) || "false".equals(fields[2]))) {
            throw new IllegalArgumentException("Candidate must be id:MODE:true|false");
        }
        return new SiteEconomicsOrchestrator.CandidateSpec(fields[0], fields[1], Boolean.parseBoolean(fields[2]));
    }

    private static String requireValue(String[] args, int index, String argument) {
        if (index >= args.length || args[index].isBlank()) throw new IllegalArgumentException(argument + " requires a value");
        return args[index];
    }

    private SiteEconomicsEvidenceCli() {
    }
}
