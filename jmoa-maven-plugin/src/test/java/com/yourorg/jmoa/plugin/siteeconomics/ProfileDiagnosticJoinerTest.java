package com.yourorg.jmoa.plugin.siteeconomics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileDiagnosticJoinerTest {
    @Test
    void joinsAtHonestResolutionWithoutPromotingSampledBytesToExact() throws Exception {
        var input=new ObjectMapper().readTree("""
                {"schemaVersion":"jmoa-service-profile-v3","lambdaSites":[
                  {"siteKey":"a#0","familyKey":"family-a","allocationBytes":null,
                   "allocationMeasurement":"SAMPLED_JFR","diagnosticJoins":[]}]}
                """);
        var output=new ProfileDiagnosticJoiner().join(input,List.of(
                evidence("JFR","FAMILY","family-a","A"),
                evidence("NMT_DETAIL","PROCESS",null,"B"),
                evidence("NATIVE_ALLOCATOR","UNRESOLVED",null,"C")));

        assertEquals(3,output.path("diagnosticEvidence").size());
        assertEquals(2,output.path("lambdaSites").get(0).path("diagnosticJoins").size());
        assertTrue(output.path("lambdaSites").get(0).path("allocationBytes").isNull());
        assertEquals("SAMPLED_JFR",output.path("lambdaSites").get(0).path("allocationMeasurement").asText());
    }

    @Test
    void failsClosedForUnknownJoinTargetsAndInvalidEvidence() throws Exception {
        var input=new ObjectMapper().readTree("""
                {"schemaVersion":"jmoa-service-profile-v3","lambdaSites":[
                  {"siteKey":"a#0","familyKey":"family-a","allocationBytes":null,"diagnosticJoins":[]}]}
                """);
        assertThrows(IOException.class,()->new ProfileDiagnosticJoiner().join(input,
                List.of(evidence("JFR","EXACT_SITE","missing","D"))));
        assertThrows(IllegalArgumentException.class,()->evidence("JFR","FAMILY",null,"E"));
    }

    private static ProfileDiagnosticJoiner.DiagnosticEvidence evidence(
            String source,String resolution,String target,String hashCharacter){
        return new ProfileDiagnosticJoiner.DiagnosticEvidence(source,resolution,"offline-keyed-join",target,
                "C:/evidence/diagnostic.json",hashCharacter.repeat(64));
    }
}
