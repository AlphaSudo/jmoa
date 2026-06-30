package com.yourorg.jmoa.plugin.runtime;

import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Tier2AdapterNamingStrategyTest {

    @Test
    void producesDeterministicSamePackageName() {
        LambdaMeta meta = new LambdaMeta(
            "site-key",
            "com/example/tier2/Tier2FixtureHost",
            "com/example/tier2",
            "build",
            "()V",
            3,
            "apply",
            "()Ljava/util/function/Function;",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            false,
            false,
            6,
            "com/example/tier2/Tier2FixtureHost",
            "decorate",
            "(Ljava/lang/String;)Ljava/lang/String;",
            "(Ljava/lang/String;)Ljava/lang/String;",
            1L
        );

        String internalName = Tier2AdapterNamingStrategy.internalName(meta);

        assertEquals(internalName, Tier2AdapterNamingStrategy.internalName(meta));
        assertTrue(internalName.startsWith("com/example/tier2/JmoaPkgAdapters$Tier2FixtureHost$S3_"));
    }

    @Test
    void producesSharedPackageSamNameAndStableFieldName() {
        LambdaMeta meta = new LambdaMeta(
            "site-key",
            "com/example/tier2/Tier2FixtureHost",
            "com/example/tier2",
            "build",
            "()V",
            7,
            "apply",
            "()Ljava/util/function/Function;",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            false,
            false,
            6,
            "com/example/tier2/Tier2FixtureHost",
            "decorate",
            "(Ljava/lang/String;)Ljava/lang/String;",
            "(Ljava/lang/String;)Ljava/lang/String;",
            1L
        );

        assertEquals(
            "com/example/tier2/JmoaPkgAdapters$Function",
            Tier2AdapterNamingStrategy.packageSamInternalName(meta)
        );
        assertTrue(Tier2AdapterNamingStrategy.fieldName(meta).startsWith("INSTANCE_S7_"));
    }
}
