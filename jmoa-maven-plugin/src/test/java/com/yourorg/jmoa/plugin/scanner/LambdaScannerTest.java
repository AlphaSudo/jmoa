package com.yourorg.jmoa.plugin.scanner;

import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class LambdaScannerTest {

    // Stateless lambda candidates for compile-time generation:
    private static final Supplier<String> SUPPLIER = () -> "hello";
    private static final Function<String, Integer> LEN_FN = String::length;

    @Test
    public void testScanningOwnClass() throws Exception {
        String resourcePath = LambdaScannerTest.class.getName().replace('.', '/') + ".class";
        URL resource = LambdaScannerTest.class.getClassLoader().getResource(resourcePath);
        assertNotNull(resource, "Could not find own class resource");

        File classFile = new File(resource.toURI());
        assertTrue(classFile.exists());

        List<LambdaSite> sites = LambdaScanner.scanClassFile(classFile);
        
        // Assert that we found stateless lambdas
        assertFalse(sites.isEmpty(), "Should have scanned at least one stateless lambda");

        boolean foundLength = false;
        boolean foundSupplier = false;
        for (LambdaSite site : sites) {
            if ("length".equals(site.implHandle().getName())) {
                foundLength = true;
            }
            if (site.implHandle().getName().startsWith("lambda$static$")) {
                foundSupplier = true;
            }
        }
        assertTrue(foundLength || foundSupplier, "Should find length method reference or supplier lambda");
    }

    @Test
    public void testLambdaSiteCanProduceStableMetadata() throws Exception {
        String resourcePath = LambdaScannerTest.class.getName().replace('.', '/') + ".class";
        URL resource = LambdaScannerTest.class.getClassLoader().getResource(resourcePath);
        assertNotNull(resource, "Could not find own class resource");

        File classFile = new File(resource.toURI());
        List<LambdaSite> firstScan = LambdaScanner.scanClassFile(classFile);
        List<LambdaSite> secondScan = LambdaScanner.scanClassFile(classFile);

        assertFalse(firstScan.isEmpty(), "Expected at least one lambda site");
        assertEquals(firstScan.size(), secondScan.size(), "Repeated scans should find the same number of sites");

        for (int i = 0; i < firstScan.size(); i++) {
            LambdaMeta firstMeta = firstScan.get(i).toMeta();
            LambdaMeta secondMeta = secondScan.get(i).toMeta();

            assertEquals(firstMeta.siteKey(), secondMeta.siteKey(), "Site key should be deterministic");
            assertEquals(firstMeta.ownerInternalName(), secondMeta.ownerInternalName());
            assertEquals(firstMeta.enclosingMethodName(), secondMeta.enclosingMethodName());
            assertEquals(firstMeta.enclosingMethodDesc(), secondMeta.enclosingMethodDesc());
            assertEquals(firstMeta.siteOrdinalInMethod(), secondMeta.siteOrdinalInMethod());
            assertEquals(firstMeta.implOwner(), secondMeta.implOwner());
            assertEquals(firstMeta.implName(), secondMeta.implName());
            assertEquals(firstMeta.implDesc(), secondMeta.implDesc());
            assertEquals(firstMeta.duplicateGroupKey(), secondMeta.duplicateGroupKey());
        }
    }
}
