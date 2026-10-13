package com.yourorg.jmoa.plugin.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LambdaProfileReaderTest {
    @TempDir
    Path temp;

    @Test
    void readsHistoricalInvocationCountWithoutChangingCompatibility() throws Exception {
        Path profile = write("historical.json", """
            {
              "hotClasses": ["a/A"],
              "coldClasses": ["b/B"],
              "lambdaSites": [{"siteKey":"a/A#m()V#0","invocationCount":42}]
            }
            """);

        LambdaProfileIndex index = LambdaProfileReader.read(profile.toFile());

        assertEquals(42L, index.invocationCount("a/A#m()V#0"));
        assertTrue(index.hotClasses().contains("a/A"));
        assertTrue(index.isColdClass("b/B"));
    }

    @Test
    void profileV2UsesStartupAndTrainingAndExcludesHeldOutValidation() throws Exception {
        Path profile = write("v2.json", """
            {
              "schemaVersion":"jmoa-service-profile-v2",
              "loadedClasses":["a/A","b/B","c/C"],
              "hotClasses":["a/A","b/B"],
              "coldClasses":["c/C"],
              "lambdaSites":[
                {"siteKey":"a/A#m()V#0","ownerInternalName":"a/A","factoryInvocationCount":17,"startupInvocationCount":10,"trainingInvocationCount":5,"validationInvocationCount":2,"selectionFactoryInvocationCount":15},
                {"siteKey":"b/B#m()V#0","ownerInternalName":"b/B","factoryInvocationCount":9,"startupInvocationCount":0,"trainingInvocationCount":0,"validationInvocationCount":9,"selectionFactoryInvocationCount":0}
              ]
            }
            """);

        LambdaProfileIndex index = LambdaProfileReader.read(profile.toFile());

        assertEquals(15L, index.invocationCount("a/A#m()V#0"));
        assertFalse(index.hasSite("b/B#m()V#0"), "validation-only sites must not leak into admission");
        assertEquals(java.util.Set.of("a/A"), index.hotClasses());
        assertEquals(java.util.Set.of("b/B", "c/C"), index.coldClasses());
    }

    @Test
    void profileV2RejectsAProjectionThatDoesNotMatchItsLifecycleCounts() throws Exception {
        Path profile = write("invalid-v2.json", """
            {
              "schemaVersion":"jmoa-service-profile-v2",
              "loadedClasses":["a/A"],
              "lambdaSites":[
                {"siteKey":"a/A#m()V#0","ownerInternalName":"a/A","factoryInvocationCount":7,"startupInvocationCount":3,"trainingInvocationCount":2,"validationInvocationCount":2,"selectionFactoryInvocationCount":7}
              ]
            }
            """);

        assertThrows(IOException.class, () -> LambdaProfileReader.read(profile.toFile()));
    }

    @Test
    void profileV2RejectsDuplicateValidationOnlySitesBeforeExcludingThem() throws Exception {
        Path profile = write("duplicate-validation-v2.json", """
            {
              "schemaVersion":"jmoa-service-profile-v2",
              "loadedClasses":["a/A"],
              "lambdaSites":[
                {"siteKey":"a/A#m()V#0","ownerInternalName":"a/A","factoryInvocationCount":2,"startupInvocationCount":0,"trainingInvocationCount":0,"validationInvocationCount":2},
                {"siteKey":"a/A#m()V#0","ownerInternalName":"a/A","factoryInvocationCount":3,"startupInvocationCount":0,"trainingInvocationCount":0,"validationInvocationCount":3}
              ]
            }
            """);

        assertThrows(IOException.class, () -> LambdaProfileReader.read(profile.toFile()));
    }

    @Test
    void profileV2RejectsOwnerlessValidationOnlySitesBeforeExcludingThem() throws Exception {
        Path profile = write("ownerless-validation-v2.json", """
            {
              "schemaVersion":"jmoa-service-profile-v2",
              "loadedClasses":["a/A"],
              "lambdaSites":[
                {"siteKey":"a/A#m()V#0","ownerInternalName":"","factoryInvocationCount":2,"startupInvocationCount":0,"trainingInvocationCount":0,"validationInvocationCount":2}
              ]
            }
            """);

        assertThrows(IOException.class, () -> LambdaProfileReader.read(profile.toFile()));
    }

    @Test
    void profileV2RejectsMissingLifecycleArrays() throws Exception {
        Path profile = write("missing-arrays-v2.json", """
            {
              "schemaVersion":"jmoa-service-profile-v2",
              "loadedClasses":[]
            }
            """);

        assertThrows(IOException.class, () -> LambdaProfileReader.read(profile.toFile()));
    }

    @Test
    void profileV2AuditsUnlabelledCountsButDoesNotUseThemForSelection() throws Exception {
        Path profile = write("unlabelled-v2.json", """
            {
              "schemaVersion":"jmoa-service-profile-v2",
              "loadedClasses":["a/A"],
              "lambdaSites":[
                {"siteKey":"a/A#m()V#0","ownerInternalName":"a/A","factoryInvocationCount":9,"startupInvocationCount":2,"trainingInvocationCount":3,"validationInvocationCount":1,"unlabelledInvocationCount":3,"selectionFactoryInvocationCount":5}
              ]
            }
            """);

        LambdaProfileIndex index = LambdaProfileReader.read(profile.toFile());

        assertEquals(5L, index.invocationCount("a/A#m()V#0"));
    }

    @Test
    void profileV3ProjectsOnlyStartupAndTrainingFromAValidFrozenRouteSplit() throws Exception {
        Path profile=write("v3.json",validV3Site(""));

        LambdaProfileIndex index=LambdaProfileReader.read(profile.toFile());

        assertEquals(3L,index.invocationCount("a/A#m()V#0"));
        assertEquals(java.util.Set.of("a/A"),index.hotClasses());
        assertTrue(index.isColdClass("b/B"));
    }

    @Test
    void profileV3FailsClosedOnUnknownSelectionFieldsAndHeldOutLeakage() throws Exception {
        assertThrows(IOException.class,()->LambdaProfileReader.read(
                write("unknown-v3.json",validV3Site(",\"futureSelector\":true")).toFile()));
        String leaked=validV3Site("")
                .replace("\"TRAINING\":{\"TRAINING\":2,\"HELD_OUT\":0,\"UNATTRIBUTED\":0}",
                        "\"TRAINING\":{\"TRAINING\":1,\"HELD_OUT\":1,\"UNATTRIBUTED\":0}")
                .replace("\"VALIDATION\":{\"TRAINING\":0,\"HELD_OUT\":1,\"UNATTRIBUTED\":0}",
                        "\"VALIDATION\":{\"TRAINING\":1,\"HELD_OUT\":0,\"UNATTRIBUTED\":0}");
        assertThrows(IOException.class,()->LambdaProfileReader.read(write("leaked-v3.json",leaked).toFile()));
    }

    @Test
    void profileV3RejectsSampledAllocationPresentedAsExactBytes() throws Exception {
        String dishonest=validV3Site("")
                .replace("\"allocationMeasurement\":\"NONE\",\"allocationBytes\":null",
                        "\"allocationMeasurement\":\"SAMPLED_JFR\",\"allocationBytes\":123");
        assertThrows(IOException.class,()->LambdaProfileReader.read(write("dishonest-v3.json",dishonest).toFile()));
    }

    private static String validV3Site(String rootExtra) {
        return """
            {
              "schemaVersion":"jmoa-service-profile-v3","profileVersion":3,
              "optimizerProjection":{"policy":"STARTUP_PLUS_TRAINING_FACTORY_INVOCATIONS","selectionPhases":["STARTUP","TRAINING"],"excludedPhases":["VALIDATION","UNLABELLED"],"heldOutPhase":"VALIDATION","validationExcludedFromAdmission":true,"countField":"selectionFactoryInvocationCount"},
              "crossFitPartitions":{"frozenBeforeCollection":true,"frozenBeforeRanking":true,"trainingPartitionRoutes":["train"],"heldOutPartitionRoutes":["held"],"requestCountPerRoute":{"train":2,"held":1}},
              "measurementHonesty":{"allocationFieldPolicy":"allocationBytes is null unless allocationMeasurement=EXACT; sampled diagnostics are never presented as exact per-site bytes","diagnosticJoinPolicy":"offline with resolution"},
              "loadedClasses":["a/A","b/B"],"hotClasses":["a/A"],"coldClasses":["b/B"],
              "lambdaSites":[{
                "siteKey":"a/A#m()V#0","ownerInternalName":"a/A",
                "factoryInvocationCount":4,"startupInvocationCount":1,"trainingInvocationCount":2,"validationInvocationCount":1,"unlabelledInvocationCount":0,"selectionFactoryInvocationCount":3,
                "phaseActivity":{
                  "STARTUP":{"invocationCount":1,"firstActivationNanos":1,"lastActivationNanos":1,"threadCardinality":1,"threads":["main"]},
                  "TRAINING":{"invocationCount":2,"firstActivationNanos":2,"lastActivationNanos":3,"threadCardinality":1,"threads":["worker"]},
                  "VALIDATION":{"invocationCount":1,"firstActivationNanos":4,"lastActivationNanos":4,"threadCardinality":1,"threads":["worker"]},
                  "UNLABELLED":{"invocationCount":0,"firstActivationNanos":null,"lastActivationNanos":null,"threadCardinality":0,"threads":[]}},
                "routeActivity":{"routePartition":"MIXED","partitionInvocationCounts":{"TRAINING":2,"HELD_OUT":1,"UNATTRIBUTED":1},"observedRouteInvocationCounts":{"train":2,"held":1},"phasePartitionInvocationCounts":{
                  "STARTUP":{"TRAINING":0,"HELD_OUT":0,"UNATTRIBUTED":1},
                  "TRAINING":{"TRAINING":2,"HELD_OUT":0,"UNATTRIBUTED":0},
                  "VALIDATION":{"TRAINING":0,"HELD_OUT":1,"UNATTRIBUTED":0},
                  "UNLABELLED":{"TRAINING":0,"HELD_OUT":0,"UNATTRIBUTED":0}}},
                "measurementCoverage":{"factoryInvocations":"MEASURED"},"allocationMeasurement":"NONE","allocationBytes":null,
                "selectionDecision":{"disposition":"DEFER","reason":"OFFLINE_STATIC_JOIN_REQUIRED","coverage":"EXACT_FACTORY_COUNTS_ONLY","confidence":"INSUFFICIENT_FOR_ADMISSION"}
              }]%s
            }
            """.formatted(rootExtra);
    }

    private Path write(String name, String json) throws IOException {
        Path path = temp.resolve(name);
        Files.writeString(path, json);
        return path;
    }
}
