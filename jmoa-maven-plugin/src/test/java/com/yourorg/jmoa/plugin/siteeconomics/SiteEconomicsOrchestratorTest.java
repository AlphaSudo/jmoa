package com.yourorg.jmoa.plugin.siteeconomics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiteEconomicsOrchestratorTest {
    @TempDir Path temporary;

    @Test
    void connectsProfileAndExactStaticSiteKeysAndSelectsWithoutPss() throws Exception {
        var orchestrator=new SiteEconomicsOrchestrator();
        var result=orchestrator.evaluate(write("profile.json",liveProfile()).toFile(),write("audit.json",audit()).toFile(),
                List.of(new SiteEconomicsOrchestrator.CandidateSpec("root-signature","ROOT_PACKAGE_SIGNATURE",true)));

        assertEquals(SiteEconomicsOrchestrator.Status.SELECTED,result.status());
        assertEquals("SELECTED_LOWEST_GENERATED_LAYOUT_BYTES_THEN_CANDIDATE_ID",result.reason());
        assertEquals(1,result.trainingSelectionSiteCount());
        assertNotNull(result.admission());
        assertEquals(List.of("a/A#m()V#0"),result.admission().entries().stream().map(
                AdmissionManifestGenerator.ManifestEntry::siteKey).toList());
    }

    @Test
    void historicalReplayProducesTypedRejectionAndCandidateLimitIsFinite() throws Exception {
        var orchestrator=new SiteEconomicsOrchestrator();
        var result=orchestrator.evaluate(write("replay.json",historicalReplay()).toFile(),write("audit.json",audit()).toFile(),
                List.of(new SiteEconomicsOrchestrator.CandidateSpec("root-signature","ROOT_PACKAGE_SIGNATURE",true)));
        assertEquals(SiteEconomicsOrchestrator.Status.REJECTED,result.status());
        assertEquals("HISTORICAL_REPLAY_HAS_NO_FROZEN_PHASE_OR_ROUTE_ATTRIBUTION",result.reason());
        assertEquals(FamilyEconomicsModelTrainer.Status.REJECTED,result.modelTraining().status());
        assertThrows(IllegalArgumentException.class,()->orchestrator.evaluate(
                write("replay-2.json",historicalReplay()).toFile(),write("audit-2.json",audit()).toFile(),List.of(
                        spec("a"),spec("b"),spec("c"),spec("d"))));
    }

    private static SiteEconomicsOrchestrator.CandidateSpec spec(String id){
        return new SiteEconomicsOrchestrator.CandidateSpec(id,"ROOT_PACKAGE_SIGNATURE",true);
    }

    private Path write(String name,String content) throws Exception {
        Path path=temporary.resolve(name);Files.writeString(path,content);return path;
    }

    private static String audit(){return """
            {"schemaVersion":"jmoa-tier2-static-mode-audit-v1","modes":[{
              "mode":"ROOT_PACKAGE_SIGNATURE","compact":true,"collisionFree":true,
              "generatedReferenceClosure":true,"byteDeterministic":true,"multiRootAdapters":0,
              "adapters":[{"internalName":"a/J$R0_x","logicalBytes":100,"siteKeys":["a/A#m()V#0"],
                "outputOrigins":[{"rootOrdinal":0,"root":"root-a"}]}]}]}
            """;}

    private static String liveProfile(){return profile(
            "LIVE_AGENT",1,2,1,0,"""
            "STARTUP":{"invocationCount":1,"firstActivationNanos":1,"lastActivationNanos":1,"threadCardinality":1,"threads":["main"]},
            "TRAINING":{"invocationCount":2,"firstActivationNanos":2,"lastActivationNanos":3,"threadCardinality":1,"threads":["worker"]},
            "VALIDATION":{"invocationCount":1,"firstActivationNanos":4,"lastActivationNanos":4,"threadCardinality":1,"threads":["worker"]},
            "UNLABELLED":{"invocationCount":0,"firstActivationNanos":null,"lastActivationNanos":null,"threadCardinality":0,"threads":[]}
            ""","""
            "STARTUP":{"TRAINING":0,"HELD_OUT":0,"UNATTRIBUTED":1},
            "TRAINING":{"TRAINING":2,"HELD_OUT":0,"UNATTRIBUTED":0},
            "VALIDATION":{"TRAINING":0,"HELD_OUT":1,"UNATTRIBUTED":0},
            "UNLABELLED":{"TRAINING":0,"HELD_OUT":0,"UNATTRIBUTED":0}
            ""","{\"TRAINING\":2,\"HELD_OUT\":1,\"UNATTRIBUTED\":1}","{\"train\":2,\"held\":1}","EXACT","4000");}

    private static String historicalReplay(){return profile(
            "HISTORICAL_DETERMINISTIC_REPLAY",0,0,0,4,"""
            "STARTUP":{"invocationCount":0,"firstActivationNanos":null,"lastActivationNanos":null,"threadCardinality":0,"threads":[]},
            "TRAINING":{"invocationCount":0,"firstActivationNanos":null,"lastActivationNanos":null,"threadCardinality":0,"threads":[]},
            "VALIDATION":{"invocationCount":0,"firstActivationNanos":null,"lastActivationNanos":null,"threadCardinality":0,"threads":[]},
            "UNLABELLED":{"invocationCount":4,"firstActivationNanos":1,"lastActivationNanos":4,"threadCardinality":0,"threads":[]}
            ""","""
            "STARTUP":{"TRAINING":0,"HELD_OUT":0,"UNATTRIBUTED":0},
            "TRAINING":{"TRAINING":0,"HELD_OUT":0,"UNATTRIBUTED":0},
            "VALIDATION":{"TRAINING":0,"HELD_OUT":0,"UNATTRIBUTED":0},
            "UNLABELLED":{"TRAINING":0,"HELD_OUT":0,"UNATTRIBUTED":4}
            ""","{\"TRAINING\":0,\"HELD_OUT\":0,\"UNATTRIBUTED\":4}","{}","NONE","null");}

    private static String profile(String mode,int startup,int training,int validation,int unlabelled,
                                  String phases,String matrix,String partitionCounts,String observed,
                                  String allocationMeasurement,String allocationBytes){
        int total=startup+training+validation+unlabelled;int selection=startup+training;
        return """
            {"schemaVersion":"jmoa-service-profile-v3","profileVersion":3,
             "captureProvenance":{"mode":"%s","limitations":[]},
             "optimizerProjection":{"policy":"STARTUP_PLUS_TRAINING_FACTORY_INVOCATIONS","selectionPhases":["STARTUP","TRAINING"],"excludedPhases":["VALIDATION","UNLABELLED"],"heldOutPhase":"VALIDATION","validationExcludedFromAdmission":true,"countField":"selectionFactoryInvocationCount"},
             "crossFitPartitions":{"frozenBeforeCollection":true,"frozenBeforeRanking":true,"trainingPartitionRoutes":["train"],"heldOutPartitionRoutes":["held"],"requestCountPerRoute":{"train":2,"held":1}},
             "measurementHonesty":{"allocationFieldPolicy":"allocationBytes is null unless allocationMeasurement=EXACT; sampled diagnostics are never presented as exact per-site bytes","diagnosticJoinPolicy":"offline resolution"},
             "loadedClasses":["a/A"],"hotClasses":["a/A"],"coldClasses":[],"lambdaSites":[{
               "siteKey":"a/A#m()V#0","ownerInternalName":"a/A","packageInternalName":"a","samMethodTypeDesc":"()V","familyKey":"a|()V",
               "factoryInvocationCount":%d,"startupInvocationCount":%d,"trainingInvocationCount":%d,"validationInvocationCount":%d,"unlabelledInvocationCount":%d,"selectionFactoryInvocationCount":%d,"firstActivationNanos":1,
               "phaseActivity":{%s},"routeActivity":{"routePartition":"%s","partitionInvocationCounts":%s,"observedRouteInvocationCounts":%s,"phasePartitionInvocationCounts":{%s}},
               "measurementCoverage":{"factoryInvocations":"MEASURED"},"allocationMeasurement":"%s","allocationBytes":%s,
               "selectionDecision":{"disposition":"DEFER","reason":"OFFLINE_STATIC_JOIN_REQUIRED","coverage":"EXACT_FACTORY_COUNTS_ONLY","confidence":"INSUFFICIENT_FOR_ADMISSION"}}]}
            """.formatted(mode,total,startup,training,validation,unlabelled,selection,phases,
                "HISTORICAL_DETERMINISTIC_REPLAY".equals(mode)?"UNATTRIBUTED":"MIXED",partitionCounts,observed,matrix,
                allocationMeasurement,allocationBytes);
    }
}
